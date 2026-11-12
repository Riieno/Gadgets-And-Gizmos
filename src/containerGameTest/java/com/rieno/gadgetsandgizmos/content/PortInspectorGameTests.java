package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedDisplayWidgets;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.display.PresentationLayout;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

// Exercise color inputs and local hologram publication through the production graph runtime
@GameTestHolder("createthrusters_visual_graph")
@PrefixGameTestTemplate(false)
public final class PortInspectorGameTests{
    // Keep saved colors working while allowing a connected color to replace them
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void colorInputsPreserveLegacyValuesAndAcceptWires(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var color = node("color", "constant_color");
        color.data().putInt("Value", 0xFF123456);
        graph.nodes().add(color);
        var runtime = new GraphRuntime(null, true);
        helper.assertTrue((int)runtime.previewOutput(graph, color, "value").asNumber() == 0xFF123456,
                "A saved color changed when its input was added");
        var source = node("source", "constant_number"); source.data().putDouble("Value", 0xFFABCDEF);
        graph.nodes().add(source);
        graph.edges().add(new AdvancedGraphDocument.Edge("color_wire", "source", "value", "color", "value"));
        graph = AdvancedGraphDocument.fromTag(graph.toTag()); color = graph.nodes().getFirst();
        helper.assertTrue((int)runtime.previewOutput(graph, color, "value").asNumber() == 0xFFABCDEF,
                "The color node ignored its connected input");
        runtime.clear(); helper.succeed();
    }

    // Publish the hologram's layout and wired values even when there is no display target
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unboundHologramsPublishLayoutAndLiveInputs(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var source = node("source", "constant_number"); source.data().putDouble("Value", 3.5);
        var widget = node("widget", "acc_hologram_widget");
        graph.nodes().addAll(List.of(source, widget));
        graph.edges().add(new AdvancedGraphDocument.Edge("width", "source", "value", "widget", "size_x"));
        var runtime = new GraphRuntime(null, true); runtime.compile(graph);
        var layout = PresentationLayout.read(runtime.hudPresentationLayout());
        helper.assertTrue(layout.size() == 1 && layout.getFirst().id().equals("widget"),
                "The world hologram was omitted from the streamed layout");
        var values = runtime.playerHudValues(UUID.randomUUID());
        helper.assertTrue(values.inputs().get("widget:size_x").asNumber() == 3.5,
                "The world hologram did not receive its wired width");
        helper.assertTrue(AdvancedDisplayWidgets.isUntargetedHologram(widget, values.inputs().get("widget:target")),
                "An unbound hologram required an ACC display");
        CompoundTag target = new CompoundTag(); target.putLong("BlockPos", BlockPos.ZERO.asLong());
        helper.assertTrue(!AdvancedDisplayWidgets.isUntargetedHologram(widget, AdvancedGraphDocument.Value.target(target)),
                "A targeted hologram also rendered above its ACC");
        runtime.clear(); helper.succeed();
    }

    // Keep the final empty layout observable before releasing nearby viewers
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void removedHologramsReleaseNearbyObserversAfterTheirFinalUpdate(GameTestHelper helper){
        helper.setBlock(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(BlockPos.ZERO);
        var graph = controller.getDraftGraph(); graph.nodes().clear(); graph.edges().clear();
        graph.nodes().add(node("widget", "acc_hologram_widget"));
        helper.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()) && controller.applyDraft(),
                "Could not apply the hologram graph");
        helper.assertTrue(controller.hasLocalHologramObservers(), "The hologram did not request nearby observers");
        graph = controller.getDraftGraph(); graph.nodes().clear(); graph.edges().clear();
        helper.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()) && controller.applyDraft(),
                "Could not remove the hologram graph");
        helper.assertTrue(controller.hasLocalHologramObservers(), "Nearby viewers lost the final empty layout");
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(!controller.hasLocalHologramObservers(), "Nearby hologram observers were retained indefinitely");
            helper.succeed();
        });
    }

    // Create the same node defaults used by the editor
    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
}
