package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDraftSession;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal;
import com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagDelta;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

// Keep generated canvases current without replacing their owning editor or interaction state
@GameTestHolder("createthrusters_visual_graph")
@PrefixGameTestTemplate(false)
public final class WidgetLivePreviewGameTests{
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void widgetSnapshotsFollowLiveInputsWithoutGraphEdits(GameTestHelper helper){
        double[] level = {10};
        for(String type : List.of("advanced_hud_element", "acc_display_widget")){
            level[0] = 10;
            var graph = graph(type);
            var runtime = new GraphRuntime(controller(helper, level), true);
            try{
                runtime.replaceSimulationGraph(null, graph); runtime.tickSimulation(graph, null);
                assertValue(helper, runtime.liveOutput("widget", "widget"), "Original", 10);
                level[0] = 75; runtime.sampleRuntimePorts(null);
                assertValue(helper, runtime.liveOutput("widget", "widget"), "Original", 75);
            }finally{ runtime.clear(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void openDraftPublishesWidgetDesignEdits(GameTestHelper helper){
        var graph = graph("acc_display_widget");
        var session = new AdvancedGraphDraftSession(controller(helper, new double[]{25}), UUID.randomUUID());
        try{
            CompoundTag original = graph.toTag();
            helper.assertTrue(session.edit(-1, 0, true, original), "Initial draft was rejected");
            session.tick(helper.getLevel().getGameTime());
            assertValue(helper, widgetChange(session.changes()), "Original", 25);
            var edited = graph.copy();
            edited.nodes().getFirst().data().getList("WidgetElements", Tag.TAG_COMPOUND).getCompound(0).putString("Text", "Edited live");
            edited.nodes().getFirst().data().putInt("WidgetWidth", 320);
            helper.assertTrue(session.edit(0, 1, false, CompoundTagDelta.between(original, edited.toTag())), "Design edit was rejected");
            session.tick(helper.getLevel().getGameTime() + 1);
            var updated = widgetChange(session.changes());
            assertValue(helper, updated, "Edited live", 25);
            helper.assertTrue(updated.payload().getInt("WidgetWidth") == 320, "The open draft kept its old canvas size");
        }finally{ session.close(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void regeneratingWidgetsPreservesSubmittedControlValues(GameTestHelper helper){
        var graph = graph("advanced_hud_element");
        var runtime = new GraphRuntime(controller(helper, new double[]{40}), true);
        try{
            runtime.replaceSimulationGraph(null, graph); runtime.tickSimulation(graph, null);
            helper.assertTrue(runtime.enqueueHudInteraction("widget", "control", AdvancedGraphDocument.Value.bool(true)), "Control input was rejected");
            runtime.tickSimulation(graph, null);
            helper.assertTrue(runtime.liveOutput("widget", "selected").asBoolean(), "The control did not retain its submitted value");
            var edited = graph.copy();
            edited.nodes().getFirst().data().getList("WidgetElements", Tag.TAG_COMPOUND).getCompound(0).putString("Text", "New style");
            runtime.replaceSimulationGraph(graph, edited); runtime.sampleRuntimePorts(null);
            assertValue(helper, runtime.liveOutput("widget", "widget"), "New style", 40);
            helper.assertTrue(runtime.liveOutput("widget", "selected").asBoolean(), "Updating the canvas reset the player's control value");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    private static AdvancedGraphDocument graph(String type){
        var graph = new AdvancedGraphDocument(); var widget = node("widget", type);
        widget.data().putInt("WidgetWidth", 200); widget.data().putInt("WidgetHeight", 100);
        widget.data().getCompound("DynamicInputs").putString("level", "number");
        var elements = new ListTag(); var text = new CompoundTag();
        text.putString("Type", "text"); text.putString("Text", "Original"); text.putInt("W", 180); text.putInt("H", 16); elements.add(text);
        var progress = new CompoundTag(); progress.putString("Type", "progress"); progress.putInt("Y", 20);
        progress.putInt("W", 180); progress.putInt("H", 20); progress.putDouble("Min", 0); progress.putDouble("Max", 100); progress.putDouble("Value", 0);
        AdvancedHudElementBinding.bind(progress, "Value", "level"); elements.add(progress);
        var toggle = new CompoundTag(); toggle.putString("Type", "toggle"); toggle.putInt("Y", 50); toggle.putInt("W", 100); toggle.putInt("H", 20);
        toggle.putString("InteractionId", "control"); toggle.putString("ValuePort", "selected"); elements.add(toggle);
        widget.data().put("WidgetElements", elements); widget.data().getCompound("DynamicOutputs").putString("selected", "boolean");
        var source = node("source", "get_block_data"); source.data().getCompound("DynamicOutputs").putString("reading", "number");
        graph.nodes().addAll(List.of(widget, source));
        graph.edges().add(new AdvancedGraphDocument.Edge("reading", "source", "reading", "widget", "level"));
        return AdvancedGraphDocument.fromTag(graph.toTag());
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, AdvancedGraphCatalog.displayName(type), 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, null));
    }

    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper, double[] readings){
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()){
            @Override public AdvancedGraphDocument.Value getGraphTargetData(AdvancedGraphDocument.Node node, String port){
                return AdvancedGraphDocument.Value.number(readings[0]);
            }
        };
        controller.setLevel(helper.getLevel()); return controller;
    }

    private static AdvancedGraphDocument.Value widgetChange(GraphRuntimeJournal.Update<AdvancedGraphDocument.Value> update){
        return update.events().stream().filter(evt -> evt.channel() == GraphRuntimeJournal.Channel.OUTPUT
                && "widget:widget".equals(evt.key())).map(GraphRuntimeJournal.Event::value).reduce((a, b) -> b).orElse(null);
    }

    private static void assertValue(GameTestHelper helper, AdvancedGraphDocument.Value val, String text, double level){
        helper.assertTrue(val != null && "widget".equals(val.type()), "The Widget snapshot was not published");
        var elements = val.payload().getList("WidgetElements", Tag.TAG_COMPOUND);
        helper.assertTrue(text.equals(elements.getCompound(0).getString("Text")), "The Widget snapshot retained its old design");
        helper.assertTrue(elements.getCompound(1).getDouble("Value") == level, "The Widget snapshot retained an old live input");
    }
}
