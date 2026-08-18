package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.content.NotationDraftStore;
import com.rieno.gadgetsandgizmos.content.PortableAdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.PublicGraphShareService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

// Apply one validated ACC graph edit against the revision the player actually opened
public record AdvancedContraptionControllerGraphPayload(MenuConfigTarget target, String action, int expectedRevision,
                                                        CompoundTag graph, String argument, long requestId)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedContraptionControllerGraphPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_contraption_controller_graph"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AdvancedContraptionControllerGraphPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(AdvancedContraptionControllerGraphPayload::encode,
                    AdvancedContraptionControllerGraphPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the advanced contraption controller graph
    public static void handle(AdvancedContraptionControllerGraphPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // ------------------------------------TARGET RESOLUTION------------------------------------
            boolean closingSave = "save_apply_close".equals(payload.action());
            AnalogueContraptionControllerBlockEntity resolved = closingSave
                    ? MenuBackedBlockEntityResolver.resolveOpenMenu(
                    context, payload.target(), AdvancedContraptionControllerMenu.class,
                    AnalogueContraptionControllerBlockEntity.class)
                    : MenuBackedBlockEntityResolver.resolve(
                    context, payload.target(), AdvancedContraptionControllerMenu.class,
                    AnalogueContraptionControllerBlockEntity.class);
            if (!(resolved instanceof AdvancedContraptionControllerBlockEntity controller)) {
                sendGraphActionResult(context, payload.target(), payload.requestId(), false,
                        "Controller is No Longer Available", -1, isSaveAction(payload.action()), false, List.of());
                return;
            }
            boolean persistPortable = false;
            // ------------------------------------DRAFTS / VALIDATION------------------------------------
            switch (payload.action()) {
                case "save" -> {
                    boolean saved = controller.saveDraft(
                            AdvancedGraphDocument.fromTag(payload.graph()), payload.expectedRevision());
                    persistPortable = saved;
                    sendGraphActionResult(context, payload.target(), payload.requestId(), saved,
                            saved ? "Graph Saved" : "Failed to Save Graph",
                            controller.getDraftGraph().revision(), true, saved, List.of());
                }
                case "validate" -> {
                    AdvancedGraphValidator.Result res = AdvancedGraphValidator.validate(
                            AdvancedGraphDocument.fromTag(payload.graph()),
                            controller.isGogglesTrackerAvailable(),
                            controller.isControllerTrackerAvailable());
                    sendGraphActionResult(context, payload.target(), payload.requestId(), res.valid(),
                            res.valid() ? "Graph Valid" : "Graph Validation Failed",
                            controller.getDraftGraph().revision(), false, false, res.diagnostics());
                }
                case "apply" -> {
                    boolean applied = controller.applyDraft();
                    persistPortable = applied;
                    sendGraphActionResult(context, payload.target(), payload.requestId(), applied,
                            applied ? "Graph Applied" : "Failed to Apply Graph",
                            controller.getDraftGraph().revision(), false, false,
                            controller.getGraphDiagnostics());
                }
                case "save_apply" -> {
                    boolean saved = controller.saveDraft(
                            AdvancedGraphDocument.fromTag(payload.graph()), payload.expectedRevision());
                    boolean applied = saved && controller.applyDraft();
                    persistPortable = saved;
                    String msg = !saved ? "Failed to Save Graph"
                            : applied ? "Graph Saved" : "Graph Saved but Failed to Apply";
                    sendGraphActionResult(context, payload.target(), payload.requestId(), saved && applied, msg,
                            controller.getDraftGraph().revision(), true, saved,
                            saved && !applied ? controller.getGraphDiagnostics() : List.of());
                }
                case "save_apply_close" -> {
                    AdvancedGraphDocument closingGraph = AdvancedGraphDocument.fromTag(payload.graph());
                    int currentRevision = controller.getDraftGraph().revision();
                    boolean saved = controller.saveDraft(closingGraph, currentRevision);
                    boolean applied = saved && controller.applyDraft();
                    persistPortable = saved;
                    String msg = !saved ? "Failed to Save Graph"
                            : applied ? "Graph Saved" : "Graph Saved but Failed to Apply";
                    sendGraphActionResult(context, payload.target(), payload.requestId(),
                            saved && applied, msg, controller.getDraftGraph().revision(),
                            true, saved,
                            saved && !applied ? controller.getGraphDiagnostics() : List.of());
                }
                case "apply_save" -> {
                    boolean saved = controller.saveDraft(
                            AdvancedGraphDocument.fromTag(payload.graph()), payload.expectedRevision());
                    boolean applied = saved && controller.applyDraft();
                    persistPortable = saved;
                    String msg = !saved ? "Failed to Save Graph Before Applying"
                            : applied ? "Graph Applied" : "Failed to Apply Graph";
                    sendGraphActionResult(context, payload.target(), payload.requestId(), saved && applied, msg,
                            controller.getDraftGraph().revision(), true, saved,
                            saved && !applied ? controller.getGraphDiagnostics() : List.of());
                }
                // ------------------------------------HISTORY / RUNTIME------------------------------------
                case "history" -> sendGraphHistory(context, payload.target(), controller, "", new CompoundTag());
                case "rollback" -> {
                    int idx;
                    try {
                        idx = Integer.parseInt(payload.argument());
                    } catch (NumberFormatException ignored) {
                        idx = -1;
                    }
                    AdvancedContraptionControllerBlockEntity.RollbackResult res =
                            controller.rollbackGraphVersion(idx, payload.expectedRevision());
                    persistPortable = res.restored();
                    sendGraphHistory(context, payload.target(), controller, res.message(),
                            res.restored() ? res.graph().toTag() : new CompoundTag());
                    sendGraphActionResult(context, payload.target(), payload.requestId(),
                            res.restored() && res.applied(), res.message(),
                            controller.getDraftGraph().revision(), res.restored(), res.restored(),
                            res.restored() && !res.applied()
                                    ? controller.getGraphDiagnostics() : List.of());
                }
                case "template" -> {
                    controller.selectTemplate(payload.argument());
                    persistPortable = true;
                }
                case "reset_outputs" -> {
                    controller.resetAllChannels();
                    persistPortable = true;
                }
                case "trigger" -> controller.triggerGraphEvent(payload.argument(),
                        context.player() instanceof ServerPlayer player ? player.getUUID() : null);
                // ------------------------------------SHARING------------------------------------
                case "shared_graphs" -> sendSharedGraphs(context, payload.target(), "", "",
                        new CompoundTag());
                case "public_share_status" -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PublicGraphShareService.availability().thenAccept(availability ->
                                player.server.execute(() -> sendPublicShare(player, payload.target(),
                                        availability.available(), false, availability.available(),
                                        availability.message(), "")));
                    }
                }
                case "upload_public_share" -> {
                    if (context.player() instanceof ServerPlayer player) {
                        String graphName = payload.argument() == null || payload.argument().isBlank()
                                ? "Contraption Graph" : payload.argument().trim();
                        String manifest = ControllerManifestStore.sharedGraphJson(
                                graphName, AdvancedGraphDocument.fromTag(payload.graph()));
                        PublicGraphShareService.upload(manifest).thenAccept(res ->
                                player.server.execute(() -> sendPublicShare(player, payload.target(),
                                        res.shared(), true, res.shared(),
                                        res.message(), res.url())));
                    }
                }
                case "share_graph" -> {
                    AdvancedContraptionControllerBlockEntity.SharedGraphShareResult res =
                            controller.shareGraphToInsertedLinker(
                                    AdvancedGraphDocument.fromTag(payload.graph()), payload.argument(),
                                    ControllerManifestStore.SharedGraphSaveMode.REJECT);
                    sendSharedGraphs(context, payload.target(), res.message(),
                            res.conflict() ? payload.argument() : "", new CompoundTag());
                    persistPortable = res.saved();
                }
                case "share_graph_overwrite" -> {
                    AdvancedContraptionControllerBlockEntity.SharedGraphShareResult res =
                            controller.shareGraphToInsertedLinker(
                                    AdvancedGraphDocument.fromTag(payload.graph()), payload.argument(),
                                    ControllerManifestStore.SharedGraphSaveMode.OVERWRITE);
                    sendSharedGraphs(context, payload.target(), res.message(), "", new CompoundTag());
                    persistPortable = res.saved();
                }
                case "share_graph_increment" -> {
                    AdvancedContraptionControllerBlockEntity.SharedGraphShareResult res =
                            controller.shareGraphToInsertedLinker(
                                    AdvancedGraphDocument.fromTag(payload.graph()), payload.argument(),
                                    ControllerManifestStore.SharedGraphSaveMode.INCREMENT);
                    sendSharedGraphs(context, payload.target(), res.message(), "", new CompoundTag());
                    persistPortable = res.saved();
                }
                case "load_shared_graph" -> {
                    String msg = controller.loadSharedGraphIntoInsertedLinker(payload.argument());
                    CompoundTag loadedGraph = msg.startsWith("Loaded shared graph")
                            ? controller.getDraftGraph().toTag() : new CompoundTag();
                    sendSharedGraphs(context, payload.target(), msg, "", loadedGraph);
                    persistPortable = true;
                }
                // ------------------------------------FUNCTION NOTATION------------------------------------
                case "notation_open" -> sendNotationData(context, payload.target(), controller,
                        "open", true, "", new CompoundTag(), true);
                case "notation_save" -> {
                    NotationDraftStore.SaveResult res = NotationDraftStore.save(
                            controller.getLevel(), controller.notationDraftOwnerId(),
                            payload.argument(), payload.graph());
                    CompoundTag savedDraft = res.saved()
                            ? NotationDraftStore.load(controller.getLevel(),
                            controller.notationDraftOwnerId(), res.id())
                            : new CompoundTag();
                    sendNotationData(context, payload.target(), controller, "save",
                            res.saved(), res.message(), savedDraft, false);
                }
                case "notation_load" -> {
                    CompoundTag loaded = NotationDraftStore.load(controller.getLevel(),
                            controller.notationDraftOwnerId(), payload.argument());
                    boolean found = !loaded.isEmpty();
                    sendNotationData(context, payload.target(), controller, "load", found,
                            found ? "Loaded function draft" : "Function draft was not found",
                            loaded, false);
                }
                case "notation_delete" -> {
                    boolean deleted = NotationDraftStore.delete(controller.getLevel(),
                            controller.notationDraftOwnerId(), payload.argument());
                    sendNotationData(context, payload.target(), controller, "delete", deleted,
                            deleted ? "Deleted function draft" : "Could not delete function draft",
                            new CompoundTag(), false);
                }
                default -> {
                }
            }
            // ------------------------------------PORTABLE STATE------------------------------------
            if (persistPortable && context.player().containerMenu instanceof PortableAdvancedContraptionControllerMenu menu) {
                menu.savePortableState();
            }
        });
    }

    // Send the shared graphs
    private static void sendSharedGraphs(IPayloadContext ctx, MenuConfigTarget target, String msg,
                                         String conflictingName, CompoundTag graph) {
        if (!(ctx.player() instanceof ServerPlayer player) || target == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new AdvancedControllerSharedGraphsPayload(
                target.pos(),
                target.subLevelId(),
                ControllerManifestStore.listSharedGraphs(),
                msg,
                conflictingName,
                graph));
    }

    // Send the notation data
    private static void sendNotationData(IPayloadContext ctx, MenuConfigTarget target,
                                         AdvancedContraptionControllerBlockEntity controller,
                                         String action, boolean success, String msg,
                                         CompoundTag draft, boolean includeScmModel) {
        if (!(ctx.player() instanceof ServerPlayer player) || target == null || controller == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new FunctionPlotterDataPayload(
                target.pos(), target.subLevelId(), action, success, msg,
                NotationDraftStore.list(controller.getLevel(), controller.notationDraftOwnerId()),
                draft, includeScmModel
                ? controller.getNotationScmModel().toTag() : new CompoundTag()));
    }

    // Send the public share
    private static void sendPublicShare(ServerPlayer player, MenuConfigTarget target,
                                        boolean available, boolean completed, boolean success,
                                        String msg, String url) {
        if (player == null || target == null) return;
        PacketDistributor.sendToPlayer(player, new AdvancedControllerPublicSharePayload(
                target.pos(), target.subLevelId(), available, completed, success, msg, url));
    }

    // Send the graph action result
    private static void sendGraphActionResult(IPayloadContext ctx, MenuConfigTarget target, long requestId,
                                              boolean success, String msg, int serverRevision,
                                              boolean saveAttempted, boolean graphSaved,
                                              List<AdvancedGraphValidator.Diagnostic> diagnostics) {
        if (requestId <= 0L || !(ctx.player() instanceof ServerPlayer player) || target == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new AdvancedControllerGraphActionResultPayload(
                target.pos(), target.subLevelId(), requestId, success, msg,
                serverRevision, saveAttempted, graphSaved, diagnostics));
    }

    // Send the graph history
    private static void sendGraphHistory(IPayloadContext ctx, MenuConfigTarget target,
                                         AdvancedContraptionControllerBlockEntity controller,
                                         String msg, CompoundTag restoredGraph) {
        if (!(ctx.player() instanceof ServerPlayer player) || target == null || controller == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new AdvancedControllerGraphHistoryPayload(
                target.pos(), target.subLevelId(), controller.getGraphVersions(), msg, restoredGraph));
    }

    // Check if this is a save action
    private static boolean isSaveAction(String action) {
        return "save".equals(action)
                || "save_apply".equals(action)
                || "save_apply_close".equals(action)
                || "apply_save".equals(action);
    }

    // Encode the advanced contraption controller graph
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedContraptionControllerGraphPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(payload.action());
        buffer.writeVarInt(payload.expectedRevision());
        GraphNbtPayloadCodec.write(buffer, payload.graph());
        buffer.writeUtf(payload.argument());
        buffer.writeLong(payload.requestId());
    }

    // Decode the advanced contraption controller graph
    private static AdvancedContraptionControllerGraphPayload decode(RegistryFriendlyByteBuf buffer) {
        MenuConfigTarget target = MenuConfigTarget.STREAM_CODEC.decode(buffer);
        String action = buffer.readUtf();
        int expectedRevision = buffer.readVarInt();
        CompoundTag graph = GraphNbtPayloadCodec.read(buffer);
        return new AdvancedContraptionControllerGraphPayload(
                target,
                action,
                expectedRevision,
                graph == null ? new CompoundTag() : graph,
                buffer.readUtf(),
                buffer.readLong());
    }
}
