package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import com.rieno.gadgetsandgizmos.lib.media.MediaSession;
import com.rieno.gadgetsandgizmos.lib.media.MusicDiscCatalog;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Exercise disc registries and tablet persistence on a real dedicated server
@GameTestHolder("createthrusters_physics_regressions")
@PrefixGameTestTemplate(false)
public final class DiagnosticTabletMediaGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", batch = "tablet_media")
    public static void registeredDiscsAndFreeAppResolve(GameTestHelper helper){
        var catalog = MusicDiscCatalog.tracks(helper.getLevel().registryAccess());
        var cat = ResourceLocation.withDefaultNamespace("music_disc_cat");
        helper.assertTrue(catalog.stream().anyMatch(track -> track.itemId().equals(cat) && track.durationTicks() > 0),
                "Registered music discs were not discovered");
        var app = com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry.definition(DiagnosticTabletMediaApp.ID);
        helper.assertTrue(app != null && DiagnosticTabletAppStoreConfig.price(app).count() == 0, "Media is not free");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tablet_media")
    public static void playbackSurvivesPlacementSavingAndPickup(GameTestHelper helper){
        var level = helper.getLevel();
        var local = new BlockPos(2, 2, 2);
        var pos = helper.absolutePos(local);
        var block = CTBlocks.DIAGNOSTIC_TABLET.get();
        ItemStack stack = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        var id = DiagnosticTabletData.ensureTabletId(stack);
        var playback = new MediaPlaybackState(ResourceLocation.withDefaultNamespace("music_disc_cat"), true,
                25, level.getGameTime(), 2000, 4);
        DiagnosticTabletMediaApp.writePlayback(stack, playback);
        helper.setBlock(local, block.defaultBlockState());
        block.setPlacedBy(level, pos, level.getBlockState(pos), null, stack);
        var tablet = (DiagnosticTabletBlockEntity)helper.getBlockEntity(local);
        helper.assertTrue(tablet.playback().equals(playback), "Placement lost playback");
        var saved = tablet.saveWithFullMetadata(level.registryAccess());
        tablet.setPlayback(MediaPlaybackState.EMPTY);
        tablet.loadWithComponents(saved, level.registryAccess());
        helper.assertTrue(tablet.playback().equals(playback) && id.equals(tablet.state().tabletId()), "Reload lost tablet playback");
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        var cloned = block.getCloneItemStack(level.getBlockState(pos), new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false),
                level, pos, player);
        helper.assertTrue(DiagnosticTabletMediaApp.readPlayback(cloned).equals(playback), "Pickup lost playback");
        DiagnosticTabletData.write(cloned, tablet.state().withApp(DiagnosticTabletData.appId("settings"), "tablet"));
        helper.assertTrue(DiagnosticTabletMediaApp.readPlayback(cloned).equals(playback), "Changing apps erased playback");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tablet_media")
    public static void placedPlaylistAdvancesThroughDatabase(GameTestHelper helper){
        var level = helper.getLevel();
        var local = new BlockPos(2, 2, 2);
        helper.setBlock(local, CTBlocks.DIAGNOSTIC_TABLET.get().defaultBlockState());
        var tablet = (DiagnosticTabletBlockEntity)helper.getBlockEntity(local);
        var id = java.util.UUID.randomUUID();
        tablet.setState(tablet.state().withTabletId(id));
        var catalog = MusicDiscCatalog.tracks(level.registryAccess());
        var a = catalog.get(0).itemId();
        var b = catalog.get(1).itemId();
        java.util.function.ToIntFunction<ResourceLocation> lengths = disc -> MusicDiscCatalog.resolve(level.registryAccess(), disc).durationTicks();
        var session = new MediaSession();
        var playlist = session.create("Ship music");
        session.toggle(playlist, a, 0, lengths);
        session.toggle(playlist, b, 0, lengths);
        long started = level.getGameTime() - lengths.applyAsInt(a);
        session.select(playlist, started, lengths);
        var db = DiagnosticTabletDatabase.forServer(level.getServer());
        db.updateApp(id, DiagnosticTabletMediaApp.ID, prev -> session.toTag());
        tablet.setPlayback(session.playback());
        DiagnosticTabletMediaApp.tick(tablet);
        var stored = MediaSession.fromTag(db.app(id, DiagnosticTabletMediaApp.ID));
        helper.assertTrue(tablet.playback().discId().equals(b) && stored.playback().equals(tablet.playback()),
                "Placed playlist did not advance and persist its playback");
        helper.succeed();
    }
}
