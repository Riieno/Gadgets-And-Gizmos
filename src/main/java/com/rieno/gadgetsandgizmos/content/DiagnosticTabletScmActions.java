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
        if (tabletId == null) return failure("The tablet identity is not available");
        String requested = value(action);
        if ("select_target".equals(action.actionId())) {
            boolean selected = DiagnosticTabletAppStorage.selectBinding(ctx.player().server,
                    tabletId, DiagnosticTabletData.appId("scm"), requested);
            sendSnapshot(ctx);
            return selected ? success("Ship target selected") : failure("That ship target is unavailable");
        }
        if ("remove_target".equals(action.actionId())) {
            DiagnosticTabletAppStorage.removeBinding(ctx.player().server, tabletId,
                    DiagnosticTabletData.appId("scm"), requested);
            sendSnapshot(ctx);
            return success("Ship target removed");
        }
        TabletActionHandler.Result res;
        if (target instanceof ShipDockBlockEntity dock) {
            res = executeDock(ctx.player(), ctx, action, dock);
        } else if (target instanceof AdvancedContraptionControllerBlockEntity controller) {
            res = executeController(ctx.player(), ctx, action, controller);
        } else if ("refresh".equals(action.actionId()) || "select".equals(action.actionId())) {
            res = success("Select a ship or ship dock to begin");
        } else {
            res = failure("Select a bound ship or ship dock before using Ship Control");
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
                if (val.isBlank()) yield failure("Enter a dock name");
                dock.configure(val, dock.canRefuel(), dock.canRestock(), dock.canHandlePackages());
                yield success("Dock renamed to " + dock.getDockName());
            }
            case "dock_capabilities" -> {
                CapabilitySelection capabilities = CapabilitySelection.parse(
                        val, dock.canRefuel(), dock.canRestock(), dock.canHandlePackages());
                dock.configure(dock.getDockName(), capabilities.refuel(),
                        capabilities.restock(), capabilities.packages());
                yield success("Dock capabilities: " + capabilities.description());
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
                yield saved ? success("Dock connector is now " + state)
                        : failure("Could not update the connector");
            }
            default -> failure("This action requires an Advanced Controller or Ship Control Module");
        };
    }

    // Run the controller
    private static TabletActionHandler.Result executeController(
            ServerPlayer player, TabletActionContext ctx,
            TabletAction action, AdvancedContraptionControllerBlockEntity controller) {
        String val = value(action);
        return switch (action.actionId()) {
            case "initialize" -> command(player, controller, "ship_initialize", Map.of(), Map.of());
            case "hover" -> command(player, controller, "ship_hover", Map.of("strength", 1.0D), Map.of());
            case "dock" -> command(player, controller, "ship_dock", Map.of(), Map.of());
            case "manual_control" -> {
                DiagnosticTabletRemoteSessions.authorize(player, controller);
                PacketDistributor.sendToPlayer(player, new DiagnosticTabletRemoteOpenPayload(
                        "interact", controller.getBlockPos(), SimulatedHelper.getContainingSubLevelId(controller)));
                yield success("Manual ship control started");
            }
            case "follow" -> navigate(controller, ctx, val);
            case "climb" -> climb(player, controller, val);
            case "stop", "ground" -> command(player, controller, "ship_stop", Map.of(), Map.of());
            case "push_configured" -> pushConfigured(player, ctx, controller, val);
            case "map_target" -> saveTarget(ctx, "mapped_target", val, "SCM target saved");
            case "test_target" -> testTarget(player, ctx, val);
            case "schedule" -> DiagnosticTabletScheduleSessions.open(player, controller)
                    ? success("Shipping schedule opened")
                    : failure("A live pilot with a shipping schedule is required");
            case "request_items" -> sendControllerEvent(controller, action.actionId(), val);
            case "route" -> navigate(controller, ctx, val);
            case "metrics" -> metrics(controller);
            case "add_waypoint" -> saveTarget(ctx, "waypoint", val, "Waypoint saved");
            case "remove_waypoint" -> DiagnosticTabletAppStorage.removeWorkspaceTarget(
                    player.server, ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"),
                    "waypoint", val)
                    ? success("Waypoint removed") : failure("Waypoint not found");
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
                    "speed", 0.5D, "tolerance", 0.75D, "avoid_collisions", 1.0D), Map.of());
        } catch (NumberFormatException err) {
            return failure("Enter a Y level");
        }
    }

    // Push the configured
    private static TabletActionHandler.Result pushConfigured(
            ServerPlayer player, TabletActionContext ctx,
            AdvancedContraptionControllerBlockEntity controller, String serialized) {
        List<Selection> selections = parseSelections(serialized);
        if (selections.isEmpty()) return failure("Read at least one block before using Push");
        if (selections.size() > MAX_PUSH_TARGETS) return failure("Push is limited to 128 blocks");

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
        if (accepted.isEmpty()) return failure("No eligible SCM control blocks were selected");

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
                ContraptionNetworkLinkerData.TargetMode.BLOCK);
        controller.getLinkerSlotHandler().setStackInSlot(0, linker);
        accepted.forEach(selection -> saveWorkspaceTarget(ctx, "mapped_target",
                selection.label() + ":" + selection.blockPos().getX() + ","
                        + selection.blockPos().getY() + "," + selection.blockPos().getZ()));
        return success(unique.size() + " SCM block(s) pushed"
                + (rejected == 0 ? "" : "; " + rejected + " rejected"));
    }

    // Test the target
    private static TabletActionHandler.Result testTarget(
            ServerPlayer player, TabletActionContext ctx, String val) {
        Selection selection = parseSelection(val);
        if (selection == null) return failure("Read a block before testing it");
        ResolvedTarget resolved = resolveTarget(player.level(), ctx.subLevelId(), selection);
        if (resolved == null || !isScmEligible(resolved.state())) return failure("That block is not an eligible SCM target");
        ScmTarget target = new ScmTarget(ctx.subLevelId(), selection.pos(),
                resolved.blockId(), selection.label());
        List<ScmControlProbe> probes = ScmControlProbeRegistry.create(
                resolved.level(), resolved.blockEntity(), target, Vec3.ZERO);
        if (probes.isEmpty()) return failure("No SCM control adapter supports this block");
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
        return success(probes.size() + " control response(s) tested; " + changed + " changed state");
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
        if (destination == null) return failure("Enter a waypoint name or coordinates");
        BlockPos pos = destination.pos();
        return command(player, controller, "ship_navigate", Map.of(
                "x", pos.getX() + 0.5D, "y", pos.getY() + 0.5D, "z", pos.getZ() + 0.5D,
                "speed", 0.6D, "tolerance", 1.0D, "avoid_collisions", 1.0D), Map.of());
    }

    // Get the metrics
    private static TabletActionHandler.Result metrics(AdvancedContraptionControllerBlockEntity controller) {
        AdvancedGraphDocument.Value ready = controller.getShipControlGraphValue("ready");
        AdvancedGraphDocument.Value x = controller.getShipControlGraphValue("x");
        AdvancedGraphDocument.Value y = controller.getShipControlGraphValue("y");
        AdvancedGraphDocument.Value z = controller.getShipControlGraphValue("z");
        String status = controller.getShipControlGraphValue("status").asString();
        return success((ready.asBoolean() ? "Ready" : "Not ready") + " | "
                + Math.round(x.asNumber()) + ", " + Math.round(y.asNumber()) + ", "
                + Math.round(z.asNumber()) + (status.isBlank() ? "" : " | " + status));
    }

    // Get the command
    private static TabletActionHandler.Result command(
            ServerPlayer player, AdvancedContraptionControllerBlockEntity controller,
            String command, Map<String, Double> numbers, Map<String, String> text) {
        boolean accepted = controller.executeShipControlGraphCommand(
                "tablet_" + player.getUUID() + "_" + command, command, numbers, text);
        return accepted ? success(command.replace("ship_", "").replace('_', ' ') + " accepted")
                : failure(command.replace("ship_", "").replace('_', ' ') + " was rejected");
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
            TabletActionContext ctx, String kind, String val, String msg) {
        return saveWorkspaceTarget(ctx, kind, val)
                ? success(msg) : failure("Could not save the target");
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
    private static String dockInventorySummary(ShipDockBlockEntity dock) {
        int items = dock.bufferedItems().stream().mapToInt(ItemStack::getCount).sum();
        long fluid = 0L;
        for (int tank = 0; tank < dock.getFluidBuffer().getTanks(); tank++) {
            fluid += dock.getFluidBuffer().getFluidInTank(tank).getAmount();
        }
        return "Dock buffers: " + items + " item(s), " + fluid + " mB, "
                + dock.getEnergyBuffer().getEnergyStored() + "/"
                + dock.getEnergyBuffer().getMaxEnergyStored() + " FE";
    }

    // Set the landing point
    private static TabletActionHandler.Result setLandingPoint(
            TabletActionContext ctx, ShipDockBlockEntity dock, String val) {
        Selection selection = parseSelection(val);
        if (selection == null) return failure("Look at a block and interact to set the landing-zone corner");
        if (ctx.blockPos() == null || ctx.sourceTabletId() == null) {
            return failure("Select a ship dock before editing its landing zone");
        }
        if (ctx.blockPos().distSqr(selection.pos()) > 128.0D * 128.0D) {
            return failure("Landing zones must remain within 128 blocks of the dock");
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
                ? "Landing zone saved for " + dock.getDockName()
                : "Landing-zone start set; interact again to set the opposite corner");
    }

    // Select the landing zone
    private static TabletActionHandler.Result selectLandingZone(TabletActionContext ctx, String val) {
        if (ctx.sourceTabletId() == null) return failure("The tablet identity is unavailable");
        try {
            UUID id = UUID.fromString(val);
            TabletStorageApi.storage().updateApp(ctx.sourceTabletId(),
                    DiagnosticTabletData.appId("scm"), data -> {
                        data.putUUID("SelectedLandingZone", id);
                        return data;
                    });
            return success("Landing zone selected");
        } catch (IllegalArgumentException err) {
            return failure("That landing zone is unavailable");
        }
    }

    // Clear the landing zone
    private static TabletActionHandler.Result clearLandingZone(TabletActionContext ctx,
                                                                ShipDockBlockEntity dock,
                                                                String val) {
        if (ctx.sourceTabletId() == null) return failure("The tablet identity is unavailable");
        CompoundTag app = DiagnosticTabletAppStorage.data(ctx.player().server,
                ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"));
        UUID id;
        try {
            id = val.isBlank() ? app.getUUID("SelectedLandingZone") : UUID.fromString(val);
        } catch (IllegalArgumentException err) {
            return failure("Select a landing zone first");
        }
        if (!dock.removeLandingZone(id)) return failure("That landing zone is unavailable");
        TabletStorageApi.storage().updateApp(
                ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"), data -> {
                    data.remove("LandingDraftStart");
                    data.remove("SelectedLandingZone");
                    return data;
                });
        return success("Landing zone cleared");
    }

    // Get the adjust landing zone
    private static TabletActionHandler.Result adjustLandingZone(
            TabletActionContext ctx, ShipDockBlockEntity dock, String val) {
        if (ctx.sourceTabletId() == null) return failure("The tablet identity is unavailable");
        String[] parts = val.split("\\|", 2);
        if (parts.length != 2) return failure("Look at a landing-zone face before scrolling");
        Direction face;
        int delta;
        try {
            face = Direction.valueOf(parts[0].toUpperCase(Locale.ROOT));
            delta = Integer.parseInt(parts[1]);
        } catch (IllegalArgumentException err) {
            return failure("Invalid landing-zone adjustment");
        }
        int amount = Math.max(-8, Math.min(8, delta));
        CompoundTag app = DiagnosticTabletAppStorage.data(ctx.player().server,
                ctx.sourceTabletId(), DiagnosticTabletData.appId("scm"));
        if (!app.hasUUID("SelectedLandingZone")) return failure("Select a landing zone first");
        return dock.adjustLandingZone(app.getUUID("SelectedLandingZone"), face, amount)
                ? success("Landing-zone face moved") : failure("Select a landing zone first");
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
    private static TabletActionHandler.Result success(String msg) {
        return TabletActionHandler.Result.success(Component.literal(msg));
    }

    // Create a failed diagnostic tablet SCM actions
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
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
