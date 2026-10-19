package com.rieno.gadgetsandgizmos.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class AccDisplayAndGraphSaveGameTests{
    private AccDisplayAndGraphSaveGameTests(){}

    @GameTest(template = "empty_container")
    public static void everyDisplayShapeMatchesItsRotatedBlockModel(GameTestHelper ctx) throws Exception{
        int checked = 0;
        for(var entry : Map.of("acc_display", CTBlocks.ACC_DISPLAY.get(),
                "acc_display_block", CTBlocks.ACC_DISPLAY_BLOCK.get(), "acc_display_panel", CTBlocks.ACC_DISPLAY_PANEL.get(),
                "acc_display_half_panel", CTBlocks.ACC_DISPLAY_HALF_PANEL.get(), "acc_display_slab", CTBlocks.ACC_DISPLAY_SLAB.get()).entrySet()){
            JsonObject variants = resource("blockstates/" + entry.getKey() + ".json").getAsJsonObject("variants");
            for(var state : entry.getValue().getStateDefinition().getPossibleStates()){
                Map<String, String> values = Map.of("facing", state.getValue(AccDisplayBlock.FACING).getSerializedName(),
                        "side", state.getValue(AccDisplayBlock.SIDE).getSerializedName(),
                        "y_alignment", state.getValue(AccDisplayBlock.Y_ALIGNMENT).getSerializedName(),
                        "z_alignment", state.getValue(AccDisplayBlock.Z_ALIGNMENT).getSerializedName());
                JsonObject variant = null;
                for(var candidate : variants.entrySet()){
                    boolean matches = true;
                    for(String condition : candidate.getKey().split(",")){
                        String[] pair = condition.split("=");matches &= pair[1].equals(values.get(pair[0]));
                    }
                    if(matches){ ctx.assertTrue(variant == null, "Ambiguous display model");variant = candidate.getValue().getAsJsonObject(); }
                }
                ctx.assertTrue(variant != null, "Missing model for " + state);
                String model = variant.get("model").getAsString().split(":", 2)[1];
                int turns = variant.has("y") ? variant.get("y").getAsInt() / 90 : 0;
                var expected = Shapes.empty();
                for(var raw : resource("models/" + model + ".json").getAsJsonArray("elements")){
                    var element = raw.getAsJsonObject();var from = element.getAsJsonArray("from");var to = element.getAsJsonArray("to");
                    if(element.has("rotation")) ctx.assertTrue(element.getAsJsonObject("rotation").get("angle").getAsDouble() == 0,
                            "Display model acquired a nonzero element rotation");
                    double minX = Double.POSITIVE_INFINITY, minZ = minX, maxX = Double.NEGATIVE_INFINITY, maxZ = maxX;
                    for(int corner = 0; corner < 4; corner++){
                        double x = ((corner & 1) == 0 ? from : to).get(0).getAsDouble();
                        double z = ((corner & 2) == 0 ? from : to).get(2).getAsDouble();
                        for(int turn = 0; turn < turns; turn++){ double prevX = x;x = 16 - z;z = prevX; }
                        minX = Math.min(minX, x);minZ = Math.min(minZ, z);maxX = Math.max(maxX, x);maxZ = Math.max(maxZ, z);
                    }
                    expected = Shapes.or(expected, Shapes.box(minX / 16, from.get(1).getAsDouble() / 16, minZ / 16,
                            maxX / 16, to.get(1).getAsDouble() / 16, maxZ / 16));
                }
                var pos = ctx.absolutePos(new BlockPos(1, 3, 1));
                ctx.assertTrue(!Shapes.joinIsNotEmpty(expected, state.getShape(ctx.getLevel(), pos), BooleanOp.NOT_SAME),
                        "Selection shape differs from the model: " + state);
                ctx.assertTrue(!Shapes.joinIsNotEmpty(expected, state.getCollisionShape(ctx.getLevel(), pos, CollisionContext.empty()), BooleanOp.NOT_SAME),
                        "Collision shape differs from the model: " + state);
                checked++;
            }
        }
        ctx.assertTrue(checked == 360, "Not every display state was checked: " + checked);
        ctx.succeed();
    }

    @GameTest(template = "empty_container")
    public static void graphSavesKeepLiveVariablesInsteadOfStaleEditorDefaults(GameTestHelper ctx) throws Exception{
        var fixture = fixture(ctx);var graph = new AdvancedGraphDocument();
        var event = node("pulse", "event_trigger");event.data().putString("Event", "store");graph.nodes().add(event);
        CompoundTag entries = new CompoundTag();entries.put("cargo", AdvancedGraphDocument.Value.number(37).toTag());
        Map<String, AdvancedGraphDocument.Value> values = Map.of("amount", AdvancedGraphDocument.Value.number(42.5),
                "enabled", AdvancedGraphDocument.Value.bool(true), "status", AdvancedGraphDocument.Value.string("flying"),
                "manifest", AdvancedGraphDocument.Value.map(entries));
        for(var entry : values.entrySet()){
            graph.variables().put(entry.getKey(), AdvancedGraphDocument.Value.number(0));
            var setter = node("set_" + entry.getKey(), "variable_set");setter.data().putString("Variable", entry.getKey());
            literal(setter, "value", entry.getValue());
            CompoundTag inputs = new CompoundTag();inputs.putString("value", entry.getValue().type());setter.data().put("DynamicInputs", inputs);
            CompoundTag outputs = new CompoundTag();outputs.putString("value", entry.getValue().type());setter.data().put("DynamicOutputs", outputs);
            graph.nodes().add(setter);edge(graph, "pulse", "exec", setter.id(), "exec");
        }
        saveAndApply(ctx, fixture.controller(), graph);
        var staleEditor = fixture.controller().getDraftGraph();
        fixture.runtime().enqueue("store");fixture.runtime().tick(active(fixture.controller()), false);
        ctx.assertTrue(values.equals(fixture.controller().getActiveGraph().variables()), "Runtime variable writes did not run");
        ctx.assertTrue(fixture.controller().saveDraft(staleEditor, staleEditor.revision()), "Stale editor save failed");
        ctx.assertTrue(values.equals(fixture.controller().getDraftGraph().variables()), "Save replaced current variables with editor defaults");
        fixture.controller().setGraphVariable("amount", AdvancedGraphDocument.Value.number(99));
        ctx.assertTrue(fixture.controller().applyDraft(), "Graph apply failed");
        ctx.assertTrue(fixture.controller().getActiveGraph().variables().get("amount").asNumber() == 99, "Apply discarded a variable updated after Save");
        for(String name : List.of("enabled", "status", "manifest")){
            ctx.assertTrue(values.get(name).equals(fixture.controller().getActiveGraph().variables().get(name)), "Lost typed variable " + name);
        }
        var edit = fixture.controller().getDraftGraph();edit.variables().remove("status");edit.nodes().removeIf(n -> n.id().equals("set_status"));
        edit.edges().removeIf(e -> e.toNode().equals("set_status"));
        edit.variables().put("new_value", AdvancedGraphDocument.Value.number(7));
        var getter = node("get_new", "variable_get");getter.data().putString("Variable", "new_value");edit.nodes().add(getter);
        saveAndApply(ctx, fixture.controller(), edit);
        var restored = AdvancedGraphDocument.fromTag(fixture.controller().getActiveGraph().toTag());
        ctx.assertTrue(!restored.variables().containsKey("status"), "Deleted variable was resurrected");
        ctx.assertTrue(restored.variables().get("new_value").asNumber() == 7, "New variable lost its initial value");
        ctx.assertTrue(restored.variables().get("amount").asNumber() == 99, "Live variable did not survive another save");
        ctx.succeed();
    }

    @GameTest(template = "empty_container")
    public static void countBooleanInputsAndRememberCountWorkThroughRealSaves(GameTestHelper ctx) throws Exception{
        var fixture = fixture(ctx);var graph = new AdvancedGraphDocument();
        var event = node("pulse", "event_trigger");event.data().putString("Event", "go");
        var kept = node("kept", "count");var ordinary = node("ordinary", "count");
        var flag = node("flag", "constant_boolean");flag.data().putBoolean("Value", true);
        graph.nodes().addAll(List.of(event, kept, ordinary, flag));
        edge(graph, "pulse", "exec", "kept", "exec");edge(graph, "kept", "exec", "ordinary", "exec");
        edge(graph, "flag", "value", "kept", "remember_count");
        saveAndApply(ctx, fixture.controller(), graph);
        var stale = fixture.controller().getDraftGraph();
        for(int pulse = 0; pulse < 5; pulse++) pulse(fixture);
        ctx.assertTrue(value(fixture, "kept") == 5 && value(fixture, "ordinary") == 5, "Counters did not increment per pulse");
        saveAndApply(ctx, fixture.controller(), stale);
        ctx.assertTrue(value(fixture, "kept") == 5 && value(fixture, "ordinary") == 0, "Save did not respect Remember Count");
        var edit = fixture.controller().getDraftGraph();
        literal(find(edit, "kept"), "decrement", AdvancedGraphDocument.Value.bool(true));
        literal(find(edit, "ordinary"), "remember_count", AdvancedGraphDocument.Value.bool(true));
        pulse(fixture);pulse(fixture);saveAndApply(ctx, fixture.controller(), edit);
        ctx.assertTrue(value(fixture, "kept") == 7 && value(fixture, "ordinary") == 2, "Enabling Remember Count lost the live count");
        pulse(fixture);ctx.assertTrue(value(fixture, "kept") == 6 && value(fixture, "ordinary") == 3, "Boolean direction or Step did not apply");
        edit = fixture.controller().getDraftGraph();literal(find(edit, "kept"), "reset", AdvancedGraphDocument.Value.bool(true));
        saveAndApply(ctx, fixture.controller(), edit);pulse(fixture);
        ctx.assertTrue(value(fixture, "kept") == 0, "Reset did not take priority over decrement");
        edit = fixture.controller().getDraftGraph();edit.edges().removeIf(e -> e.toPort().equals("remember_count"));
        literal(find(edit, "ordinary"), "remember_count", AdvancedGraphDocument.Value.bool(false));
        saveAndApply(ctx, fixture.controller(), edit);
        ctx.assertTrue(value(fixture, "kept") == 0 && value(fixture, "ordinary") == 0, "Disabling Remember Count retained old state");
        ctx.succeed();
    }

    @GameTest(template = "empty_container")
    public static void percentageExecutesInARealAccGraph(GameTestHelper ctx) throws Exception{
        var fixture = fixture(ctx);var graph = new AdvancedGraphDocument();
        var event = node("pulse", "event_trigger");event.data().putString("Event", "calculate");
        var percentage = node("percentage", "createthrusters:percentage");
        literal(percentage, "a", AdvancedGraphDocument.Value.number(50));literal(percentage, "b", AdvancedGraphDocument.Value.number(200));
        var setter = node("set", "variable_set");setter.data().putString("Variable", "result");
        CompoundTag inputs = new CompoundTag();inputs.putString("value", "number");setter.data().put("DynamicInputs", inputs);
        CompoundTag outputs = new CompoundTag();outputs.putString("value", "number");setter.data().put("DynamicOutputs", outputs);
        graph.variables().put("result", AdvancedGraphDocument.Value.number(0));graph.nodes().addAll(List.of(event, percentage, setter));
        edge(graph, "pulse", "exec", "set", "exec");edge(graph, "percentage", "value", "set", "value");
        saveAndApply(ctx, fixture.controller(), graph);fixture.runtime().enqueue("calculate");fixture.runtime().tick(active(fixture.controller()), false);
        ctx.assertTrue(fixture.controller().getActiveGraph().variables().get("result").asNumber() == 25, "Percentage did not calculate 50 / 200 * 100");
        ctx.succeed();
    }

    private static JsonObject resource(String path) throws Exception{
        try(var stream = AccDisplayAndGraphSaveGameTests.class.getResourceAsStream("/assets/createthrusters/" + path)){
            if(stream == null) throw new IllegalStateException("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void literal(AdvancedGraphDocument.Node node, String port, AdvancedGraphDocument.Value value){
        CompoundTag defaults = node.data().getCompound("Defaults");defaults.put(port, value.toTag());node.data().put("Defaults", defaults);
    }
    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
    private static AdvancedGraphDocument.Node find(AdvancedGraphDocument graph, String id){
        return graph.nodes().stream().filter(node -> id.equals(node.id())).findFirst().orElseThrow();
    }
    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input){
        graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input, from, output, to, input));
    }
    private static void saveAndApply(GameTestHelper ctx, AdvancedContraptionControllerBlockEntity controller, AdvancedGraphDocument graph){
        ctx.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()), "Graph save failed");
        ctx.assertTrue(controller.applyDraft(), "Graph apply failed: " + controller.getGraphDiagnostics());
    }
    private static AdvancedGraphDocument active(AdvancedContraptionControllerBlockEntity controller) throws Exception{
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("activeGraph");field.setAccessible(true);
        return (AdvancedGraphDocument)field.get(controller);
    }
    private static void pulse(Fixture fixture) throws Exception{
        fixture.runtime().enqueue("go");fixture.runtime().tick(active(fixture.controller()), false);
    }
    private static double value(Fixture fixture, String id) throws Exception{
        var graph = active(fixture.controller());fixture.runtime().beginPreviewSample(graph);
        return fixture.runtime().previewOutput(graph, find(graph, id), "value").asNumber();
    }
    private static Fixture fixture(GameTestHelper ctx) throws Exception{
        var pos = ctx.absolutePos(new BlockPos(2, 3, 2));
        ctx.getLevel().setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(pos);
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");field.setAccessible(true);
        return new Fixture(controller, (GraphRuntime)field.get(controller));
    }
    private record Fixture(AdvancedContraptionControllerBlockEntity controller, GraphRuntime runtime){}
}
