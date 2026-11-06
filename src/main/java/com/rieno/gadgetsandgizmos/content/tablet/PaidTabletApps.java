package com.rieno.gadgetsandgizmos.content.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.lib.text.UserMessageException;
import com.rieno.gadgetsandgizmos.neoforge.network.CTPayloadTransfers;
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
    public static final TabletAppDefinition CCTV = app("cctv", "createthrusters.text.cctv", "createthrusters.text.watch_and_control_bound_cameras", 0xFF5BAAA3,
            tab("live", "createthrusters.gyroscope_link.mode.live", "refresh", "pair", "select_camera", "control", "forget", "rename"));
    public static final TabletAppDefinition DIGISABLE = app("digisable", "createthrusters.text.digisable", "createthrusters.text.view_locate_store_and_extract_sublevels", 0xFF5EBAF2,
            tab("archives", "createthrusters.text.stored", "refresh", "preview", "prepare_extract", "extract", "store"),
            tab("sublevels", "createthrusters.text.sublevels", "refresh", "preview", "store", "store_selected", "teleport", "delete"),
            tab("schematics", "createthrusters.text.schematics", "refresh", "export", "schematic_preview", "materials", "prepare_build", "build", "pair_network", "cancel_build"));
    public static final TabletAppDefinition MANIFEST = app("manifest", "createthrusters.text.manifest", "createthrusters.text.detailed_container_cargo_and_resources", 0xFFDCB75F,
            tab("cargo", "createthrusters.smart_container_manifest.contents", "refresh", "inspect", "detach"),
            tab("resources", "createthrusters.smart_container_manifest.resources", "refresh", "inspect", "detach"),
            tab("network", "createthrusters.smart_container_manifest.network", "refresh", "inspect", "detach"));
    public static final TabletAppDefinition BLOCKMATES = app("blockmates", "createthrusters.text.blockmates", "createthrusters.text.manage_acc_workers_and_request_deliveries_or_crafting", 0xFF80C978,
            tab("workers", "createthrusters.text.workers", "refresh", "pair", "worker_enable", "cancel"),
            tab("request", "createthrusters.text.request", "refresh", "fluids", "request", "request_selected",
                    "request_fluid", "request_fluid_selected"),
            tab("inventory", "createthrusters.text.inventory", "refresh", "request"));

    private PaidTabletApps(){}

    public static void register(){
        TabletAppRegistry.register(DIGISABLE, (ctx, action) -> execute(ctx, action, DigisableApp::execute));
        TabletAppRegistry.register(MANIFEST, (ctx, action) -> execute(ctx, action, ManifestApp::execute));
        TabletAppRegistry.register(BLOCKMATES, (ctx, action) -> execute(ctx, action, BlockmatesApp::execute));
        TabletAppRegistry.register(CCTV, (ctx, action) -> execute(ctx, action, CctvApp::execute));
        com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationRuntime.setTransferLimit(
                () -> com.rieno.gadgetsandgizmos.config.TabletAppsServerConfig.TRANSFER.get());
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
                throw new UserMessageException(Component.translatable("createthrusters.text.purchase_this_application_in_the_app_store_first"));
            }
            String message = handler.execute(ctx, action);
            if(!message.isBlank()) ctx.player().displayClientMessage(Component.literal(message), true);
            return TabletActionHandler.Result.success(Component.literal(message));
        }catch(Exception err){
            Component message = LocalizedText.message(err);
            ctx.player().displayClientMessage(message, true);
            CompoundTag data = CCTV.id().equals(action.appId()) && ctx.sourceTabletId() != null
                    ? CctvApp.snapshot(ctx) : new CompoundTag();
            if(DIGISABLE.id().equals(action.appId()) && ctx.sourceTabletId() != null) data = DigisableSchematics.errorSnapshot(ctx);
            LocalizedText.put(data, "Error", message);
            if(MANIFEST.id().equals(action.appId()) && ctx.sourceTabletId() != null){
                data.putBoolean("Bound", com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.selectedBinding(
                        ctx.player().server, ctx.sourceTabletId(), MANIFEST.id()) != null);
                LocalizedText.put(data, "Help", Component.translatable("createthrusters.text.choose_another_container_or_stop_inspecting_the_current_target"));
            }
            snapshot(ctx, action.appId(), data);
            return TabletActionHandler.Result.failure(message);
        }
    }

    public static void snapshot(TabletActionContext ctx, net.minecraft.resources.ResourceLocation app, CompoundTag data){
        CTPayloadTransfers.CHANNEL.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(app, ctx, data));
    }

    private static TabletAppDefinition app(String id, String title, String description, int color, TabletTabDefinition... tabs){
        return new TabletAppDefinition(DiagnosticTabletData.appId(id), Component.translatable(title), Component.translatable(description), color, List.of(tabs));
    }

    private static TabletTabDefinition tab(String id, String title, String... actions){
        return new TabletTabDefinition(id, Component.translatable(title), List.of(actions), Set.of());
    }

    @FunctionalInterface
    private interface Handler{ String execute(TabletActionContext ctx, TabletAction action) throws Exception; }
}
