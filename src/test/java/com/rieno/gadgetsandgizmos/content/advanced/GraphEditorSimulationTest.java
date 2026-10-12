package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimePayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphEditorSimulationTest{
    @Test void fullLiveValuesReachEveryStageAfterPanningZoomingMovingAndCollapsing(){
        AdvancedGraphDocument source = graph();
        AdvancedGraphDocument draft = source.copy();
        GraphRuntime preview = new GraphRuntime(null, true);
        for(int interaction = 0; interaction < 6; interaction++){
            draft.setViewport(interaction * 40, interaction * -30, 0.5 + interaction * 0.2);
            var prev = node(draft, "interpolate");
            draft.nodes().set(draft.nodes().indexOf(prev), new AdvancedGraphDocument.Node(
                    prev.id(), prev.type(), "Moved", interaction * 100, interaction * 70, prev.data()));
            node(draft, "interpolate").data().putBoolean("EditorCollapsed", interaction % 2 == 0);
            preview.beginPreviewSample(draft, source, Map.of("debug", value(6)),
                    Map.of("interpolate:value", value(8), "multiply:value", value(0)));
            assertEquals(6, preview.previewOutput(draft, node(draft, "getter"), "value").asNumber());
            assertEquals(8, preview.previewOutput(draft, node(draft, "interpolate"), "value").asNumber());
            assertEquals(24, preview.previewOutput(draft, node(draft, "multiply"), "value").asNumber());
            assertEquals(24, preview.previewInput(draft, node(draft, "clamp"), "value").asNumber());
            assertEquals(24, preview.previewOutput(draft, node(draft, "clamp"), "value").asNumber());
        }
        assertTrue(preview.liveOutputs().isEmpty());
        assertEquals(0, draft.variables().get("debug").asNumber());
    }

    @Test void logicEditsInvalidateStaleSnapshotsAndCalculateFromNewInputs(){
        AdvancedGraphDocument source = graph();
        AdvancedGraphDocument draft = source.copy();
        defaults(node(draft, "interpolate"), Map.of("b", 14.0, "amount", 0.5));
        GraphRuntime preview = new GraphRuntime(null, true);
        preview.beginPreviewSample(draft, source, Map.of("debug", value(6)),
                Map.of("interpolate:value", value(8), "multiply:value", value(0)));
        assertEquals(10, preview.previewOutput(draft, node(draft, "interpolate"), "value").asNumber());
        assertEquals(30, preview.previewOutput(draft, node(draft, "clamp"), "value").asNumber());
        defaults(node(draft, "multiply"), Map.of("b", 4.0));
        preview.beginPreviewSample(draft, source, Map.of("debug", value(6)), Map.of("interpolate:value", value(8)));
        assertEquals(40, preview.previewOutput(draft, node(draft, "clamp"), "value").asNumber());
    }

    @Test void statefulEditorHistorySurvivesLayoutChangesWithoutChangingTheRunningGraph(){
        AdvancedGraphDocument source = new AdvancedGraphDocument();
        source.nodes().add(create("source", "controller_channel_input"));
        source.nodes().add(create("delta", "math_delta"));
        source.edges().add(edge("source", "value", "delta", "In"));
        GraphRuntime server = new GraphRuntime(null);
        server.compile(source);
        CompoundTag before = server.createShutdownSnapshot();
        server.beginPreviewSample(source, source, Map.of(), Map.of("source:value", value(5)));
        server.previewOutput(source, node(source, "delta"), "Delta");
        assertEquals(before, server.createShutdownSnapshot());
        GraphRuntime editor = new GraphRuntime(null, true);
        AdvancedGraphDocument draft = source.copy();
        editor.beginPreviewSample(draft, source, Map.of(), Map.of("source:value", value(5)));
        assertEquals(0, editor.previewOutput(draft, node(draft, "delta"), "Delta").asNumber());
        draft.setViewport(100, -50, 1.5);
        editor.beginPreviewSample(draft, source, Map.of(), Map.of("source:value", value(8)));
        assertEquals(3, editor.previewOutput(draft, node(draft, "delta"), "Delta").asNumber());
        assertEquals(before, server.createShutdownSnapshot());
    }

    @Test void transportKeepsLongTextNestedMapsListsAndZeroValuesForCalculation(){
        String text = "x".repeat(1500);
        CompoundTag entries = new CompoundTag();
        entries.put("0", AdvancedGraphDocument.Value.string(text).toTag());
        entries.put("1", AdvancedGraphDocument.Value.bool(false).toTag());
        CompoundTag map = new CompoundTag();
        map.put("list", AdvancedGraphDocument.Value.list(entries).toTag());
        map.put("number", value(0).toTag());
        var raw = AdvancedGraphDocument.Value.map(map);
        var display = AdvancedGraphLiveValue.from(raw);
        assertTrue(display.textValue().length() <= 512);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try{
            var packet = new AdvancedControllerRuntimePayload(BlockPos.ZERO, null, 1, Map.of(),
                    Map.of("input:value", AdvancedGraphLiveValue.from(AdvancedGraphDocument.Value.string(text))),
                    Map.of("output:value", display), Map.of(), Map.of("debug", raw));
            AdvancedControllerRuntimePayload.STREAM_CODEC.encode(buffer, packet);
            var decoded = AdvancedControllerRuntimePayload.STREAM_CODEC.decode(buffer);
            assertEquals(raw, decoded.liveOutputs().get("output:value").value());
            assertEquals(text, decoded.liveInputs().get("input:value").value().asString());
            assertEquals(raw, decoded.variables().get("debug"));
            var copy = decoded.liveOutputs().get("output:value").value();
            copy.payload().remove("list");
            assertEquals(raw, decoded.liveOutputs().get("output:value").value());
        }finally{ buffer.release(); }
    }


    @Test void functionFramesPropagateCompleteVariablesAcrossCallsAndLayoutChanges(){
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.variables().put("debug", value(0));
        graph.nodes().add(create("getter", "variable_get"));
        node(graph, "getter").data().putString("Variable", "debug");
        var function = new AdvancedGraphDocument.FunctionGraph("scale", "Scale");
        var input = create("input", "function_input");
        CompoundTag outputs = new CompoundTag();
        outputs.putString("value", "number");
        input.data().put("DynamicOutputs", outputs);
        var output = create("output", "function_output");
        CompoundTag inputs = new CompoundTag();
        inputs.putString("value", "number");
        output.data().put("DynamicInputs", inputs);
        var multiply = create("scale_value", "multiply");
        defaults(multiply, Map.of("b", 3.0));
        function.nodes().add(input);
        function.nodes().add(multiply);
        function.nodes().add(output);
        function.edges().add(edge("input", "value", "scale_value", "a"));
        function.edges().add(edge("scale_value", "value", "output", "value"));
        graph.functions().add(function);
        graph.nodes().add(create("call", "function_call"));
        AdvancedGraphFunctions.configureCall(node(graph, "call"), function);
        graph.nodes().add(create("result", "add"));
        defaults(node(graph, "result"), Map.of("b", 1.0));
        graph.edges().add(edge("getter", "value", "call", "value"));
        graph.edges().add(edge("call", "value", "result", "a"));
        var draft = graph.copy();
        var preview = new GraphRuntime(null, true);
        preview.beginPreviewSample(draft, graph, Map.of("debug", value(7)), Map.of());
        assertEquals(22, preview.previewOutput(draft, node(draft, "result"), "value").asNumber());
        var local = draft.function("scale").nodes().stream().filter(node -> node.id().equals("scale_value")).findFirst().orElseThrow();
        assertEquals(21, preview.previewFunctionOutput(draft, "scale", local, "value").asNumber());
        draft.function("scale").setViewport(100, 200, 0.5);
        preview.beginPreviewSample(draft, graph, Map.of("debug", value(9)), Map.of());
        assertEquals(28, preview.previewOutput(draft, node(draft, "result"), "value").asNumber());
    }


    @Test void structuredAndLongStringVariablesDriveCollectionStringAndMathNodes(){
        AdvancedGraphDocument source = new AdvancedGraphDocument();
        source.variables().put("debug", AdvancedGraphDocument.Value.list(new CompoundTag()));
        source.nodes().add(create("getter", "variable_get"));
        node(source, "getter").data().putString("Variable", "debug");
        source.nodes().add(create("split", "split_list"));
        CompoundTag ports = new CompoundTag();
        ports.putString("0", "string");
        ports.putString("1", "boolean");
        node(source, "split").data().put("DynamicOutputs", ports);
        source.nodes().add(create("length", "str_length"));
        source.nodes().add(create("result", "multiply"));
        defaults(node(source, "result"), Map.of("b", 2.0));
        source.edges().add(edge("getter", "value", "split", "value"));
        source.edges().add(edge("split", "0", "length", "In"));
        source.edges().add(edge("length", "Length", "result", "a"));
        CompoundTag values = new CompoundTag();
        values.put("0", AdvancedGraphDocument.Value.string("x".repeat(1500)).toTag());
        values.put("1", AdvancedGraphDocument.Value.bool(false).toTag());
        var draft = source.copy();
        var preview = new GraphRuntime(null, true);
        preview.beginPreviewSample(draft, source, Map.of("debug", AdvancedGraphDocument.Value.list(values)), Map.of());
        assertEquals(3000, preview.previewOutput(draft, node(draft, "result"), "value").asNumber());
        assertEquals(AdvancedGraphDocument.Value.bool(false),
                preview.previewOutput(draft, node(draft, "split"), "1"));
    }

    private static AdvancedGraphDocument graph(){
        var graph = new AdvancedGraphDocument();
        graph.variables().put("debug", value(0));
        graph.nodes().add(create("getter", "variable_get"));
        node(graph, "getter").data().putString("Variable", "debug");
        graph.nodes().add(create("interpolate", "lerp"));
        defaults(node(graph, "interpolate"), Map.of("b", 10.0, "amount", 0.5));
        graph.nodes().add(create("multiply", "multiply"));
        defaults(node(graph, "multiply"), Map.of("b", 3.0));
        graph.nodes().add(create("clamp", "clamp"));
        defaults(node(graph, "clamp"), Map.of("min", 0.0, "max", 100.0));
        graph.edges().add(edge("getter", "value", "interpolate", "a"));
        graph.edges().add(edge("interpolate", "value", "multiply", "a"));
        graph.edges().add(edge("multiply", "value", "clamp", "value"));
        return graph;
    }

    private static AdvancedGraphDocument.Value value(double value){ return AdvancedGraphDocument.Value.number(value); }
    private static AdvancedGraphDocument.Node create(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, new CompoundTag());
    }
    private static AdvancedGraphDocument.Node node(AdvancedGraphDocument graph, String id){
        return graph.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow();
    }
    private static AdvancedGraphDocument.Edge edge(String from, String fromPort, String to, String toPort){
        return new AdvancedGraphDocument.Edge(from + to, from, fromPort, to, toPort);
    }
    private static void defaults(AdvancedGraphDocument.Node node, Map<String, Double> values){
        CompoundTag defaults = new CompoundTag();
        values.forEach((port, value) -> defaults.put(port, value(value).toTag()));
        node.data().put("Defaults", defaults);
    }
}
