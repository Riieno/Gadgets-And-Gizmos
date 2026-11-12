package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachPath;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotNavigationMode;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Exercise connectorless dock geometry and schedule controls through the main graph runtime
@GameTestHolder("createthrusters_docking_lanes")
@PrefixGameTestTemplate(false)
public final class ShippingScheduleGameTests{
    // Keep obsolete terminals in storage without presenting or selecting them as current dock goals
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void walkerRouteOverlayExcludesMovedDockTerminals(GameTestHelper helper) throws Exception{
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(pos, com.rieno.gadgetsandgizmos.registry.CTBlocks.SHIP_DOCK.get().defaultBlockState(), 3);
        var dockBlock = (ShipDockBlockEntity)helper.getLevel().getBlockEntity(pos);
        var registry = ShipDockRegistry.get(helper.getLevel().getServer());
        registry.update(dockBlock);
        var dock = registry.get(dockBlock.getDockId());
        var ctrl = new ConnectorlessController();
        ctrl.setLevel(helper.getLevel());
        ctrl.scheduled = true;
        try{
            var runtime = (ShipControlModuleRuntime)NavigationSpeedGameTests.field(ctrl, "shipControlRuntime");
            Vec3 target = dock.worldPosition().add(0, 5, 0);
            helper.assertTrue(runtime.createEditableScheduledRoutes(List.of(
                    new ShipControlModuleRuntime.ScheduledRouteDestination(0, dock.id(), target.add(20, 0, 0), target.add(4, 0, 0)),
                    new ShipControlModuleRuntime.ScheduledRouteDestination(0, dock.id(), target.add(20, 0, 0), target))),
                    "The stored terminal fixtures could not be created");
            var available = (List<?>)NavigationSpeedGameTests.invoke(runtime, "availableScheduledRoutes");
            var snapshot = NavigationSpeedGameTests.invoke(runtime, "shippingRouteSplineSnapshot");
            helper.assertTrue(available.size() == 1 && ((List<?>)NavigationSpeedGameTests.invoke(snapshot, "routes")).size() == 1,
                    "A saved endpoint away from the moved dock remained selectable or visible");
            helper.assertTrue(((List<?>)NavigationSpeedGameTests.field(runtime, "completedScheduledRoutes")).size() == 2,
                    "Filtering obsolete terminals deleted the player's saved route geometry");
            registry.remove(dock.id());
            var removed = NavigationSpeedGameTests.invoke(runtime, "shippingRouteSplineSnapshot");
            helper.assertTrue(((List<?>)NavigationSpeedGameTests.invoke(removed, "routes")).isEmpty()
                            && !NavigationSpeedGameTests.invoke(snapshot, "revision").equals(NavigationSpeedGameTests.invoke(removed, "revision")),
                    "Removing a dock did not invalidate the subscribed route overlay");
        }finally{
            ctrl.setRemoved();
            registry.remove(dockBlock.getDockId());
            helper.getLevel().removeBlock(pos, false);
        }
        helper.succeed();
    }

    // Complete coarse transit without requesting a ship connector which this walker does not have
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void connectorlessWalkerLeavesTransitAtAConnectorDock(GameTestHelper helper) throws Exception{
        for(boolean configured : new boolean[]{false, true}){
            var ctrl = new ConnectorlessController();
            ctrl.setLevel(helper.getLevel());
            var schedule = (ShippingScheduleRuntime)NavigationSpeedGameTests.field(ctrl, "shippingScheduleRuntime");
            Vec3 tip = new Vec3(0, 0, 0);
            var path = new DockingApproachPath(22, configured ? List.of(new Vec3(-32, 0, 2.5), new Vec3(32, 0, 2.5)) : List.of());
            var connector = new ShipDockRegistry.ConnectorTarget(null, BlockPos.ZERO, tip, new Vec3(0, 1, 0), new Vec3(0, 0, 1), path);
            var dock = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null,
                    BlockPos.ZERO, tip, new Vec3(0, 0, 1), "Transit regression", false, false, false,
                    null, BlockPos.ZERO, tip, connector.facing(), connector.up(), 0, List.of(connector));
            try{
                var cache = (Map<UUID, ShipDockRegistry.Dock>)NavigationSpeedGameTests.field(schedule, "routeDockCache");
                cache.put(dock.id(), dock);
                NavigationSpeedGameTests.set(schedule, "currentDockId", dock.id());
                helper.assertTrue((boolean)NavigationSpeedGameTests.invoke(schedule, "issueRouteTerminalTransit", dock),
                        "Coarse transit required a missing ship connector; configured=" + configured);
                helper.assertTrue(ctrl.parameters.get("tolerance") == 0.25D
                                && ctrl.parameters.get("x") == dock.worldPosition().x
                                && ctrl.parameters.get("z") == dock.worldPosition().z,
                        "The walker approach retained a hull-sized arrival margin or an offset target: " + ctrl.parameters);
                NavigationSpeedGameTests.invoke(schedule, "issueConnectorlessHover", new Vec3(20, 40, 60));
                helper.assertTrue(ctrl.parameters.get("follow_distance") == 0.1D
                                && ctrl.parameters.get("schedule_route_entry") == -1.0D,
                        "Final centering could stop at a retained transit endpoint or a hull-sized distance: " + ctrl.parameters);
                Object phase = NavigationSpeedGameTests.field(schedule, "phase");
                for(Object val : phase.getClass().getEnumConstants()) if("TRANSITING".equals(val.toString())) phase = val;
                NavigationSpeedGameTests.set(schedule, "phase", phase);
                NavigationSpeedGameTests.set(schedule, "routeTerminalTransitPending", true);
                NavigationSpeedGameTests.invoke(schedule, "tickRouteTerminalTransit");
                helper.assertTrue(!(boolean)NavigationSpeedGameTests.field(schedule, "routeTerminalTransitPending")
                                && !"TRANSITING".equals(NavigationSpeedGameTests.field(schedule, "phase").toString()),
                        "A completed coarse approach waited for a nonexistent ship connector; configured=" + configured);
            }finally{ ctrl.setRemoved(); }
        }
        helper.succeed();
    }

    // Keep connectorless targets directly above the placed dock, including rotated UP axes
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void connectorlessTargetsFollowDockUp(GameTestHelper helper){
        for(Vec3 up : List.of(new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, -1))){
            var dock = dock(helper, up);
            helper.assertTrue(dock.approach().equals(dock.worldPosition().add(up.scale(1.5D))),
                    "Connectorless approach drifted tangentially from the dock");
            helper.assertTrue(ShippingScheduleRuntime.navigationTarget(dock, false).equals(dock.approach()),
                    "Schedule navigation disagreed with the connectorless dock target");
            Vec3 reference = dock.worldPosition().add(up.scale(14));
            helper.assertTrue(ShipDockApproachHandoff.connectorlessTarget(dock, reference).equals(reference),
                    "A tall walker was pulled along the dock's UP axis");
            helper.assertTrue(ShipDockApproachHandoff.connectorlessReached(dock, reference,
                            new SableAssemblyBoundsApi.Envelope(16, 30, 16)),
                    "A tall walker overlapping the finite detection axis could not arrive");
            Vec3 sideways = Math.abs(up.x) < 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
            for(double distance : new double[]{0.75D, 4, 14}){
                helper.assertTrue(!ShipDockApproachHandoff.connectorlessReached(dock, reference.add(sideways.scale(distance)),
                                new SableAssemblyBoundsApi.Envelope(20, 30, 16)),
                        "A wide hull completed arrival " + distance + " blocks from the actual dock axis");
            }
            helper.assertTrue(ShipDockApproachHandoff.connectorlessReached(dock, reference.add(sideways.scale(0.49D)),
                            new SableAssemblyBoundsApi.Envelope(20, 30, 16)),
                    "The dock's half-block control-point tolerance was not accepted");
            helper.assertTrue(!ShipDockApproachHandoff.connectorlessReached(dock,
                            reference.add(100, 100, 100), SableAssemblyBoundsApi.Envelope.DEFAULT),
                    "A remote vehicle could complete a connectorless stop");
        }
        helper.succeed();
    }

    // Resolve placement UP from the moving dock instead of assuming world UP
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rotatedDockKeepsItsPlacementUp(GameTestHelper helper){
        var body = ShipDoorGameTests.newBody(helper);
        UUID dockId = null;
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            view.setBlock(BlockPos.ZERO, com.rieno.gadgetsandgizmos.registry.CTBlocks.SHIP_DOCK.get().defaultBlockState(), 3);
            var block = (ShipDockBlockEntity)helper.getLevel().getBlockEntity(body.getPlot().getCenterBlock());
            dockId = block.getDockId();
            var pipeline = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(helper.getLevel()).physicsSystem().getPipeline();
            pipeline.teleport(body, body.logicalPose().position(), new Quaterniond().rotateZ(Math.PI / 2));
            var registry = ShipDockRegistry.get(helper.getLevel().getServer());
            registry.update(block);
            var resolved = registry.get(dockId);
            helper.assertTrue(resolved != null && resolved.up().distanceTo(new Vec3(-1, 0, 0)) < 1.0E-6D,
                    "The rotated dock retained world UP: " + resolved);
        }finally{
            if(dockId != null) ShipDockRegistry.get(helper.getLevel().getServer()).remove(dockId);
            SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
        }
        helper.succeed();
    }

    // Preserve additive orientation metadata through SQLite reads and dock copies
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dockUpSurvivesDatabaseRoundTrip(GameTestHelper helper){
        var dock = dock(helper, new Vec3(1, 0, 0));
        try{
            helper.assertTrue(ShippingRouteDatabase.upsert(helper.getLevel().getServer(), dock), "Dock storage failed");
            var loaded = ShippingRouteDatabase.get(helper.getLevel().getServer(), dock.id());
            helper.assertTrue(loaded != null && loaded.up().equals(dock.up())
                            && loaded.withLandingZones(List.of()).up().equals(dock.up()),
                    "Dock storage or copying discarded placement UP");
        }finally{ ShippingRouteDatabase.delete(helper.getLevel().getServer(), dock.id()); }
        helper.succeed();
    }

    // Pulse every schedule action from a normal ACC graph without opening the schedule editor
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mainGraphControlsInstalledSchedule(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper, true)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)fixture.body().getLevel().getBlockEntity(
                    fixture.body().getPlot().getCenterBlock().offset(0, 2, 0));
            var facing = ctrl.getBlockState().getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING);
            fixture.body().getLevel().setBlock(ctrl.getBlockPos().relative(facing), AllBlocks.BLAZE_BURNER.getDefaultState(), 3);
            var schedule = (ShippingScheduleRuntime)NavigationSpeedGameTests.field(ctrl, "shippingScheduleRuntime");
            helper.assertTrue(schedule.install(schedule(), UUID.randomUUID(), ShippingAutoRefuelSettings.DEFAULT, true),
                    "The test schedule could not be installed");
            pulse(helper, ctrl, "shipping_start");
            pulse(helper, ctrl, "shipping_skip");
            pulse(helper, ctrl, "shipping_skip");
            helper.assertTrue((int)NavigationSpeedGameTests.field(schedule, "currentEntry") == 2,
                    "Consecutive Skip pulses were suppressed");
            pulse(helper, ctrl, "shipping_pause");
            helper.assertTrue(NavigationSpeedGameTests.field(schedule, "phase").toString().equals("PAUSED"), "Pause did not stop the schedule");
            pulse(helper, ctrl, "shipping_resume");
            helper.assertTrue(!NavigationSpeedGameTests.field(schedule, "phase").toString().equals("PAUSED"), "Resume left the schedule paused");
            pulse(helper, ctrl, "shipping_stop");
            helper.assertTrue(NavigationSpeedGameTests.field(schedule, "phase").toString().equals("PAUSED")
                    && (int)NavigationSpeedGameTests.field(schedule, "currentEntry") == 0, "Stop did not retain and reset the schedule");
            pulse(helper, ctrl, "shipping_restart");
            NavigationSpeedGameTests.set(schedule, "currentEntry", 2);
            pulse(helper, ctrl, "shipping_restart");
            helper.assertTrue((int)NavigationSpeedGameTests.field(schedule, "currentEntry") == 0, "A later Restart pulse was suppressed");
        }
        helper.succeed();
    }

    // Require execution for actions and retain their Success output for later main-graph reads
    private static void pulse(GameTestHelper helper, AdvancedContraptionControllerBlockEntity ctrl, String type){
        var graph = new AdvancedGraphDocument();
        var trigger = node("trigger", "event_graph_ready");
        var action = node("action", type);
        graph.nodes().addAll(List.of(trigger, action));
        graph.edges().add(new AdvancedGraphDocument.Edge("pulse", trigger.id(), "exec", action.id(), "exec"));
        var runtime = new GraphRuntime(ctrl);
        try{
            runtime.tick(graph, false);
            helper.assertTrue(runtime.liveOutput(action.id(), "success").asBoolean(), "Main-graph action failed: " + type + "; " + ctrl.getShippingScheduleStatus());
            helper.assertTrue(runtime.previewOutput(graph, action, "success").asBoolean(), "The action lost its Success output: " + type);
        }finally{ runtime.clear(); }
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, type, 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }

    private static Schedule schedule(){
        var schedule = new Schedule();
        schedule.cyclic = true;
        for(int idx = 0; idx < 4; idx++){
            var destination = new DestinationInstruction();
            destination.getData().putString("Text", "Test Dock " + idx);
            schedule.entries.add(new ScheduleEntry(destination, new ArrayList<>()));
        }
        return schedule;
    }

    private static ShipDockRegistry.Dock dock(GameTestHelper helper, Vec3 up){
        return new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null, BlockPos.ZERO,
                new Vec3(10, 20, 30), new Vec3(0, 0, -1), "Connectorless", false, false, false,
                null, null, null, null, null, 0, List.of(), List.of(), up);
    }

    // Supply the completed approach reported by the stopped live walker
    private static final class ConnectorlessController extends AdvancedContraptionControllerBlockEntity{
        private Map<String, Double> parameters = Map.of();
        private boolean scheduled;
        private final ScmControlMode mode = new ScmPilotNavigationMode(ResourceLocation.fromNamespaceAndPath("test", "walker"),
                "Walker", host -> true, host -> ScmPilotControl.Profile.GROUND).bind(null);

        private ConnectorlessController(){
            super(BlockPos.ZERO, com.rieno.gadgetsandgizmos.registry.CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        }

        @Override public ScmControlMode getShipControlMode(){ return mode; }
        @Override public boolean hasShippingSchedule(){ return scheduled; }
        @Override public boolean isShipControlGraphCommandComplete(String id, String type){ return true; }
        @Override public boolean executeShipControlGraphCommand(String id, String type, Map<String, Double> values){ parameters = Map.copyOf(values); return true; }
        @Override public boolean executeShipControlGraphCommand(String id, String type, Map<String, Double> values, Map<String, String> text){ parameters = Map.copyOf(values); return true; }
    }
}
