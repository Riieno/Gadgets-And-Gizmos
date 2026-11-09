package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

// Exercise persistent speed settings and retained Follow input through the production graph and SCM
@GameTestHolder("createthrusters_navigation_speed")
@PrefixGameTestTemplate(false)
public final class ShipSpeedSettingsGameTests{
    // Update an existing percentage command without resetting movement and retain the setting through save and load
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void speedLimitsPersistAndUpdateExistingCommands(GameTestHelper helper) throws Exception{
        try(var fixture = new NavigationSpeedGameTests.Fixture(helper)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)state(fixture.runtime, "controller");
            Vec3 target = ((Vec3)invoke(fixture.telemetry, "position")).add(0, 0, 2000);
            helper.assertTrue(ctrl.executeShipControlGraphCommand("limit", "ship_max_speed", Map.of("speed", 40.0D)), "Max Speed was rejected");
            fixture.runtime.execute("travel", "ship_navigate", Map.of("x", target.x, "y", target.y, "z", target.z,
                    "speed", .5D, "speed_fraction", 1.0D));
            Object command = commands(fixture.runtime).get("travel" + '\0' + "ship_navigate");
            helper.assertTrue(speed(fixture, command) == 20, "50% was not scaled against the saved 40 blocks/s maximum");
            for(String type : List.of("ship_follow", "ship_climb", "ship_face", "ship_dock")){
                helper.assertTrue(fixture.runtime.execute(type, type, Map.of("x", target.x, "y", target.y + 2000, "z", target.z,
                        "speed", .5D, "speed_fraction", 1.0D)), "A percentage command was rejected: " + type);
                helper.assertTrue(speed(fixture, commands(fixture.runtime).get(type + '\0' + type)) == 20,
                        "The speed input did not use the saved maximum: " + type);
            }
            Object docking = commands(fixture.runtime).get("ship_dock" + '\0' + "ship_dock");
            ctrl.executeShipControlGraphCommand("limit", "ship_max_speed", Map.of("speed", 12.0D));
            helper.assertTrue(speed(fixture, command) == 6, "Updating the limit did not affect an already active command");
            ctrl.executeShipControlGraphCommand("stop", "ship_stop", Map.of());
            helper.assertTrue(ctrl.getShipSpeedLimit().blocksPerSecond() == 12, "Stop Ship Control cleared the persistent limit");
            var saved = ctrl.saveWithFullMetadata(helper.getLevel().registryAccess());
            var restored = (AdvancedContraptionControllerBlockEntity)BlockEntity.loadStatic(ctrl.getBlockPos(), ctrl.getBlockState(),
                    saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored != null && restored.getShipSpeedLimit().blocksPerSecond() == 12, "Saving and loading lost Max Speed");
            ((ShipControlModuleRuntime)state(restored, "shipControlRuntime")).close();
            ctrl.executeShipControlGraphCommand("limit", "ship_max_speed", Map.of("speed", -1.0D));
            helper.assertTrue(!ctrl.getShipSpeedLimit().enabled() && speed(fixture, command) > 100,
                    "A nonpositive value did not restore unrestricted navigation");
            helper.assertTrue(speed(fixture, docking) == 14, "An unrestricted docking command changed its existing speed behavior");
            helper.succeed();
        }
    }

    // Keep schedule and named-dock percentages tied to the saved ceiling without replacing an active destination
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void scheduleAndNamedDockPercentagesUseTheSavedMaximum(GameTestHelper helper) throws Exception{
        try(var fixture = new NavigationSpeedGameTests.Fixture(helper)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)state(fixture.runtime, "controller");
            var schedule = (ShippingScheduleRuntime)state(ctrl, "shippingScheduleRuntime");
            var throttle = ShippingScheduleRuntime.class.getDeclaredField("routeThrottle");
            throttle.setAccessible(true); throttle.setDouble(schedule, .25D);
            ctrl.setShipMaximumSpeed(40);
            helper.assertTrue((double)state(schedule, "routeSpeed") == 10, "A schedule retained its old fixed speed reference");
            Vec3 target = ((Vec3)invoke(fixture.telemetry, "position")).add(0, 0, 2000);
            @SuppressWarnings("unchecked") var values = (Map<String, Double>)invoke(schedule, "routeCruiseParameters",
                    Map.of("x", target.x, "y", target.y, "z", target.z));
            fixture.runtime.execute("schedule", "ship_navigate", values);
            Object command = commands(fixture.runtime).get("schedule" + '\0' + "ship_navigate");
            helper.assertTrue(speed(fixture, command) == 10, "Schedule cruise did not request 25% of the saved maximum");
            var named = state(fixture.runtime, "namedDocking");
            helper.assertTrue(Math.abs((double)invoke(named, "requestedSpeed") - 14) < 1.0E-9D,
                    "Named docking did not scale its 35% input against Max Speed");
            ctrl.setShipMaximumSpeed(20);
            helper.assertTrue((double)state(schedule, "routeSpeed") == 5 && speed(fixture, command) == 5
                    && Math.abs((double)invoke(named, "requestedSpeed") - 7) < 1.0E-9D,
                    "Changing Max Speed did not update existing schedule and docking percentages");
            helper.succeed();
        }
    }

    // Keep raw blocks/s on Max Speed and apply its latest input only when its execution is pulsed
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void maxSpeedNodeUpdatesOnlyOnExecution(GameTestHelper helper) throws Exception{
        try(var fixture = new NavigationSpeedGameTests.Fixture(helper)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)state(fixture.runtime, "controller");
            var graph = new AdvancedGraphDocument();
            graph.variables().put("maximum", AdvancedGraphDocument.Value.number(40));
            var input = node("input", "variable_get"); input.data().putString("Variable", "maximum");
            var update = node("update", "event_trigger"); update.data().putString("Event", "update_maximum");
            var limit = node("limit", "ship_max_speed");
            graph.nodes().addAll(List.of(node("ready", "event_graph_ready"), input, update, limit));
            edge(graph, "ready", "exec", "limit", "exec"); edge(graph, "update", "exec", "limit", "exec");
            edge(graph, "input", "value", "limit", "speed");
            var runtime = new GraphRuntime(ctrl, false);
            runtime.previewOutput(graph, limit, "success");
            helper.assertTrue(!ctrl.getShipSpeedLimit().enabled(), "A passive node read changed the maximum");
            runtime.tick(graph, false);
            helper.assertTrue(ctrl.getShipSpeedLimit().blocksPerSecond() == 40, "The Max Speed number input was interpreted as a percentage");
            graph.variables().put("maximum", AdvancedGraphDocument.Value.number(60));
            runtime.tick(graph, false);
            helper.assertTrue(ctrl.getShipSpeedLimit().blocksPerSecond() == 40, "An unpulsed input change updated Max Speed");
            runtime.enqueue("update_maximum"); runtime.tick(graph, false);
            helper.assertTrue(ctrl.getShipSpeedLimit().blocksPerSecond() == 60, "A later pulse did not replace the maximum");
            runtime.clear();
            helper.assertTrue(ctrl.getShipSpeedLimit().blocksPerSecond() == 60, "Clearing the graph removed the ship setting");
            helper.succeed();
        }
    }

    // Follow retains live coordinates after one pulse and Stop Following releases only that retained request
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void followRefreshesCoordinatesUntilItsStopInputIsPulsed(GameTestHelper helper){
        var ctrl = new FollowingController(); ctrl.setLevel(helper.getLevel());
        var graph = new AdvancedGraphDocument();
        graph.variables().put("target_x", AdvancedGraphDocument.Value.number(10));
        var input = node("input", "variable_get"); input.data().putString("Variable", "target_x");
        var stop = node("stop", "event_trigger"); stop.data().putString("Event", "stop_following");
        var restart = node("restart", "event_trigger"); restart.data().putString("Event", "restart_following");
        graph.nodes().addAll(List.of(node("ready", "event_graph_ready"), input, stop, restart, node("follow", "ship_follow"), node("count", "count")));
        edge(graph, "ready", "exec", "follow", "exec"); edge(graph, "input", "value", "follow", "x");
        edge(graph, "follow", "exec", "count", "exec"); edge(graph, "stop", "exec", "follow", "stop_following");
        edge(graph, "restart", "exec", "follow", "exec");
        var runtime = new GraphRuntime(ctrl, false);
        runtime.tick(graph, false);
        helper.assertTrue(ctrl.following && ctrl.parameters.get("x") == 10, "The initial Follow pulse did not start tracking");
        graph.variables().put("target_x", AdvancedGraphDocument.Value.number(35));
        runtime.tick(graph, false);
        helper.assertTrue(ctrl.parameters.get("x") == 35, "Follow required another pulse to read changing coordinates");
        helper.assertTrue(runtime.liveOutput("count", "value").asNumber() == 1, "Refreshing Follow emitted another execution pulse");
        runtime.enqueue("stop_following"); runtime.tick(graph, false);
        helper.assertTrue(!ctrl.following, "The Stop Following execution input did not cancel tracking");
        int stopped = ctrl.updates;
        graph.variables().put("target_x", AdvancedGraphDocument.Value.number(70));
        runtime.tick(graph, false);
        helper.assertTrue(ctrl.updates == stopped, "Changing coordinates reactivated a stopped Follow node");
        runtime.enqueue("restart_following"); runtime.tick(graph, false);
        helper.assertTrue(ctrl.following && ctrl.parameters.get("x") == 70, "Follow did not restart with the latest coordinates");
        runtime.clear();
        helper.assertTrue(!ctrl.following, "Removing the graph left its Follow command active");
        helper.succeed();
    }

    // Preserve another controller action when this node releases Follow
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stopFollowingPreservesIndependentShipControls(GameTestHelper helper) throws Exception{
        try(var fixture = new NavigationSpeedGameTests.Fixture(helper)){
            fixture.runtime.execute("following", "ship_follow", Map.of("x", 2000.0D, "speed", .5D, "speed_fraction", 1.0D));
            fixture.runtime.execute("yaw", "ship_yaw_right", Map.of("amount", .5D));
            helper.assertTrue(fixture.runtime.stopFollowing("following"), "The retained follow command could not be released");
            var commands = commands(fixture.runtime);
            helper.assertTrue(!commands.containsKey("following" + '\0' + "ship_follow") && commands.containsKey("yaw" + '\0' + "ship_yaw_right"),
                    "Stop Following removed an independent steering command");
            helper.succeed();
        }
    }

    // Observe the real graph command path without requiring a physical moving target
    private static final class FollowingController extends AdvancedContraptionControllerBlockEntity{
        private boolean following;
        private int updates;
        private Map<String, Double> parameters;
        private FollowingController(){ super(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()); }
        @Override public boolean executeShipControlGraphCommand(java.util.UUID viewer, String id, String type, Map<String, Double> values, Map<String, String> text){
            if(!"ship_follow".equals(type)) return false;
            following = true; updates++; parameters = Map.copyOf(values); return true;
        }
        @Override public boolean isShipControlGraphCommandPending(String id, String type){ return following; }
        @Override public boolean isShipControlGraphCommandComplete(String id, String type){ return false; }
        @Override public boolean stopShipControlFollowing(String id){ following = false; return true; }
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, id, 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input){
        graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input, from, output, to, input));
    }
    private static Object state(Object owner, String name) throws Exception{ return NavigationSpeedGameTests.field(owner, name); }
    private static Object invoke(Object owner, String name, Object... args) throws Exception{ return NavigationSpeedGameTests.invoke(owner, name, args); }
    private static Map<?, ?> commands(ShipControlModuleRuntime runtime) throws Exception{ return (Map<?, ?>)state(runtime, "activeCommands"); }
    private static double speed(NavigationSpeedGameTests.Fixture fixture, Object command) throws Exception{
        return (double)invoke(invoke(fixture.runtime, "navigationSpeedCommand", command, fixture.telemetry), "targetSpeed");
    }
}
