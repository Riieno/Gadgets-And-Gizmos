package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphCollectionNodesTest{
    @Test void allFourOperationsEvaluateListsAndMapsThroughTheRegisteredLibraryExecutors(){
        for(var collection : List.of(GraphValue.list(List.of("first", "second")),
                GraphValue.map(GraphPortOrder.entries(Map.entry("zeta", "first"), Map.entry("alpha", "second"))))){
            var graph = new AdvancedGraphDocument();
            for(String operation : List.of("push", "pop", "contains", "index_of")){
                String type = "createthrusters:collection_" + operation;
                var node = new AdvancedGraphDocument.Node(operation, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
                graph.nodes().add(node);
                defaults(node, "collection", GraphRuntime.fromLibraryValue(collection));
                defaults(node, "value", AdvancedGraphDocument.Value.string("second"));
                defaults(node, "key", AdvancedGraphDocument.Value.string("middle"));
                defaults(node, "index", AdvancedGraphDocument.Value.number(1));
                assertNotNull(AdvancedGraphCatalog.get(type));
            }
            var runtime = new GraphRuntime(null, true);runtime.beginPreviewSample(graph);
            var pushed = runtime.previewOutput(graph, graph.nodes().get(0), "result");
            assertEquals(collection.type(), pushed.type());assertEquals(3, GraphRuntime.splitListOutputTypesFor(pushed).size());
            var popped = runtime.previewOutput(graph, graph.nodes().get(1), "result");
            assertEquals(collection.type(), popped.type());assertEquals(1, GraphRuntime.splitListOutputTypesFor(popped).size());
            assertEquals("second", runtime.previewOutput(graph, graph.nodes().get(1), "value").asString());
            assertTrue(runtime.previewOutput(graph, graph.nodes().get(1), "removed").asBoolean());
            assertTrue(runtime.previewOutput(graph, graph.nodes().get(2), "result").asBoolean());
            assertEquals(1, runtime.previewOutput(graph, graph.nodes().get(3), "index").asNumber());
            defaults(graph.nodes().get(3), "value", AdvancedGraphDocument.Value.string("missing"));
            runtime.beginPreviewSample(graph);
            assertEquals(-1, runtime.previewOutput(graph, graph.nodes().get(3), "index").asNumber());
        }
    }

    @Test void appliedLoopStateBecomesVisibleWithoutReopeningTheEditor(){
        var oldSource = new AdvancedGraphDocument();
        var draft = new AdvancedGraphDocument();
        var loop = new AdvancedGraphDocument.Node("loop", "bounded_loop", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("bounded_loop", null));draft.nodes().add(loop);
        var preview = new GraphRuntime(null, true);
        var outputs = Map.of("loop:index", AdvancedGraphDocument.Value.number(2));
        preview.beginPreviewSample(draft, oldSource, Map.of(), outputs);
        assertEquals(0, preview.previewOutput(draft, loop, "index").asNumber());
        preview.beginPreviewSample(draft, draft.copy(), Map.of(), outputs);
        assertEquals(2, preview.previewOutput(draft, loop, "index").asNumber());
        preview.beginPreviewSample(draft, draft.copy(), Map.of(), Map.of("loop:index", AdvancedGraphDocument.Value.number(3)));
        assertEquals(3, preview.previewOutput(draft, loop, "index").asNumber());
    }

    private static void defaults(AdvancedGraphDocument.Node node, String port, AdvancedGraphDocument.Value value){
        CompoundTag defaults = node.data().getCompound("Defaults");defaults.put(port, value.toTag());node.data().put("Defaults", defaults);
    }
}
