package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphExplanation;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphFunctions;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.TargetGraphNodes;
import com.rieno.gadgetsandgizmos.lib.graph.explain.GraphExplanation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Exercise explanations against actual ACC definitions without creating or starting a graph runtime
@GameTestHolder("createthrusters_targets")
@PrefixGameTestTemplate(false)
public final class ExplainSelectionGameTests{
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void throttleTraceUsesFiveReadableStepsAndEffectiveZeroDefaults(GameTestHelper helper){
        var graph = throttleGraph();
        CompoundTag before = graph.toTag().copy();
        String markdown = explainMarkdown(graph, ids(graph));
        helper.assertTrue(markdown.contains("pressed and again when it is released") && markdown.contains("0–1 to 0–15")
                && markdown.contains("**Round** rounds") && markdown.contains("variable **throttle**"),
                "The readable trace lost the configured throttle chain: " + markdown);
        helper.assertTrue(markdown.lines().filter(line -> line.startsWith("- ")).count() == 5,
                "The throttle chain should have five meaningful bullets: " + markdown);
        helper.assertTrue(!markdown.contains("unconfigured") && !markdown.contains("saved initial value")
                && markdown.contains("Fall is 0, so it will not decrease"), "The trace contains misleading settings: " + markdown);
        helper.assertTrue(before.equals(graph.toTag()), "Rendering a readable trace changed the draft");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void explainsConfiguredPitchControlWithoutChangingTheDraft(GameTestHelper helper){
        var graph = controlGraph();
        CompoundTag before = graph.toTag().copy();
        String explanation = explain(graph, ids(graph));
        helper.assertTrue(explanation.contains("pitch") && explanation.contains("PID") && explanation.contains("port thrusters")
                && explanation.contains("towards 15") && explanation.contains("appears intended"),
                "Configured feedback control lost its measured input, goal or selected destination: " + explanation);
        helper.assertTrue(before.equals(graph.toTag()), "Explaining a draft changed its saved state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wiredTargetsOverrideSavedSelections(GameTestHelper helper){
        var graph = controlGraph();
        var target = node("target", "createthrusters:target");
        TargetGraphNodes.select(target.data(), target("starboard thrusters"));
        graph.nodes().add(target);
        edge(graph, "target", "target", "output", "target");
        String explanation = explain(graph, ids(graph));
        helper.assertTrue(explanation.contains("starboard thrusters") && !explanation.contains("port thrusters ("),
                "The explanation used a stale saved target instead of the wired target: " + explanation);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dynamicBlockPropertiesRetainTheirConfiguredTargets(GameTestHelper helper){
        var graph = controlGraph();
        var output = node("output", "set_block_data");
        CompoundTag inputs = new CompoundTag();
        inputs.putString("force", "number");
        output.data().put("DynamicInputs", inputs);
        ListTag targets = new ListTag();
        targets.add(target("rear thrusters").toTag());
        output.data().put(AdvancedGraphCatalog.DATA_TARGETS_TAG, targets);
        CompoundTag bindings = new CompoundTag();
        ListTag ports = new ListTag();
        CompoundTag binding = new CompoundTag();
        binding.putString("Target", "rear thrusters");
        binding.putString("Port", "thrust");
        ports.add(binding);
        bindings.put("force", ports);
        output.data().put(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG, bindings);
        graph.nodes().removeIf(node -> node.id().equals("output"));
        graph.nodes().add(output);
        graph.edges().removeIf(edge -> edge.toNode().equals("output") && edge.toPort().equals("value"));
        edge(graph, "pid", "value", "output", "force");
        String explanation = explain(graph, ids(graph));
        helper.assertTrue(explanation.contains("rear thrusters") && explanation.contains("thrust") && explanation.contains("PID"),
                "Saved dynamic property bindings were not used: " + explanation);
        helper.succeed();
    }

    // Keep opaque linker IDs out of property names and destination text
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void linkerPropertiesUseTheirTargetDisplayNames(GameTestHelper helper){
        var graph = linkerGraph();
        CompoundTag before = graph.toTag().copy();
        String markdown = explainMarkdown(graph, ids(graph));
        helper.assertTrue(markdown.contains("redstone signal strength \\[Redstone Lamp West Linker Plane\\]")
                        && markdown.contains("on Redstone Lamp West Linker Plane"),
                "The linker property and destination did not use the same display name: " + markdown);
        helper.assertTrue(!markdown.contains("ct linker") && !markdown.contains("ct_linker")
                        && !markdown.contains("face=west") && !markdown.contains("minecraft:redstone_lamp"),
                "The readable explanation exposed a serialized target: " + markdown);
        helper.assertTrue(before.equals(graph.toTag()), "Resolving display names changed the saved graph");
        helper.succeed();
    }

    // Describe missing saved labels without displaying the serialized linker key
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void missingLinkerLabelsDoNotExposeTargetIds(GameTestHelper helper){
        var graph = linkerGraph();
        var output = graph.nodes().stream().filter(node -> node.id().equals("output")).findFirst().orElseThrow();
        output.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND).getCompound(0).remove("Label");
        String markdown = explainMarkdown(graph, ids(graph));
        helper.assertTrue(markdown.contains("an unresolved saved target") && !markdown.contains("ct linker")
                        && !markdown.contains("ct_linker") && !markdown.contains("face=west"),
                "A missing display name fell back to the internal target ID: " + markdown);
        helper.succeed();
    }

    // Use the same translated node summaries as the editor for every profiler output
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void profilerStepsDescribeTheirActualReadings(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        for(String type : List.of("profiler_mspt", "profiler_fps", "profiler_tps", "profiler_frametime"))
            graph.nodes().add(node(type, type));
        GraphExplanation.TextResolver text = (key, fallback, args) -> {
            if(key.equals(AdvancedGraphCatalog.summaryTranslationKey("profiler_mspt"))) return "Outputs the server's average milliseconds per tick.";
            if(key.equals(AdvancedGraphCatalog.summaryTranslationKey("profiler_fps"))) return "Outputs the latest client frames per second.";
            if(key.equals(AdvancedGraphCatalog.summaryTranslationKey("profiler_tps"))) return "Outputs the server's effective ticks per second.";
            if(key.equals(AdvancedGraphCatalog.summaryTranslationKey("profiler_frametime"))) return "Outputs the latest client frame time in milliseconds.";
            return String.format(Locale.ROOT, fallback, args);
        };
        var analysis = AdvancedGraphExplanation.analyse(graph, graph, ids(graph), text);
        var explanation = new GraphExplanation(text);
        String markdown = explanation.explainMarkdown(analysis);
        helper.assertTrue(markdown.contains("milliseconds per tick") && markdown.contains("client frames per second")
                        && markdown.contains("effective ticks per second") && markdown.contains("frame time in milliseconds")
                        && !markdown.contains("configured output"), "Profiler readings were replaced by generic wording: " + markdown);
        helper.assertTrue(explanation.explainSteps(analysis).size() == 4, "Profiler bubbles lost their node identities");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void namedEventsDescribeTransportWithoutInventingExecutionEdges(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var send = node("send", "send_named_controller_event");
        send.data().putString("Event", "cargo_ready");
        var receive = node("receive", "event_named_controller");
        receive.data().putString("Event", "cargo_ready");
        graph.nodes().addAll(List.of(send, receive));
        String explanation = explain(graph, ids(graph));
        helper.assertTrue(explanation.contains("cargo_ready") && explanation.contains("delivery is not verified")
                && explanation.contains("No complete execution route"),
                "Named transport was incorrectly treated as a direct execution connection: " + explanation);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partialSelectionsExposeExternalInputsAndExecution(GameTestHelper helper){
        var graph = controlGraph();
        String explanation = explain(graph, Set.of("pid", "output"));
        helper.assertTrue(explanation.contains("external") && explanation.contains("pitch") && explanation.contains("outside the selection"),
                "Partial selection hid its external dependencies: " + explanation);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void branchAndIndependentOutputsUseRealConditions(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var branch = node("branch", "branch");
        setDefault(branch, "condition", AdvancedGraphDocument.Value.bool(false));
        var off = node("off", "local_redstone_output");
        var on = node("on", "local_redstone_output");
        setDefault(off, "value", AdvancedGraphDocument.Value.number(0));
        setDefault(on, "value", AdvancedGraphDocument.Value.number(15));
        graph.nodes().addAll(List.of(node("tick", "event_tick"), branch, off, on));
        edge(graph, "tick", "exec", "branch", "exec");
        edge(graph, "branch", "true", "on", "exec");
        edge(graph, "branch", "false", "off", "exec");
        String explanation = explain(graph, ids(graph));
        helper.assertTrue(explanation.contains("not (false)") && explanation.contains("redstone")
                && explanation.contains("value = 15") && explanation.contains("value = 0"),
                "Branch explanations lost the configured condition or independent actions: " + explanation);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void functionsAndMutableVariablesResolveFromSavedDefinitions(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        graph.variables().put("amount", AdvancedGraphDocument.Value.number(8));
        var read = node("read", "variable_get");
        read.data().putString("Variable", "amount");
        graph.nodes().add(read);
        var function = new AdvancedGraphDocument.FunctionGraph("double", "Double value");
        var input = node("input", "function_input");
        CompoundTag outputs = new CompoundTag();
        outputs.putString("exec", "exec");
        outputs.putString("value", "number");
        input.data().put("DynamicOutputs", outputs);
        var add = node("add", "add");
        var output = node("return", "function_output");
        CompoundTag inputs = new CompoundTag();
        inputs.putString("exec", "exec");
        inputs.putString("value", "number");
        output.data().put("DynamicInputs", inputs);
        function.nodes().addAll(List.of(input, add, output));
        function.edges().add(new AdvancedGraphDocument.Edge("arg1", "input", "value", "add", "a"));
        function.edges().add(new AdvancedGraphDocument.Edge("arg2", "input", "value", "add", "b"));
        function.edges().add(new AdvancedGraphDocument.Edge("result", "add", "value", "return", "value"));
        function.edges().add(new AdvancedGraphDocument.Edge("entry", "input", "exec", "return", "exec"));
        graph.functions().add(function);
        var call = node("call", "function_call");
        AdvancedGraphFunctions.configureCall(call, function);
        graph.nodes().add(call);
        String explanation = explain(graph, ids(graph));
        helper.assertTrue(explanation.contains("Double value") && explanation.contains("sum of")
                && explanation.contains("saved initial value 8") && explanation.contains("mutable"),
                "The explanation failed to resolve a saved function body or mutable variable: " + explanation);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cyclesAndUnknownNodesRemainBounded(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        graph.nodes().addAll(List.of(node("a", "add"), node("b", "reroute"),
                new AdvancedGraphDocument.Node("unknown", "example:missing", "Unknown", 0, 0, new CompoundTag())));
        edge(graph, "a", "value", "b", "value");
        edge(graph, "b", "value", "a", "a");
        String explanation = explain(graph, ids(graph));
        helper.assertTrue(explanation.contains("Cyclic") && explanation.contains("unsupported"),
                "Cycles or unknown nodes did not produce a factual fallback: " + explanation);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recursiveFunctionsAndUnexecutedActionsDoNotInventBehaviour(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var function = new AdvancedGraphDocument.FunctionGraph("recursive", "Recursive");
        var recursive = node("self", "function_call");
        recursive.data().putString(AdvancedGraphFunctions.FUNCTION_ID, function.id());
        function.nodes().add(recursive);
        graph.functions().add(function);
        var call = node("call", "function_call");
        call.data().putString(AdvancedGraphFunctions.FUNCTION_ID, function.id());
        graph.nodes().addAll(List.of(call, node("unwired", "local_redstone_output")));
        String explanation = explain(graph, ids(graph));
        helper.assertTrue(explanation.contains("cannot be resolved") && explanation.contains("No complete execution route"),
                "Recursive references or unexecuted operations were incorrectly assumed to run: " + explanation);
        helper.succeed();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public static AdvancedGraphDocument controlGraph(){
        var graph = new AdvancedGraphDocument();
        var pid = node("pid", "pid");
        setDefault(pid, "target", AdvancedGraphDocument.Value.number(15));
        var output = node("output", "direct_target_output");
        TargetGraphNodes.select(output.data(), target("port thrusters"));
        graph.nodes().addAll(List.of(node("tick", "event_tick"), node("telemetry", "ship_telemetry"), pid, output));
        edge(graph, "telemetry", "pitch", "pid", "actual");
        edge(graph, "pid", "value", "output", "value");
        edge(graph, "tick", "exec", "output", "exec");
        return graph;
    }

    // Reproduce a configured key driving a saved linker-face property
    public static AdvancedGraphDocument linkerGraph(){
        var graph = new AdvancedGraphDocument();
        var key = node("key", "controller_channel_input");
        key.data().putBoolean("GraphOwnedBinding", true);
        key.data().putString("BindingLabel", "Space");
        key.data().putString("PulseBehavior", "both");
        var output = node("output", "set_block_data");
        var target = new ControllerDiscoveryNode("ct_linker:output@-7146825568316#world::face=west",
                ControllerDiscoveryKind.UNKNOWN, "", "minecraft:redstone_lamp", "Redstone Lamp West Linker Plane",
                null, new BlockPos(2, 4, 6));
        ListTag targets = new ListTag();
        targets.add(target.toTag());
        output.data().put(AdvancedGraphCatalog.DATA_TARGETS_TAG, targets);
        CompoundTag inputs = new CompoundTag();
        inputs.putString("signal", "number");
        output.data().put("DynamicInputs", inputs);
        CompoundTag binding = new CompoundTag();
        binding.putString("Target", target.nodeId());
        binding.putString("Port", "redstone_signal_strength");
        ListTag ports = new ListTag();
        ports.add(binding);
        CompoundTag bindings = new CompoundTag();
        bindings.put("signal", ports);
        output.data().put(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG, bindings);
        graph.nodes().addAll(List.of(key, output));
        edge(graph, "key", "value", "output", "signal");
        edge(graph, "key", "exec", "output", "exec");
        return graph;
    }

    public static AdvancedGraphDocument throttleGraph(){
        var graph = new AdvancedGraphDocument();
        var key = node("key", "controller_channel_input");
        key.data().putBoolean("GraphOwnedBinding", true);
        key.data().putInt("KeyCode", 87);
        key.data().putString("BindingId", "graph_forward");
        key.data().putString("BindingLabel", "W");
        key.data().putString("PulseBehavior", "both");
        var range = node("range", "map_range");
        setDefault(range, "in_max", AdvancedGraphDocument.Value.number(1));
        setDefault(range, "out_max", AdvancedGraphDocument.Value.number(15));
        var ramp = node("ramp", "ramp");
        setDefault(ramp, "rise", AdvancedGraphDocument.Value.number(.5));
        setDefault(ramp, "fall", AdvancedGraphDocument.Value.number(0));
        var store = node("store", "variable_set");
        store.data().putString("Variable", "throttle");
        graph.variables().put("throttle", AdvancedGraphDocument.Value.number(-15));
        graph.nodes().addAll(List.of(key, range, ramp, node("round", "round"), store));
        edge(graph, "key", "value", "range", "value");
        edge(graph, "key", "exec", "ramp", "exec");
        edge(graph, "range", "value", "ramp", "value");
        edge(graph, "ramp", "value", "round", "value");
        edge(graph, "ramp", "exec", "store", "exec");
        edge(graph, "round", "value", "store", "value");
        return graph;
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, AdvancedGraphCatalog.displayName(type), 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    private static void setDefault(AdvancedGraphDocument.Node node, String port, AdvancedGraphDocument.Value val){
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.put(port, val.toTag());
        node.data().put("Defaults", defaults);
    }

    private static ControllerDiscoveryNode target(String label){
        return new ControllerDiscoveryNode(label, ControllerDiscoveryKind.UNKNOWN, "main engine group",
                "createthrusters:thruster", label, null, new BlockPos(2, 4, 6));
    }

    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input){
        graph.edges().add(new AdvancedGraphDocument.Edge("wire" + graph.edges().size(), from, output, to, input));
    }

    private static Set<String> ids(AdvancedGraphDocument graph){
        return graph.nodes().stream().map(AdvancedGraphDocument.Node::id).collect(Collectors.toSet());
    }

    private static String explain(AdvancedGraphDocument graph, Set<String> selection){
        GraphExplanation.TextResolver text = (key, fallback, args) -> String.format(Locale.ROOT, fallback, args);
        return new GraphExplanation(text).explain(AdvancedGraphExplanation.analyse(graph, graph, selection, text));
    }

    private static String explainMarkdown(AdvancedGraphDocument graph, Set<String> selection){
        GraphExplanation.TextResolver text = (key, fallback, args) -> String.format(Locale.ROOT, fallback, args);
        return new GraphExplanation(text).explainMarkdown(AdvancedGraphExplanation.analyse(graph, graph, selection, text));
    }
}

