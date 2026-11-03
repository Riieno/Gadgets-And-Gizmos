package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachPath;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachAxis;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachLanes;
import com.rieno.gadgetsandgizmos.lib.navigation.DockTrafficGeometry;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingDeparturePlan;
import com.rieno.gadgetsandgizmos.lib.navigation.RouteTrafficPriority;
import com.rieno.gadgetsandgizmos.lib.navigation.TrafficCoordinator;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings.Group;
import com.rieno.gadgetsandgizmos.lib.shipping.NamedDockingOperation;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipDockScheduler;
import com.rieno.gadgetsandgizmos.lib.navigation.SplineRouteTraversal;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSplineData;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

// Replay configured lanes against live connector frames and the production flight demand
@GameTestHolder("createthrusters_docking_lanes")
@PrefixGameTestTemplate(false)
public final class DockingLaneGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/
    // Reusing a schedule command id must start a new reverse leg and retain its occupied connector
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "secondBerthDeparture")
    public static void departureKeepsTheSecondBerthAfterTheEntryChanges(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, fixture);
            var runtime = fixture.runtime();
            var ctrl = (AdvancedContraptionControllerBlockEntity)state(runtime, "controller");
            Vec3 tip = fixture.tip(), facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            var second = clearanceDock(helper, tip, facing, up).connectorTargets().getFirst();
            Vec3 otherTip = tip.add(40, 0, 0);
            var first = new ShipDockRegistry.ConnectorTarget(null, second.pos().offset(40, 0, 0), otherTip, facing, up,
                    clearanceDock(helper, otherTip, facing, up).approachPath());
            var dock = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                    first.pos(), otherTip, facing, "Two berths", false, false, false,
                    null, first.pos(), otherTip, facing, up, 0, List.of(first, second));
            helper.assertTrue(runtime.prepareDockDeparture(dock.withConnector(second), 0), "The second berth was not prepared");
            var selected = runtime.dockDepartureTarget(dock, -1);
            helper.assertTrue(selected.dock().connectorPos().equals(second.pos()) && selected.connectorIdx() == 0,
                    "Advancing the entry selected another station connector");
            var schedule = new ShippingScheduleRuntime(ctrl);
            ((Map<UUID, ShipDockRegistry.Dock>)state(schedule, "routeDockCache")).put(dock.id(), dock);
            setState(schedule, "departureDockId", dock.id());
            setState(schedule, "departureControls", List.of(tip, tip.add(0, 0, 10)));
            setState(schedule, "departureConnectorIdx", 0);
            String id = "shipping_schedule_departure";
            var complete = (Set<String>)state(runtime, "completedCommands");
            complete.add("graph_dock:" + id + ":backout" + '\0' + "ship_dock");
            complete.add("graph_dock:" + id + ":exit" + '\0' + "ship_navigate");
            helper.assertTrue((boolean)invoke(schedule, "issueUndockingControl"), "The confirmed departure was discarded during handoff");
            helper.assertTrue(!(boolean)state(runtime, "departureExiting") && !runtime.dockDepartureComplete(id),
                    "Completion from an earlier trip skipped this departure");
            var command = ((Map<?, ?>)state(runtime, "activeCommands")).get("graph_dock:" + id + ":backout" + '\0' + "ship_dock");
            var plan = (DockingDeparturePlan)state(runtime, "dockDeparturePlan");
            helper.assertTrue(command != null && plan.clearancePoint().equals(invoke(command, "targetPosition")),
                    "Departure did not start at the selected berth's exit crossing");
            helper.succeed();
        }
    }

    // A full fuel reserve must continue filling engine buffers before the ship can leave
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "refuelBuffers")
    public static void fullReserveDoesNotFinishRefuellingEmptyEngines(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)state(fixture.runtime(), "controller");
            var logistics = ctrl.logistics();
            var reserve = new EnergyStorage(1000);
            var engine = new EnergyStorage(4000);
            reserve.receiveEnergy(1000, false);
            var source = new ResourceRouting.Pool(List.of(), List.of(), List.of(reserve));
            var input = new ResourceRouting.Pool(List.of(), List.of(), List.of(engine));
            ((Map<Group, ResourceRouting.Pool>)state(logistics, "pools")).put(Group.FUEL, source);
            setState(logistics, "consumerInputs", List.of(input));
            setState(logistics, "consumers", List.of());
            setState(logistics, "poolTick", helper.getLevel().getGameTime());
            var schedule = new ShippingScheduleRuntime(ctrl);
            var low = new ShipCargoAutomation.FuelStatus(0.2D, 1, 0, false);
            helper.assertTrue(!(boolean)invoke(schedule, "currentFuelRefillComplete", low),
                    "A full supply container released a ship whose engines still need fuel");
            helper.assertTrue(!ShippingScheduleRuntime.fuelRefillComplete(new ShipCargoAutomation.FuelStatus(0, 0, 0, false)),
                    "Temporarily unavailable fuel storage ended refuelling");
            engine.receiveEnergy(4000, false);
            helper.assertTrue((boolean)invoke(schedule, "currentFuelRefillComplete", low),
                    "A full reserve and filled engines did not finish refuelling");
            var solid = new ItemStackHandler(1);
            var solidInput = new ItemStackHandler(1);
            solid.setStackInSlot(0, new ItemStack(Items.COAL, 64));
            var solidSource = new ResourceRouting.Pool(List.of(solid), List.of(), List.of());
            var solidTarget = new ResourceRouting.Pool(List.of(solidInput), List.of(), List.of());
            helper.assertTrue(ResourceRouting.canMove(solidSource, solidTarget, stack -> true, stack -> false),
                    "Pending solid engine fuel was not detected");
            helper.assertTrue(!ResourceRouting.canMove(solidSource, solidTarget, stack -> false, stack -> false)
                    && solid.getStackInSlot(0).getCount() == 64 && solidInput.getStackInSlot(0).isEmpty(),
                    "Checking solid fuel changed storage or bypassed the fuel filter");
            var fluid = new FluidTank(4000);
            var fluidInput = new FluidTank(16000);
            fluid.setFluid(new FluidStack(Fluids.WATER, 4000));
            var fluidSource = new ResourceRouting.Pool(List.of(), List.of(fluid), List.of());
            var fluidTarget = new ResourceRouting.Pool(List.of(), List.of(fluidInput), List.of());
            helper.assertTrue(ResourceRouting.canMove(fluidSource, fluidTarget, stack -> false, stack -> true),
                    "Pending engine fluid was not detected");
            helper.assertTrue(!ResourceRouting.canMove(fluidSource, fluidTarget, stack -> false, stack -> false)
                    && fluid.getFluidAmount() == 4000 && fluidInput.isEmpty(),
                    "Checking fluid fuel changed storage or bypassed the fuel filter");
            helper.succeed();
        }
    }

    // Free flight and captured lanes use the same verified traffic move without resetting either route
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "coordinatedFreeFlight")
    public static void coordinatedEscapeDrivesFreeFlightAndRetainsItsRoute(GameTestHelper helper) throws Exception{
        try(var first = ShipDockTrafficGameTests.fixture(helper, true);
            var second = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, first);
            placeNearOrigin(helper, second);
            move(helper, second, first.tip().add(10, 0, 0));
            var fixtures = List.of(first, second);
            Map<UUID, ShipDockTrafficGameTests.Fixture> hosts = new java.util.LinkedHashMap<>();
            List<TrafficCoordinator.Vehicle> fleet = new java.util.ArrayList<>();
            String id = "graph_dock:traffic_free", key = id + '\0' + "ship_navigate";
            for(var fixture : fixtures){
                var runtime = fixture.runtime();
                Vec3 start = fixture.tip(), target = start.add(fixture == first ? 40 : -40, 0, 0);
                helper.assertTrue(runtime.execute(id, "ship_navigate", Map.of("x", target.x, "y", target.y, "z", target.z,
                        "speed", 4.0D, "avoid_collisions", 1.0D), Map.of("target_point", "docking_connector:0")),
                        "The free-flight command was rejected");
                Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(key);
                Object current = invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), cmd);
                invoke(runtime, "publishRouteTrafficIntent", key, current, cmd);
                Object intent = ((Map<?, ?>)state(runtime, "routeTrafficIntents")).get(key);
                var participant = (RouteTrafficPriority.Participant)invoke(intent, "participant");
                hosts.put(participant.vehicleId(), fixture);
                fleet.add(new TrafficCoordinator.Vehicle(participant, true, true, false));
            }
            var coordinator = new TrafficCoordinator();
            long tick = helper.getLevel().getGameTime();
            coordinator.update(tick - 40L, fleet, null, (vehicle, start, target) -> TrafficCoordinator.Clearance.PENDING);
            coordinator.update(tick, fleet, null, (vehicle, start, target) -> {
                var runtime = hosts.get(vehicle).runtime();
                Object cmd = ((Map<?, ?>)uncheckedState(runtime, "activeCommands")).get(key);
                try{
                    TrafficCoordinator.Clearance res = TrafficCoordinator.Clearance.PENDING;
                    for(int idx = 0; idx < 64 && res == TrafficCoordinator.Clearance.PENDING; idx++)
                        res = (TrafficCoordinator.Clearance)invoke(runtime, "trafficEscapeProbe", cmd, start, target);
                    return res;
                }catch(Exception err){ throw new IllegalStateException(err); }
            });
            UUID mover = hosts.keySet().stream().filter(vehicle -> coordinator.directive(vehicle).motion() != null)
                    .findFirst().orElseThrow(() -> new AssertionError("No verified free-flight escape was selected"));
            var runtime = hosts.get(mover).runtime();
            var field = ShipControlModuleRuntime.class.getDeclaredField("TRAFFIC_COORDINATORS");
            field.setAccessible(true);
            var coordinators = (Map<Object, TrafficCoordinator>)field.get(null);
            Object previous = coordinators.put(helper.getLevel(), coordinator);
            try{
                Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(key);
                Object current = invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), cmd);
                var guidance = (ShipControlModuleRuntime.NavigationGuidance)invoke(runtime, "navigationGuidanceUnprotected",
                        key, current, cmd, invoke(cmd, "targetPosition"));
                helper.assertTrue(guidance.reactiveCollisionOverride() && guidance.controlTarget().equals(coordinator.directive(mover).motion().target()),
                        "Free-flight navigation replaced the coordinator's escape target");
                var guarded = (ShipControlModuleRuntime.NavigationGuidance)invoke(runtime, "applyReactiveCollisionAvoidance", key, current, cmd, guidance);
                helper.assertTrue(guarded.controlTarget().equals(guidance.controlTarget()), "Reactive avoidance fought the coordinated escape");
                Vec3 forward = (Vec3)invoke(runtime, "rootDirectionToWorld", invoke(runtime, "controllerForwardRoot"));
                Vec3 up = (Vec3)invoke(runtime, "rootDirectionToWorld", invoke(runtime, "controllerUpRoot"));
                Object demand = invoke(runtime, "controlModeDemand", key, current, cmd, guarded, forward, up, forward.cross(up).normalize(), false);
                Object speed = invoke(demand, "speedPlan");
                helper.assertTrue((double)invoke(speed, "permittedSpeed") > 0,
                        "Collision or traffic braking vetoed a verified clearing move: " + speed);
                Vec3 original = (Vec3)invoke(cmd, "targetPosition");
                runtime.executeApproachSpline(id, Map.of("speed", 4.0D, "tolerance", 0.5D),
                        Map.of("target_point", "docking_connector:0"), List.of(hosts.get(mover).tip(), original), false, UUID.randomUUID());
                var retained = (SplineRouteTraversal)((Map<?, ?>)state(runtime, "authoredDockRoutes")).get(key);
                int segment = retained.segment();
                guidance = (ShipControlModuleRuntime.NavigationGuidance)invoke(runtime, "navigationGuidanceUnprotected", key, current, cmd, original);
                helper.assertTrue(guidance.reactiveCollisionOverride() && retained.segment() == segment,
                        "Moving aside reset the captured lane cursor");
                helper.assertTrue(original.equals(invoke(((Map<?, ?>)state(runtime, "activeCommands")).get(key), "targetPosition")),
                        "Temporary recovery changed the ship's destination");
            }finally{
                if(previous == null) coordinators.remove(helper.getLevel());
                else coordinators.put(helper.getLevel(), (TrafficCoordinator)previous);
            }
            helper.succeed();
        }
    }

    private static Object uncheckedState(Object target, String name){
        try{ return state(target, name); }
        catch(Exception err){ throw new IllegalStateException(err); }
    }

    // Moving aside must preserve another ship's already reserved departure merge
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "coordinatedDepartureReservation")
    public static void coordinatedEscapeRespectsDepartureReservations(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, fixture);
            var runtime = fixture.runtime();
            Vec3 start = fixture.tip(), target = start.add(10, 0, 0);
            String id = "graph_dock:reserved_traffic", key = id + '\0' + "ship_navigate";
            helper.assertTrue(runtime.execute(id, "ship_navigate", Map.of("x", target.x, "y", target.y, "z", target.z,
                    "speed", 4.0D), Map.of("target_point", "docking_connector:0")), "Free flight was rejected");
            Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(key);
            var owner = new ShipDockScheduler.RequestKey(UUID.randomUUID(), "dock_clearance");
            var scheduler = ShipDockScheduler.get(helper.getLevel().getServer());
            scheduler.retainClearance(owner, new ShipDockScheduler.DockSlot(UUID.randomUUID(), "reserved_merge"),
                    List.of(new AABB(start.add(6, -20, -20), start.add(10, 20, 20))), helper.getLevel().getServer().getTickCount());
            try{
                helper.assertTrue(invoke(runtime, "trafficEscapeProbe", cmd, start, target) == TrafficCoordinator.Clearance.BLOCKED,
                        "A coordinated clearing move crossed another ship's departure reservation");
            }finally{ scheduler.releaseClearance(owner); }
            helper.succeed();
        }
    }

    // Keep a docked ship behind passing traffic, then reserve its merge until the hull clears it
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "departurePassingTraffic")
    public static void passingTrafficClearsBeforeTheDepartureMergeIsReserved(GameTestHelper helper) throws Exception{
        try(var first = ShipDockTrafficGameTests.fixture(helper, true);
            var passing = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, first);
            placeNearOrigin(helper, passing);
            move(helper, passing, first.tip().add(100, 0, 0));
            var runtime = first.runtime();
            var dock = clearanceDock(helper, first.tip(), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
            helper.assertTrue(runtime.prepareDockDeparture(dock, 0), "The empty departure was not available");
            var plan = (DockingDeparturePlan)state(runtime, "dockDeparturePlan");
            Vec3 merge = DockTrafficGeometry.routePrefix(plan.exitControls(), 4).getLast();
            var scheduler = ShipDockScheduler.get(helper.getLevel().getServer());
            var owner = (ShipDockScheduler.RequestKey)state(runtime, "maneuverOwner");
            scheduler.releaseClearance(owner);
            invoke(runtime, "clearDockManeuver");
            move(helper, passing, merge.add(0, 0, 4));
            publishPassingTraffic(helper, passing.runtime(), new Vec3(0, 0, -8));
            helper.assertTrue(!runtime.prepareDockDeparture(dock, 0), "Departure cut across traffic already passing the exit merge");
            helper.assertTrue(state(runtime, "departureCommand").equals(""), "A blocked departure started its backout");
            move(helper, passing, merge.add(0, 0, -20));
            publishPassingTraffic(helper, passing.runtime(), new Vec3(0, 0, -8));
            helper.assertTrue(runtime.prepareDockDeparture(dock, 0), "Departure did not recover when passing traffic cleared");
            long tick = helper.getLevel().getServer().getTickCount();
            List<AABB> hull = List.of(new AABB(-0.25D, -0.25D, -0.25D, 0.25D, 0.25D, 0.25D));
            List<Vec3> crossing = List.of(merge.add(0, 0, -6), merge.add(0, 0, 6));
            helper.assertTrue(DockTrafficGeometry.blocker(crossing, hull, scheduler.clearanceObstacles(null, tick)) != null,
                    "The departure reservation did not stop traffic crossing its exit lane");
            helper.assertTrue(DockTrafficGeometry.blocker(crossing.stream().map(pos -> pos.add(0, 30, 0)).toList(),
                    hull, scheduler.clearanceObstacles(null, tick)) == null, "The merge reservation blocked a separate lane");
            helper.assertTrue(runtime.beginDockDeparture("passing_exit", dock, 0), "The reserved departure did not begin");
            setState(runtime, "departureExiting", true);
            move(helper, first, plan.clearancePoint().add(80, 0, 0));
            invoke(runtime, "tickDockClearance");
            helper.assertTrue(scheduler.clearanceObstacles(null, tick).stream().noneMatch(val -> val.owner().equals(owner.ownerId())),
                    "The departure kept blocking its merge after its hull cleared it");
            helper.assertTrue(state(runtime, "dockDeparturePlan") != null && !runtime.dockDepartureComplete("passing_exit"),
                    "Releasing merge ownership discarded the remaining exit lane");
            helper.succeed();
        }
    }

    // One graph movement request must survive blocked departure and retry without another graph pulse
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "queuedGraphDeparture")
    public static void graphDepartureRetriesAfterPassingTrafficClears(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, fixture);
            var runtime = fixture.runtime();
            var dock = clearanceDock(helper, fixture.tip(), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
            helper.setBlock(new BlockPos(1, 1, 4), fixture.connector().getBlockState());
            var station = (DockingConnectorBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 4));
            fixture.connector().powered = true;
            station.powered = true;
            fixture.connector().extension.startWithValue(1);
            station.extension.startWithValue(1);
            fixture.connector().setDock(station, true, null, null, null);
            station.setDock(fixture.connector(), true, null, null, null);
            Object named = state(runtime, "namedDocking");
            var choiceType = Class.forName(NamedShipDockRuntime.class.getName() + "$Choice");
            var choiceCtor = choiceType.getDeclaredConstructors()[0];
            choiceCtor.setAccessible(true);
            Object choice = choiceCtor.newInstance(dock, 0);
            var targetCtor = NamedShipDockRuntime.Target.class.getDeclaredConstructors()[0];
            targetCtor.setAccessible(true);
            Object target = targetCtor.newInstance(dock.name(), List.of(choice));
            setState(target, "choice", choice);
            setState(target, "engaged", true);
            setState(named, "selected", target);
            Object operation = state(named, "operation");
            setState(operation, "target", target);
            setState(operation, "phase", NamedDockingOperation.Phase.COMPLETE);
            var scheduler = ShipDockScheduler.get(helper.getLevel().getServer());
            var blocker = new ShipDockScheduler.RequestKey(UUID.randomUUID(), "passing");
            scheduler.retainClearance(blocker, new ShipDockScheduler.DockSlot(UUID.randomUUID(), "passing"),
                    List.of(new AABB(fixture.tip().add(-30, -2, 8), fixture.tip().add(30, 8, 12))),
                    helper.getLevel().getServer().getTickCount());
            try{
                Vec3 destination = fixture.tip().add(100, 0, 0);
                helper.assertTrue(runtime.execute("next_graph_trip", "ship_navigate", Map.of("x", destination.x, "y", destination.y, "z", destination.z),
                        Map.of("target_point", "docking_connector:0")), "The queued graph request was rejected");
                helper.assertTrue(state(runtime, "pendingDockMovement") != null, "A blocked departure lost its graph movement request");
                helper.assertTrue(state(runtime, "departureCommand").equals(""), "Graph movement departed before its merge cleared");
                helper.assertTrue(DockingConnectorAutomation.isLockedPair(fixture.connector(), station),
                        "Waiting for departure released the native docking pair");
                scheduler.releaseClearance(blocker);
                invoke(runtime, "tickPendingDockMovement");
                helper.assertTrue(state(runtime, "departureCommand").equals("graph_dock:departure"), "The graph departure needed another command to recover");
                helper.assertTrue(state(runtime, "pendingDockMovement") != null,
                        "Graph movement replaced departure before its exit lane was complete");
            }finally{
                scheduler.releaseClearance(blocker);
                fixture.connector().unDock();
                station.unDock();
            }
            helper.succeed();
        }
    }



    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "northConnector")
    public static void northConnectorCorrectsItsLaneOffset(GameTestHelper helper) throws Exception{
        verifyLane(helper, Direction.NORTH);
    }

    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "eastConnector")
    public static void eastConnectorCorrectsItsLaneOffset(GameTestHelper helper) throws Exception{
        verifyLane(helper, Direction.EAST);
    }

    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "southConnector")
    public static void southConnectorCorrectsItsLaneOffset(GameTestHelper helper) throws Exception{
        verifyLane(helper, Direction.SOUTH);
    }

    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "westConnector")
    public static void westConnectorCorrectsItsLaneOffset(GameTestHelper helper) throws Exception{
        verifyLane(helper, Direction.WEST);
    }

    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "upConnector")
    public static void upConnectorCorrectsItsLaneOffset(GameTestHelper helper) throws Exception{
        verifyLane(helper, Direction.UP);
    }

    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "downConnector")
    public static void downConnectorCorrectsItsLaneOffset(GameTestHelper helper) throws Exception{
        verifyLane(helper, Direction.DOWN);
    }

    // Replay the live curve cursor where braking has already reached the arrival tolerance
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "curvedArrival")
    public static void curvedLaneReleasesDockingWithinArrivalTolerance(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true);
            var input = DockingLaneGameTests.class.getResourceAsStream("/data/createthrusters_docking_lanes/curved-lane-arrival.snbt")){
            var runtime = fixture.runtime();
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            system.getPipeline().teleport(fixture.platform(), new Vector3d(fixture.platform().logicalPose().position()).add(200, 0, 0),
                    fixture.platform().logicalPose().orientation());
            system.updatePose(fixture.platform());
            system.getPipeline().teleport(fixture.body(), new Vector3d(0, 32, 0), new Quaterniond());
            system.updatePose(fixture.body());
            invoke(runtime, "invalidateTelemetryCache");
            var tag = TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            Vec3 offset = fixture.tip().subtract(WaypointSplineData.vector(tag.getCompound("Position")));
            var recorded = SplineRouteTraversal.fromTag(tag).orElseThrow();
            List<Vec3> controls = recorded.spline().waypoints().stream().map(point -> point.add(offset)).toList();
            Vec3 target = controls.getLast();
            String id = "shipping_schedule_route", key = id + '\0' + "ship_navigate";
            helper.assertTrue(runtime.executeApproachSpline(id, Map.of("x", target.x, "y", target.y, "z", target.z,
                    "speed", 4.0D, "tolerance", 1.25D), Map.of("target_point", "docking_connector:0"), controls, true, UUID.randomUUID()),
                    "The recorded curved approach was rejected");
            tag.put("Points", WaypointSplineData.points(controls));
            var route = SplineRouteTraversal.fromTag(tag).orElseThrow();
            ((Map<String, SplineRouteTraversal>)state(runtime, "authoredDockRoutes")).put(key, route);
            Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(key);
            Object current = invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), cmd);
            Vec3 pos = (Vec3)invoke(current, "position");
            invoke(runtime, "navigationGuidanceUnprotected", key, current, cmd, target);
            helper.assertTrue(!route.finalLeg() && route.remainingDistance(pos) <= 1.25D,
                    "The fixture did not reproduce a short curved leg inside the braking tolerance");
            invoke(runtime, "updateCommandCompletion", invoke(runtime, "telemetry"));
            helper.assertTrue(runtime.isCommandComplete(id, "ship_navigate"),
                    "Braking stopped within arrival tolerance while a tiny unvisited curve segment prevented docking");
            helper.succeed();
        }
    }

    // A lane change must move the active endpoint used by braking and command completion
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "laneReassignment")
    public static void uncommittedLaneChangeUpdatesItsCommandTarget(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            var runtime = fixture.runtime();
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            system.getPipeline().teleport(fixture.platform(), new Vector3d(fixture.platform().logicalPose().position()).add(200, 0, 0),
                    fixture.platform().logicalPose().orientation());
            system.updatePose(fixture.platform());
            system.getPipeline().teleport(fixture.body(), new Vector3d(0, 32, 0), new Quaterniond());
            system.updatePose(fixture.body());
            invoke(runtime, "invalidateTelemetryCache");
            Vec3 tip = fixture.tip().add(0, 0, -10), facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            var axes = List.of(new DockingApproachAxis(tip, facing, 64),
                    new DockingApproachAxis(tip.add(26, 0, 0), facing, 64), new DockingApproachAxis(tip.add(52, 0, 0), facing, 64));
            var path = DockingApproachPath.captureLanes(tip, facing, up, tip.add(0, 0, 64), DockingApproachLanes.defaults(axes, up));
            var connector = new ShipDockRegistry.ConnectorTarget(null, helper.absolutePos(new BlockPos(1, 1, 4)), tip, facing, up, path);
            var dock = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                    connector.pos(), tip, facing, "Reassignment test", false, false, false,
                    null, connector.pos(), tip, facing, up, 0, List.of(connector));
            String id = "graph_dock:lane_choice:approach", key = id + '\0' + "ship_navigate";
            var primary = runtime.selectApproachRoute(id, dock, false, fixture.tip());
            var hull = List.of(new AABB(-1, -1, -1, 1, 1, 1));
            var obstruction = new DockTrafficGeometry.Obstacle(UUID.randomUUID(), List.of(new AABB(20, -1, 1, 32, 6, 11).move(tip)));
            var bypass = DockTrafficGeometry.assess(path, tip, facing, up, fixture.tip(), hull, List.of(obstruction)).controls();
            helper.assertTrue(primary.getLast().distanceTo(bypass.getLast()) > 1, "The departure did not select another lane");
            helper.assertTrue(runtime.executeApproachSpline(id, Map.of("speed", 4.0D, "tolerance", 1.25D),
                    Map.of("target_point", "docking_connector:0"), bypass, true, dock.id()), "The provisioned bypass was rejected");
            var next = runtime.refreshApproachMovement(key, dock, fixture.tip(), hull, List.of());
            helper.assertTrue(next.spline().waypoints().equals(primary), "The cleared primary lane was not restored before capture");
            Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(key);
            helper.assertTrue(primary.getLast().equals(invoke(cmd, "targetPosition")), "The active command retained the outer lane endpoint");
            helper.assertTrue(cmd.equals(((Map<?, ?>)state(runtime, "lastIssuedCommands")).get(key)),
                    "The issued command retained an obsolete endpoint");
            helper.succeed();
        }
    }

    // A rejected departure cannot fall through into travel toward another dock
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "blockedDeparture")
    public static void blockedDepartureCannotStartTransitToAnotherDock(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)state(fixture.runtime(), "controller");
            var schedule = new ShippingScheduleRuntime(ctrl);
            Vec3 tip = fixture.tip(), facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            var path = DockingApproachPath.capture(tip, facing, up, tip.add(0, 0, 10),
                    List.of(tip.add(30, 0, 10), tip.add(-30, 0, 10)));
            var connector = new ShipDockRegistry.ConnectorTarget(null, helper.absolutePos(new BlockPos(1, 1, 4)), tip, facing, up, path);
            var destination = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                    connector.pos(), tip, facing, "Next dock", false, false, false,
                    null, connector.pos(), tip, facing, up, 0, List.of(connector));
            UUID departing = UUID.randomUUID();
            setState(schedule, "attachedDockId", departing);
            setState(schedule, "departureDockId", departing);
            setState(schedule, "departureControls", List.of(tip, tip.add(0, 0, 10)));
            setState(schedule, "departureConnectorIdx", 0);
            invoke(schedule, "startRouteTerminalTransit", destination);
            var commands = (Map<?, ?>)state(fixture.runtime(), "activeCommands");
            helper.assertTrue(!commands.containsKey("shipping_schedule_route_terminal" + '\0' + "ship_navigate"),
                    "A failed departure issued transit and left the previous dock's departure state behind");
            helper.assertTrue(state(schedule, "phase").toString().equals("UNDOCKING"), "The blocked departure did not retain its owning phase");
            helper.assertTrue(commands.containsKey("shipping_schedule_dock_recovery_hold" + '\0' + "ship_climb"),
                    "Waiting for departure clearance disabled altitude support");
            helper.succeed();
        }
    }

    // Reconcile the old berth before reissuing a departure to a ship already at another entry
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "staleDeparture")
    public static void clearedDepartureDoesNotReturnToPreviousDock(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            var runtime = fixture.runtime();
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            system.getPipeline().teleport(fixture.body(), new Vector3d(0, 32, 0), new Quaterniond());
            system.updatePose(fixture.body());
            invoke(runtime, "invalidateTelemetryCache");
            Vec3 tip = fixture.tip(), facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            var path = DockingApproachPath.capture(tip, facing, up, tip.add(0, 0, 10),
                    List.of(tip.add(30, 0, 10), tip.add(-30, 0, 10)));
            var connector = new ShipDockRegistry.ConnectorTarget(null, helper.absolutePos(new BlockPos(1, 1, 4)), tip, facing, up, path);
            var departing = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                    connector.pos(), tip, facing, "Previous dock", false, false, false,
                    null, connector.pos(), tip, facing, up, 0, List.of(connector));
            String id = "shipping_schedule_departure";
            helper.assertTrue(!runtime.dockDepartureCleared(id, departing, 0), "A ship still inside its docking sweep was released");
            move(helper, fixture, tip.add(100, 0, 0));
            setState(runtime, "departureCommand", id);
            helper.assertTrue(!runtime.dockDepartureCleared(id, departing, 0), "An active departure skipped its remaining exit lane");
            setState(runtime, "departureCommand", "");
            helper.assertTrue(runtime.dockDepartureCleared(id, departing, 0), "The separated hull retained an obsolete docking sweep");
            var ctrl = (AdvancedContraptionControllerBlockEntity)state(runtime, "controller");
            var schedule = new ShippingScheduleRuntime(ctrl);
            ((Map<UUID, ShipDockRegistry.Dock>)state(schedule, "routeDockCache")).put(departing.id(), departing);
            setState(schedule, "departureDockId", departing.id());
            setState(schedule, "departureControls", List.of(tip, tip.add(0, 0, 10)));
            setState(schedule, "departureConnectorIdx", 0);
            invoke(schedule, "tickUndocking");
            helper.assertTrue(((List<?>)state(schedule, "departureControls")).isEmpty(), "The schedule retained departure state from another dock");
            helper.assertTrue(state(runtime, "departureCommand").equals(""), "The ship was sent back to its previous dock");
            helper.succeed();
        }
    }

    // Publish live hulls in each tracked connector's frame before resolving fleet traffic
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "separatedFleet")
    public static void separatedLiveRoutesDoNotBlockEachOther(GameTestHelper helper) throws Exception{
        try(var first = ShipDockTrafficGameTests.fixture(helper, true);
            var second = ShipDockTrafficGameTests.fixture(helper, true)){
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            for(var fixture : List.of(first, second)){
                system.getPipeline().teleport(fixture.body(), new Vector3d(0, fixture == first ? 32 : 48, 0), new Quaterniond());
                system.updatePose(fixture.body());
                invoke(fixture.runtime(), "invalidateTelemetryCache");
                String id = fixture == first ? "graph_dock:one:approach" : "shipping_schedule_route";
                Vec3 start = fixture.tip(), target = start.add(30, 0, 0);
                var runtime = fixture.runtime();
                helper.assertTrue(runtime.executeApproachSpline(id, Map.of("speed", 4.0D, "tolerance", 1.25D),
                        Map.of("target_point", "docking_connector:0"), List.of(start, target), false, UUID.randomUUID()), "The separated route was rejected");
                String key = id + '\0' + "ship_navigate";
                Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(key);
                Object current = invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), cmd);
                invoke(runtime, "publishRouteTrafficIntent", key, current, cmd);
            }
            for(var fixture : List.of(first, second)){
                String key = (fixture == first ? "graph_dock:one:approach" : "shipping_schedule_route") + '\0' + "ship_navigate";
                Object res = invoke(fixture.runtime(), "routeTrafficDecision", key);
                helper.assertTrue(!(boolean)invoke(res, "conflict"), "Separate docks' live routes acquired each other's traffic ownership: " + res);
            }
            helper.succeed();
        }
    }

    // Final docking reserves its local axis instead of the preceding navigation path
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "localClearance")
    public static void remoteShipCannotReserveItsEntireFlightToADock(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, fixture);
            Vec3 anchor = fixture.tip(), tip = anchor.add(40, 0, 0), facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            var dock = clearanceDock(helper, tip, facing, up);
            helper.assertTrue(fixture.runtime().prepareDockManeuver(dock, 0), "The empty final docking corridor was rejected");
            var bounds = (List<AABB>)state(fixture.runtime(), "maneuverBounds");
            helper.assertTrue(bounds.stream().noneMatch(box -> box.contains(anchor)),
                    "Final docking reserved the remote ship's forty-block flight instead of its docking axis");
            helper.assertTrue(bounds.stream().allMatch(box -> box.getXsize() < 20), "A local docking reservation spanned unrelated routes");
            helper.succeed();
        }
    }

    // Direct docking without authored lanes continues to publish its actual physical occupancy
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "directClearance")
    public static void unconfiguredDockKeepsItsPhysicalClearance(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, fixture);
            var runtime = fixture.runtime();
            Vec3 anchor = fixture.tip(), tip = anchor.add(40, 0, 0), facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            var connector = new ShipDockRegistry.ConnectorTarget(null, helper.absolutePos(new BlockPos(1, 1, 4)), tip, facing, up, DockingApproachPath.DEFAULT);
            var dock = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                    connector.pos(), tip, facing, "Direct dock", false, false, false,
                    null, connector.pos(), tip, facing, up, 0, List.of(connector));
            helper.assertTrue(runtime.prepareDockManeuver(dock, 0), "The direct docking clearance was rejected");
            var bounds = List.copyOf((List<AABB>)state(runtime, "maneuverBounds"));
            invoke(runtime, "tickDockClearance");
            helper.assertTrue(bounds.equals(state(runtime, "maneuverBounds")), "Direct docking gained an authored lane reservation");
            helper.succeed();
        }
    }

    // Rebuild a restored oversized reservation before deciding that its owning hull is still inside it
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "restoredClearance")
    public static void oversizedOldDockClearanceCannotHoldAnotherDock(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, fixture);
            var runtime = fixture.runtime();
            Vec3 anchor = fixture.tip(), tip = anchor.add(40, 0, 0), facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            var dock = clearanceDock(helper, tip, facing, up);
            helper.assertTrue(runtime.prepareDockManeuver(dock, 0), "The initial docking reservation was rejected");
            var owner = (ShipDockScheduler.RequestKey)state(runtime, "maneuverOwner");
            var slot = (ShipDockScheduler.DockSlot)state(runtime, "maneuverSlot");
            var hull = (List<AABB>)invoke(runtime, "approachHull", dock);
            var stale = DockTrafficGeometry.sweptBounds(List.of(anchor, tip), hull, 1);
            setState(runtime, "maneuverBounds", stale);
            runtime.restoreShutdownSnapshot(runtime.createShutdownSnapshot());
            var scheduler = ShipDockScheduler.get(helper.getLevel().getServer());
            scheduler.retainClearance(owner, slot, stale, helper.getLevel().getServer().getTickCount());
            invoke(runtime, "tickDockClearance");
            helper.assertTrue(state(runtime, "maneuverSlot") == null,
                    "The ship remained trapped inside its obsolete navigation-sized reservation");
            helper.assertTrue(scheduler.clearanceObstacles(null, helper.getLevel().getServer().getTickCount()).stream()
                    .noneMatch(obstacle -> obstacle.owner().equals(owner.ownerId())), "The obsolete sweep still blocked other schedules");
            var next = clearanceDock(helper, anchor.add(0, 0, -10), facing, up);
            helper.assertTrue(runtime.prepareDockManeuver(next, 0), "The old dock's retained clearance prevented docking at another dock");
            helper.succeed();
        }
    }

    // Keep altitude correction available when the lane projection is blocked far below the actual ship
    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "laneRejoin")
    public static void displacedCapturedShipRejoinsWithoutScheduleReset(GameTestHelper helper) throws Exception{
        var scheduler = ShipDockScheduler.get(helper.getLevel().getServer());
        var owner = new ShipDockScheduler.RequestKey(UUID.randomUUID(), "other_dock_clearance");
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            placeNearOrigin(helper, fixture);
            var runtime = fixture.runtime();
            Vec3 forward = (Vec3)invoke(runtime, "controllerForwardRoot"), up = (Vec3)invoke(runtime, "controllerUpRoot");
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            double yaw = Math.atan2(forward.z, forward.x);
            system.getPipeline().teleport(fixture.body(), fixture.body().logicalPose().position(), new Quaterniond().rotateY(yaw));
            system.updatePose(fixture.body());
            invoke(runtime, "invalidateTelemetryCache");
            Vec3 entry = fixture.tip(), target = entry.add(30, 0, 0);
            var dock = clearanceDock(helper, entry.add(40, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
            String id = "shipping_schedule_route", key = id + '\0' + "ship_navigate";
            helper.assertTrue(runtime.executeApproachSpline(id, Map.of("speed", 4.0D, "tolerance", 1.25D),
                    Map.of("target_point", "docking_connector:0"), List.of(entry, entry.add(15, 0, 0), target), false, dock.id()),
                    "The directed lane was rejected");
            ((Map<String, ShipDockRegistry.Dock>)state(runtime, "authoredDockTargets")).put(key, dock);
            scheduler.retainClearance(owner, new ShipDockScheduler.DockSlot(UUID.randomUUID(), "separate_dock"),
                    List.of(new AABB(-4, -4, -4, 4, 4, 4).move(entry)), helper.getLevel().getServer().getTickCount());
            move(helper, fixture, entry.add(0, 20, 0));
            Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(key);
            Object current = invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), cmd);
            Object guidance = invoke(runtime, "navigationGuidanceUnprotected", key, current, cmd, target);
            Object demand = invoke(runtime, "controlModeDemand", key, current, cmd, guidance, forward, up, forward.cross(up).normalize(), false);
            helper.assertTrue((double)invoke(invoke(demand, "speedPlan"), "permittedSpeed") > 0,
                    "An obstacle at the lane projection stopped the displaced ship from rejoining");
            Vec3 force = (Vec3)invoke(invoke(demand, "output"), "force");
            helper.assertTrue(force.y < -1.0E-4D, "The displaced ship did not receive a downward correction toward its lane: " + force);
            var route = (SplineRouteTraversal)((Map<?, ?>)state(runtime, "authoredDockRoutes")).get(key);
            helper.assertTrue(route.captured() && route.segment() == 0, "Rejoining discarded the lane's ordered progress");
            helper.succeed();
        }finally{ scheduler.releaseClearance(owner); }
    }

    @GameTest(template = "empty", setupTicks = 2, timeoutTicks = 100, batch = "departurePoint")
    public static void backoutReachesTheAuthoredExitCrossing(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            var runtime = fixture.runtime();
            Vec3 tip = fixture.tip(), axis = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            Vec3 crossing = tip.add(axis.scale(10));
            var path = DockingApproachPath.capture(tip, axis, up, crossing,
                    List.of(crossing.add(30, 0, 0), crossing.add(-30, 0, 0)));
            var connector = new ShipDockRegistry.ConnectorTarget(null, helper.absolutePos(new BlockPos(1, 1, 4)), tip, axis, up, path);
            var dock = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                    connector.pos(), tip, axis, "Exit test", false, false, false,
                    null, connector.pos(), tip, axis, up, 0, List.of(connector));
            helper.assertTrue(runtime.prepareDockDeparture(dock, 0), "The exit route was not available");
            helper.assertTrue(runtime.beginDockDeparture("lane_exit", dock, 0), "The backout command was rejected");
            Object plan = state(runtime, "dockDeparturePlan");
            Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get("graph_dock:lane_exit:backout" + '\0' + "ship_dock");
            Vec3 target = (Vec3)invoke(cmd, "targetPosition");
            helper.assertTrue(target.distanceTo((Vec3)invoke(plan, "clearancePoint")) < 1.0E-6D,
                    "Backout applied another connector offset to the authored exit crossing");
            helper.succeed();
        }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void verifyLane(GameTestHelper helper, Direction facing) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true);
            var input = DockingLaneGameTests.class.getResourceAsStream("/data/createthrusters_docking_lanes/horizontal-approach.snbt")){
            var runtime = fixture.runtime();
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            system.getPipeline().teleport(fixture.platform(), new Vector3d(fixture.platform().logicalPose().position()).add(200, 0, 0),
                    fixture.platform().logicalPose().orientation());
            system.updatePose(fixture.platform());
            system.getPipeline().teleport(fixture.body(), new Vector3d(0, 32, 0), new Quaterniond());
            system.updatePose(fixture.body());
            invoke(runtime, "invalidateTelemetryCache");
            helper.getLevel().setBlock(fixture.connector().getBlockPos(), fixture.connector().getBlockState()
                    .setValue(BlockStateProperties.FACING, facing), 3);
            String probeId = "graph_dock:tracking_point";
            Vec3 nativeTip = fixture.tip();
            runtime.execute(probeId, "ship_navigate", Map.of("x", nativeTip.x, "y", nativeTip.y, "z", nativeTip.z),
                    Map.of("target_point", "docking_connector:0"));
            Object directCmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(probeId + '\0' + "ship_navigate");
            Vec3 directPoint = (Vec3)invoke(invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), directCmd), "position");
            runtime.executeApproachSpline(probeId, Map.of("x", nativeTip.x, "y", nativeTip.y, "z", nativeTip.z),
                    Map.of("target_point", "docking_connector:0"), List.of(nativeTip.add(0, 0, 20), nativeTip), false, UUID.randomUUID());
            Object laneCmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(probeId + '\0' + "ship_navigate");
            Vec3 lanePoint = (Vec3)invoke(invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), laneCmd), "position");
            helper.assertTrue(directPoint.distanceTo(lanePoint) < 1.0E-6D,
                    "Enabling approach lanes moved the tracked connector by " + directPoint.distanceTo(lanePoint) + " blocks");
            var path = DockingApproachPath.fromTag(TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
            Vec3 tip = fixture.tip().add(0, 0, -10), axis = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
            var connector = new ShipDockRegistry.ConnectorTarget(null, helper.absolutePos(new BlockPos(1, 1, 4)), tip, axis, up, path);
            var dock = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                    connector.pos(), tip, axis, "Lane test", false, false, false,
                    null, connector.pos(), tip, axis, up, 0, List.of(connector));
            String id = "graph_dock:lane_replay", key = id + '\0' + "ship_navigate";
            var controls = runtime.selectApproachRoute(id, dock, false, fixture.tip());
            helper.assertTrue(controls.size() >= 2, "Configured ingress route was unavailable");
            Vec3 target = controls.getLast();
            helper.assertTrue(runtime.executeApproachSpline(id, Map.of("x", target.x, "y", target.y, "z", target.z,
                    "speed", 4.0D, "drive_throttle", 0.6D, "tolerance", 0.75D, "avoid_collisions", 1.0D),
                    Map.of("target_point", "docking_connector:0"), controls, false, dock.id()), "Lane command rejected");
            var routes = (Map<?, ?>)state(runtime, "authoredDockRoutes");
            var route = (SplineRouteTraversal)routes.get(key);
            for(Vec3 pos : route.spline().sample(0.25D)) route.update(pos, 1.25D, 0.5D);
            Vec3 forward = (Vec3)invoke(runtime, "controllerForwardRoot");
            Vec3 localUp = (Vec3)invoke(runtime, "controllerUpRoot");
            Vec3 heading = route.forward(0).normalize();
            double yaw = Math.atan2(forward.z, forward.x) - Math.atan2(heading.z, heading.x);
            system.getPipeline().teleport(fixture.body(), fixture.body().logicalPose().position(), new Quaterniond().rotateY(yaw));
            system.updatePose(fixture.body());
            Vec3 offset = new Vec3(-heading.z, 0, heading.x).normalize().scale(3);
            move(helper, fixture, target.add(offset));
            Object cmd = ((Map<?, ?>)state(runtime, "activeCommands")).get(key);
            Object current = invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), cmd);
            helper.assertTrue(((Vec3)invoke(current, "position")).distanceTo(target.add(offset)) < 0.01D,
                    "Lane telemetry added a connector offset: expected=" + target.add(offset)
                            + "; native=" + fixture.tip() + "; actual=" + invoke(current, "position")
                            + "; mapped=" + runtime.mappedDockingConnector(0));
            Object guidance = invoke(runtime, "navigationGuidanceUnprotected", key, current, cmd, target);
            Object demand = invoke(runtime, "controlModeDemand", key, current, cmd, guidance,
                    forward, localUp, forward.cross(localUp).normalize(), false);
            Object plan = invoke(demand, "speedPlan");
            helper.assertTrue((double)invoke(plan, "permittedSpeed") > 0,
                    "Configured lane stopped the connector before its crossing: " + facing + "; " + plan);
            Vec3 force = (Vec3)invoke(invoke(demand, "output"), "force");
            helper.assertTrue(force.dot(offset) < -1.0E-4D, "Configured lane did not correct its horizontal offset: " + force);
            move(helper, fixture, target);
            current = invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), cmd);
            invoke(runtime, "navigationGuidanceUnprotected", key, current, cmd, target);
            invoke(runtime, "updateCommandCompletion", invoke(runtime, "telemetry"));
            helper.assertTrue(runtime.isCommandComplete(id, "ship_navigate"), "Connector at the configured crossing did not release the lane");
            helper.succeed();
        }
    }

    private static void move(GameTestHelper helper, ShipDockTrafficGameTests.Fixture fixture, Vec3 point) throws Exception{
        var system = SubLevelPhysicsSystem.get(helper.getLevel());
        Vec3 delta = point.subtract(fixture.tip());
        system.getPipeline().teleport(fixture.body(), new Vector3d(fixture.body().logicalPose().position()).add(delta.x, delta.y, delta.z),
                fixture.body().logicalPose().orientation());
        system.updatePose(fixture.body());
        invoke(fixture.runtime(), "invalidateTelemetryCache");
    }

    private static void publishPassingTraffic(GameTestHelper helper, ShipControlModuleRuntime runtime, Vec3 velocity) throws Exception{
        Vec3 pos = (Vec3)invoke(invoke(runtime, "telemetry"), "position");
        var participant = new RouteTrafficPriority.Participant(UUID.randomUUID(), pos, velocity,
                List.of(pos, pos.add(velocity.scale(2))), null, 0.5D, 0.5D, velocity.length());
        var type = Class.forName(ShipControlModuleRuntime.class.getName() + "$TimedRouteTrafficIntent");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        ((Map<String, Object>)state(runtime, "routeTrafficIntents")).put("passing",
                ctor.newInstance(helper.getLevel().getGameTime(), participant));
    }

    private static void placeNearOrigin(GameTestHelper helper, ShipDockTrafficGameTests.Fixture fixture) throws Exception{
        var system = SubLevelPhysicsSystem.get(helper.getLevel());
        system.getPipeline().teleport(fixture.platform(), new Vector3d(fixture.platform().logicalPose().position()).add(200, 0, 0),
                fixture.platform().logicalPose().orientation());
        system.updatePose(fixture.platform());
        system.getPipeline().teleport(fixture.body(), new Vector3d(0, 32, 0), new Quaterniond());
        system.updatePose(fixture.body());
        invoke(fixture.runtime(), "invalidateTelemetryCache");
    }

    private static ShipDockRegistry.Dock clearanceDock(GameTestHelper helper, Vec3 tip, Vec3 facing, Vec3 up){
        var path = DockingApproachPath.capture(tip, facing, up, tip.add(0, 0, 10),
                List.of(tip.add(30, 0, 10), tip.add(-30, 0, 10)));
        var connector = new ShipDockRegistry.ConnectorTarget(null, helper.absolutePos(new BlockPos(1, 1, 4)), tip, facing, up, path);
        return new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                connector.pos(), tip, facing, "Clearance test", false, false, false,
                null, connector.pos(), tip, facing, up, 0, List.of(connector));
    }

    private static Object state(Object target, String name) throws Exception{
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setState(Object target, String name, Object val) throws Exception{
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, val);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception{
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
