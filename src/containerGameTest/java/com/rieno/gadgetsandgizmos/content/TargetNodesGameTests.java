package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.TargetGraphNodes;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Verify saved target identity and list iteration through the native graph runtime
@GameTestHolder("createthrusters_targets")
@PrefixGameTestTemplate(false)
public final class TargetNodesGameTests{
    // A saved selection must reach another node without losing its packed position or sublevel
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void selectedTargetSurvivesSaveAndWiresIntoDataNodes(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var selected = target("selected");
        var source = node("source", "createthrusters:target");
        TargetGraphNodes.select(source.data(), selected);
        graph.nodes().add(source);
        graph.nodes().add(node("read", "get_block_data"));
        edge(graph, "source", "target", "read", "target");
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        var runtime = new GraphRuntime(controller(helper, List.of()), true);
        var read = graph.nodes().stream().filter(node -> "read".equals(node.id())).findFirst().orElseThrow();
        var value = runtime.previewInput(graph, read, "target");
        helper.assertTrue("target".equals(value.type()) && selected.toTag().equals(value.payload()),
                "Wiring a saved target changed its identity or metadata");
        runtime.clear();
        helper.succeed();
    }

    // Existing list and loop nodes must deliver a typed target on every iteration
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void currentTargetsCanBeIndexedAndLooped(GameTestHelper helper){
        List<ControllerDiscoveryNode> targets = new ArrayList<>(List.of(target("first"), target("second")));
        var graph = new AdvancedGraphDocument();
        var source = node("targets", "createthrusters:get_targets");
        var capture = node("capture", "variable_set");
        capture.data().putString("VariableType", "target");
        capture.data().getCompound("DynamicInputs").putString("value", "target");
        capture.data().getCompound("DynamicInputs").putString("default", "target");
        capture.data().getCompound("DynamicOutputs").putString("value", "target");
        capture.data().getCompound("Defaults").put("type", AdvancedGraphDocument.Value.string("target").toTag());
        capture.data().getCompound("Defaults").put("default", AdvancedGraphDocument.Value.target(new CompoundTag()).toTag());
        graph.nodes().addAll(List.of(node("start", "event_graph_ready"), source, node("length", "arr_length"),
                node("loop", "bounded_loop"), node("item", "list_get"), capture));
        edge(graph, "start", "exec", "loop", "exec");
        edge(graph, "targets", "targets", "length", "Arr");
        edge(graph, "length", "Length", "loop", "iterations");
        edge(graph, "targets", "targets", "item", "list");
        edge(graph, "loop", "index", "item", "index");
        edge(graph, "loop", "body", "capture", "exec");
        edge(graph, "item", "value", "capture", "value");
        var runtime = new GraphRuntime(controller(helper, targets), true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        assertTarget(helper, runtime.liveOutput("capture", "value"), targets.getFirst());
        helper.runAtTickTime(2, () -> {
            runtime.tickSimulation(graph, null);
            assertTarget(helper, runtime.liveOutput("capture", "value"), targets.getLast());
            targets.clear();
            var empty = runtime.previewOutput(graph, source, "targets");
            helper.assertTrue("list".equals(empty.type()) && empty.payload().isEmpty(),
                    "Get Targets retained targets after the host removed them");
            runtime.clear();
            helper.succeed();
        });
    }

    // Match the native editor's construction and port definitions
    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, AdvancedGraphCatalog.displayName(type), 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input){
        graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input, from, output, to, input));
    }

    private static ControllerDiscoveryNode target(String id){
        return new ControllerDiscoveryNode(id, ControllerDiscoveryKind.MACHINE, "group", "minecraft:chest", id,
                UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), new BlockPos(30_000_001, -19, -29_999_997));
    }

    private static void assertTarget(GameTestHelper helper, AdvancedGraphDocument.Value value, ControllerDiscoveryNode target){
        helper.assertTrue("target".equals(value.type()) && target.toTag().equals(value.payload()),
                "List iteration changed the target type, identity or metadata");
    }

    // Let the real adapter read a changing host list without requiring linker network setup
    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper, List<ControllerDiscoveryNode> targets){
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()){
            @Override public List<ControllerDiscoveryNode> getAssignableTargets(){ return targets; }
        };
        controller.setLevel(helper.getLevel());
        return controller;
    }
}
