package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbe;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletRemoteOpenPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// Handle the server actions used by the Diagnostic Tablet SCM app
public final class DiagnosticTabletScmActions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_PUSH_TARGETS = 128;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet SCM actions
    private DiagnosticTabletScmActions() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet SCM actions
    public static TabletActionHandler.Result execute(
            TabletActionContext ctx, TabletAction action, @Nullable BlockEntity target) {
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return failure(Component.translatable("createthrusters.text.the_tablet_identity_is_not_available"));
        String requested = value(action);
        if ("select_target".equals(action.actionId())) {
            boolean selected = DiagnosticTabletAppStorage.selectBinding(ctx.player().server,
                    tabletId, DiagnosticTabletData.appId("scm"), requested);
            sendSnapshot(ctx);
            return selected ? success(Component.translatable("createthrusters.text.ship_target_selected")) : failure(Component.translatable("createthrusters.text.that_ship_target_is_unavailable"));
        }
        if ("remove_target".equals(action.actionId())) {
            DiagnosticTabletAppStorage.removeBinding(ctx.player().server, tabletId,
                    DiagnosticTabletData.appId("scm"), requested);
            sendSnapshot(ctx);
            return success(Component.translatable("createthrusters.text.ship_target_removed"));
        }
        TabletActionHandler.Result res;
        if (target instanceof ShipDockBlockEntity dock) {
            res = executeDock(ctx.player(), ctx, action, dock);
        } else if (target instanceof AdvancedContraptionControllerBlockEntity controller) {
            res = executeController(ctx.player(), ctx, action, controller);
        } else if ("refresh".equals(action.actionId()) || "select".equals(action.actionId())) {
            res = success(Component.translatable("createthrusters.text.select_a_ship_or_ship_dock_to_begin"));
        } else {
            res = failure(Component.translatable("createthrusters.text.select_a_bound_ship_or_ship_dock_before_using_ship_control"));
        }
        sendSnapshot(ctx);
        return res;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the dock
    private static TabletActionHandler.Result executeDock(
            ServerPlayer player, TabletActionContext ctx,
            TabletAction action, ShipDockBlockEntity dock) {
        String val = value(action);
        return switch (action.actionId()) {
            case "rename_dock" -> {
                if (val.isBlank()) yield failure(Component.translatable("createthrusters.text.enter_a_dock_name"));
                dock.configure(val, dock.canRefuel(), dock.canRestock(), dock.canHandlePackages());
                yield success(Component.translatable("createthrusters.text.dock_renamed_to_1_s", dock.getDockName()));
            }
            case "dock_capabilities" -> {
                CapabilitySelection capabilities = CapabilitySelection.parse(
                        val, dock.canRefuel(), dock.canRestock(), dock.canHandlePackages());
                dock.configure(dock.getDockName(), capabilities.refuel(),
                        capabilities.restock(), capabilities.packages());
                yield success(Component.translatable("createthrusters.text.dock_capabilities_1_s", capabilities.description()));
            }
            case "landing_zone" -> {
                yield setLandingPoint(ctx, dock, val);
            }
            case "select_landing_zone" -> selectLandingZone(ctx, val);
            case "remove_landing_zone" -> clearLandingZone(ctx, dock, val);
            case "landing_adjust" -> adjustLandingZone(ctx, dock, val);
            case "connected_inventories", "inventory", "refresh" -> success(dockInventorySummary(dock));
            case "enable_connector", "disable_connector", "reserve_connector", "release_connector" -> {
                String state = switch (action.actionId()) {
                    case "enable_connector" -> "enabled";
                    case "disable_connector" -> "disabled";
                    case "reserve_connector" -> "reserved:" + player.getUUID();
                    default -> "available";
                };
                boolean saved = saveWorkspaceTarget(ctx,
                        dockKind(dock, "connector_" + state), val);
                yield saved ? success(Component.translatable("createthrusters.text.dock_connector_is_now_1_s", state))
                        : failure(Component.translatable("createthrusters.text.could_not_update_the_connector"));
            }
            default -> failure(Component.translatable("createthrusters.text.this_action_requires_an_advanced_controller_or_ship_control_module"));
        };
    }

    // Run the controller
    private static TabletActionHandler.Result executeController(
            ServerPlayer player, TabletActionContext ctx,
            TabletAction action, AdvancedContraptionControllerBlockEntity controller) {
        String val = value(action);
        if("permissions_access".equals(action.actionId())){
            if(!"true".equals(val) && !"false".equals(val)) return failure(Component.translatable("createthrusters.text.select_open_or_closed_access"));
            return ShipPermissions.setOpen(player, controller, Boolean.parseBoolean(val))
                    ? success(Component.translatable("createthrusters.text.ship_access_updated")) : failure(Component.translatable("createthrusters.text.ship_access_could_not_be_updated"));
        }
        if("permissions_transfer".equals(action.actionId())){
            try{
                return ShipPermissions.transferOwnership(player, controller, UUID.fromString(val))
                        ? success(Component.translatable("createthrusters.text.ship_ownership_transferred")) : failure(Component.translatable("createthrusters.text.ship_ownership_could_not_be_transferred"));
            }catch(IllegalArgumentException err){
                return failure(Component.translatable("createthrusters.text.invalid_player"));
            }
        }
        if("permissions_set".equals(action.actionId())){
            String[] parts = val.split("\\|", 3);
            if(parts.length != 3) return failure(Component.translatable("createthrusters.text.select_a_player_and_permission"));
            try{
                UUID targetId = UUID.fromString(parts[0]);
                ShipPermission permission = ShipPermission.fromId(parts[1]);
                return ShipPermissions.set(player, controller, targetId, permission, Boolean.parseBoolean(parts[2]))
                        ? success(Component.translatable("createthrusters.text.ship_permission_updated")) : failure(Component.translatable("createthrusters.text.only_the_ship_owner_can_change_permissions"));
            }catch(IllegalArgumentException err){
                return failure(Component.translatable("createthrusters.text.invalid_player_or_permission"));
            }
        }
        if(!ShipPermissions.allows(player, controller, ShipPermission.INTERACT)){
            return failure(Component.translatable("createthrusters.text.ship_interaction_permission_denied"));
        }
        return switch (action.actionId()) {
            case "initialize" -> command(player, controller, "ship_initialize", Map.of(), Map.of());
            case "hover" -> command(player, controller, "ship_hover", Map.of("strength", 1.0D), Map.of());
            case "dock" -> command(player, controller, "ship_dock", Map.of(), Map.of());
            case "manual_control" -> {
                DiagnosticTabletRemoteSessions.authorize(player, controller);
                PacketDistributor.sendToPlayer(player, new DiagnosticTabletRemoteOpenPayload(
                        "interact", controller.getBlockPos(), SimulatedHelper.getContainingSubLevelId(controller)));
                yield success(Component.translatable("createthrusters.text.manual_ship_control_started"));
            }
            case "follow" -> navigate(controller, ctx, val);
            case "climb" -> climb(player, controller, val);
            case "stop", "ground" -> command(player, controller, "ship_stop", Map.of(), Map.of());
            case "push_configured" -> pushConfigured(player, ctx, controller, val);
            case "map_target" -> saveTarget(ctx, "mapped_target", val, Component.translatable("createthrusters.text.scm_target_saved"));
            case "test_target" -> testTarget(player, ctx, val);
            case "schedule" -> DiagnosticTabletScheduleSessions.open(player, controller)
                    ? success(Component.translatable("createthrusters.text.shipping_schedule_opened"))
                    : failure(Component.translatable("createthrusters.text.a_live_pilot_with_a_shipping_schedule_is_required"));
            case "request_items" -> sendControllerEvent(controller, action.actionId(), val);
            case "route" -> navigate(controller, ctx, val);
            case "metrics" -> metrics(controller);
            case "add_waypoint" -> saveTarget(ctx, "waypoint", val, Component.translatable("createthrusters.text.waypoint_saved"));
            case "remove_waypoint" -> DiagnosticTabletAppStorage.removeWorkspaceTarget(
                    player.server, ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"),
                    "waypoint", val)
                    ? success(Component.translatable("createthrusters.text.waypoint_removed")) : failure(Component.translatable("createthrusters.text.waypoint_not_found"));
            case "navigate" -> navigate(controller, ctx, val);
            default -> sendControllerEvent(controller, action.actionId(), val);
        };
    }

    // Get the climb
    private static TabletActionHandler.Result climb(ServerPlayer player,
                                                     AdvancedContraptionControllerBlockEntity controller,
                                                     String val) {
        try {
            double y = Double.parseDouble(val.strip());
            return command(player, controller, "ship_navigate", Map.of(
                    "x", controller.getShipControlGraphValue("x").asNumber(), "y", y,
                    "z", controller.getShipControlGraphValue("z").asNumber(),
                    "speed", 1.0D, "speed_fraction", 1.0D, "tolerance", 0.75D, "avoid_collisions", 1.0D), Map.of());
        } catch (NumberFormatException err) {
            return failure(Component.translatable("createthrusters.text.enter_a_y_level"));
        }
    }

    // Push the configured
    private static TabletActionHandler.Result pushConfigured(
            ServerPlayer player, TabletActionContext ctx,
            AdvancedContraptionControllerBlockEntity controller, String serialized) {
        List<Selection> selections = parseSelections(serialized);
        if (selections.isEmpty()) return failure(Component.translatable("createthrusters.text.read_at_least_one_block_before_using_push"));
        if (selections.size() > MAX_PUSH_TARGETS) return failure(Component.translatable("createthrusters.text.push_is_limited_to_128_blocks"));

        UUID rootSubLevelId = SimulatedHelper.getContainingSubLevelId(controller);
        List<ContraptionNetworkLinkerData.LinkedTarget> accepted = new ArrayList<>();
        int rejected = 0;
        for (Selection selection : selections) {
            ResolvedTarget resolved = resolveTarget(player.level(), rootSubLevelId, selection);
            if (resolved == null || !isScmEligible(resolved.state())) {
                rejected++;
                continue;
            }
            accepted.add(new ContraptionNetworkLinkerData.LinkedTarget(
                    selection.pos(), rootSubLevelId, resolved.blockId(), selection.label(),
                    ContraptionNetworkLinkerData.LinkMode.SCM,
                    ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of()));
        }
        if (accepted.isEmpty()) return failure(Component.translatable("createthrusters.text.no_eligible_scm_control_blocks_were_selected"));

        ItemStack linker = controller.getStoredLinker().isEmpty()
                ? new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get())
                : controller.getStoredLinker().copy();
        List<ContraptionNetworkLinkerData.LinkedTarget> merged = new ArrayList<>(
                ContraptionNetworkLinkerData.readTargets(linker).stream()
                        .filter(existing -> existing.mode() != ContraptionNetworkLinkerData.LinkMode.SCM)
                        .toList());
        Map<String, ContraptionNetworkLinkerData.LinkedTarget> unique = new LinkedHashMap<>();
        for (ContraptionNetworkLinkerData.LinkedTarget selection : accepted) {
            unique.put(selection.subLevelId() + ":" + selection.blockPos().asLong(), selection);
        }
        merged.addAll(unique.values());
        ContraptionNetworkLinkerData.writeTargets(linker, merged,
                ContraptionNetworkLinkerData.LinkMode.SCM,
                ContraptionNetworkLinkerData.TargetMode.FACE);
        controller.getLinkerSlotHandler().setStackInSlot(0, linker);
        accepted.forEach(selection -> saveWorkspaceTarget(ctx, "mapped_target",
                selection.label() + ":" + selection.blockPos().getX() + ","
                        + selection.blockPos().getY() + "," + selection.blockPos().getZ()));
        return success(Component.translatable("createthrusters.text.1_s_scm_block_s_pushed_2_s_rejected", unique.size(), rejected));
    }

    // Test the target
    private static TabletActionHandler.Result testTarget(
            ServerPlayer player, TabletActionContext ctx, String val) {
        Selection selection = parseSelection(val);
        if (selection == null) return failure(Component.translatable("createthrusters.text.read_a_block_before_testing_it"));
        ResolvedTarget resolved = resolveTarget(player.level(), ctx.subLevelId(), selection);
        if (resolved == null || !isScmEligible(resolved.state())) return failure(Component.translatable("createthrusters.text.that_block_is_not_an_eligible_scm_target"));
        ScmTarget target = new ScmTarget(ctx.subLevelId(), selection.pos(),
                resolved.blockId(), selection.label());
        List<ScmControlProbe> probes = ScmControlProbeRegistry.create(
                resolved.level(), resolved.blockEntity(), target, Vec3.ZERO);
        if (probes.isEmpty()) return failure(Component.translatable("createthrusters.text.no_scm_control_adapter_supports_this_block"));
        int changed = 0;
        for (ScmControlProbe probe : probes) {
            try {
                ScmControlProbe.Reading before = probe.read();
                probe.apply(probe.maxControl());
                ScmControlProbe.Reading after = probe.read();
                if (before.active() != after.active()
                        || Math.abs(before.effect() - after.effect()) > 1.0E-6D
                        || Math.abs(before.speed() - after.speed()) > 1.0E-6D) changed++;
            } finally {
                probe.restore();
            }
        }
        return success(Component.translatable("createthrusters.text.1_s_control_response_s_tested_2_s_changed_state", probes.size(), changed));
    }

    // Navigate the diagnostic tablet SCM actions
    private static TabletActionHandler.Result navigate(
            AdvancedContraptionControllerBlockEntity controller, TabletActionContext ctx,
            String val) {
        ServerPlayer player = ctx.player();
        Selection destination = parseSelection(val);
        if (destination == null) {
            DiagnosticTabletAppStorage.WorkspaceTarget stored =
                    DiagnosticTabletAppStorage.workspaceTarget(player.server,
                            ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"),
                            "waypoint", val);
            if (stored != null) destination = new Selection(stored.name(), stored.pos());
        }
        if (destination == null) return failure(Component.translatable("createthrusters.text.enter_a_waypoint_name_or_coordinates"));
        BlockPos pos = destination.pos();
        return command(player, controller, "ship_navigate", Map.of(
                "x", pos.getX() + 0.5D, "y", pos.getY() + 0.5D, "z", pos.getZ() + 0.5D,
                "speed", 1.0D, "speed_fraction", 1.0D, "tolerance", 1.0D, "avoid_collisions", 1.0D), Map.of());
    }

    // Get the metrics
    private static TabletActionHandler.Result metrics(AdvancedContraptionControllerBlockEntity controller) {
        AdvancedGraphDocument.Value ready = controller.getShipControlGraphValue("ready");
        AdvancedGraphDocument.Value x = controller.getShipControlGraphValue("x");
        AdvancedGraphDocument.Value y = controller.getShipControlGraphValue("y");
        AdvancedGraphDocument.Value z = controller.getShipControlGraphValue("z");
        String status = controller.getShipControlGraphValue("status").asString();
        return success(Component.translatable("createthrusters.text.1_s_2_s_3_s_4_s_5_s", Component.translatable(ready.asBoolean() ? "createthrusters.text.ready" : "createthrusters.text.not_ready"), Math.round(x.asNumber()), Math.round(y.asNumber()), Math.round(z.asNumber()), status.isBlank() ? "" : " | " + status));
    }

    // Get the command
    private static TabletActionHandler.Result command(
            ServerPlayer player, AdvancedContraptionControllerBlockEntity controller,
            String command, Map<String, Double> numbers, Map<String, String> text) {
        boolean accepted = controller.executeShipControlGraphCommand(
                "tablet_" + player.getUUID() + "_" + command, command, numbers, text);
        return accepted ? success(Component.translatable("createthrusters.text.1_s_accepted", commandName(command)))
                : failure(Component.translatable("createthrusters.text.1_s_was_rejected", commandName(command)));
    }

    // Name the command without exposing its protocol identifier
    private static Component commandName(String command){
        return switch(command){
            case "ship_initialize" -> Component.translatable("createthrusters.text.initialize");
            case "ship_hover" -> Component.translatable("createthrusters.text.hover");
            case "ship_dock" -> Component.translatable("createthrusters.text.dock");
            case "ship_stop" -> Component.translatable("createthrusters.navigation_table.button.stop");
            case "ship_navigate" -> Component.translatable("createthrusters.text.navigate");
            default -> Component.literal(command);
        };
    }

    // Send the controller event
    private static TabletActionHandler.Result sendControllerEvent(
            AdvancedContraptionControllerBlockEntity controller, String action, String val) {
        controller.receiveNamedControllerEvent("tablet.scm." + action,
                AdvancedGraphDocument.Value.string(val));
        return TabletActionHandler.Result.success(Component.empty());
    }

    // Save the target
    private static TabletActionHandler.Result saveTarget(
            TabletActionContext ctx, String kind, String val, Component msg) {
        return saveWorkspaceTarget(ctx, kind, val)
                ? success(msg) : failure(Component.translatable("createthrusters.text.could_not_save_the_target"));
    }

    // Save the workspace target
    private static boolean saveWorkspaceTarget(TabletActionContext ctx,
                                               String kind, String val) {
        Selection selection = parseSelection(val);
        BlockPos pos = selection == null ? ctx.player().blockPosition() : selection.pos();
        String name = selection == null ? val == null || val.isBlank()
                ? kind.replace('_', ' ') : val.strip() : selection.label();
        if (name.length() > 64) name = name.substring(0, 64);
        DiagnosticTabletAppStorage.saveWorkspaceTarget(ctx.player().server,
                ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"),
                new DiagnosticTabletAppStorage.WorkspaceTarget(kind, name,
                        ctx.player().level().dimension().location().toString(), pos));
        return true;
    }

    // Resolve the target
    private static @Nullable ResolvedTarget resolveTarget(
            Level parentLevel, @Nullable UUID subLevelId, Selection selection) {
        Object subLevel = SubLevelBlockEntityCollector.getSubLevel(parentLevel, subLevelId);
        Level targetLevel = subLevel instanceof SubLevel val ? val.getLevel() : parentLevel;
        if (!targetLevel.isLoaded(selection.pos())) return null;
        BlockState state = targetLevel.getBlockState(selection.pos());
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                parentLevel, subLevelId, selection.pos());
        return new ResolvedTarget(targetLevel, state,
                id == null ? "minecraft:air" : id.toString(), blockEntity);
    }

    // Check if this is SCM eligible
    private static boolean isScmEligible(BlockState state) {
        if (state == null || state.isAir()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String blockId = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
        return !blockId.contains("thruster") && !blockId.contains("bearing")
                && !blockId.contains("sail");
    }

    // Parse the selections
    private static List<Selection> parseSelections(String val) {
        if (val == null || val.isBlank()) return List.of();
        List<Selection> selections = new ArrayList<>();
        for (String entry : val.split(";")) {
            Selection selection = parseSelection(entry);
            if (selection != null) selections.add(selection);
        }
        return List.copyOf(selections);
    }

    // Parse the selection
    private static @Nullable Selection parseSelection(String val) {
        if (val == null || val.isBlank()) return null;
        String trimmed = val.trim();
        int separator = trimmed.lastIndexOf(':');
        String label = separator < 0 ? "Target" : trimmed.substring(0, separator).trim();
        String coordinates = separator < 0 ? trimmed : trimmed.substring(separator + 1);
        String[] parts = coordinates.trim().split("[, ]+");
        if (parts.length != 3) return null;
        try {
            return new Selection(label.isBlank() ? "Target" : label,
                    new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Get the dock inventory summary
    private static Component dockInventorySummary(ShipDockBlockEntity dock) {
        int items = dock.bufferedItems().stream().mapToInt(ItemStack::getCount).sum();
        long fluid = 0L;
        for (int tank = 0; tank < dock.getFluidBuffer().getTanks(); tank++) {
            fluid += dock.getFluidBuffer().getFluidInTank(tank).getAmount();
        }
        return Component.translatable("createthrusters.text.dock_buffers_1_s_item_s_2_s_mb_3_s_4_s_fe", items, fluid, dock.getEnergyBuffer().getEnergyStored(), dock.getEnergyBuffer().getMaxEnergyStored());
    }

    // Set the landing point
    private static TabletActionHandler.Result setLandingPoint(
            TabletActionContext ctx, ShipDockBlockEntity dock, String val) {
        Selection selection = parseSelection(val);
        if (selection == null) return failure(Component.translatable("createthrusters.text.look_at_a_block_and_interact_to_set_the_landing_zone_corner"));
        if (ctx.blockPos() == null || ctx.sourceTabletId() == null) {
            return failure(Component.translatable("createthrusters.text.select_a_ship_dock_before_editing_its_landing_zone"));
        }
        if (ctx.blockPos().distSqr(selection.pos()) > 128.0D * 128.0D) {
            return failure(Component.translatable("createthrusters.text.landing_zones_must_remain_within_128_blocks_of_the_dock"));
        }
        String dockKey = (ctx.subLevelId() == null ? "world" : ctx.subLevelId())
                + ":" + ctx.blockPos().asLong();
        final boolean[] completed = {false};
        TabletStorageApi.storage().updateApp(
                ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"), data -> {
                    if (!data.contains("LandingDraftStart")
                            || !dockKey.equals(data.getString("LandingDock"))) {
                        data.putLong("LandingDraftStart", selection.pos().asLong());
                        data.putString("LandingDock", dockKey);
                        data.remove("LandingZone");
                    } else {
                        BlockPos first = BlockPos.of(data.getLong("LandingDraftStart"));
                        ShipDockBlockEntity.LandingZone zone = dock.addLandingZone(first, selection.pos());
                        data.putUUID("SelectedLandingZone", zone.id());
                        data.remove("LandingDraftStart");
                        completed[0] = true;
                    }
                    return data;
                });
        return success(completed[0]
                ? Component.translatable("createthrusters.text.landing_zone_saved_for_1_s", dock.getDockName())
                : Component.translatable("createthrusters.text.landing_zone_start_set_interact_again_to_set_the_opposite_corner"));
    }

    // Select the landing zone
    private static TabletActionHandler.Result selectLandingZone(TabletActionContext ctx, String val) {
        if (ctx.sourceTabletId() == null) return failure(Component.translatable("createthrusters.text.the_tablet_identity_is_unavailable"));
        try {
            UUID id = UUID.fromString(val);
            TabletStorageApi.storage().updateApp(ctx.sourceTabletId(),
                    DiagnosticTabletData.appId("scm"), data -> {
                        data.putUUID("SelectedLandingZone", id);
                        return data;
                    });
            return success(Component.translatable("createthrusters.text.landing_zone_selected"));
        } catch (IllegalArgumentException err) {
            return failure(Component.translatable("createthrusters.text.that_landing_zone_is_unavailable"));
        }
    }

    // Clear the landing zone
    private static TabletActionHandler.Result clearLandingZone(TabletActionContext ctx,
                                                                ShipDockBlockEntity dock,
                                                                String val) {
        if (ctx.sourceTabletId() == null) return failure(Component.translatable("createthrusters.text.the_tablet_identity_is_unavailable"));
        CompoundTag app = DiagnosticTabletAppStorage.data(ctx.player().server,
                ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"));
        UUID id;
        try {
            id = val.isBlank() ? app.getUUID("SelectedLandingZone") : UUID.fromString(val);
        } catch (IllegalArgumentException err) {
            return failure(Component.translatable("createthrusters.text.select_a_landing_zone_first"));
        }
        if (!dock.removeLandingZone(id)) return failure(Component.translatable("createthrusters.text.that_landing_zone_is_unavailable"));
        TabletStorageApi.storage().updateApp(
                ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"), data -> {
                    data.remove("LandingDraftStart");
                    data.remove("SelectedLandingZone");
                    return data;
                });
        return success(Component.translatable("createthrusters.text.landing_zone_cleared"));
    }

    // Get the adjust landing zone
    private static TabletActionHandler.Result adjustLandingZone(
            TabletActionContext ctx, ShipDockBlockEntity dock, String val) {
        if (ctx.sourceTabletId() == null) return failure(Component.translatable("createthrusters.text.the_tablet_identity_is_unavailable"));
        String[] parts = val.split("\\|", 2);
        if (parts.length != 2) return failure(Component.translatable("createthrusters.text.look_at_a_landing_zone_face_before_scrolling"));
        Direction face;
        int delta;
        try {
            face = Direction.valueOf(parts[0].toUpperCase(Locale.ROOT));
            delta = Integer.parseInt(parts[1]);
        } catch (IllegalArgumentException err) {
            return failure(Component.translatable("createthrusters.text.invalid_landing_zone_adjustment"));
        }
        int amount = Math.max(-8, Math.min(8, delta));
        CompoundTag app = DiagnosticTabletAppStorage.data(ctx.player().server,
                ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"));
        if (!app.hasUUID("SelectedLandingZone")) return failure(Component.translatable("createthrusters.text.select_a_landing_zone_first"));
        return dock.adjustLandingZone(app.getUUID("SelectedLandingZone"), face, amount)
                ? success(Component.translatable("createthrusters.text.landing_zone_face_moved")) : failure(Component.translatable("createthrusters.text.select_a_landing_zone_first"));
    }

    // Send the snapshot
    private static void sendSnapshot(TabletActionContext ctx) {
        // ------------------------------------SNAPSHOT SETUP------------------------------------
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return;
        CompoundTag root = new CompoundTag();
        ListTag targets = new ListTag();
        CompoundTag appData = DiagnosticTabletAppStorage.data(ctx.player().server,
                tabletId, DiagnosticTabletData.appId("scm"));
        DiagnosticTabletData.Binding selected = DiagnosticTabletAppStorage.selectedBinding(
                ctx.player().server, tabletId, DiagnosticTabletData.appId("scm"));
        // -----------------------------------------------------BOUND TARGETS-----------------------------------------------------
        for (DiagnosticTabletData.Binding binding : DiagnosticTabletAppStorage.bindings(
                ctx.player().server, tabletId, DiagnosticTabletData.appId("scm"))) {
            CompoundTag target = binding.toTag();
            String key = DiagnosticTabletAppStorage.key(binding);
            target.putString("Key", key);
            target.putBoolean("Selected", selected != null
                    && key.equals(DiagnosticTabletAppStorage.key(selected)));
            BlockEntity loaded = DiagnosticTabletApps.resolveBoundTarget(
                    ctx.player().level(), binding);
            target.putBoolean("Online", loaded != null);
            if (loaded instanceof ShipDockBlockEntity dock) {
                target.putString("Kind", "dock");
                target.putString("Name", dock.getDockName());
                target.putBoolean("Refuel", dock.canRefuel());
                target.putBoolean("Restock", dock.canRestock());
                target.putBoolean("Packages", dock.canHandlePackages());
            } else if (loaded instanceof AdvancedContraptionControllerBlockEntity controller) {
                target.putString("Kind", "ship");
                target.putString("Name", controller.getDisplayName().getString());
                target.putString("Status", controller.getShipControlGraphValue("status").asString());
                target.putBoolean("Ready", controller.getShipControlGraphValue("ready").asBoolean());
                target.putBoolean("Pilot", controller.hasShippingSchedule());
                ShipCargoAutomation.ResourceStatus resources =
                        ShipCargoAutomation.resourceStatus(controller);
                target.putLong("ManagedItems", resources.items());
                target.putLong("ManagedFluids", resources.fluids());
                target.putLong("ManagedEnergy", resources.energy());
            }
            targets.add(target);
        }
        root.put("Targets", targets);
        // -----------------------------------------------------LANDING ZONES-----------------------------------------------------
        if (selected != null) {
            BlockEntity selectedEntity = DiagnosticTabletApps.resolveBoundTarget(
                    ctx.player().level(), selected);
            if(selectedEntity instanceof AdvancedContraptionControllerBlockEntity controller){
                root.put("ShipPermissions", ShipPermissions.snapshot(ctx.player(), controller));
            }
            if (selectedEntity instanceof ShipDockBlockEntity dock) {
                ListTag zones = new ListTag();
                for (ShipDockBlockEntity.LandingZone zone : dock.landingZones()) {
                    CompoundTag tag = new CompoundTag();
                    tag.putUUID("Id", zone.id());
                    tag.putString("Name", zone.name());
                    tag.putLong("Min", zone.min().asLong());
                    tag.putLong("Max", zone.max().asLong());
                    tag.putInt("QueueOrder", zone.queueOrder());
                    tag.putBoolean("Selected", appData.hasUUID("SelectedLandingZone")
                            && appData.getUUID("SelectedLandingZone").equals(zone.id()));
                    zones.add(tag);
                }
                root.put("LandingZones", zones);
            }
        }
        if (appData.contains("LandingDraftStart")) {
            root.putLong("LandingDraftStart", appData.getLong("LandingDraftStart"));
        }
        root.put("Settings", appData.getCompound("Settings").copy());
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                DiagnosticTabletData.appId("scm"), ctx, root));
    }

    // Get the dock kind
    private static String dockKind(ShipDockBlockEntity dock, String suffix) {
        return "dock_" + dock.getDockId() + "_" + suffix;
    }

    // Get the value
    private static String value(TabletAction action) {
        return action.arguments().getOrDefault("value", "").trim();
    }

    // Create a successful diagnostic tablet SCM actions
    private static TabletActionHandler.Result success(Component msg) {
        return TabletActionHandler.Result.success(msg);
    }

    // Create a failed diagnostic tablet SCM actions
    private static TabletActionHandler.Result failure(Component msg) {
        return TabletActionHandler.Result.failure(msg);
    }

    // Store the selection
    private record Selection(String label, BlockPos pos) {
    }

    // Store the resolved target
    private record ResolvedTarget(Level level, BlockState state, String blockId,
                                  @Nullable BlockEntity blockEntity) {
    }

    // Store the capability selection
    private record CapabilitySelection(boolean refuel, boolean restock, boolean packages) {
        // Parse the capability selection
        private static CapabilitySelection parse(
                String val, boolean refuel, boolean restock, boolean packages) {
            if (val == null || val.isBlank()) return new CapabilitySelection(refuel, restock, packages);
            String normalized = val.toLowerCase(Locale.ROOT);
            if (normalized.contains("none")) return new CapabilitySelection(false, false, false);
            return new CapabilitySelection(normalized.contains("refuel"),
                    normalized.contains("restock"), normalized.contains("package"));
        }

        // Get the description
        private String description() {
            List<String> enabled = new ArrayList<>();
            if (refuel) enabled.add("refuel");
            if (restock) enabled.add("restock");
            if (packages) enabled.add("packages");
            return enabled.isEmpty() ? "none" : String.join(", ", enabled);
        }
    }
}
