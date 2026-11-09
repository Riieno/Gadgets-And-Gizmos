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
import com.rieno.gadgetsandgizmos.neoforge.ControllerGraphWebServer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Verify cardinal output through the addon graph and exported editor metadata
@GameTestHolder("createthrusters_angles")
@PrefixGameTestTemplate(false)
public final class AngleConversionGameTests{
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
