package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValueMirrors;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphRuntimePreviewTest{
    @Test void draftPreviewPropagatesLiveVariablesThroughDataWires(){
        AdvancedGraphDocument active = variableGraph();
        AdvancedGraphDocument draft = active.copy();
        GraphRuntime runtime = new GraphRuntime(null);
        runtime.compile(active);
        active.variables().put("debug", AdvancedGraphDocument.Value.number(41));
        runtime.beginPreviewSample(draft);
        assertEquals(41, runtime.previewOutput(draft, node(draft, "getter"), "value").asNumber());
        assertEquals(41, runtime.previewOutput(draft, node(draft, "setter"), "value").asNumber());
        assertEquals(42, runtime.previewOutput(draft, node(draft, "sum"), "value").asNumber());
        assertEquals(42, runtime.previewInput(draft, node(draft, "downstream"), "a").asNumber());
        assertEquals(43, runtime.previewOutput(draft, node(draft, "downstream"), "value").asNumber());
        active.variables().put("debug", AdvancedGraphDocument.Value.number(0));
        runtime.beginPreviewSample(draft);
        assertEquals(2, runtime.previewOutput(draft, node(draft, "downstream"), "value").asNumber());
        assertEquals(0, draft.variables().get("debug").asNumber());
        assertTrue(runtime.liveOutputs().isEmpty());
        assertTrue(runtime.liveInputs().isEmpty());
    }

    @Test void observerSamplingDoesNotRetriggerGraphReadyOrDiscardItsOutputs(){
        AdvancedGraphDocument active = new AdvancedGraphDocument();
        active.nodes().add(createNode("ready", "event_graph_ready"));
        active.nodes().add(createNode("counter", "count"));
        node(active, "counter").data().put("Defaults", defaults("step", AdvancedGraphDocument.Value.number(1)));
        active.edges().add(edge("ready", "exec", "counter", "exec"));
        AdvancedGraphDocument draft = active.copy();
        GraphRuntime runtime = new GraphRuntime(null, true);
        runtime.tick(active, false);
        assertEquals(1, runtime.liveOutput("counter", "value").asNumber());
        long revision = runtime.liveValueRevision();
        runtime.beginPreviewSample(draft);
        assertTrue(runtime.previewOutput(draft, node(draft, "ready"), "ready").asBoolean());
        assertEquals(1, runtime.previewOutput(draft, node(draft, "counter"), "value").asNumber());
        assertEquals(revision, runtime.liveValueRevision());
        runtime.tick(active, false);
        assertEquals(1, runtime.liveOutput("counter", "value").asNumber());
        assertTrue(runtime.diagnostics().isEmpty());
    }

    @Test void previewPreservesStructuredVariablesAndNewDraftDefaults(){
        AdvancedGraphDocument active = variableGraph();
        CompoundTag payload = new CompoundTag();
        payload.put("value", AdvancedGraphDocument.Value.number(9).toTag());
        AdvancedGraphDocument.Value structured = AdvancedGraphDocument.Value.map(payload);
        AdvancedGraphDocument draft = active.copy();
        GraphRuntime runtime = new GraphRuntime(null);
        runtime.compile(active);
        active.variables().put("debug", structured);
        draft.variables().put("new_variable", AdvancedGraphDocument.Value.bool(false));
        draft.nodes().add(createNode("new_getter", "variable_get"));
        node(draft, "new_getter").data().putString("Variable", "new_variable");
        runtime.beginPreviewSample(draft);
        assertEquals(structured, runtime.previewOutput(draft, node(draft, "getter"), "value"));
        assertEquals(AdvancedGraphDocument.Value.bool(false),
                runtime.previewOutput(draft, node(draft, "new_getter"), "value"));
        node(draft, "getter").data().putString("Variable", "new_variable");
        runtime.beginPreviewSample(draft);
        assertEquals(AdvancedGraphDocument.Value.bool(false),
                runtime.previewOutput(draft, node(draft, "getter"), "value"));
        runtime.clear();
        runtime.beginPreviewSample(draft);
        assertEquals(0, runtime.previewOutput(draft, node(draft, "setter"), "value").asNumber());
    }


    @Test void executedSettersAndVariableEventsRemainVisibleDownstream(){
        AdvancedGraphDocument active = variableGraph();
        active.nodes().add(createNode("tick", "event_tick"));
        active.nodes().add(createNode("changed", "event_variable_change"));
        node(active, "changed").data().putString("Variable", "debug");
        node(active, "setter").data().put("Defaults", defaults("default", AdvancedGraphDocument.Value.number(7)));
        active.edges().add(edge("tick", "exec", "setter", "exec"));
        AdvancedGraphDocument draft = active.copy();
        GraphRuntime runtime = new GraphRuntime(null, true);
        runtime.tick(active, false);
        runtime.beginPreviewSample(draft);
        assertEquals(7, runtime.previewOutput(draft, node(draft, "setter"), "value").asNumber());
        assertEquals(7, runtime.previewOutput(draft, node(draft, "getter"), "value").asNumber());
        assertEquals(7, runtime.previewOutput(draft, node(draft, "changed"), "value").asNumber());
        assertEquals(9, runtime.previewOutput(draft, node(draft, "downstream"), "value").asNumber());
        var mirrors = GraphValueMirrors.merge(runtime.liveOutputs(), Map.of(
                "setter:value", AdvancedGraphDocument.Value.number(0),
                "changed:value", AdvancedGraphDocument.Value.number(0)), runtime::isRetainedOutput);
        assertEquals(7, mirrors.get("setter:value").asNumber());
        assertEquals(7, mirrors.get("changed:value").asNumber());
        assertFalse(runtime.isRetainedOutput("sum:value"));
        assertEquals(0, draft.variables().get("debug").asNumber());
        assertTrue(runtime.diagnostics().isEmpty());
    }

    private static AdvancedGraphDocument variableGraph(){
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.variables().put("debug", AdvancedGraphDocument.Value.number(0));
        graph.nodes().add(createNode("getter", "variable_get"));
        graph.nodes().add(createNode("setter", "variable_set"));
        graph.nodes().add(createNode("sum", "add"));
        graph.nodes().add(createNode("downstream", "add"));
        node(graph, "getter").data().putString("Variable", "debug");
        node(graph, "setter").data().putString("Variable", "debug");
        node(graph, "sum").data().put("Defaults", defaults("b", AdvancedGraphDocument.Value.number(1)));
        node(graph, "downstream").data().put("Defaults", defaults("b", AdvancedGraphDocument.Value.number(1)));
        graph.edges().add(edge("getter", "value", "sum", "a"));
        graph.edges().add(edge("sum", "value", "downstream", "a"));
        return graph;
    }

    private static AdvancedGraphDocument.Node createNode(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, new CompoundTag());
    }

    private static AdvancedGraphDocument.Node node(AdvancedGraphDocument graph, String id){
        return graph.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow();
    }

    private static AdvancedGraphDocument.Edge edge(String from, String fromPort, String to, String toPort){
        return new AdvancedGraphDocument.Edge(from + ":" + to, from, fromPort, to, toPort);
    }

    private static CompoundTag defaults(String port, AdvancedGraphDocument.Value value){
        CompoundTag defaults = new CompoundTag();
        defaults.put(port, value.toTag());
        return defaults;
    }
}
