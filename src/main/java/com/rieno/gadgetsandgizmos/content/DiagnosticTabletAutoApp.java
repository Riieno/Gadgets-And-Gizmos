package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelTelemetryApi;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Implement the tablet's automatic diagnostics page and repair actions
final class DiagnosticTabletAutoApp {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet auto app
    private DiagnosticTabletAutoApp() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet auto app
    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action) {
        AutoTarget target = target(ctx);
        if (target == null) {
            sendSnapshot(ctx, new CompoundTag());
            return failure("G&G Auto is available only on an initialized ship");
        }
        AdvancedContraptionControllerBlockEntity controller = target.controller();
        String val = action.arguments().getOrDefault("value", "").strip();
        TabletActionHandler.Result res = switch (action.actionId()) {
            case "refresh", "select" -> success("");
            case "hover" -> command(ctx, controller, "ship_hover", java.util.Map.of("strength", 1.0D));
            case "dock" -> command(ctx, controller, "ship_dock", java.util.Map.of());
            case "navigate" -> navigate(ctx, controller, val);
            default -> failure("Unknown G&G Auto action");
        };
        sendSnapshot(ctx, snapshot(ctx, target));
        return res;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is available
    static boolean available(TabletActionContext ctx) {
        return target(ctx) != null;
    }

    // Get the target
    private static AutoTarget target(TabletActionContext ctx) {
        UUID sourceSubLevelId = sourceSubLevelId(ctx);
        if (sourceSubLevelId == null) return null;
        Set<UUID> connectedSubLevelIds = SableSubLevelTelemetryApi.connectedSubLevelIds(
                ctx.player().serverLevel(), sourceSubLevelId);
        if (connectedSubLevelIds.isEmpty()) return null;
        for (Object candidate : SubLevelBlockEntityCollector.getSubLevels(ctx.player().level())) {
            if (!(candidate instanceof SubLevel subLevel)
                    || !connectedSubLevelIds.contains(subLevel.getUniqueId())) {
                continue;
            }
            AdvancedContraptionControllerBlockEntity controller = readyController(candidate);
            if (controller != null) {
                return new AutoTarget(controller, sourceSubLevelId, connectedSubLevelIds);
            }
        }
        return null;
    }

    // Get the source sublevel id
    private static UUID sourceSubLevelId(TabletActionContext ctx) {
        if (ctx.placedSource()) return ctx.sourceSubLevelId();
        Object tracking = SimulatedHelper.getEntityTrackingSubLevel(ctx.player());
        return tracking instanceof SubLevel subLevel && !subLevel.isRemoved()
                ? subLevel.getUniqueId() : null;
    }

    // Get the ready controller
    private static AdvancedContraptionControllerBlockEntity readyController(Object subLevel) {
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
            if (blockEntity instanceof AdvancedContraptionControllerBlockEntity controller
                    && controller.getShipControlGraphValue("ready").asBoolean()) {
                return controller;
            }
        }
        return null;
    }

    // Navigate the diagnostic tablet auto app
    private static TabletActionHandler.Result navigate(TabletActionContext ctx,
                                                        AdvancedContraptionControllerBlockEntity controller,
                                                        String val) {
        String[] parts = val.split("[, ]+");
        if (parts.length < 3) return failure("Enter X, Y and Z coordinates");
        try {
            return command(ctx, controller, "ship_navigate", java.util.Map.of(
                    "x", Double.parseDouble(parts[0]), "y", Double.parseDouble(parts[1]),
                    "z", Double.parseDouble(parts[2]), "speed", 0.65D,
                    "tolerance", 1.0D, "avoid_collisions", 1.0D));
        } catch (NumberFormatException err) {
            return failure("Coordinates must be numbers");
        }
    }

    // Get the command
    private static TabletActionHandler.Result command(TabletActionContext ctx,
                                                       AdvancedContraptionControllerBlockEntity controller,
                                                       String command,
                                                       java.util.Map<String, Double> values) {
        boolean accepted = controller.executeShipControlGraphCommand(
                "tablet_auto_" + ctx.player().getUUID() + "_" + command,
                command, values, java.util.Map.of());
        return accepted ? success("") : failure("The ship rejected that command");
    }

    // Get the snapshot
    private static CompoundTag snapshot(TabletActionContext ctx, AutoTarget target) {
        AdvancedContraptionControllerBlockEntity controller = target.controller();
        CompoundTag data = new CompoundTag();
        data.putBoolean("Available", true);
        data.putString("Ship", controller.getShipName());
        data.putString("Status", controller.getShipControlGraphValue("status").asString());
        data.putString("Destination", controller.getCrnShipDisplayData().targetStop());
        SableSubLevelTelemetryApi.Snapshot motion = SableSubLevelTelemetryApi.sample(
                ctx.player().serverLevel(), target.sourceSubLevelId());
        data.putBoolean("PhysicsAvailable", motion.physicsAvailable());
        data.putDouble("Speed", motion.speed());
        data.putDouble("VelocityX", motion.linearVelocity().x);
        data.putDouble("VelocityY", motion.linearVelocity().y);
        data.putDouble("VelocityZ", motion.linearVelocity().z);
        data.putDouble("AngularVelocityX", motion.angularVelocity().x);
        data.putDouble("AngularVelocityY", motion.angularVelocity().y);
        data.putDouble("AngularVelocityZ", motion.angularVelocity().z);
        data.putDouble("X", motion.position().x);
        data.putDouble("Y", motion.position().y);
        data.putDouble("Z", motion.position().z);
        data.putDouble("Weight", shipMass(ctx, target, motion));
        ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(controller);
        data.putDouble("Fuel", fuel.ratio());
        ShipCargoAutomation.ResourceStatus resources = ShipCargoAutomation.resourceStatus(controller);
        data.putLong("Cargo", resources.items());
        data.putLong("Fluids", resources.fluids());
        data.putLong("Energy", resources.energy());
        data.putInt("StockNetworks",
                controller.getShipStockNetworkSnapshot().networks().size());
        data.putBoolean("Pilot", controller.hasShippingSchedule());
        data.putLong("SampleTick", ctx.player().serverLevel().getGameTime());
        return data;
    }

    // Get the ship mass
    private static double shipMass(TabletActionContext ctx,
                                   AutoTarget target,
                                   SableSubLevelTelemetryApi.Snapshot src) {
        Set<UUID> subLevelIds = new LinkedHashSet<>(target.connectedSubLevelIds());
        subLevelIds.addAll(target.controller().getMappedShipSubLevelIds());
        subLevelIds.add(target.sourceSubLevelId());
        double mass = 0.0D;
        for (UUID subLevelId : subLevelIds) {
            double bodyMass = SableSubLevelTelemetryApi.sample(
                    ctx.player().serverLevel(), subLevelId).mass();
            if (!Double.isFinite(bodyMass) || bodyMass <= 0.0D) continue;
            if (mass > Double.MAX_VALUE - bodyMass) return Double.MAX_VALUE;
            mass += bodyMass;
        }
        return mass > 0.0D ? mass : src.mass();
    }

    // Send the snapshot
    private static void sendSnapshot(TabletActionContext ctx, CompoundTag data) {
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                DiagnosticTabletData.appId("gg_auto"), ctx, data));
    }

    // Create a successful diagnostic tablet auto app
    private static TabletActionHandler.Result success(String msg) {
        return TabletActionHandler.Result.success(Component.literal(msg));
    }

    // Create a failed diagnostic tablet auto app
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
    }

    // Store the auto target
    private record AutoTarget(AdvancedContraptionControllerBlockEntity controller,
                              UUID sourceSubLevelId,
                              Set<UUID> connectedSubLevelIds) {
        // Initialize the auto target
        private AutoTarget {
            connectedSubLevelIds = Set.copyOf(connectedSubLevelIds);
        }
    }
}
