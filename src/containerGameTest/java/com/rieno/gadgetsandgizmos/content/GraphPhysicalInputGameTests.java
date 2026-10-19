package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.GraphSignalRange;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Exercise real Create sources and graph reads on a running server
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class GraphPhysicalInputGameTests{
    private GraphPhysicalInputGameTests(){}

    @GameTest(template = "empty_container")
    public static void leverAndReceivingLinkKeepTheirPhysicalStrength(GameTestHelper ctx){
        var level = ctx.getLevel();
        var leverPos = ctx.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(leverPos, AllBlocks.ANALOG_LEVER.getDefaultState(), 3);
        var lever = (AnalogLeverBlockEntity)level.getBlockEntity(leverPos);
        for(int idx = 0; idx < 6; idx++) lever.changeState(false);
        var ref = new ControllerDirectTargetReference("test:lever", "analog_lever", "", "Lever", null, leverPos);
        ctx.assertTrue(Math.abs(ControllerRedstoneCompat.sampleTarget(level, "", ref) - 0.4D) < 1.0E-6D,
                "Direct lever input did not read six");
        ctx.assertTrue(Math.abs(ControllerRedstoneCompat.sampleTarget(level, "", ref.withFace(Direction.EAST)) - 0.4D) < 1.0E-6D,
                "Face input read a neighbouring block instead of the lever");
        var controllerPos = ctx.absolutePos(new BlockPos(3, 3, 1));
        level.setBlock(controllerPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)level.getBlockEntity(controllerPos);
        var graph = new AdvancedGraphDocument();
        var data = AdvancedGraphNodeFactory.createDefaultData("linker_face_input", AdvancedGraphNodeFactory.Context.EMPTY);
        data.put("TargetData", new ControllerDiscoveryNode("test:lever", ControllerDiscoveryKind.ANALOG_LEVER,
                "", "create:analog_lever", "Lever", null, leverPos).toTag());
        var input = new AdvancedGraphDocument.Node("input", "linker_face_input", "", 0, 0, data);
        graph.nodes().add(input);
        var runtime = new GraphRuntime(controller, true);
        runtime.beginPreviewSample(graph);
        ctx.assertTrue(runtime.previewOutput(graph, input, "value").asNumber() == 6, "Draft input lost physical strength");
        ctx.assertTrue(controller.getGraphTargetData(input, GraphSignalRange.REDSTONE_SIGNAL_PORT).asNumber() == 6,
                "Physical data read returned the controller's unwritten output");
        var direct = new AdvancedGraphDocument.Node("direct", "discovered_target_input", "", 0, 0, data.copy());
        graph.nodes().add(direct);
        runtime.invalidatePreview();
        runtime.beginPreviewSample(graph);
        ctx.assertTrue(runtime.previewOutput(graph, direct, "value").asNumber() == 6,
                "Direct redstone input was normalized and would round to zero at its output");
        var linkPos = ctx.absolutePos(new BlockPos(5, 3, 1));
        level.setBlock(linkPos, AllBlocks.REDSTONE_LINK.getDefaultState().setValue(RedstoneLinkBlock.RECEIVER, true), 3);
        var link = (RedstoneLinkBlockEntity)level.getBlockEntity(linkPos);
        link.setSignal(6);
        var linkRef = new ControllerDirectTargetReference("test:link", "redstone_link", "", "Link", null, linkPos);
        ctx.assertTrue(Math.abs(ControllerRedstoneCompat.sampleTarget(level, "", linkRef) - 0.4D) < 1.0E-6D,
                "Receiving link read its inactive transmitter");
        ctx.succeed();
    }
}
