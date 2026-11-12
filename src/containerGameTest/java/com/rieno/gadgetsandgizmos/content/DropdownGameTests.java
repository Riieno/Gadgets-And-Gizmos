package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudDropdown;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractions;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.display.DropdownOptions;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Exercise dropdown selections through saved graph ports and the real event scheduler
@GameTestHolder("createthrusters_visual_graph")
@PrefixGameTestTemplate(false)
public final class DropdownGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void namedCollectionsReturnTypedValuesAndPulseOnlyOnSelectionChanges(GameTestHelper helper){
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            var graph = graph(type, choices(12));
            var runtime = runtime(helper);
            try{
                runtime.replaceSimulationGraph(null, graph); runtime.tickSimulation(graph, null);
                helper.assertTrue("any".equals(AdvancedGraphCatalog.outputs(graph.nodes().getFirst()).get("selected")), "A dropdown cannot return nested values");
                select(runtime, graph, "Home");
                var output = runtime.liveOutput("widget", "selected");
                helper.assertTrue("map".equals(output.type()) && GraphRuntime.toLibraryValue(output).member("x").asNumber() == 12,
                        "The dropdown returned a key or index instead of its coordinate map");
                count(helper, runtime, 1);
                select(runtime, graph, "Home"); count(helper, runtime, 1);
                select(runtime, graph, "Home copy"); count(helper, runtime, 2);
                var widget = runtime.liveOutput("widget", "widget");
                helper.assertTrue(!AdvancedGraphCatalog.outputs(graph.nodes().getFirst()).containsKey("widget")
                        || widget != null && "Home copy".equals(widget.payload().getList("WidgetElements", Tag.TAG_COMPOUND).getCompound(0).getString("SelectedKey")),
                        "Equal values lost the selected option's identity");
                select(runtime, graph, "Storage"); count(helper, runtime, 3);
                helper.assertTrue("list".equals(runtime.liveOutput("widget", "selected").type()), "A nested list lost its type");
                select(runtime, graph, "Missing"); count(helper, runtime, 3);
                helper.assertTrue("list".equals(runtime.liveOutput("widget", "selected").type()), "A stale key replaced the last valid selection");
            }finally{ runtime.clear(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void liveOptionValuesRefreshWithoutAnExtraSelectionPulse(GameTestHelper helper){
        var graph = graph("acc_display_widget", choices(12));
        var runtime = runtime(helper);
        try{
            runtime.replaceSimulationGraph(null, graph); runtime.tickSimulation(graph, null); select(runtime, graph, "Home");
            var edited = graph.copy();
            edited.nodes().getFirst().data().getCompound("Defaults").put("options", GraphRuntime.fromLibraryValue(choices(42)).toTag());
            runtime.replaceSimulationGraph(graph, edited); runtime.tickSimulation(edited, null);
            helper.assertTrue(GraphRuntime.toLibraryValue(runtime.liveOutput("widget", "selected")).member("x").asNumber() == 42,
                    "The selected value stayed stale after its bound data changed");
            count(helper, runtime, 1);
            var popup = AdvancedHudDropdown.popup(edited.nodes().getFirst().data().getList("WidgetElements", Tag.TAG_COMPOUND).getCompound(0),
                    port -> "options".equals(port) ? GraphRuntime.fromLibraryValue(choices(42)) : runtime.liveOutput("widget", port));
            helper.assertTrue(popup.getList("DropdownEntries", Tag.TAG_COMPOUND).getCompound(0).getString("Label").equals("Home")
                    && !popup.contains("DropdownOptions"), "The display popup replaced option labels with serialized values");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void listSelectionAndNestedValuesSurviveRuntimeSnapshots(GameTestHelper helper){
        var items = GraphValue.list(List.of("Plain", GraphValue.map(Map.of("x", 8, "z", -3)), GraphValue.list(List.of("nested"))));
        var graph = graph("advanced_hud_element", items);
        var original = runtime(helper); var restored = runtime(helper);
        try{
            original.replaceSimulationGraph(null, graph); original.tickSimulation(graph, null); select(original, graph, "1");
            var snapshot = original.createShutdownSnapshot();
            restored.replaceSimulationGraph(null, AdvancedGraphDocument.fromTag(graph.toTag()));
            restored.restoreShutdownSnapshot(snapshot); restored.tickSimulation(graph, null);
            helper.assertTrue(GraphRuntime.toLibraryValue(restored.liveOutput("widget", "selected")).member("x").asNumber() == 8,
                    "A saved dropdown selection lost its nested map");
            count(helper, restored, 1);
            select(restored, graph, "1"); count(helper, restored, 1);
            select(restored, graph, "2"); count(helper, restored, 2);
            helper.assertTrue("list".equals(restored.liveOutput("widget", "selected").type()), "A list choice was flattened");
            restored.clearForGraphSave(graph); restored.tickSimulation(graph, null);
            helper.assertTrue("list".equals(restored.liveOutput("widget", "selected").type()), "Applying a saved graph cleared its dropdown selection");
        }finally{ original.clear(); restored.clear(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void invalidOptionDataCannotCreateASelection(GameTestHelper helper){
        var graph = graph("acc_display_widget", GraphValue.string("Not a collection"));
        var runtime = runtime(helper);
        try{
            runtime.replaceSimulationGraph(null, graph); runtime.tickSimulation(graph, null); select(runtime, graph, "0");
            helper.assertTrue("any".equals(runtime.liveOutput("widget", "selected").type()), "Invalid option data produced a selected value");
            count(helper, runtime, 0);
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    private static GraphValue choices(int x){
        var res = new LinkedHashMap<String, GraphValue>();
        var home = GraphValue.map(Map.of("x", x, "y", 64, "z", -8));
        res.put("Home", home); res.put("Storage", GraphValue.list(List.of("Crates", GraphValue.map(Map.of("slots", 27)))));
        res.put("Home copy", home);
        return GraphValue.map(res);
    }

    private static AdvancedGraphDocument graph(String type, GraphValue options){
        var graph = new AdvancedGraphDocument();
        var widget = node("widget", type);
        widget.data().getCompound("DynamicInputs").putString("options", "map".equals(options.type()) ? "map" : "list");
        widget.data().getCompound("Defaults").put("options", GraphRuntime.fromLibraryValue(options).toTag());
        var elements = new ListTag(); var elm = new CompoundTag();
        elm.putString("Type", "dropdown"); elm.putString("Text", "Destination"); elm.putString("InteractionId", "destination");
        elm.putString("ValuePort", "selected"); elm.putString("ExecPort", "changed"); elm.putInt("W", 120); elm.putInt("H", 24);
        AdvancedHudElementBinding.bind(elm, DropdownOptions.PROPERTY, "options"); elements.add(elm);
        widget.data().put("WidgetElements", elements); AdvancedHudInteractions.synchronize(widget, graph.edges());
        var counter = node("count", "count");
        counter.data().getCompound("Defaults").put("step", AdvancedGraphDocument.Value.number(1).toTag());
        graph.nodes().addAll(List.of(widget, counter));
        graph.edges().add(new AdvancedGraphDocument.Edge("changed", "widget", "changed", "count", "exec"));
        return AdvancedGraphDocument.fromTag(graph.toTag());
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }

    private static GraphRuntime runtime(GameTestHelper helper){
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(helper.getLevel());
        return new GraphRuntime(controller, true);
    }

    private static void select(GraphRuntime runtime, AdvancedGraphDocument graph, String key){
        runtime.enqueueHudInteraction("widget", "destination", AdvancedGraphDocument.Value.string(key)); runtime.tickSimulation(graph, null);
    }

    private static void count(GameTestHelper helper, GraphRuntime runtime, int expected){
        helper.assertTrue(runtime.liveOutput("count", "value").asNumber() == expected, "Dropdown selection produced the wrong number of execution pulses");
    }
}
