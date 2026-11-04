package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerMouseCapturePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerMouseInputPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Verify capture changes without an editor or goggles observer driving graph sampling
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class MouseCaptureGameTests{
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void captureFollowsWiredBooleanAndRetainsHeldInputs(GameTestHelper helper){
        var controller = controller(helper);
        var graph = new AdvancedGraphDocument();
        var variable = node("condition", "variable_get");
        variable.data().putString("Variable", "capture");
        graph.variables().put("capture", AdvancedGraphDocument.Value.bool(false));
        graph.nodes().add(variable);
        var axis = mouse("axis", "mouse_x");
        var button = mouse("button", "left_click");
        var disabled = mouse("disabled", "mouse_x");
        disabled.data().getCompound("Defaults").put("capture_input", AdvancedGraphDocument.Value.bool(false).toTag());
        graph.nodes().addAll(List.of(axis, button, disabled));
        graph.edges().add(new AdvancedGraphDocument.Edge("axis_capture", "condition", "value", "axis", "capture_input"));
        graph.edges().add(new AdvancedGraphDocument.Edge("button_capture", "condition", "value", "button", "capture_input"));
        install(helper, controller, graph);
        var runtime = controller.displayRuntime();
        var active = controller.activeGraphView();
        helper.assertTrue("boolean".equals(AdvancedGraphCatalog.inputs(axis).get("capture_input")), "Capture Input is not wireable");
        helper.assertTrue(runtime.needsRegularTick(active, false), "Capture requires an editor observer");
        runtime.tick(active, false);
        helper.assertTrue(controller.getCapturedMouseInputs().isEmpty(), "False condition captured the mouse");
        controller.handleMouseInput("mouse_x", .5, true);
        helper.assertTrue(controller.getMouseInputValue("mouse_x") == 0, "Disabled input accepted a client value");
        controller.setGraphVariable("capture", AdvancedGraphDocument.Value.bool(true));
        runtime.tick(active, false);
        helper.assertTrue(controller.getCapturedMouseInputs().equals(Set.of("mouse_x", "left_click")), "Wired condition did not capture both controls");
        controller.handleMouseInput("mouse_x", .75, true);
        controller.handleMouseInput("left_click", 1, true);
        runtime.tick(active, false);
        helper.assertTrue(runtime.liveOutput("axis", "value").asNumber() == .75, "Captured axis lost its value");
        helper.assertTrue(!runtime.liveOutput("disabled", "active").asBoolean(), "Disabled duplicate node received another node's capture");
        controller.setGraphVariable("capture", AdvancedGraphDocument.Value.bool(false));
        runtime.tick(active, false);
        helper.assertTrue(controller.getCapturedMouseInputs().isEmpty(), "False condition failed to release capture");
        helper.assertTrue(controller.getMouseInputValue("mouse_x") == .75 && controller.isMouseInputActive("left_click"), "Pausing capture cleared held input values");
        helper.assertTrue(runtime.liveOutput("axis", "value").asNumber() == .75
                && runtime.liveOutput("button", "active").asBoolean(), "Paused node lost its retained outputs");
        controller.handleMouseInput("mouse_x", .25, true);
        runtime.tick(active, false);
        helper.assertTrue(controller.getMouseInputValue("mouse_x") == .75, "Paused capture accepted new movement");
        controller.setGraphVariable("capture", AdvancedGraphDocument.Value.bool(true));
        runtime.tick(active, false);
        helper.assertTrue(controller.getCapturedMouseInputs().contains("mouse_x"), "Capture did not resume after release");
        helper.assertTrue(controller.getMouseInputValue("mouse_x") == .75, "Resuming capture cleared the held axis");
        controller.handleMouseInput("mouse_x", .25, true);
        runtime.tick(active, false);
        helper.assertTrue(runtime.liveOutput("axis", "value").asNumber() == .25, "Resumed capture did not update the axis");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void resetAxisStillResetsWhileCaptureIsPaused(GameTestHelper helper){
        var controller = controller(helper);
        var graph = new AdvancedGraphDocument();
        var variable = node("condition", "variable_get");
        variable.data().putString("Variable", "capture");
        graph.variables().put("capture", AdvancedGraphDocument.Value.bool(true));
        var axis = mouse("axis", "mouse_x");
        axis.data().getCompound("Defaults").put("reset_axis", AdvancedGraphDocument.Value.bool(true).toTag());
        axis.data().getCompound("Defaults").put("timeout", AdvancedGraphDocument.Value.number(1).toTag());
        graph.nodes().addAll(List.of(variable, axis));
        graph.edges().add(new AdvancedGraphDocument.Edge("capture", "condition", "value", "axis", "capture_input"));
        install(helper, controller, graph);
        var runtime = controller.displayRuntime();
        runtime.tick(controller.activeGraphView(), false);
        controller.handleMouseInput("mouse_x", .75, true);
        runtime.tick(controller.activeGraphView(), false);
        controller.setGraphVariable("capture", AdvancedGraphDocument.Value.bool(false));
        runtime.tick(controller.activeGraphView(), false);
        helper.assertTrue(controller.getMouseInputValue("mouse_x") == .75, "Capture pause reset the axis before its timeout");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(controller.getMouseInputValue("mouse_x") == 0, "Reset Axis did not clear the paused axis");
            helper.assertTrue(runtime.liveOutput("axis", "value").asNumber() == 0
                    && !runtime.liveOutput("axis", "active").asBoolean(), "Reset Axis left retained node outputs active");
            helper.succeed();
        });
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void legacyDefaultsAndSharedSourcesRetainCapture(GameTestHelper helper){
        var controller = controller(helper);
        var graph = new AdvancedGraphDocument();
        var legacy = mouse("legacy", "mouse_y");
        legacy.data().getCompound("Defaults").remove("capture_input");
        var disabled = mouse("disabled", "mouse_y");
        disabled.data().getCompound("Defaults").put("capture_input", AdvancedGraphDocument.Value.bool(false).toTag());
        graph.nodes().addAll(List.of(legacy, disabled));
        install(helper, controller, graph);
        controller.displayRuntime().tick(controller.activeGraphView(), false);
        helper.assertTrue(controller.getCapturedMouseInputs().equals(Set.of("mouse_y")), "A disabled duplicate released the legacy source");
        var replacement = controller.getActiveGraph();
        replacement.nodes().removeIf(node -> node.id().equals("legacy"));
        install(helper, controller, replacement);
        controller.displayRuntime().tick(controller.activeGraphView(), false);
        helper.assertTrue(controller.getCapturedMouseInputs().isEmpty(), "Removing the last active source retained capture");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 40)
    public static void capturePacketsKeepTheirControllerTargetAndCompleteState(GameTestHelper helper){
        for(var target : AdvancedControllerMouseInputPayload.Target.values()){
            var packet = new AdvancedControllerMouseCapturePayload(target, new BlockPos(3, 5, 7), UUID.randomUUID(),
                    InteractionHand.OFF_HAND, Map.of("first", "mouse_x", "second", "left_click"));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try{
                AdvancedControllerMouseCapturePayload.STREAM_CODEC.encode(buffer, packet);
                var decoded = AdvancedControllerMouseCapturePayload.STREAM_CODEC.decode(buffer);
                helper.assertTrue(decoded.equals(packet) && !buffer.isReadable(), "Capture packet lost its session or requests");
            }finally{ buffer.release(); }
        }
        helper.succeed();
    }

    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper){
        var pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        return (AdvancedContraptionControllerBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static void install(GameTestHelper helper, AdvancedContraptionControllerBlockEntity controller, AdvancedGraphDocument graph){
        helper.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()), "Could not save graph");
        helper.assertTrue(controller.applyDraft(), "Could not apply graph: " + controller.getGraphDiagnostics());
    }

    private static AdvancedGraphDocument.Node mouse(String id, String input){
        var node = node(id, "mouse_input");
        node.data().putString("MouseInput", input);
        return node;
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, id, 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }
}
