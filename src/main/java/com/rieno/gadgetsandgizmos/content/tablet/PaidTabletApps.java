package com.rieno.gadgetsandgizmos.content.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletTabDefinition;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

// Register independent paid app runtimes through the tablet API
public final class PaidTabletApps{
    public static final TabletAppDefinition CCTV = app("cctv", "CCTV", "Watch and control bound cameras", 0xFF5BAAA3,
            tab("live", "Live", "refresh", "pair", "select_camera", "control", "forget", "rename"));
    public static final TabletAppDefinition DIGISABLE = app("digisable", "Digisable", "View, locate, store and extract sublevels", 0xFF5EBAF2,
            tab("archives", "Stored", "refresh", "preview", "prepare_extract", "extract", "store", "export"),
            tab("sublevels", "Sublevels", "refresh", "preview", "store", "teleport", "delete"),
            tab("schematics", "Schematics", "refresh", "schematic_preview", "materials", "prepare_build", "build", "pair_network", "cancel_build"));
    public static final TabletAppDefinition MANIFEST = app("manifest", "Manifest", "Detailed cargo, Create filters and automatic stocking", 0xFFDCB75F,
            tab("cargo", "Cargo", "refresh", "inspect", "detach", "filter", "lock", "push", "pull"),
            tab("resources", "Resources", "refresh", "inspect", "detach"),
            tab("stock", "Stock", "refresh", "inspect", "detach", "stock"));
    public static final TabletAppDefinition BLOCKMATES = app("blockmates", "Blockmates", "Manage ACC workers and request deliveries or crafting", 0xFF80C978,
            tab("workers", "Workers", "refresh", "pair", "worker_enable", "cancel"),
            tab("request", "Request", "refresh", "fluids", "request", "request_selected",
                    "request_fluid", "request_fluid_selected"),
            tab("inventory", "Inventory", "refresh", "request"));

    private PaidTabletApps(){}

    public static void register(){
        TabletAppRegistry.register(DIGISABLE, (ctx, action) -> execute(ctx, action, DigisableApp::execute));
        TabletAppRegistry.register(MANIFEST, (ctx, action) -> execute(ctx, action, ManifestApp::execute));
        TabletAppRegistry.register(BLOCKMATES, (ctx, action) -> execute(ctx, action, BlockmatesApp::execute));
        TabletAppRegistry.register(CCTV, (ctx, action) -> execute(ctx, action, CctvApp::execute));
        ManifestContainerPolicies.register();
    }

    public static boolean canonical(TabletAppDefinition app){
        return app == DIGISABLE || app == MANIFEST || app == BLOCKMATES || app == CCTV;
    }

    public static boolean ownsId(net.minecraft.resources.ResourceLocation id){
        return DIGISABLE.id().equals(id) || MANIFEST.id().equals(id) || BLOCKMATES.id().equals(id) || CCTV.id().equals(id);
    }

    public static boolean standalone(net.minecraft.resources.ResourceLocation id){
        return DIGISABLE.id().equals(id) || MANIFEST.id().equals(id) || CCTV.id().equals(id);
    }

    public static boolean readerEnabled(net.minecraft.resources.ResourceLocation id){
        return DIGISABLE.id().equals(id) || BLOCKMATES.id().equals(id) || CCTV.id().equals(id);
    }

    public static boolean inspectManifest(net.minecraft.server.level.ServerPlayer player, java.util.UUID tabletId,
                                          com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.Binding binding){
        return ManifestApp.inspect(player, tabletId, binding);
    }

    // Keep failures visible in the app and in the player's action bar
    private static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action, Handler handler){
        try{
            var definition = TabletAppRegistry.definition(action.appId());
            if(ctx.sourceTabletId() == null || !com.rieno.gadgetsandgizmos.lib.tablet.TabletAppAccess.canUse(definition,
                    com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPurchaseScope.PLAYER, ctx.player().getUUID(), ctx.sourceTabletId())){
                throw new IllegalArgumentException("Purchase this application in the App Store first");
            }
            String message = handler.execute(ctx, action);
            if(!message.isBlank()) ctx.player().displayClientMessage(Component.literal(message), true);
            return TabletActionHandler.Result.success(Component.literal(message));
        }catch(Exception err){
            String message = err.getMessage() == null ? "Application operation failed" : err.getMessage();
            ctx.player().displayClientMessage(Component.literal(message), true);
            CompoundTag data = CCTV.id().equals(action.appId()) && ctx.sourceTabletId() != null
                    ? CctvApp.snapshot(ctx) : new CompoundTag();
            if(DIGISABLE.id().equals(action.appId()) && ctx.sourceTabletId() != null) data = DigisableSchematics.errorSnapshot(ctx);
            data.putString("Error", message);
            if(MANIFEST.id().equals(action.appId()) && ctx.sourceTabletId() != null){
                data.putBoolean("Bound", com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.selectedBinding(
                        ctx.player().server, ctx.sourceTabletId(), MANIFEST.id()) != null);
                data.putString("Help", "Choose another container or stop inspecting the current target");
            }
            snapshot(ctx, action.appId(), data);
            return TabletActionHandler.Result.failure(Component.literal(message));
        }
    }

    public static void snapshot(TabletActionContext ctx, net.minecraft.resources.ResourceLocation app, CompoundTag data){
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(app, ctx, data));
    }

    private static TabletAppDefinition app(String id, String title, String description, int color, TabletTabDefinition... tabs){
        return new TabletAppDefinition(DiagnosticTabletData.appId(id), Component.literal(title), Component.literal(description), color, List.of(tabs));
    }

    private static TabletTabDefinition tab(String id, String title, String... actions){
        return new TabletTabDefinition(id, Component.literal(title), List.of(actions), Set.of());
    }

    @FunctionalInterface
    private interface Handler{ String execute(TabletActionContext ctx, TabletAction action) throws Exception; }
}
