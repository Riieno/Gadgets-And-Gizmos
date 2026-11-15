package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// A saved Lua node must remain inspectable when its optional engine is removed
@GameTestHolder("createthrusters_lua_optional")
@PrefixGameTestTemplate(false)
public final class LuaOptionalGameTests{
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void savedLuaNodeReportsMissingComputerCraft(GameTestHelper helper){
        helper.assertTrue(!ModList.get().isLoaded("computercraft"), "This check requires a server without CC:Tweaked");
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(helper.getLevel());
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("script", "createthrusters:lua", "Lua", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY)));
        var runtime = new GraphRuntime(controller, true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        helper.assertTrue(runtime.liveOutput("script", "error").asString().contains("CC:Tweaked is required"),
                "The missing optional engine was not reported through the script's error output");
        helper.assertTrue(!runtime.liveOutput("script", "valid").asBoolean(), "An unavailable script was marked valid");
        runtime.clear();
        helper.succeed();
    }
}
