package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonObject;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphValues;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.neoforge.ControllerGraphWebServer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Verify rotation components and cardinal output through saved graphs and editor metadata
@GameTestHolder("createthrusters_angles")
@PrefixGameTestTemplate(false)
public final class AngleConversionGameTests{
    // Carry degree rotations through numeric wires, saved maps and both rotation selectors
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serializedGraphsReturnDegreeRotationComponents(GameTestHelper helper){
        for(String format : List.of("euler", "tait_bryan")){
            var graph = new AdvancedGraphDocument();
            var numberData = AdvancedGraphNodeFactory.createDefaultData("constant_number", null);
            numberData.putDouble("Value", 60);
            graph.nodes().add(new AdvancedGraphDocument.Node("angle", "constant_number", "", 0, 0, numberData));
            var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:convert_angle", null);
            data.putString(AngleConversionNode.INPUT_TYPE, "degrees");
            data.putString(AngleConversionNode.OUTPUT_TYPE, format);
            data.getCompound("Defaults").put("axis", GraphRuntime.fromLibraryValue(MathGraphValues.vector(new Vector3(0, 1, 0))).toTag());
            graph.nodes().add(new AdvancedGraphDocument.Node("convert", "createthrusters:convert_angle", "", 0, 0, data));
            graph.edges().add(new AdvancedGraphDocument.Edge("input", "angle", "value", "convert", "angle"));
            var back = AdvancedGraphNodeFactory.createDefaultData("createthrusters:convert_angle", null);
            back.putString(AngleConversionNode.INPUT_TYPE, format);
            back.putString(AngleConversionNode.OUTPUT_TYPE, "degrees");
            graph.nodes().add(new AdvancedGraphDocument.Node("back", "createthrusters:convert_angle", "", 0, 0, back));
            graph.edges().add(new AdvancedGraphDocument.Edge("roundtrip", "convert", format, "back", format));
            graph = AdvancedGraphDocument.fromTag(graph.toTag());
            var runtime = new GraphRuntime(null, true);
            var node = graph.nodes().get(1);
            String port = format.equals("euler") ? "beta" : "pitch";
            helper.assertTrue(Math.abs(runtime.previewOutput(graph, node, port).asNumber() - 60) < 1.0E-8D,
                    format + " returned radians or the wrong physical axis");
            var map = GraphRuntime.toLibraryValue(runtime.previewOutput(graph, node, format));
            String key = format.equals("euler") ? "beta" : "pitch";
            helper.assertTrue(Math.abs(MathGraphValues.component(map, key, Double.NaN) - 60) < 1.0E-8D,
                    format + " map disagreed with its component output");
            helper.assertTrue(format.equals(map.member("representation").asString())
                            && (format.equals("euler") ? "zyz" : "xyz").equals(map.member("convention").asString())
                            && "degrees".equals(map.member("unit").asString()),
                    format + " lost its rotation type, convention or units");
            helper.assertTrue(Math.abs(runtime.previewOutput(graph, graph.nodes().get(2), "angle").asNumber() - 60) < 1.0E-8D,
                    format + " lost its units on a saved map wire");
            runtime.clear();
        }
        helper.succeed();
    }

    // Decode radian coordinate maps and degree maps across saved converter wires
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serializedCoordinateMapsRetainProperEulerRotations(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var coords = AdvancedGraphNodeFactory.createDefaultData("createthrusters:angle_from_coords", null);
        coords.getCompound("Defaults").put("target", GraphRuntime.fromLibraryValue(MathGraphValues.vector(new Vector3(3, -2, 4))).toTag());
        graph.nodes().add(new AdvancedGraphDocument.Node("coords", "createthrusters:angle_from_coords", "", 0, 0, coords));
        var degrees = AdvancedGraphNodeFactory.createDefaultData("createthrusters:convert_angle", null);
        degrees.putString(AngleConversionNode.INPUT_TYPE, "euler");
        degrees.putString(AngleConversionNode.OUTPUT_TYPE, "euler");
        graph.nodes().add(new AdvancedGraphDocument.Node("degrees", "createthrusters:convert_angle", "", 0, 0, degrees));
        graph.nodes().add(new AdvancedGraphDocument.Node("back", "euler_to_quaternion", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("euler_to_quaternion", null)));
        graph.edges().add(new AdvancedGraphDocument.Edge("radians", "coords", "euler", "degrees", "euler"));
        graph.edges().add(new AdvancedGraphDocument.Edge("degrees", "degrees", "euler", "back", "euler"));
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        var runtime = new GraphRuntime(null, true);
        var source = GraphRuntime.toLibraryValue(runtime.previewOutput(graph, graph.nodes().getFirst(), "quaternion"));
        var actual = GraphRuntime.toLibraryValue(runtime.previewOutput(graph, graph.nodes().getLast(), "quaternion"));
        var expected = MathGraphValues.quaternion(source);
        var rotation = MathGraphValues.quaternion(actual);
        for(Vector3 basis : List.of(new Vector3(1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1)))
            helper.assertTrue(expected.rotate(basis).distance(rotation.rotate(basis)) < 1.0E-8D,
                    "A saved Euler wire changed its rotation or converted units twice");
        helper.assertTrue(runtime.previewOutput(graph, graph.nodes().getLast(), "valid").asBoolean(), "The standalone converter rejected a tagged degree map");
        runtime.clear();
        helper.succeed();
    }

    // Retain the string schema and world directions through graph serialization and numeric wires
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serializedGraphsReturnCardinalStrings(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        List<String> expected = List.of("South", "South East", "East", "North East", "North", "North West", "West", "South West");
        for(int idx = 0; idx < expected.size(); idx++){
            var numberData = AdvancedGraphNodeFactory.createDefaultData("constant_number", null);
            numberData.putDouble("Value", idx * 45);
            graph.nodes().add(new AdvancedGraphDocument.Node("angle" + idx, "constant_number", "", 0, 0, numberData));
            var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:convert_angle", null);
            data.putString(AngleConversionNode.INPUT_TYPE, "degrees");
            data.putString(AngleConversionNode.OUTPUT_TYPE, "cardinal");
            graph.nodes().add(new AdvancedGraphDocument.Node("convert" + idx, "createthrusters:convert_angle", "", 0, 0, data));
            graph.edges().add(new AdvancedGraphDocument.Edge("edge" + idx, "angle" + idx, "value", "convert" + idx, "angle"));
        }
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        var runtime = new GraphRuntime(null, true);
        for(int idx = 0; idx < expected.size(); idx++){
            var node = graph.nodes().get(idx * 2 + 1);
            helper.assertTrue("string".equals(AdvancedGraphCatalog.outputs(node).get("cardinal")), "Cardinal output lost its string schema");
            var output = runtime.previewOutput(graph, node, "cardinal");
            helper.assertTrue("string".equals(output.type()) && expected.get(idx).equals(output.asString()),
                    "The graph returned " + output + " instead of " + expected.get(idx));
            helper.assertTrue(runtime.previewOutput(graph, node, "valid").asBoolean(), "A valid world heading was rejected");
        }
        runtime.clear();
        helper.succeed();
    }

    // Offer Cardinal only on the output selector in the exported web editor catalog
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void webCatalogPublishesCardinalAsAnOutputChoice(GameTestHelper helper) throws Exception{
        var method = ControllerGraphWebServer.class.getDeclaredMethod("nodeCatalogJson");
        method.setAccessible(true);
        var catalog = (JsonObject)method.invoke(null);
        for(var entry : catalog.getAsJsonArray("nodes")){
            var node = entry.getAsJsonObject();
            if(!"createthrusters:convert_angle".equals(node.get("id").getAsString())) continue;
            var properties = node.getAsJsonObject("properties");
            helper.assertTrue(!properties.getAsJsonObject(AngleConversionNode.INPUT_TYPE).getAsJsonArray("options").toString().contains("cardinal"),
                    "The editor offered Cardinal as an angle input");
            helper.assertTrue(properties.getAsJsonObject(AngleConversionNode.OUTPUT_TYPE).getAsJsonArray("options").toString().contains("cardinal"),
                    "The editor omitted Cardinal from angle outputs");
            helper.succeed();
            return;
        }
        helper.fail("The exported catalog omitted Convert Angle");
    }
}
