package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Resolve wired target changes while passive sampling and collapsed outputs are being evaluated
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class AccTargetSchemaGameTests{
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 40)
    public static void unavailableCameraTargetsDoNotInvalidatePassiveSampling(GameTestHelper helper){
        var controller = controller(helper);
        var graph = graph("camera");
        var runtime = new GraphRuntime(controller);
        for(int idx = 0; idx < 40; idx++){
            graph.variables().put("target", target(helper, new BlockPos(3 + idx % 2, 1, 1)));
            runtime.tick(graph, true);
        }
        for(var port : AdvancedGraphCatalog.outputs(graph.nodes().get(1)).entrySet()){
            if("exec".equals(port.getValue())) continue;
            helper.assertTrue(port.getValue().equals(runtime.liveOutput("reader", port.getKey()).type()),
                    "Passive sampling lost the camera output " + port.getKey());
        }
        helper.assertTrue(runtime.diagnostics().isEmpty(), "Wired camera sampling produced diagnostics: " + runtime.diagnostics());
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 40)
    public static void collapsedDataOutputsSurvivePendingTargetSchemaRefresh(GameTestHelper helper){
        var controller = controller(helper);
        var graph = graph("get_block_data");
        var reader = graph.nodes().get(1);
        reader.data().putBoolean(AdvancedGraphCatalog.COLLAPSE_OUTPUTS_TO_MAP_TAG, true);
        CompoundTag outputs = new CompoundTag();
        outputs.putString("saved_first", "number");
        outputs.putString("saved_second", "boolean");
        reader.data().put("DynamicOutputs", outputs);
        graph.variables().put("target", target(helper, new BlockPos(3, 1, 1)));
        var runtime = new GraphRuntime(controller);
        for(int idx = 0; idx < 20; idx++){
            runtime.tick(graph, false);
            var val = runtime.previewOutput(graph, reader, AdvancedGraphCatalog.COLLAPSED_OUTPUT_MAP_PORT);
            helper.assertTrue("map".equals(val.type()), "A pending target lost its collapsed output map");
            helper.assertTrue(runtime.diagnostics().isEmpty(), "Collapsed target output failed: " + runtime.diagnostics());
        }
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 40)
    public static void loadedDataTargetsRebindAndKeepCurrentOutputValues(GameTestHelper helper){
        var controller = controller(helper);
        var first = new BlockPos(3, 1, 1);
        var second = new BlockPos(4, 1, 1);
        helper.setBlock(first, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        helper.setBlock(second, net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
        var graph = graph("get_block_data");
        var reader = graph.nodes().get(1);
        reader.data().putBoolean(AdvancedGraphCatalog.COLLAPSE_OUTPUTS_TO_MAP_TAG, true);
        var runtime = new GraphRuntime(controller);
        for(int idx = 0; idx < 20; idx++){
            int phase = idx;
            helper.runAfterDelay(idx + 1, () -> {
                BlockPos pos = phase % 2 == 0 ? first : second;
                graph.variables().put("target", target(helper, pos));
                runtime.tick(graph, false);
                var val = runtime.previewOutput(graph, reader, "data");
                helper.assertTrue("map".equals(val.type()) && !val.payload().isEmpty(), "A loaded target lost its data");
                String expected = phase % 2 == 0 ? "minecraft:stone" : "minecraft:gold_block";
                helper.assertTrue(val.payload().toString().contains(expected), "Target data did not follow the new target: " + val);
                helper.assertTrue(runtime.diagnostics().isEmpty(), "A loaded target refresh failed: " + runtime.diagnostics());
                if(phase == 19) helper.succeed();
            });
        }
    }

    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper){
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        return (AdvancedContraptionControllerBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static AdvancedGraphDocument graph(String type){
        var graph = new AdvancedGraphDocument();
        CompoundTag source = AdvancedGraphNodeFactory.createDefaultData("variable_get", AdvancedGraphNodeFactory.Context.EMPTY);
        source.putString("Variable", "target");
        graph.nodes().add(new AdvancedGraphDocument.Node("source", "variable_get", "Target", 0, 0, source));
        graph.nodes().add(new AdvancedGraphDocument.Node("reader", type, "Reader", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY)));
        graph.edges().add(new AdvancedGraphDocument.Edge("target_wire", "source", "value", "reader", "target"));
        return graph;
    }

    private static AdvancedGraphDocument.Value target(GameTestHelper helper, BlockPos pos){
        return AdvancedGraphDocument.Value.target(new ControllerDiscoveryNode("target_" + pos.getX(),
                ControllerDiscoveryKind.MACHINE, "", "", "Target", null, helper.absolutePos(pos)).toTag());
    }
}
