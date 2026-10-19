package com.rieno.gadgetsandgizmos.content.advanced;

import com.google.gson.JsonParser;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphValues;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AngleUnitConversionGraphTest{
    @Test void catalogAndFactoryExposeNumberPortsAlongsideMapPorts(){
        for(String id : List.of("degrees_to_radians", "radians_to_degrees")){
            var node = node(id, id);
            assertEquals("number", AdvancedGraphCatalog.inputs(node).get("angle"));
            assertEquals("number", AdvancedGraphCatalog.outputs(node).get("angle"));
            assertEquals("map", AdvancedGraphCatalog.inputs(node).get("angles"));
            assertEquals("map", AdvancedGraphCatalog.outputs(node).get("angles"));
            assertEquals("number", node.data().getCompound("Defaults").getCompound("angle").getString("Type"));
        }
    }

    @Test void savedGraphWiresCoordinateAngleThroughBothNumericConverters(){
        var graph = new AdvancedGraphDocument();
        var coords = node("coords", "angle_from_coords");
        var defaults = coords.data().getCompound("Defaults");
        defaults.put("origin", GraphRuntime.fromLibraryValue(MathGraphValues.vector(new Vector3(10, 2, 3))).toTag());
        defaults.put("target", GraphRuntime.fromLibraryValue(MathGraphValues.vector(new Vector3(11, 2, 3))).toTag());
        graph.nodes().add(coords);graph.nodes().add(node("degrees", "radians_to_degrees"));
        graph.nodes().add(node("radians", "degrees_to_radians"));
        graph.edges().add(new AdvancedGraphDocument.Edge("to_degrees", "coords", "angle", "degrees", "angle"));
        graph.edges().add(new AdvancedGraphDocument.Edge("to_radians", "degrees", "angle", "radians", "angle"));
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        var runtime = new GraphRuntime(null);runtime.beginPreviewSample(graph);
        var degrees = graph.nodes().get(1);var radians = graph.nodes().get(2);
        assertTrue(runtime.previewOutput(graph, degrees, "valid").asBoolean());
        assertEquals(90, runtime.previewOutput(graph, degrees, "angle").asNumber(), 1.0E-10);
        assertEquals(Math.PI / 2, runtime.previewOutput(graph, radians, "angle").asNumber(), 1.0E-12);
        assertTrue(runtime.diagnostics().isEmpty(), runtime.diagnostics().toString());
    }

    @Test void savedMapOnlyNodesRemainUsableWithoutScalarDefaults(){
        for(String id : List.of("degrees_to_radians", "radians_to_degrees")){
            var graph = new AdvancedGraphDocument();var node = node("legacy", id);
            node.data().getCompound("Defaults").remove("angle");
            for(String schema : List.of("InputOrder", "OutputOrder")){
                var order = new net.minecraft.nbt.ListTag();order.add(net.minecraft.nbt.StringTag.valueOf("angles"));
                if(schema.equals("OutputOrder")) order.add(net.minecraft.nbt.StringTag.valueOf("valid"));
                node.data().put(schema, order);
            }
            node.data().getCompound("Defaults").put("angles", GraphRuntime.fromLibraryValue(
                    GraphValue.map(Map.of("yaw", id.equals("degrees_to_radians") ? -180 : -Math.PI))).toTag());
            graph.nodes().add(node);graph = AdvancedGraphDocument.fromTag(graph.toTag());node = graph.nodes().getFirst();
            assertEquals("number", AdvancedGraphCatalog.inputs(node).get("angle"));
            assertEquals("number", AdvancedGraphCatalog.outputs(node).get("angle"));
            var runtime = new GraphRuntime(null);runtime.beginPreviewSample(graph);
            assertTrue(runtime.previewOutput(graph, node, "valid").asBoolean());
            var result = GraphRuntime.toLibraryValue(runtime.previewOutput(graph, node, "angles"));
            assertEquals(id.equals("degrees_to_radians") ? -Math.PI : -180, result.member("yaw").asNumber(), 1.0E-10);
        }
    }

    @Test void webCatalogExposesTheSameNumericAndMapPorts() throws Exception{
        try(var stream = getClass().getResourceAsStream("/assets/createthrusters/web/graph-editor/nodes.json")){
            assertNotNull(stream);
            var catalog = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            int found = 0;
            for(var value : catalog.getAsJsonArray("nodes")){
                var entry = value.getAsJsonObject();String id = entry.get("id").getAsString();
                if(!List.of("createthrusters:degrees_to_radians", "createthrusters:radians_to_degrees").contains(id)) continue;
                found++;
                for(String schema : List.of("baseInputs", "baseOutputs", "inputs", "outputs")){
                    assertEquals("number", entry.getAsJsonObject(schema).get("angle").getAsString());
                    assertEquals("map", entry.getAsJsonObject(schema).get("angles").getAsString());
                }
                assertEquals("number", entry.getAsJsonObject("defaultData").getAsJsonObject("Defaults")
                        .getAsJsonObject("angle").get("Type").getAsString());
            }
            assertEquals(2, found);
        }
    }

    private static AdvancedGraphDocument.Node node(String name, String id){
        String type = "createthrusters:" + id;
        return new AdvancedGraphDocument.Node(name, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
}
