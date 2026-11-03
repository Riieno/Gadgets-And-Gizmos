package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.DockTrafficGeometry;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachAxis;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachLanes;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachPath;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingDeparturePlan;
import com.rieno.gadgetsandgizmos.lib.navigation.SplineRouteTraversal;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyHullApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipDockScheduler;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Exercise dock traffic against full vessel footprints and the live controller's docking stages
@GameTestHolder("createthrusters_ship_doors")
@PrefixGameTestTemplate(false)
public final class ShipDockTrafficGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Vec3 FACING = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final List<AABB> HULL = List.of(new AABB(-7, -0.5D, -7, 7, 6, 0));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Docking status cannot obstruct lanes outside the parked vessel's physical bounds
    @GameTest(template = "empty")
    public static void dockedHullBlocksOnlyIntersectingLane(GameTestHelper helper){
        var obstacle = obstacle(new AABB(20, -0.5D, 1, 32, 6, 11));
        List<Vec3> primary = List.of(new Vec3(80, 0, 10), new Vec3(-30, 0, 10));
        List<Vec3> bypass = List.of(new Vec3(80, 0, 22), new Vec3(-30, 0, 22));
        helper.assertTrue(DockTrafficGeometry.blocker(primary, HULL, List.of(obstacle)) != null,
                "The primary lane ignored a docked hull in its swept path");
        helper.assertTrue(DockTrafficGeometry.blocker(bypass, HULL, List.of(obstacle)) == null,
                "A docked ship blocked a physically separate bypass");
        var clear = obstacle(new AABB(20, -0.5D, -8, 32, 6, 2));
        helper.assertTrue(DockTrafficGeometry.blocker(primary, HULL, List.of(clear)) == null,
                "Docking beside the lane incorrectly occupied it");
        helper.succeed();
    }

    // Select a connected bypass before entering an occupied primary lane
    @GameTest(template = "empty")
    public static void incomingTrafficUsesFreeConnectedLane(GameTestHelper helper){
        var path = approach(false);
        var obstacles = List.of(obstacle(new AABB(20, -0.5D, 1, 32, 6, 11)));
        var res = DockTrafficGeometry.assess(path, Vec3.ZERO, FACING, UP, new Vec3(90, 0, 10), HULL, obstacles);
        helper.assertTrue(res.reachable(), "A free connected bypass was not reachable");
        helper.assertTrue(DockTrafficGeometry.blocker(res.controls(), HULL, obstacles) == null,
                "Incoming traffic selected the occupied primary lane");
        helper.assertTrue(res.controls().stream().anyMatch(pos -> pos.z > 18), "Incoming traffic did not use the bypass");
        helper.succeed();
    }

    // Reassess traffic while the connected split remains ahead of the retained cursor
    @GameTest(template = "empty")
    public static void capturedTrafficCanTakeUpcomingBypass(GameTestHelper helper){
        var path = approach(false);
        var res = DockTrafficGeometry.assess(path, Vec3.ZERO, FACING, UP, new Vec3(90, 0, 10), HULL, List.of());
        var retained = new SplineRouteTraversal(WaypointSpline.of(res.controls()), false);
        Vec3 pos = retained.entry().add(retained.forward(0).normalize());
        retained.update(pos, 1.25D, 1);
        var obstacles = List.of(obstacle(new AABB(20, -0.5D, 1, 32, 6, 11)));
        var next = DockTrafficGeometry.refreshApproach(path, Vec3.ZERO, FACING, UP, retained, pos, HULL, obstacles);
        helper.assertTrue(next != retained, "A captured ship ignored the free split ahead");
        helper.assertTrue(next.captured(), "A connected bypass reset lane capture");
        helper.assertTrue(DockTrafficGeometry.blocker(next.remainingControls(pos), HULL, obstacles) == null,
                "The refreshed route retained the blocked lane");
        helper.succeed();
    }

    // An occupied route remains a waitable lane, with braking before the obstructing hull
    @GameTest(template = "empty")
    public static void occupiedLanesProvideStoppingDistance(GameTestHelper helper){
        var path = approach(false);
        var obstacles = List.of(obstacle(new AABB(20, -0.5D, 1, 32, 6, 28)));
        var res = DockTrafficGeometry.assess(path, Vec3.ZERO, FACING, UP, new Vec3(90, 0, 10),
                HULL, obstacles, null, true);
        helper.assertTrue(res.reachable() && res.blocker() != null, "Occupied traffic lost its waitable directed route");
        double clearance = DockTrafficGeometry.clearance(res.controls(), 200, HULL, obstacles);
        helper.assertTrue(clearance > 0 && clearance < WaypointSpline.of(res.controls()).length(),
                "The lane did not expose braking room before the blocked hull");
        helper.assertTrue(DockTrafficGeometry.clearance(List.of(new Vec3(35, 0, 10), new Vec3(0, 0, 10)),
                100, HULL, obstacles) == 0, "A ship touching occupied traffic was allowed to continue");
        helper.succeed();
    }

    // A bend must be checked along the lane rather than by a direct ray to the destination
    @GameTest(template = "empty")
    public static void laneBrakingFollowsCurves(GameTestHelper helper){
        var points = List.of(Vec3.ZERO, new Vec3(10, 0, 0), new Vec3(10, 0, 10));
        var hull = List.of(new AABB(-1, -1, -1, 1, 1, 1));
        var obstacle = obstacle(new AABB(9, -1, 7, 11, 1, 9));
        double clearance = DockTrafficGeometry.clearance(points, 100, hull, List.of(obstacle));
        helper.assertTrue(Math.abs(clearance - 15.75D) < 1.0E-6D, "Stopping distance did not follow the ordered bend");
        helper.succeed();
    }

    // A close follower behind the initial hull cannot impose a braking limit on its leader
    @GameTest(template = "empty")
    public static void followingHullDoesNotSlowLeadShip(GameTestHelper helper){
        var hull = List.of(new AABB(-4, -1, -2, 4, 1, 2));
        var behind = obstacle(new AABB(-12, -1, -2, -3.9D, 1, 2));
        var points = List.of(Vec3.ZERO, Vec3.ZERO, new Vec3(24, 0, 0));
        helper.assertTrue(DockTrafficGeometry.clearance(points, 24, hull, List.of(behind)) == 24,
                "A follower behind the hull imposed a braking limit on the leader");
        var ahead = obstacle(new AABB(3.9D, -1, -2, 12, 1, 2));
        helper.assertTrue(DockTrafficGeometry.clearance(points, 24, hull, List.of(ahead)) == 0,
                "The leader stopped checking an obstruction ahead");
        double returning = DockTrafficGeometry.clearance(List.of(Vec3.ZERO, new Vec3(24, 0, 0), new Vec3(-24, 0, 0)),
                100, hull, List.of(behind));
        helper.assertTrue(returning > 24 && returning < 72, "The lane ignored a later return toward the following hull");
        helper.succeed();
    }

    // Back out on the connector axis and then follow a free forward exit
    @GameTest(template = "empty")
    public static void departureUsesFreeDirectedExit(GameTestHelper helper){
        Vec3 tip = new Vec3(52, 0, 0);
        var path = approach(false, tip);
        var obstacles = List.of(obstacle(new AABB(20, -0.5D, 1, 32, 6, 11)));
        var plan = DockingDeparturePlan.select(path, tip, FACING, UP, tip.add(0, 0, 10), HULL, obstacles,
                DockTrafficGeometry.Traffic.EMPTY);
        helper.assertTrue(plan.isPresent(), "Departure ignored a free exit lane");
        var selected = plan.orElseThrow();
        helper.assertTrue(Math.abs(selected.clearancePoint().x - tip.x) < 1.0E-6D,
                "Undocking left the connector axis before reaching its lane");
        helper.assertTrue(selected.exitControls().getFirst().equals(selected.clearancePoint()),
                "Undocking did not hand off at the exit spline");
        helper.assertTrue(selected.exitControls().getLast().x < tip.x, "Departure ignored the authored exit direction");
        var controls = new ArrayList<>(selected.exitControls());
        controls.addFirst(tip);
        helper.assertTrue(DockTrafficGeometry.blocker(controls, HULL, obstacles) == null,
                "Departure chose the occupied lane");
        helper.succeed();
    }

    // Respect a player reversing the station's enter and exit roles
    @GameTest(template = "empty")
    public static void departureRespectsReversedLaneDirection(GameTestHelper helper){
        Vec3 tip = new Vec3(26, 0, 0);
        var plan = DockingDeparturePlan.select(approach(true, tip), tip, FACING, UP, tip.add(0, 0, 10), HULL,
                List.of(), DockTrafficGeometry.Traffic.EMPTY).orElseThrow();
        helper.assertTrue(plan.exitControls().getLast().x > tip.x, "Reversed exit roles still sent ships in the old direction");
        helper.succeed();
    }

    // Keep the selected merge reserved while native lock state survives the departure request
    @GameTest(template = "empty", timeoutTicks = 100, batch = "departureClearance")
    public static void preparedDepartureRetainsItsLaneWhileLocked(GameTestHelper helper){
        try(var fixture = fixture(helper)){
            Vec3 tip = fixture.tip();
            helper.setBlock(new BlockPos(1, 1, 4), fixture.connector().getBlockState());
            var peer = (DockingConnectorBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 4));
            fixture.connector().powered = true;
            peer.powered = true;
            fixture.connector().extension.startWithValue(1);
            peer.extension.startWithValue(1);
            fixture.connector().setDock(peer, true, null, null, null);
            peer.setDock(fixture.connector(), true, null, null, null);
            helper.assertTrue(DockingConnectorAutomation.isLockedPair(fixture.connector(), peer), "The connector pair was not locked");
            var lanes = DockingApproachLanes.defaults(List.of(new DockingApproachAxis(tip, FACING, 64)), UP)
                    .transformed(pos -> pos.add(FACING.scale(30)));
            var path = DockingApproachPath.captureLanes(tip, FACING, UP, tip.add(FACING.scale(64)), lanes);
            var dock = dock(helper, tip, FACING, UP, path, peer.getBlockPos());
            helper.assertTrue(fixture.runtime().prepareDockDeparture(dock, 0), "The clear departure was rejected");
            var plan = (DockingDeparturePlan)state(fixture.runtime(), "dockDeparturePlan");
            Vec3 merge = plan.clearancePoint();
            List<Vec3> crossing = List.of(merge.add(-40, 0, 0), merge.add(40, 0, 0));
            List<AABB> hull = List.of(new AABB(-0.5D, -0.5D, -0.5D, 0.5D, 0.5D, 0.5D));
            var scheduler = ShipDockScheduler.get(helper.getLevel().getServer());
            long tick = helper.getLevel().getServer().getTickCount();
            helper.assertTrue(DockTrafficGeometry.blocker(crossing, hull, scheduler.clearanceObstacles(null, tick)) != null,
                    "Preparing departure did not occupy the selected lane crossing");
            var snapshot = fixture.runtime().createShutdownSnapshot();
            helper.assertTrue(snapshot.getCompound("DockManeuver").getBoolean("DeparturePrepared"),
                    "The reserved departure was not retained for reload");
            fixture.runtime().restoreShutdownSnapshot(snapshot);
            invoke(fixture.runtime(), "tickDockClearance");
            var obstacles = scheduler.clearanceObstacles(null, tick);
            helper.assertTrue(DockTrafficGeometry.blocker(crossing, hull, obstacles) != null,
                    "Native lock state shrank the departure reservation back to the parked hull");
            helper.assertTrue(!scheduler.tryRetainClearance(new ShipDockScheduler.RequestKey(UUID.randomUUID(), "test_merge"),
                    new ShipDockScheduler.DockSlot(dock.id(), "another_connector"),
                    DockTrafficGeometry.sweptBounds(crossing, hull, 0), tick), "A conflicting manoeuvre entered the reserved merge");
            var separate = crossing.stream().map(pos -> pos.add(0, 20, 0)).toList();
            helper.assertTrue(DockTrafficGeometry.blocker(separate, hull, obstacles) == null,
                    "Departure also blocked an unrelated lane");
            fixture.connector().unDock();
            peer.unDock();
            helper.succeed();
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    // Exercise the schedule watchdog through long turns and a vertical arrival using its selected connector
    @GameTest(template = "empty", timeoutTicks = 100, batch = "approachProgress")
    public static void scheduleRetainsTwistingVerticalApproach(GameTestHelper helper){
        try(var fixture = fixture(helper, true)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)state(fixture.runtime(), "controller");
            var schedule = new ShippingScheduleRuntime(ctrl);
            Object phase = state(schedule, "phase");
            setState(schedule, "phase", java.util.Arrays.stream(phase.getClass().getEnumConstants())
                    .filter(val -> val.toString().equals("NAVIGATING")).findFirst().orElseThrow());
            Vec3 origin = fixture.point();
            var controls = List.of(Vec3.ZERO, new Vec3(100, 0, 0), new Vec3(100, 40, 80), new Vec3(-40, 60, 80),
                    new Vec3(-40, 80, -40), new Vec3(0, 100, -40), new Vec3(0, 100, 0))
                    .stream().map(origin::add).toList();
            Vec3 target = controls.getLast(), tip = target.subtract(UP.scale(20));
            var path = DockingApproachPath.capture(tip, UP, FACING, target, controls);
            var dock = dock(helper, tip, UP, FACING, path, helper.absolutePos(new BlockPos(1, 1, 4)));
            String id = "shipping_schedule_route", key = id + '\0' + "ship_navigate";
            helper.assertTrue(fixture.runtime().executeApproachSpline(id, Map.of("x", target.x, "y", target.y, "z", target.z,
                    "speed", 1.0D, "avoid_collisions", 1.0D), Map.of("target_point", "docking_connector:0"),
                    controls, false, dock.id()), "The approach command was rejected");
            // Start with the preceding direct-distance baseline to verify the metric handoff
            setState(schedule, "bestRouteDistance", 1.0D);
            setState(schedule, "lastRouteProgressTick", 0L);
            var routes = (Map<?, ?>)state(fixture.runtime(), "authoredDockRoutes");
            var route = (SplineRouteTraversal)routes.get(key);
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            long tick = 1000;
            for(Vec3 pos : route.spline().sample(1.0D)){
                Vec3 delta = pos.subtract(fixture.point());
                var bodyPos = new Vector3d(fixture.body().logicalPose().position()).add(delta.x, delta.y, delta.z);
                system.getPipeline().teleport(fixture.body(), bodyPos, fixture.body().logicalPose().orientation());
                system.updatePose(fixture.body());
                invoke(fixture.runtime(), "invalidateTelemetryCache");
                route.update(fixture.point(), 1.25D, 0.5D);
                helper.assertTrue(Boolean.FALSE.equals(invoke(schedule, "routeReservationLost", dock, tick)),
                        "The schedule abandoned an advancing approach at " + pos);
                tick += 10;
            }
            helper.assertTrue(route.finalLeg(), "The connector never reached the vertical arrival leg");
            helper.assertTrue(Boolean.TRUE.equals(invoke(schedule, "routeReservationLost", dock, tick + 601)),
                    "An actual stalled approach stopped triggering recovery");
            ((Map<String, Long>)state(fixture.runtime(), "blockedApproachSince")).put(key, tick);
            helper.assertTrue(Boolean.FALSE.equals(invoke(schedule, "routeReservationLost", dock, tick + 1202)),
                    "Waiting for occupied lane traffic abandoned the approach");
            helper.succeed();
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    // Keep correcting the selected connector after its projection reaches the spline endpoint
    @GameTest(template = "empty", timeoutTicks = 100, batch = "connectorCentreline")
    public static void verticalConnectorReachesApproachCentreline(GameTestHelper helper){
        verifyConnectorCentreline(helper, Direction.DOWN, -1);
    }

    // Apply the same endpoint correction to a connector mounted on top of the ship
    @GameTest(template = "empty", timeoutTicks = 100, batch = "connectorCentreline")
    public static void upwardConnectorReachesApproachCentreline(GameTestHelper helper){
        verifyConnectorCentreline(helper, Direction.UP, -1);
    }

    // Keep schedule propulsion available while its lower connector corrects the final offset
    @GameTest(template = "empty", timeoutTicks = 100, batch = "connectorCentreline")
    public static void throttledVerticalConnectorReachesApproachCentreline(GameTestHelper helper){
        verifyConnectorCentreline(helper, Direction.DOWN, 0.5D);
    }

    // Keep schedule propulsion available while its upper connector corrects the final offset
    @GameTest(template = "empty", timeoutTicks = 100, batch = "connectorCentreline")
    public static void throttledUpwardConnectorReachesApproachCentreline(GameTestHelper helper){
        verifyConnectorCentreline(helper, Direction.UP, 0.5D);
    }

    // A captured connector follows its lane instead of escaping a nearby platform
    @GameTest(template = "empty", timeoutTicks = 100, batch = "platformControl")
    public static void capturedApproachCannotEscapeItsLane(GameTestHelper helper){
        verifyPlatformControl(helper, false);
    }

    // The same lane ownership applies after the connector's reverse back-out
    @GameTest(template = "empty", timeoutTicks = 100, batch = "platformControl")
    public static void capturedDepartureCannotEscapeItsLane(GameTestHelper helper){
        verifyPlatformControl(helper, true);
    }

    // Connector capture cannot invoke proximity avoidance against its docking platform
    @GameTest(template = "empty", timeoutTicks = 100, batch = "platformControl")
    public static void connectorCaptureBypassesPlatformAvoidance(GameTestHelper helper){
        try(var fixture = fixture(helper)){
            Vec3 pos = fixture.tip();
            String id = "graph_dock:platform_capture";
            helper.assertTrue(fixture.runtime().execute(id, "ship_dock", Map.of(
                    "x", pos.x, "y", pos.y, "z", pos.z - 2, "speed", 0.35D, "avoid_collisions", 1.0D,
                    "target_direction_z", -1.0D), Map.of("target_point", "docking_connector:0")), "Capture command rejected");
            Object cmd = commands(fixture.runtime()).get(id + '\0' + "ship_dock");
            helper.assertTrue(Boolean.FALSE.equals(invoke(fixture.runtime(), "collisionAvoidanceEnabled", cmd)),
                    "Docking capture still enabled platform avoidance");
            Object telemetry = invoke(fixture.runtime(), "commandTargetTelemetry", invoke(fixture.runtime(), "telemetry"), cmd);
            Object guidance = invoke(fixture.runtime(), "dockingApproachGuidance", telemetry, cmd);
            Object res = invoke(fixture.runtime(), "applyReactiveCollisionAvoidance", id + '\0' + "ship_dock", telemetry, cmd, guidance);
            helper.assertTrue(res == guidance, "Platform avoidance changed the controlled docking translation");
            helper.succeed();
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    // Removing one connector must not transfer its active route to the COM or another connector
    @GameTest(template = "empty", timeoutTicks = 100, batch = "connectorTracking")
    public static void missingSelectedConnectorCannotShiftTrackingPoint(GameTestHelper helper){
        try(var fixture = fixture(helper)){
            var view = fixture.body().getPlot().getEmbeddedLevelAccessor();
            view.setBlock(new BlockPos(-1, 1, -2), Blocks.STONE.defaultBlockState(), 3);
            view.setBlock(new BlockPos(-1, 1, -3), fixture.connector().getBlockState(), 3);
            helper.assertTrue(fixture.runtime().mappedDockingConnectors().size() == 2, "The fixture did not expose both connectors");
            Vec3 target = fixture.point().add(0, 0, -30);
            String id = "graph_dock:selected_connector";
            helper.assertTrue(fixture.runtime().executeApproachSpline(id, Map.of("x", target.x, "y", target.y, "z", target.z,
                    "speed", 4.0D, "avoid_collisions", 1.0D), Map.of("target_point", "docking_connector:0"),
                    List.of(fixture.point(), target), false, UUID.randomUUID()), "Connector route rejected");
            Object cmd = commands(fixture.runtime()).get(id + '\0' + "ship_navigate");
            fixture.body().getLevel().setBlock(fixture.connector().getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(fixture.runtime().mappedDockingConnector(0) == null,
                    "The selected connector silently changed to another connector");
            helper.assertTrue(fixture.runtime().mappedDockingConnectors().size() == 1, "The other connector was lost");
            Object telemetry = invoke(fixture.runtime(), "commandTargetTelemetry", invoke(fixture.runtime(), "telemetry"), cmd);
            helper.assertTrue(Boolean.FALSE.equals(invoke(telemetry, "available")), "Missing connector telemetry fell back to the COM");
            helper.assertTrue(Boolean.FALSE.equals(invoke(fixture.runtime(), "commandTargetReached", telemetry, cmd)),
                    "A command completed without its selected tracking point");
            helper.succeed();
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Verify endpoint braking and flight demand against one live connector frame
    private static void verifyConnectorCentreline(GameTestHelper helper, Direction facing, double throttle){
        try(var fixture = fixture(helper)){
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            var platformPos = new Vector3d(fixture.platform().logicalPose().position()).add(100, 0, 0);
            system.getPipeline().teleport(fixture.platform(), platformPos, fixture.platform().logicalPose().orientation());
            system.updatePose(fixture.platform());
            fixture.body().getLevel().setBlock(fixture.connector().getBlockPos(),
                    fixture.connector().getBlockState().setValue(BlockStateProperties.FACING, facing), 3);
            Vec3 forward = (Vec3)invoke(fixture.runtime(), "controllerForwardRoot");
            Vec3 up = (Vec3)invoke(fixture.runtime(), "controllerUpRoot");
            Vec3 right = forward.cross(up).normalize();
            Vec3 heading = (Vec3)invoke(fixture.runtime(), "rootDirectionToWorld", forward);
            Vec3 pos = fixture.tip(), target = pos.add(0, -3, 0);
            String id = "graph_dock:connector_centreline", key = id + '\0' + "ship_navigate";
            helper.assertTrue(fixture.runtime().executeApproachSpline(id, Map.of("x", target.x, "y", target.y, "z", target.z,
                    "speed", 4.0D, "tolerance", 0.25D, "avoid_collisions", 1.0D, "drive_throttle", throttle),
                    Map.of("target_point", "docking_connector:0"), List.of(target.subtract(heading.scale(30)), target),
                    false, UUID.randomUUID()), "The connector approach was rejected");
            Object cmd = commands(fixture.runtime()).get(key);
            Object current = invoke(fixture.runtime(), "commandTargetTelemetry", invoke(fixture.runtime(), "telemetry"), cmd);
            helper.assertTrue(((Vec3)invoke(current, "position")).distanceTo(pos) < 0.05D,
                    "The vertical approach did not track the native connector contact point");
            helper.assertTrue(((Vec3)invoke(current, "position")).distanceTo(fixture.point()) > 1.49D,
                    "The approach switched to the connector base");
            Object guidance = invoke(fixture.runtime(), "navigationGuidanceUnprotected", key, current, cmd, target);
            Object demand = invoke(fixture.runtime(), "controlModeDemand", key, current, cmd, guidance, forward, up, right, false);
            Object speed = invoke(demand, "speedPlan");
            helper.assertTrue((double)invoke(speed, "targetDistance") >= 2.99D,
                    "Braking discarded the connector's three-block offset above the approach endpoint");
            helper.assertTrue((double)invoke(speed, "permittedSpeed") > 0,
                    "The connector stopped above the spline endpoint: " + facing + ", throttle=" + throttle + ", " + speed);
            Vec3 force = (Vec3)invoke(invoke(demand, "output"), "force");
            helper.assertTrue(force.y < -1.0E-4D, "The flight controller did not pull the connector down onto the spline");
            helper.assertTrue(Boolean.FALSE.equals(invoke(fixture.runtime(), "commandTargetReached", current, cmd)),
                    "The approach completed while its connector was above the endpoint");
            var bodyPos = new Vector3d(fixture.body().logicalPose().position()).add(0, -3, 0);
            system.getPipeline().teleport(fixture.body(), bodyPos, fixture.body().logicalPose().orientation());
            system.updatePose(fixture.body());
            invoke(fixture.runtime(), "invalidateTelemetryCache");
            current = invoke(fixture.runtime(), "commandTargetTelemetry", invoke(fixture.runtime(), "telemetry"), cmd);
            helper.assertTrue(Boolean.TRUE.equals(invoke(fixture.runtime(), "commandTargetReached", current, cmd)),
                    "The native contact point could not reach its approach endpoint");
            invoke(fixture.runtime(), "navigationGuidanceUnprotected", key, current, cmd, target);
            invoke(fixture.runtime(), "updateCommandCompletion", invoke(fixture.runtime(), "telemetry"));
            helper.assertTrue(fixture.runtime().isCommandComplete(id, "ship_navigate"),
                    "Connector arrival did not release the approach for docking");
            Vec3 tip = fixture.tip();
            helper.assertTrue(tip.distanceTo(target) < 0.05D, "The contact tip did not reach the authored crossing");
            String capture = "graph_dock:connector_capture";
            helper.assertTrue(fixture.runtime().execute(capture, "ship_dock", Map.of("x", tip.x, "y", tip.y, "z", tip.z,
                    "speed", 0.35D, "tolerance", 0.25D), Map.of("target_point", "docking_connector:0")),
                    "The final docking command was rejected");
            Object captureCmd = commands(fixture.runtime()).get(capture + '\0' + "ship_dock");
            Object captureTelemetry = invoke(fixture.runtime(), "commandTargetTelemetry", invoke(fixture.runtime(), "telemetry"), captureCmd);
            helper.assertTrue(((Vec3)invoke(captureTelemetry, "position")).distanceTo(tip) < 0.05D,
                    "Final docking changed the connector tracking point");
            helper.succeed();
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    // Use a three-berth station with an outward bypass and directed split and merge curves
    private static DockingApproachPath approach(boolean reversed){
        return approach(reversed, Vec3.ZERO);
    }

    // Each connector retains the same shared lanes in its own material frame
    private static DockingApproachPath approach(boolean reversed, Vec3 tip){
        var axes = List.of(new DockingApproachAxis(Vec3.ZERO, FACING, 64),
                new DockingApproachAxis(new Vec3(26, 0, 0), FACING, 64),
                new DockingApproachAxis(new Vec3(52, 0, 0), FACING, 64));
        var lanes = DockingApproachLanes.defaults(axes, UP);
        if(reversed) lanes = DockingApproachLanes.flip(lanes);
        return DockingApproachPath.captureLanes(tip, FACING, UP, tip.add(0, 0, 64), lanes);
    }

    private static DockTrafficGeometry.Obstacle obstacle(AABB bounds){
        return new DockTrafficGeometry.Obstacle(UUID.randomUUID(), List.of(bounds));
    }

    // Use the native selected connector and a platform touching the actual Sable hull
    private static void verifyPlatformControl(GameTestHelper helper, boolean departing){
        var fixture = fixture(helper);
        helper.startSequence().thenExecuteAfter(3, () -> {
            try(fixture){
                Vec3 pos = fixture.tip(), target = pos.add(0, 0, -30);
                String id = "graph_dock:platform_lane";
                helper.assertTrue(fixture.runtime().executeApproachSpline(id, Map.of("x", target.x, "y", target.y, "z", target.z,
                        "speed", 4.0D, "avoid_collisions", 1.0D), Map.of("target_point", "docking_connector:0"),
                        List.of(pos, target), false, UUID.randomUUID()), "Lane command rejected");
                String key = id + '\0' + "ship_navigate";
                Object cmd = commands(fixture.runtime()).get(key);
                Object telemetry = invoke(fixture.runtime(), "commandTargetTelemetry", invoke(fixture.runtime(), "telemetry"), cmd);
                Vec3 tracked = (Vec3)invoke(telemetry, "position");
                helper.assertTrue(tracked.distanceTo(pos) < 0.05D,
                        "Approach tracking used " + tracked + " instead of the selected connector contact point " + pos
                                + "; connectors=" + fixture.runtime().mappedDockingConnectors() + "; removed=" + fixture.body().isRemoved());
                var departures = ShipControlModuleRuntime.class.getDeclaredField("authoredRouteDepartures");
                departures.setAccessible(true);
                ((Map<String, Boolean>)departures.get(fixture.runtime())).put(key, departing);
                Object ctx = invoke(fixture.runtime(), "collisionScanContext");
                Object hull = invoke(fixture.runtime(), "cachedShipHullBounds", pos, ctx);
                Object hazard = invoke(fixture.runtime(), "collisionProximityHazard", telemetry, ctx, hull);
                helper.assertTrue(Boolean.TRUE.equals(invoke(hazard, "available")), "The platform fixture did not create a proximity hazard");
                Object guidance = invoke(fixture.runtime(), "navigationGuidanceUnprotected", key, telemetry, cmd, target);
                Object escape = invoke(fixture.runtime(), "proximityAvoidanceGuidance", key, telemetry, cmd, ctx, hull, hazard, false);
                helper.assertTrue(escape != null && Boolean.TRUE.equals(invoke(escape, "reactiveCollisionOverride")),
                        "The fixture did not reproduce an available generic proximity escape");
                Object res = invoke(fixture.runtime(), "applyReactiveCollisionAvoidance", key, telemetry, cmd, guidance);
                helper.assertTrue(Boolean.FALSE.equals(invoke(res, "reactiveCollisionOverride")),
                        "A platform proximity escape overrode the captured lane");
                helper.succeed();
            }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
        }).thenSucceed();
    }

    // Load a real SCM, native connector and collision geometry without running a thrust simulation
    static Fixture fixture(GameTestHelper helper){
        return fixture(helper, false);
    }

    // Use the controller-owned runtime when exercising schedule callbacks
    static Fixture fixture(GameTestHelper helper, boolean controllerOwned){
        var body = ShipDoorGameTests.cabin(helper, AllBlocks.TRAIN_DOOR.getDefaultState(), false);
        var view = body.getPlot().getEmbeddedLevelAccessor();
        view.setBlock(new BlockPos(0, 1, 0), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
        view.setBlock(new BlockPos(0, 2, 0), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var ctrlPos = body.getPlot().getCenterBlock().offset(0, 2, 0);
        var ctrl = (AdvancedContraptionControllerBlockEntity) body.getLevel().getBlockEntity(ctrlPos);
        var connector = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:docking_connector")).defaultBlockState();
        view.setBlock(new BlockPos(1, 1, -2), Blocks.STONE.defaultBlockState(), 3);
        view.setBlock(new BlockPos(1, 1, -3), connector, 3);
        var ship = (DockingConnectorBlockEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock().offset(1, 1, -3));
        ShipControlModuleRuntime runtime;
        try{ runtime = controllerOwned ? (ShipControlModuleRuntime)state(ctrl, "shipControlRuntime") : new ShipControlModuleRuntime(ctrl); }
        catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
        var map = new ShipControlMap(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                body.getUniqueId(), ctrlPos, Vec3.ZERO, List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        ShipControlMapStore.save(helper.getLevel(), map);
        runtime.setMapId(map.id());
        runtime.tick();
        var bounds = SableAssemblyHullApi.worldBounds(body);
        var platform = ShipDoorGameTests.newBody(helper, new Vec3(bounds.maxX + 0.625D, bounds.minY + 0.5D, bounds.minZ + 0.5D));
        SableSubLevelLifecycleApi.transfer(helper.getLevel(), platform, () -> {
            var surface = platform.getPlot().getEmbeddedLevelAccessor();
            for(int y = 0; y <= 6; y++) for(int z = 0; z <= 12; z++)
                surface.setBlock(new BlockPos(0, y, z), Blocks.STONE.defaultBlockState(), 3);
        });
        return new Fixture(body, runtime, ship, platform);
    }

    record Fixture(ServerSubLevel body, ShipControlModuleRuntime runtime, DockingConnectorBlockEntity connector,
                           ServerSubLevel platform) implements AutoCloseable{
        // Track the base block centre independently of connector extension
        Vec3 point(){ return SableTransformApi.toWorldPosition(body, connector.getBlockPos().getCenter()); }

        // Resolve the native contact point used only by final docking
        Vec3 tip(){ return SableTransformApi.toWorldPosition(body, connector.getTipPosition()); }

        @Override
        public void close(){
            runtime.close();
            SableSubLevelLifecycleApi.remove(body.getLevel(), List.of(body, platform));
        }
    }

    // Inspect the command admitted through the production control API
    private static Map<?, ?> commands(ShipControlModuleRuntime runtime) throws ReflectiveOperationException{
        var field = ShipControlModuleRuntime.class.getDeclaredField("activeCommands");
        field.setAccessible(true);
        return (Map<?, ?>)field.get(runtime);
    }

    // Supply the station frame without adding a second live controller to the fixture
    private static ShipDockRegistry.Dock dock(GameTestHelper helper, Vec3 tip, Vec3 facing, Vec3 up,
            DockingApproachPath path, BlockPos connectorPos){
        var target = new ShipDockRegistry.ConnectorTarget(null, connectorPos, tip, facing, up, path);
        return new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                connectorPos, tip, facing, "Traffic test", false, false, false,
                null, connectorPos, tip, facing, up, 0, List.of(target));
    }

    // Inspect retained state at a production stage boundary
    private static Object state(Object target, String name) throws ReflectiveOperationException{
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    // Set the preceding stage's inputs before exercising its production transition
    private static void setState(Object target, String name, Object val) throws ReflectiveOperationException{
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, val);
    }

    // Exercise private stage boundaries without changing their production visibility
    private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException{
        for(Method method : target.getClass().getDeclaredMethods()){
            if(!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean matches = true;
            for(int idx = 0; idx < args.length; idx++) if(args[idx] != null && !method.getParameterTypes()[idx].isPrimitive()
                    && !method.getParameterTypes()[idx].isInstance(args[idx])) matches = false;
            if(!matches) continue;
            method.setAccessible(true);
            return method.invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }
}
