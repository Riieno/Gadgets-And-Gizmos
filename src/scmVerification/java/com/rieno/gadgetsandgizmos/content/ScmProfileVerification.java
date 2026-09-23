package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import com.rieno.gadgetsandgizmos.lib.scm.ScmCommandRouting;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlAxes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSpeedControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSpeedGroupAllocator;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSteeringMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Verify profile migration and editing without starting a game world. */
public final class ScmProfileVerification {
    public static void main(String[] args) throws ReflectiveOperationException{
        require(ShipControlModuleRuntime.directScalarControl(0.15D) == 0.15D
                        && ShipControlModuleRuntime.directScalarControl(0.72D) == 0.72D
                        && ShipControlModuleRuntime.directScalarControl(1.0D) == 1.0D,
                "Direct thruster control was remapped or quantized");
        double lowPower = ShipControlModuleRuntime.mapAllocationControl(0.03D, 0.0D, 1.0D, true);
        require(ScmSpeedControl.quantizedSignal(lowPower, 15) == 1,
                "Positive route demand failed to power its redstone controller");
        double faceControl = ScmSpeedGroupAllocator.regulatedControl(
                0.0D, 1.0D, 1.0D / 15.0D);
        require(ScmSpeedControl.quantizedSignal(faceControl, 15) == 1
                        && ScmSpeedControl.quantizedSignal(
                        ScmSpeedGroupAllocator.regulatedControl(
                                faceControl, 1.0D, 1.0D / 15.0D), 15) == 2,
                "Closed-loop face control skipped its analogue range");
        require(ShipControlModuleRuntime.commandSpeed(Map.of(
                        "speed", 1.0D, "speed_fraction", 1.0D), 0.6D) == 28.0D
                        && ShipControlModuleRuntime.commandDriveThrottle(Map.of(
                        "speed", 1.0D, "speed_fraction", 1.0D)) == 1.0D,
                "A full graph speed percentage did not request full cruise power");
        CompoundTag speedData = new CompoundTag();
        speedData.putBoolean(AdvancedGraphCatalog.SHIP_SPEED_PERCENT_TAG, true);
        AdvancedGraphDocument.Node speedNode = new AdvancedGraphDocument.Node(
                "speed", "ship_navigate", "Navigate", 0.0D, 0.0D, speedData);
        require(AdvancedGraphCatalog.normalizeShipSpeedInput(speedNode, "speed", 100.0D, false) == 1.0D
                        && AdvancedGraphCatalog.normalizeShipSpeedInput(speedNode, "speed", 60.0D, true) == 0.6D,
                "Graph speed inputs were not treated as percentages");
        var branches = List.of(
                new ScmSpeedGroupAllocator.Influence(new Vec3(0.0D, 100.0D, 0.0D), Vec3.ZERO),
                new ScmSpeedGroupAllocator.Influence(new Vec3(0.0D, 0.0D, 100.0D), Vec3.ZERO));
        double[] efforts = ScmSpeedGroupAllocator.allocate(branches,
                new Vec3(0.0D, 0.4D, 0.03D), Vec3.ZERO, 1.0D);
        require(ScmSpeedControl.quantizedSignal(ShipControlModuleRuntime.mapAllocationControl(
                        efforts[0], 0.0D, 1.0D, true), 15) == 6
                        && ScmSpeedControl.quantizedSignal(ShipControlModuleRuntime.mapAllocationControl(
                        efforts[1], 0.0D, 1.0D, true), 15) == 1,
                "Lift and drive controllers received one shared on/off output");
        require(ShipControlModuleRuntime.commandDriveThrottle(Map.of()) == -1.0D
                        && ShipControlModuleRuntime.commandDriveThrottle(Map.of("drive_throttle", -1.0D)) == -1.0D
                        && ShipControlModuleRuntime.commandDriveThrottle(Map.of("drive_throttle", 0.0D)) == 0.0D,
                "Automatic throttle was converted into an explicit stop");
        Class<?> commandType = Class.forName(ShipControlModuleRuntime.class.getName() + "$ActiveShipCommand");
        var factory = commandType.getDeclaredMethod("target", String.class, String.class,
                Vec3.class, double.class, double.class, boolean.class);
        factory.setAccessible(true);
        Object command = factory.invoke(null, "regression", "ship_navigate",
                new Vec3(40.0D, 0.0D, 0.0D), 8.0D, 0.75D, true);
        var throttle = commandType.getDeclaredMethod("driveThrottle");
        throttle.setAccessible(true);
        require((double) throttle.invoke(command) == -1.0D,
                "Command construction erased automatic propulsion");
        for(double speed : new double[]{0.0D, 8.0D, 8.0001D, 8.1D}){
            ScmSpeedControl.Demand speedGroup = ScmSpeedControl.planSpeedGroup(
                    new ScmSpeedControl.Request(speed, 8.0D, 8.0D, 0.75D, 0.35D));
            double signal = ScmSpeedControl.accelerationSetpoint(
                    speedGroup.acceleration(), 0.0D);
            require(ShipControlModuleRuntime.mapAllocationControl(
                            signal, -1.0D, 1.0D, false) == 0.75D,
                    "Route acceleration was cut or inverted during braking");
            require(speed <= 8.0D || speedGroup.brake() > 0.0D,
                    "Route overspeed did not apply Brake with Acceleration");
        }
        require(ShipControlModuleRuntime.mapAllocationControl(-0.75D, -1.0D, 1.0D, false)
                        == 0.0D
                        && ShipControlModuleRuntime.mapAllocationControl(-0.75D, -1.0D, 1.0D, true)
                        == 0.0D,
                "Acceleration mapping allowed a negative signal");
        verifyArticulatedFollowerYawConstraint();
        verifyArticulatedCoordinatedYaw();
        verifyArticulatedRotationalForceDistribution();
        for(String type : new String[]{"ship_navigate", "ship_follow", "ship_hover", "ship_climb"}){
            double power = ScmCommandRouting.sustainingAccelerationPower(type, -1.0D, 1.0D);
            double signal = ScmSpeedControl.accelerationSignal(0.0D, 1.0D, 1.0D, power);
            require(ShipControlModuleRuntime.mapAllocationControl(signal, 0.0D, 1.0D, true) == 1.0D,
                    "Flight braking removed takeoff/hover engine power");
        }
        Vec3 lift = ScmControlAxes.withLiftSupport(Vec3.ZERO,
                new Vec3(0.0D, 0.5D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D));
        require(lift.y == 0.5D && lift.x == 0.0D && lift.z == 0.0D,
                "Hover support was masked or altered the travel direction");
        ScmOrientation defaults = new ScmOrientation(Direction.SOUTH, Direction.UP);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Version", 4);
        ScmConfigurationProfile profile = ScmConfigurationProfile.fromTag(legacy);
        require(profile.orientationOverride() == null, "Legacy profile must follow ACC");
        require(profile.vehicleType().equals("auto"), "Legacy profiles default to auto vehicle type");
        require(profile.steeringType().equals(ScmSteeringMode.CUSTOM.id()),
                "Legacy profiles retain custom steering");
        ScmConfigurationProfile.UnitReference retainedUnit = new ScmConfigurationProfile.UnitReference(
                UUID.randomUUID(), new BlockPos(1, 2, 3), "synaxis:dynamic_motor");
        profile.replace(UUID.randomUUID(), List.of(
                        new ScmConfigurationProfile.Group("empty", "Empty", Set.of()),
                        new ScmConfigurationProfile.Group("retained", "Retained", Set.of(retainedUnit))),
                Map.of("discarded", "empty", ScmConfigurationProfile.AUTO_ACTION, "retained"), Set.of());
        require(profile.groups().size() == 1 && profile.groups().getFirst().id().equals("retained")
                        && profile.actionGroups().size() == 1,
                "Empty calibrated actuator groups were retained");
        profile = ScmConfigurationProfile.fromTag(profile.toTag());
        require(profile.groups().size() == 1 && profile.actionGroups().size() == 1,
                "Empty calibrated actuator groups survived profile reload");
        profile.setVehicleType("car");
        profile.setSteeringType(ScmSteeringMode.FOUR_WHEEL.id());
        require(profile.resolveOrientation(defaults).equals(defaults), "ACC default sign preserved");
        for(Direction forward : Direction.values()){
            for(Direction up : Direction.values()){
                if(!ScmOrientation.isValid(forward, up)) continue;
                ScmOrientation frame = new ScmOrientation(forward, up);
                profile.setOrientationOverride(frame);
                profile.replace(UUID.randomUUID(), List.of(), Map.of(), Set.of());
                profile = ScmConfigurationProfile.fromTag(profile.toTag());
                require(frame.equals(profile.resolveOrientation(defaults)), "Frame survived save/rebase");
                require(profile.vehicleType().equals("car"), "Explicit mode survives save/rebase");
                require(profile.steeringType().equals(ScmSteeringMode.FOUR_WHEEL.id()),
                        "Steering mode survives save/rebase");
                require(profile.toTag().getInt("Version") == 9, "Profile version");
            }
        }
        profile.setOrientationOverride(null);
        require(!profile.toTag().contains("Orientation"), "Reset stores automatic defaults");
        require(ScmConfigurationProfile.fromTag(profile.toTag()).resolveOrientation(defaults).equals(defaults),
                "Reset survives reload");
        UUID connectorSubLevelId = UUID.randomUUID();
        BlockPos connectorPosition = new BlockPos(7, 8, 9);
        profile.setDockingConnectorGroup(connectorSubLevelId, connectorPosition,
                ScmConfigurationProfile.DockingConnectorGroup.FUEL);
        profile = ScmConfigurationProfile.fromTag(profile.toTag());
        require(profile.dockingConnectorGroup(connectorSubLevelId, connectorPosition)
                        == ScmConfigurationProfile.DockingConnectorGroup.FUEL,
                "Docking connector group survives reload");
        CompoundTag malformed = profile.toTag();
        CompoundTag orientation = new CompoundTag();
        orientation.putString("Forward", "up");
        orientation.putString("Up", "down");
        malformed.put("Orientation", orientation);
        require(ScmConfigurationProfile.fromTag(malformed).orientationOverride() == null, "Invalid save fallback");
        UUID dockId = UUID.randomUUID();
        ShipDockRegistry.ConnectorTarget connector = new ShipDockRegistry.ConnectorTarget(
                null, new BlockPos(3, 4, 5), new Vec3(3.5D, 4.5D, 6.0D),
                new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 1.0D, 0.0D));
        ShipDockRegistry.Dock storedDock = new ShipDockRegistry.Dock(
                dockId, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), null,
                BlockPos.ZERO, Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), "Dock",
                false, false, false, connector.subLevelId(), connector.pos(),
                connector.worldPosition(), connector.facing(), connector.up(), 1L,
                List.of(connector));
        var horizontalTerminal = ShippingScheduleRuntime.dockRouteTerminal(
                storedDock, 12.0D, 7.0D, 4.0D);
        require(horizontalTerminal.position().distanceToSqr(
                        new Vec3(3.5D, 4.5D, 18.0D)) < 1.0E-9D,
                "A horizontal connector route ended above or away from the connector axis");
        ShipDockRegistry.ConnectorTarget verticalConnector = new ShipDockRegistry.ConnectorTarget(
                UUID.randomUUID(), connectorPosition, new Vec3(3.5D, 4.5D, 6.0D),
                new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D));
        var verticalTerminal = ShippingScheduleRuntime.dockRouteTerminal(
                storedDock.withConnector(verticalConnector), 12.0D, 7.0D, 4.0D);
        require(verticalTerminal.position().distanceToSqr(
                        new Vec3(3.5D, 16.5D, 6.0D)) < 1.0E-9D,
                "A vertical connector route changed X/Z or added a world-Y offset");
        ShipDockRegistry.Dock loadingDock = new ShipDockRegistry.Dock(
                dockId, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), null,
                BlockPos.ZERO, Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), "Dock",
                false, false, false, null, null, null, null, null, 2L, List.of());
        require(ShipDockRegistry.preserveLoadingConnectorTargets(
                        storedDock, loadingDock, false).hasDockingConnector(),
                "Loading a dock erased its persisted connector target");
        require(!ShipDockRegistry.preserveLoadingConnectorTargets(
                        storedDock, loadingDock, true).hasDockingConnector(),
                "An explicit connector edit did not clear its persisted target");
        require(ShipControlModuleRuntime.navigationCollisionScanRange(0.0D) == 1.0D
                        && ShipControlModuleRuntime.navigationCollisionScanRange(0.5D)
                        < ShipControlModuleRuntime.navigationCollisionScanRange(4.0D)
                        && ShipControlModuleRuntime.navigationCollisionScanRange(8.0D) < 64.0D,
                "Autopilot collision range is no longer driven by live speed");
        double movingLookahead =
                ShipControlModuleRuntime.navigationCollisionScanRange(4.0D);
        require(ShipControlModuleRuntime.navigationSafeTravelSpeed(movingLookahead) >= 4.0D,
                "Autopilot live probe no longer covers its stopping envelope");
        require(ShipControlModuleRuntime.navigationCaptureApproachSpeed(6.5D, 6.35D)
                        >= 0.25D
                        && ShipControlModuleRuntime.navigationCaptureApproachSpeed(6.35D, 6.35D)
                        == 0.0D,
                "Autopilot target capture can stall just outside its arrival radius");
        require(!ShipControlModuleRuntime.navigationArrivalReached(0.25D, 0.75D, 28.0D)
                        && ShipControlModuleRuntime.navigationArrivalReached(0.25D, 0.75D, 0.08D),
                "Navigation replaced its route before the vehicle settled at the target");
        SableAssemblyBoundsApi.Envelope arrivalHull =
                new SableAssemblyBoundsApi.Envelope(3.0D, 4.0D, 2.0D, 5.0D);
        require(Math.abs(ShippingScheduleRuntime.connectorlessArrivalTolerance(arrivalHull)
                        - 7.0D) <= 1.0E-9D,
                "Connectorless arrival does not use the complete hull plus two blocks");
        AdvancedGraphCatalog.Definition brain = AdvancedGraphCatalog.get("scm_brain_debug");
        require(brain != null && AdvancedGraphCatalog.isShipControlPassiveType(brain.id())
                        && "map".equals(brain.outputs().get("scm_brain_data")),
                "SCM brain debug graph node is missing or not passive");
        AdvancedGraphDocument retiredGraph = new AdvancedGraphDocument();
        retiredGraph.nodes().add(new AdvancedGraphDocument.Node(
                "old_initialize", "ship_initialize", "Initialize", 0.0D, 0.0D,
                new CompoundTag()));
        retiredGraph.nodes().add(new AdvancedGraphDocument.Node(
                "survivor", "constant_boolean", "Keep", 100.0D, 0.0D,
                new CompoundTag()));
        retiredGraph.edges().add(new AdvancedGraphDocument.Edge(
                "old_edge", "old_initialize", "complete", "survivor", "value"));
        AdvancedGraphDocument.FunctionGraph retiredFunction =
                new AdvancedGraphDocument.FunctionGraph("legacy", "Legacy");
        retiredFunction.nodes().add(new AdvancedGraphDocument.Node(
                "nested_initialize", "ship_initialize", "Initialize", 0.0D, 0.0D,
                new CompoundTag()));
        retiredFunction.nodes().add(new AdvancedGraphDocument.Node(
                "nested_survivor", "constant_boolean", "Keep", 100.0D, 0.0D,
                new CompoundTag()));
        retiredFunction.edges().add(new AdvancedGraphDocument.Edge(
                "nested_old_edge", "nested_survivor", "value",
                "nested_initialize", "exec"));
        retiredGraph.functions().add(retiredFunction);
        retiredGraph.setScmActionFunction("ship_initialize", "legacy");
        AdvancedGraphDocument cleanedGraph = AdvancedGraphDocument.fromTag(
                retiredGraph.toTag());
        require(cleanedGraph.nodes().stream().noneMatch(
                        node -> "ship_initialize".equals(node.type()))
                        && cleanedGraph.edges().stream().noneMatch(edge ->
                        "old_initialize".equals(edge.fromNode())
                                || "old_initialize".equals(edge.toNode()))
                        && cleanedGraph.function("legacy").nodes().stream().noneMatch(
                        node -> "ship_initialize".equals(node.type()))
                        && cleanedGraph.function("legacy").edges().isEmpty()
                        && cleanedGraph.scmActionFunction("ship_initialize").isBlank(),
                "Retired Initialize nodes or their incident edges survived graph load");
        System.out.println("SCM profiles: throttle contracts, legacy defaults, 24 custom frames, rebase, reload and reset passed.");
    }

    // Verify that follower translation preserves the coupler's compliant yaw axis
    private static void verifyArticulatedFollowerYawConstraint(){
        UUID root = UUID.randomUUID();
        UUID follower = UUID.randomUUID();
        List<ShipControlMap.PropulsionUnit> units = List.of(
                propulsionUnit(0, root, Vec3.ZERO, 11.0D),
                propulsionUnit(1, follower, new Vec3(0.0D, 0.0D, 1.0D), 10.0D),
                propulsionUnit(2, follower, new Vec3(0.0D, 0.0D, -1.0D), 1.0D));
        ShipControlMap controlMap = new ShipControlMap(
                UUID.randomUUID(), "verification", root, BlockPos.ZERO,
                Vec3.ZERO, units, 0L);
        ShipControlAllocator.Allocation allocation = ShipControlAllocator.allocateArticulated(
                controlMap, List.of(
                        new ShipControlAllocator.CarriageDemand(
                                root, Set.of(root), Vec3.ZERO, 1.0D, Vec3.ZERO, true),
                        new ShipControlAllocator.CarriageDemand(
                                follower, Set.of(follower), Vec3.ZERO,
                                1.0D, Vec3.ZERO, false)),
                new Vec3(0.1D, 0.0D, 0.0D), Vec3.ZERO, true);
        double[] controls = allocation.controls();
        require(controls.length == units.size(), "Articulated allocator lost a propulsion unit");
        double followerYaw = controls[1] * 10.0D - controls[2];
        require(Math.abs(followerYaw) <= 0.05D,
                "Follower translation injected yaw into a compliant carriage coupler: " + followerYaw);
    }

    // Verify that whole-train turning retains follower yaw propulsion
    private static void verifyArticulatedCoordinatedYaw(){
        UUID root = UUID.randomUUID();
        UUID follower = UUID.randomUUID();
        List<ShipControlMap.PropulsionUnit> units = List.of(
                propulsionUnit(0, root, new Vec3(0.0D, 0.0D, 1.0D), 10.0D),
                propulsionUnit(1, root, new Vec3(0.0D, 0.0D, -1.0D), 10.0D),
                propulsionUnit(2, follower, new Vec3(0.0D, 0.0D, 5.0D), 10.0D),
                propulsionUnit(3, follower, new Vec3(0.0D, 0.0D, 3.0D), 10.0D));
        ShipControlMap controlMap = new ShipControlMap(
                UUID.randomUUID(), "verification", root, BlockPos.ZERO,
                Vec3.ZERO, units, 0L);
        ShipControlAllocator.Allocation allocation = ShipControlAllocator.allocateArticulated(
                controlMap, List.of(
                        new ShipControlAllocator.CarriageDemand(
                                root, Set.of(root), Vec3.ZERO, 1.0D,
                                new Vec3(0.0D, 1.0D, 0.0D), Vec3.ZERO, true),
                        new ShipControlAllocator.CarriageDemand(
                                follower, Set.of(follower), new Vec3(0.0D, 0.0D, 4.0D),
                                1.0D, new Vec3(0.0D, 1.0D, 0.0D), Vec3.ZERO, true)),
                Vec3.ZERO, Vec3.ZERO, false);
        double[] controls = allocation.controls();
        require(controls[2] > controls[3] + 0.05D,
                "Coordinated yaw did not use the follower's yaw propulsion");
    }

    // Verify that COM-offset rotational acceleration is applied to its carriage
    private static void verifyArticulatedRotationalForceDistribution(){
        UUID root = UUID.randomUUID();
        UUID follower = UUID.randomUUID();
        List<ShipControlMap.PropulsionUnit> units = List.of(
                propulsionUnit(0, root, Vec3.ZERO, 10.0D),
                propulsionUnit(1, follower, new Vec3(0.0D, 0.0D, 4.0D), 10.0D));
        ShipControlMap controlMap = new ShipControlMap(
                UUID.randomUUID(), "verification", root, BlockPos.ZERO,
                Vec3.ZERO, units, 0L);
        ShipControlAllocator.Allocation allocation = ShipControlAllocator.allocateArticulated(
                controlMap, List.of(
                        new ShipControlAllocator.CarriageDemand(
                                root, Set.of(root), Vec3.ZERO, 1.0D,
                                Vec3.ZERO, Vec3.ZERO, true),
                        new ShipControlAllocator.CarriageDemand(
                                follower, Set.of(follower), new Vec3(0.0D, 0.0D, 4.0D),
                                1.0D, Vec3.ZERO, new Vec3(5.0D, 0.0D, 0.0D), true)),
                Vec3.ZERO, Vec3.ZERO, false);
        double[] controls = allocation.controls();
        require(controls[1] > 0.2D && controls[0] < 0.05D,
                "Rotational force was not applied to the offset follower carriage");
    }

    // Create a synthetic scalar-propulsion unit for allocator verification
    private static ShipControlMap.PropulsionUnit propulsionUnit(
            int index, UUID subLevelId, Vec3 rootPosition, double thrust
    ){
        return new ShipControlMap.PropulsionUnit(
                index, subLevelId, BlockPos.ZERO, "verification", "direct_signal_v5", true,
                rootPosition, new Vec3(1.0D, 0.0D, 0.0D), 0.0D, 1.0D,
                0.0D, thrust, 0.0D, List.of());
    }

    private static void require(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }
}
