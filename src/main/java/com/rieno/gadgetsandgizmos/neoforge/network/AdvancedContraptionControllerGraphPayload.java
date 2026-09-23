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
import com.rieno.gadgetsandgizmos.content.ScmConfigurationProfile;
import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import com.rieno.gadgetsandgizmos.content.PortableAdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.PublicGraphShareService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
                    boolean saved = saveControllerDraft(controller, payload, payload.expectedRevision());
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
                    boolean saved = saveControllerDraft(controller, payload, payload.expectedRevision());
                    boolean applied = saved && controller.applyDraft();
                    persistPortable = saved;
                    String msg = !saved ? "Failed to Save Graph"
                            : applied ? "Graph Saved" : "Graph Saved but Failed to Apply";
                    sendGraphActionResult(context, payload.target(), payload.requestId(), saved && applied, msg,
                            controller.getDraftGraph().revision(), true, saved,
                            saved && !applied ? controller.getGraphDiagnostics() : List.of());
                }
                case "save_apply_close" -> {
                    int currentRevision = controller.getDraftGraph().revision();
                    boolean saved = saveControllerDraft(controller, payload, currentRevision);
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
                    boolean saved = saveControllerDraft(controller, payload, payload.expectedRevision());
                    boolean applied = saved && controller.applyDraft();
                    persistPortable = saved;
                    String msg = !saved ? "Failed to Save Graph Before Applying"
                            : applied ? "Graph Applied" : "Failed to Apply Graph";
                    sendGraphActionResult(context, payload.target(), payload.requestId(), saved && applied, msg,
                            controller.getDraftGraph().revision(), true, saved,
                            saved && !applied ? controller.getGraphDiagnostics() : List.of());
                }
                // ------------------------------------HIDDEN SCHEDULE SCRATCH GRAPH------------------------------------
                case "schedule_save" -> {
                    boolean saved = controller.saveShippingScheduleGraph(
                            AdvancedGraphDocument.fromTag(payload.graph()), payload.expectedRevision());
                    persistPortable = saved;
                    if (saved && context.player() instanceof ServerPlayer player) {
                        AdvancedControllerGraphSnapshotPayload.send(player, controller.getBlockPos(),
                                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper
                                        .getContainingSubLevelId(controller),
                                controller.getDraftGraph(), controller.getActiveGraph(),
                                controller.getShippingScheduleDraftGraph(),
                                controller.getShippingScheduleActiveGraph());
                    }
                    sendGraphActionResult(context, payload.target(), payload.requestId(), saved,
                            saved ? "Schedule saved" : "Schedule unavailable or changed",
                            controller.getShippingScheduleDraftGraph().revision(), true, saved, List.of());
                }
                case "schedule_read" -> {
                    boolean read = controller.readShippingScheduleFromPilot();
                    if (context.player() instanceof ServerPlayer player) {
                        AdvancedControllerGraphSnapshotPayload.send(player, controller.getBlockPos(),
                                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper
                                        .getContainingSubLevelId(controller),
                                controller.getDraftGraph(), controller.getActiveGraph(),
                                controller.getShippingScheduleDraftGraph(),
                                controller.getShippingScheduleActiveGraph());
                    }
                    sendGraphActionResult(context, payload.target(), payload.requestId(), read,
                            read ? "Read held shipping schedule" : "Pilot is not holding a shipping schedule",
                            controller.getShippingScheduleDraftGraph().revision(), false, read, List.of());
                }
                case "schedule_write" -> {
                    boolean written = controller.writeShippingScheduleToPilot();
                    persistPortable = written;
                    if (written && context.player() instanceof ServerPlayer player) {
                        AdvancedControllerGraphSnapshotPayload.send(player, controller.getBlockPos(),
                                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper
                                        .getContainingSubLevelId(controller),
                                controller.getDraftGraph(), controller.getActiveGraph(),
                                controller.getShippingScheduleDraftGraph(),
                                controller.getShippingScheduleActiveGraph());
                    }
                    sendGraphActionResult(context, payload.target(), payload.requestId(), written,
                            written ? "Wrote schedule to pilot" : "Could not write schedule to pilot",
                            controller.getShippingScheduleDraftGraph().revision(), false, written, List.of());
                }
                case "schedule_start", "schedule_pause", "schedule_resume", "schedule_stop" -> {
                    boolean changed = switch (payload.action()) {
                        case "schedule_start" -> controller.startShippingScheduleGraph();
                        case "schedule_pause" -> controller.controlShippingScheduleGraph("shipping_pause");
                        case "schedule_resume" -> controller.controlShippingScheduleGraph("shipping_resume");
                        case "schedule_stop" -> controller.controlShippingScheduleGraph("shipping_stop");
                        default -> false;
                    };
                    if (context.player() instanceof ServerPlayer player) {
                        AdvancedControllerGraphSnapshotPayload.send(player, controller.getBlockPos(),
                                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper
                                        .getContainingSubLevelId(controller),
                                controller.getDraftGraph(), controller.getActiveGraph(),
                                controller.getShippingScheduleDraftGraph(),
                                controller.getShippingScheduleActiveGraph());
                    }
                    String verb = switch (payload.action()) {
                        case "schedule_start" -> "started";
                        case "schedule_pause" -> "paused";
                        case "schedule_resume" -> "resumed";
                        default -> "stopped";
                    };
                    sendGraphActionResult(context, payload.target(), payload.requestId(), changed,
                            changed ? "Schedule " + verb : "Could not " + verb + " SCM schedule",
                            controller.getShippingScheduleDraftGraph().revision(), false, changed, List.of());
                }
                case "schedule_precalculate_route" -> {
                    boolean queued = controller.precalculateShippingScheduleRoute();
                    sendGraphActionResult(context, payload.target(), payload.requestId(), queued,
                            queued ? "Schedule route calculation queued" : "Could not queue schedule route calculation",
                            controller.getShippingScheduleDraftGraph().revision(), false, false, List.of());
                }
                case "schedule_delete_route" -> {
                    boolean deleted = controller.deleteShippingScheduleRoute();
                    sendGraphActionResult(context, payload.target(), payload.requestId(), deleted,
                            deleted ? "Schedule route deleted" : "No schedule route to delete",
                            controller.getShippingScheduleDraftGraph().revision(), false, false, List.of());
                }
                case "scm_set_vehicle_name" -> {
                    controller.setShipName(payload.argument());
                    sendGraphActionResult(context, payload.target(), payload.requestId(), true,
                            "Vehicle name updated", controller.getDraftGraph().revision(), false, false, List.of());
                }
                case "scm_set_display_progress" -> {
                    controller.setScmDisplayProgress(payload.graph().getBoolean("DisplayProgress"));
                    sendGraphActionResult(context, payload.target(), payload.requestId(), true,
                            "SCM display progress updated", controller.getDraftGraph().revision(), false, false, List.of());
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
                // ------------------------------------SCM CONFIGURATION------------------------------------
                case "scm_configuration_open" -> {
                    // The modal renders the loaded Sable bodies directly. Opening it must
                    // not crawl, probe, or otherwise initialize the craft merely to make
                    // blocks selectable.
                    controller.getScmConfigurationMapId();
                    sendScmConfiguration(context, payload.target(), controller, true);
                    sendGraphActionResult(context, payload.target(), payload.requestId(), true,
                            "SCM configuration loaded", controller.getDraftGraph().revision(),
                            false, false, List.of());
                }
                case "scm_configuration_refresh" ->
                        sendScmConfiguration(context, payload.target(), controller, false);
                case "scm_configuration_scan" -> {
                    boolean started = controller.scanScmConfiguration();
                    sendScmConfiguration(context, payload.target(), controller, true);
                    sendGraphActionResult(context, payload.target(), payload.requestId(), started,
                            started ? "SCM configuration scan started" :
                                    "Could not start SCM configuration scan",
                            controller.getDraftGraph().revision(), false, false, List.of());
                }
                case "scm_configuration_reinitialize" -> {
                    boolean validOrientation = !payload.graph().contains("Orientation")
                            || ScmOrientation.fromTag(payload.graph().getCompound("Orientation")).isPresent();
                    boolean saved = validOrientation && controller.replaceScmConfigurationProfile(
                            ScmConfigurationProfile.fromTag(payload.graph()));
                    boolean started = saved && controller.reinitializeScmConfiguration();
                    persistPortable = saved;
                    sendScmConfiguration(context, payload.target(), controller, true);
                    sendGraphActionResult(context, payload.target(), payload.requestId(), started,
                            started ? "SCM groups are reinitializing" : !validOrientation
                                    ? "Forward and up must be valid perpendicular directions"
                                    : "Could not reinitialize SCM groups",
                            controller.getDraftGraph().revision(), false, false, List.of());
                }
                case "scm_configuration_save" -> {
                    boolean validOrientation = !payload.graph().contains("Orientation")
                            || ScmOrientation.fromTag(payload.graph().getCompound("Orientation")).isPresent();
                    boolean saved = validOrientation && controller.replaceScmConfigurationProfile(
                            ScmConfigurationProfile.fromTag(payload.graph()));
                    persistPortable = saved;
                    sendScmConfiguration(context, payload.target(), controller, true);
                    sendGraphActionResult(context, payload.target(), payload.requestId(), saved,
                            saved ? "SCM configuration saved" : !validOrientation
                                    ? "Forward and up must be valid perpendicular directions"
                                    : "Could not save SCM configuration",
                            controller.getDraftGraph().revision(), false, false, List.of());
                }
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
            if (persistPortable && isSaveAction(payload.action()) && payload.graph().contains("ScmConfiguration")) {
                sendScmConfiguration(context, payload.target(), controller, false);
            }
            if (persistPortable && context.player().containerMenu instanceof PortableAdvancedContraptionControllerMenu menu) {
                menu.savePortableState();
            }
        });
    }

    // Revision validation must succeed before any SCM state is changed by a combined Save.
    private static boolean saveControllerDraft(AdvancedContraptionControllerBlockEntity controller,
                                               AdvancedContraptionControllerGraphPayload payload, int revision) {
        CompoundTag scm = payload.graph().contains("ScmConfiguration")
                ? payload.graph().getCompound("ScmConfiguration") : null;
        if (scm != null && (scm.contains("Orientation")
                && ScmOrientation.fromTag(scm.getCompound("Orientation")).isEmpty()
                || scm.contains("VehicleType")
                && !com.rieno.gadgetsandgizmos.lib.scm.ScmVehicleClassifier.isSelection(scm.getString("VehicleType")))) {
            return false;
        }
        if (!controller.saveDraft(AdvancedGraphDocument.fromTag(payload.graph()), revision)) return false;
        return scm == null || controller.replaceScmConfigurationProfile(ScmConfigurationProfile.fromTag(scm));
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

    // Send the isolated, server-selected block list for the calibration modal.
    private static void sendScmConfiguration(IPayloadContext ctx, MenuConfigTarget target,
                                             AdvancedContraptionControllerBlockEntity controller,
                                             boolean includePreview) {
        if (!(ctx.player() instanceof ServerPlayer player) || target == null || controller == null) {
            return;
        }
        List<ScmConfigurationProfile.DockingConnectorReference> liveDockingConnectors =
                controller.getScmConfigurationDockingConnectors();
        CompoundTag profile = controller.getScmConfigurationProfile().toTag();
        if (controller.getScmConfigurationMapId() != null) {
            profile.putUUID("MapId", controller.getScmConfigurationMapId());
        }
        CompoundTag candidates = new CompoundTag();
        candidates.put("DefaultOrientation", controller.getScmDefaultOrientation().toTag());
        candidates.putString("DetectedVehicleType", controller.getScmDetectedVehicleType());
        candidates.putString("DetectedSteeringType", controller.getScmDetectedSteeringType());
        ListTag entries = new ListTag();
        controller.getScmConfigurationCandidates().stream().limit(2048).forEach(unit -> {
            entries.add(unit.toTag());
        });
        candidates.put("Units", entries);
        ListTag dockingConnectors = new ListTag();
        liveDockingConnectors.forEach(reference ->
                dockingConnectors.add(reference.toTag(
                        ScmConfigurationProfile.DockingConnectorGroup.ANY)));
        candidates.put("DockingConnectors", dockingConnectors);
        ListTag viewSubLevels = new ListTag();
        controller.getScmConfigurationViewSubLevelIds().forEach(id -> {
            CompoundTag body = new CompoundTag();
            body.putUUID("Id", id);
            viewSubLevels.add(body);
        });
        candidates.put("ViewSubLevels", viewSubLevels);
        if (includePreview) {
            // Keep SCM usable when a controller menu remains open outside the
            // craft's normal client tracking range. This detached block scene
            // is only a renderer/picker fallback; all selected targets are
            // still checked against the server's current assembled bodies.
            ListTag previewBlocks = new ListTag();
            controller.getScmConfigurationPreviewBlocks(16_384).forEach(block -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("SubLevelId", block.subLevelId());
                entry.putLong("Position", block.position().asLong());
                entry.put("State", NbtUtils.writeBlockState(block.state()));
                entry.putDouble("RootX", block.rootPosition().x);
                entry.putDouble("RootY", block.rootPosition().y);
                entry.putDouble("RootZ", block.rootPosition().z);
                previewBlocks.add(entry);
            });
            candidates.put("PreviewBlocks", previewBlocks);
        }
        candidates.put("Telemetry", scmTelemetrySnapshot(controller));
        PacketDistributor.sendToPlayer(player, new ScmConfigurationSnapshotPayload(
                target.pos(), target.subLevelId(), controller.getScmConfigurationRootSubLevelId(),
                controller.isScmConfigurationScanning(),
                controller.getScmConfigurationStatus(), profile, candidates));
    }

    // Send the live SCM telemetry which backs the SCM workspace's simulation
    // sidebar. Keeping it in the existing configuration snapshot avoids a
    // second client-only authority path and means the rendered values are the
    // same values graph nodes receive from the server runtime.
    private static CompoundTag scmTelemetrySnapshot(AdvancedContraptionControllerBlockEntity controller) {
        CompoundTag telemetry = new CompoundTag();
        for (String port : List.of(
                "status", "ready", "attached", "initialized", "initializing", "progress", "map_id",
                "unit_count", "bearing_count", "vector_thruster_count", "docking_connector_count",
                "controllable_count", "mass", "weight", "facing", "inertia_tensor",
                "center_of_mass_x", "center_of_mass_y", "center_of_mass_z",
                "center_of_lift_x", "center_of_lift_y", "center_of_lift_z",
                "x", "y", "z", "velocity_x", "velocity_y", "velocity_z", "speed",
                "angular_velocity_x", "angular_velocity_y", "angular_velocity_z", "yaw", "pitch", "roll",
                "nearest_collision_distance", "collision_distance_forward",
                "collision_distance_backward", "collision_distance_left",
                "collision_distance_right", "collision_distance_up", "collision_distance_down",
                "collision_scan_range", "navigation_target_distance")) {
            AdvancedGraphDocument.Value value = controller.getShipControlGraphValue(port);
            CompoundTag encoded = new CompoundTag();
            encoded.putString("Type", value.type());
            encoded.put("Payload", value.payload().copy());
            telemetry.put(port, encoded);
        }
        return telemetry;
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
