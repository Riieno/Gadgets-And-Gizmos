package com.rieno.gadgetsandgizmos.content.advanced;

import com.google.gson.JsonParser;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphValues;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AngleFromCoordsGraphTest{
    private static final String TYPE = "createthrusters:angle_from_coords";

    @Test void catalogAndFactoryExposeOrderedPortsAndEditableCoordinateMaps(){
        var node = node();
        assertEquals("Angle from Coords", AdvancedGraphCatalog.displayName(TYPE));
        assertEquals(List.of("origin", "target"), List.copyOf(AdvancedGraphCatalog.inputs(node).keySet()));
        assertEquals(List.of("euler", "quaternion", "tait_bryan", "axis", "angle", "degrees", "valid"),
                List.copyOf(AdvancedGraphCatalog.outputs(node).keySet()));
        var defaults = node.data().getCompound("Defaults");
        assertEquals("map", defaults.getCompound("origin").getString("Type"));
        assertEquals("map", defaults.getCompound("target").getString("Type"));
        assertEquals("number", AdvancedGraphCatalog.outputs(node).get("degrees"));
    }

    @Test void previewAndSavedGraphsUseTheRegisteredLibraryExecutor(){
        var graph = new AdvancedGraphDocument();
        var node = node();graph.nodes().add(node);
        var defaults = node.data().getCompound("Defaults");
        defaults.put("origin", GraphRuntime.fromLibraryValue(MathGraphValues.vector(new Vector3(10, 2, 3))).toTag());
        defaults.put("target", GraphRuntime.fromLibraryValue(MathGraphValues.vector(new Vector3(10, 12, 3))).toTag());
        graph = AdvancedGraphDocument.fromTag(graph.toTag());node = graph.nodes().getFirst();
        var runtime = new GraphRuntime(null);
        runtime.beginPreviewSample(graph);
        assertTrue(runtime.previewOutput(graph, node, "valid").asBoolean());
        assertEquals(Math.PI / 2, runtime.previewOutput(graph, node, "angle").asNumber(), 1.0E-12);
        assertEquals("number", runtime.previewOutput(graph, node, "degrees").type());
        assertEquals(0, runtime.previewOutput(graph, node, "degrees").asNumber(), 1.0E-10);
        var rotation = MathGraphValues.quaternion(GraphRuntime.toLibraryValue(runtime.previewOutput(graph, node, "quaternion")));
        Vector3 aimed = rotation.rotate(new Vector3(0, 0, 1));
        assertEquals(0, aimed.x(), 1.0E-12);assertEquals(1, aimed.y(), 1.0E-12);assertEquals(0, aimed.z(), 1.0E-12);
    }

    @Test void packagedWebCatalogContainsTheNewNodeAndCoordinateDefaults() throws Exception{
        try(var stream = getClass().getResourceAsStream("/assets/createthrusters/web/graph-editor/nodes.json")){
            assertNotNull(stream);
            var catalog = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var matches = new java.util.ArrayList<com.google.gson.JsonObject>();
            for(var entry : catalog.getAsJsonArray("nodes")){
                if(entry.getAsJsonObject().get("id").getAsString().equals(TYPE)) matches.add(entry.getAsJsonObject());
            }
            assertEquals(1, matches.size());
            var entry = matches.getFirst();
            assertEquals("Angle from Coords", entry.get("name").getAsString());
            assertEquals(List.of("origin", "target"), List.copyOf(entry.getAsJsonObject("inputs").keySet()));
            assertEquals(List.of("euler", "quaternion", "tait_bryan", "axis", "angle", "degrees", "valid"),
                    List.copyOf(entry.getAsJsonObject("outputs").keySet()));
            assertEquals(1, entry.getAsJsonObject("defaultData").getAsJsonObject("Defaults").getAsJsonObject("target")
                    .getAsJsonObject("Payload").getAsJsonObject("z").getAsJsonObject("Payload").get("Value").getAsInt());
        }
    }

    @Test void savedGraphsPreviewTheFullCircleEvenWhenHeightChanges(){
        for(int degrees : new int[]{0, 90, 179, 180, 181, 225, 270, 359}){
            var graph = new AdvancedGraphDocument();var node = node();graph.nodes().add(node);
            double radians = Math.toRadians(degrees);
            node.data().getCompound("Defaults").put("target", GraphRuntime.fromLibraryValue(MathGraphValues.vector(
                    new Vector3(Math.sin(radians), degrees % 2 == 0 ? 50 : -50, Math.cos(radians)))).toTag());
            graph = AdvancedGraphDocument.fromTag(graph.toTag());node = graph.nodes().getFirst();
            var runtime = new GraphRuntime(null);runtime.beginPreviewSample(graph);
            assertTrue(runtime.previewOutput(graph, node, "valid").asBoolean());
            assertEquals(degrees, runtime.previewOutput(graph, node, "degrees").asNumber(), 1.0E-10);
        }
    }

    private static AdvancedGraphDocument.Node node(){
        return new AdvancedGraphDocument.Node("angle", TYPE, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(TYPE, null));
    }
}
