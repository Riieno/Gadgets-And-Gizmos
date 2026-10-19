package com.rieno.gadgetsandgizmos.content.advanced;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphAuthoredValuesTest{
    @Test void editedBodyInputsAndOutputsReachEveryDownstreamNodeIncludingZero(){
        var graph = new AdvancedGraphDocument();
        var source = add(graph, "source", "constant_number");
        source.data().putDouble("Value", 2);
        assertEquals(2, AdvancedGraphPortState.outputDefault(source, "value", "number").asNumber());
        var multiply = add(graph, "multiply", "multiply");
        defaults(multiply, "b", 3);
        var sum = add(graph, "sum", "add");
        defaults(sum, "b", 1);
        graph.edges().add(new AdvancedGraphDocument.Edge("multiply", "source", "value", "multiply", "a"));
        graph.edges().add(new AdvancedGraphDocument.Edge("sum", "multiply", "value", "sum", "a"));
        var runtime = new GraphRuntime(null, true);
        runtime.beginPreviewSample(graph);
        assertEquals(7, runtime.previewOutput(graph, sum, "value").asNumber());
        defaults(multiply, "b", 4);
        runtime.invalidatePreview();
        runtime.beginPreviewSample(graph);
        assertEquals(9, runtime.previewOutput(graph, sum, "value").asNumber());
        AdvancedGraphPortState.setOutputDefault(source, "value", "number", AdvancedGraphDocument.Value.number(0));
        assertEquals(0, source.data().getDouble("Value"));
        runtime.invalidatePreview();
        runtime.beginPreviewSample(graph);
        assertEquals(1, runtime.previewOutput(graph, sum, "value").asNumber());
        defaults(sum, "b", -5);
        runtime.invalidatePreview();
        runtime.beginPreviewSample(graph);
        assertEquals(-5, runtime.previewOutput(graph, sum, "value").asNumber());
    }

    @Test void authoredPersistentDefaultsWinOverLiveSnapshotsButLayoutOnlySavesKeepLiveValues(){
        var source = new AdvancedGraphDocument();
        var node = add(source, "node", "multiply");
        AdvancedGraphPortState.setPersistent(node, "a", false, true, AdvancedGraphDocument.Value.number(4));
        var edited = source.copy();
        var changed = edited.nodes().getFirst();
        AdvancedGraphPortState.setInputDefault(changed, "a", "number", AdvancedGraphDocument.Value.number(0));
        AdvancedGraphPortState.mergePersistentValues(source, edited);
        assertEquals(0, AdvancedGraphPortState.inputDefault(changed, "a", "number").asNumber());
        AdvancedGraphPortState.clearAuthoredEdits(edited);
        AdvancedGraphPortState.updatePersistentValue(node, "a", false, AdvancedGraphDocument.Value.number(7));
        AdvancedGraphPortState.mergePersistentValues(source, edited);
        assertEquals(7, AdvancedGraphPortState.inputDefault(changed, "a", "number").asNumber());
    }

    @Test void everyRegisteredNodeHasAnEnglishSummary() throws Exception{
        try(var stream = getClass().getResourceAsStream("/assets/createthrusters/lang/en_us.json")){
            assertNotNull(stream);
            var translations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var missing = AdvancedGraphCatalog.all().stream().map(AdvancedGraphCatalog.Definition::id)
                    .filter(id -> !translations.has(AdvancedGraphCatalog.summaryTranslationKey(id))).toList();
            assertTrue(missing.isEmpty(), "Missing node summaries: " + missing);
        }
    }

    private static AdvancedGraphDocument.Node add(AdvancedGraphDocument graph, String id, String type){
        var node = new AdvancedGraphDocument.Node(id, type, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
        graph.nodes().add(node);
        return node;
    }

    private static void defaults(AdvancedGraphDocument.Node node, String port, double value){
        AdvancedGraphPortState.setInputDefault(node, port, "number", AdvancedGraphDocument.Value.number(value));
    }
}
