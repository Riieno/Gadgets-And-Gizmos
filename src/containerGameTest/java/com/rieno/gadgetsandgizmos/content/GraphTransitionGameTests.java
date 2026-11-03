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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Exercise condition edges and numeric transitions through the graph event scheduler
@GameTestHolder("createthrusters_graph_transitions")
@PrefixGameTestTemplate(false)
public final class GraphTransitionGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // A held condition fires once and rearms when its upstream value becomes false
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pulseIfRearmsAfterTheConditionBecomesFalse(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var input = node("input", "constant_number");
        input.data().putDouble("Value", 1);
        var condition = node("condition", "pulse_if");
        number(condition, "b", 0);
        condition.data().getCompound("Defaults").put("operator", AdvancedGraphDocument.Value.string(">").toTag());
        graph.nodes().add(input);
        graph.nodes().add(condition);
        graph.nodes().add(node("counter", "count"));
        graph.edges().add(new AdvancedGraphDocument.Edge("value", "input", "value", "condition", "a"));
        graph.edges().add(new AdvancedGraphDocument.Edge("pulse", "condition", "exec", "counter", "exec"));
        var runtime = new GraphRuntime(controller(helper), true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        runtime.tickSimulation(graph, null);
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "counter", 1);
        var next = graph.copy();
        next.nodes().stream().filter(node -> node.id().equals("input")).findFirst().orElseThrow().data().putDouble("Value", 0);
        runtime.replaceSimulationGraph(graph, next);
        runtime.tickSimulation(next, null);
        value(helper, runtime, "counter", 1);
        graph = next.copy();
        graph.nodes().stream().filter(node -> node.id().equals("input")).findFirst().orElseThrow().data().putDouble("Value", 1);
        runtime.replaceSimulationGraph(next, graph);
        runtime.tickSimulation(graph, null);
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "counter", 2);
        runtime.clear();
        helper.succeed();
    }

    // One pulse sustains a ramp until its endpoint and the next pulse reverses it
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void rampContinuesUpAndDownBetweenPulses(GameTestHelper helper){
        var graph = responseGraph("ramp");
        var ramp = graph.nodes().get(1);
        number(ramp, "rise", .25);
        number(ramp, "fall", .25);
        var runtime = new GraphRuntime(controller(helper), true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "response", .25);
        for(int idx = 2; idx <= 4; idx++){
            int step = idx;
            helper.runAtTickTime(idx, () -> {
                runtime.tickSimulation(graph, null);
                value(helper, runtime, "response", step * .25);
            });
        }
        helper.runAtTickTime(7, () -> {
            runtime.tickSimulation(graph, null);
            value(helper, runtime, "response", 1);
            helper.assertTrue(!runtime.hasPendingWork(), "A completed ramp retained scheduled work");
        });
        helper.runAtTickTime(8, () -> {
            runtime.enqueue("graph_ready");
            runtime.tickSimulation(graph, null);
            value(helper, runtime, "response", .75);
        });
        for(int idx = 9; idx <= 11; idx++){
            int step = idx;
            helper.runAtTickTime(idx, () -> {
                runtime.tickSimulation(graph, null);
                value(helper, runtime, "response", (11 - step) * .25);
            });
        }
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(!runtime.hasPendingWork(), "A stopped ramp retained scheduled work");
            runtime.clear();
            helper.succeed();
        });
    }

    // Preview reads cannot advance the ramp or make it step twice in the same tick
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rampReadsDoNotAdvanceItsState(GameTestHelper helper){
        var graph = responseGraph("ramp");
        var ramp = graph.nodes().get(1);
        number(ramp, "rise", .25);
        number(ramp, "fall", .1);
        var runtime = new GraphRuntime(controller(helper), true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        for(int idx = 0; idx < 10; idx++) runtime.previewOutput(graph, ramp, "value");
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "response", .25);
        runtime.enqueue("graph_ready");
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "response", .25);
        helper.runAtTickTime(2, () -> {
            runtime.tickSimulation(graph, null);
            value(helper, runtime, "response", .15);
            runtime.clear();
            helper.succeed();
        });
    }

    // A new upward pulse cancels a delayed fall before it can reverse the ramp
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rampCancelsAnOldDelayedFall(GameTestHelper helper){
        var graph = responseGraph("ramp");
        var ramp = graph.nodes().get(1);
        number(ramp, "rise", .5);
        number(ramp, "fall", .5);
        number(ramp, "reset_delay", 4);
        var runtime = new GraphRuntime(controller(helper), true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        runtime.enqueue("graph_ready");
        runtime.tickSimulation(graph, null);
        helper.runAtTickTime(2, () -> {
            runtime.tickSimulation(graph, null);
            value(helper, runtime, "response", .5);
            runtime.enqueue("graph_ready");
            runtime.tickSimulation(graph, null);
            value(helper, runtime, "response", 1);
        });
        helper.runAtTickTime(7, () -> {
            runtime.tickSimulation(graph, null);
            value(helper, runtime, "response", 1);
            helper.assertTrue(!runtime.hasPendingWork(), "The cancelled fall retained scheduled work");
            runtime.clear();
            helper.succeed();
        });
    }

    // Interpolation advances for every pulse, including separate pulses in the same tick
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lerpAdvancesOnlyWhenPulsed(GameTestHelper helper){
        var graph = responseGraph("lerp");
        var lerp = graph.nodes().get(1);
        number(lerp, "b", 10);
        number(lerp, "amount", .25);
        var runtime = new GraphRuntime(controller(helper), true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "response", 2.5);
        for(int idx = 0; idx < 10; idx++) runtime.previewOutput(graph, lerp, "value");
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "response", 2.5);
        helper.runAtTickTime(5, () -> {
            runtime.tickSimulation(graph, null);
            value(helper, runtime, "response", 2.5);
            for(int idx = 2; idx <= 4; idx++){
                runtime.enqueue("graph_ready");
                runtime.tickSimulation(graph, null);
                value(helper, runtime, "response", idx * 2.5);
            }
            runtime.enqueue("graph_ready");
            runtime.tickSimulation(graph, null);
            value(helper, runtime, "response", 10);
            helper.assertTrue(!runtime.hasPendingWork(), "Stepped interpolation scheduled automatic advances");
            runtime.clear();
            helper.succeed();
        });
    }

    // Changing a wired endpoint starts a new interpolation range on the next pulse
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lerpUsesWiredInputsAndRestartsForANewRange(GameTestHelper helper){
        var graph = responseGraph("lerp");
        number(graph.nodes().get(1), "amount", .25);
        var target = node("target", "constant_number");
        target.data().putDouble("Value", 8);
        graph.nodes().add(target);
        graph.edges().add(new AdvancedGraphDocument.Edge("target", "target", "value", "response", "b"));
        var runtime = new GraphRuntime(controller(helper), true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "response", 2);
        runtime.enqueue("graph_ready");
        runtime.tickSimulation(graph, null);
        value(helper, runtime, "response", 4);
        var next = graph.copy();
        next.nodes().stream().filter(node -> node.id().equals("target")).findFirst().orElseThrow().data().putDouble("Value", -8);
        runtime.replaceSimulationGraph(graph, next);
        runtime.enqueue("graph_ready");
        runtime.tickSimulation(next, null);
        value(helper, runtime, "response", -2);
        runtime.clear();
        helper.succeed();
    }

    // Build a response node triggered once when its graph becomes ready
    private static AdvancedGraphDocument responseGraph(String type){
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(node("start", "event_graph_ready"));
        graph.nodes().add(node("response", type));
        graph.edges().add(new AdvancedGraphDocument.Edge("start", "start", "exec", "response", "exec"));
        return graph;
    }

    // Create node defaults through the same factory as the editor
    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, id, 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    // Set a numeric literal without bypassing the serialized input representation
    private static void number(AdvancedGraphDocument.Node node, String port, double val){
        var defaults = node.data().getCompound("Defaults");
        defaults.put(port, AdvancedGraphDocument.Value.number(val).toTag());
        node.data().put("Defaults", defaults);
    }

    // Compare the executed value without sampling or changing the node
    private static void value(GameTestHelper helper, GraphRuntime runtime, String node, double expected){
        double actual = runtime.liveOutput(node, "value").asNumber();
        helper.assertTrue(Math.abs(actual - expected) < 1e-9,
                node + " returned " + actual + " instead of " + expected);
    }

    // Give scheduled runtime work the real server clock
    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper){
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        return (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(pos);
    }
}
