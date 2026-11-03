package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import com.rieno.gadgetsandgizmos.lib.media.MediaSession;
import com.rieno.gadgetsandgizmos.lib.media.MusicDiscCatalog;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

// Store tablet playlists in the app database and mirror only the playback timeline to clients
public final class DiagnosticTabletMediaApp{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ResourceLocation ID = DiagnosticTabletData.appId("media");
    private static final String ITEM_KEY = "createthrusters:media";
    private DiagnosticTabletMediaApp(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static MediaPlaybackState readPlayback(ItemStack stack){
        return MediaPlaybackState.fromTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getCompound(ITEM_KEY));
    }

    public static void writePlayback(ItemStack stack, MediaPlaybackState state){
        if(state.equals(readPlayback(stack))) return;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(ITEM_KEY, state.toTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action){
        UUID id = ctx.sourceTabletId() == null ? DiagnosticTabletData.ensureTabletId(ctx.tablet()) : ctx.sourceTabletId();
        var db = DiagnosticTabletDatabase.forServer(ctx.player().server);
        var session = MediaSession.fromTag(db.app(id, ID));
        var lengths = MusicDiscCatalog.lengths(ctx.player().registryAccess());
        long now = ctx.player().level().getGameTime();
        String val = action.arguments().getOrDefault("value", "");
        String error = "";
        try{
            session.advance(now, lengths);
            switch(action.actionId()){
                case "refresh", "select" -> {}
                case "play" -> session.play(ResourceLocation.tryParse(val), now, lengths);
                case "pause" -> session.togglePlayback(now, catalog(ctx), lengths);
                case "next" -> session.step(1, now, catalog(ctx), lengths);
                case "previous" -> session.step(-1, now, catalog(ctx), lengths);
                case "playlist_create" -> session.create(val);
                case "playlist_select" -> session.select(val.isEmpty() ? null : UUID.fromString(val), now, lengths);
                case "playlist_delete" -> session.delete(UUID.fromString(val), now);
                case "playlist_rename", "playlist_toggle" -> {
                    int sep = val.indexOf('|');
                    if(sep < 0) throw new IllegalArgumentException("Playlist unavailable");
                    UUID playlist = UUID.fromString(val.substring(0, sep));
                    String data = val.substring(sep + 1);
                    if(action.actionId().equals("playlist_rename")) session.rename(playlist, data);
                    else{
                        var disc = ResourceLocation.tryParse(data);
                        if(disc == null) throw new IllegalArgumentException("Music disc unavailable");
                        session.toggle(playlist, disc, now, lengths);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown Media action");
            }
        }catch(IllegalArgumentException err){ error = err.getMessage(); }
        var data = session.toTag();
        if(!data.equals(db.app(id, ID))) db.updateApp(id, ID, prev -> data);
        publish(ctx, session.playback());
        var snapshot = data.copy();
        if(!error.isEmpty()) snapshot.putString("Error", error);
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(ID, ctx, snapshot));
        return error.isEmpty() ? TabletActionHandler.Result.success(Component.empty())
                : TabletActionHandler.Result.failure(Component.literal(error));
    }

    // Only consult app storage when the current disc reaches its end
    public static void tick(DiagnosticTabletBlockEntity tablet){
        if(!(tablet.getLevel() instanceof ServerLevel level) || tablet.state().tabletId() == null) return;
        if(!tablet.playback().finished(level.getGameTime())) return;
        tablet.setPlayback(advance(level, tablet.state().tabletId()));
    }

    public static void tick(ItemStack stack, ServerPlayer player){
        if(!readPlayback(stack).finished(player.level().getGameTime())) return;
        UUID id = DiagnosticTabletData.read(stack).tabletId();
        if(id != null) writePlayback(stack, advance(player.serverLevel(), id));
    }

    private static MediaPlaybackState advance(ServerLevel level, UUID id){
        var db = DiagnosticTabletDatabase.forServer(level.getServer());
        var session = MediaSession.fromTag(db.app(id, ID));
        if(session.advance(level.getGameTime(), MusicDiscCatalog.lengths(level.registryAccess()))){
            var data = session.toTag();
            db.updateApp(id, ID, prev -> data);
        }
        return session.playback();
    }

    private static void publish(TabletActionContext ctx, MediaPlaybackState state){
        if(ctx.placedSource()){
            if(SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), ctx.sourceSubLevelId(),
                    ctx.sourceBlockPos()) instanceof DiagnosticTabletBlockEntity tablet) tablet.setPlayback(state);
        }else{
            writePlayback(ctx.tablet(), state);
            ctx.player().getInventory().setChanged();
        }
    }

    private static java.util.List<ResourceLocation> catalog(TabletActionContext ctx){
        return MusicDiscCatalog.tracks(ctx.player().registryAccess()).stream().map(MusicDiscCatalog.Track::itemId).toList();
    }

}
