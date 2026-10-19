package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.*;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class GraphWidgetBooleanGameTests{
    private GraphWidgetBooleanGameTests(){}

    @GameTest(template = "empty_container", batch = "widget_boolean")
    public static void worldWidgetVisibilityAndButtonsSurviveCameraConsumers(GameTestHelper ctx) throws Exception{
        BlockPos controllerPos = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos displayPos = controllerPos.east(2);
        ctx.getLevel().setBlock(controllerPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        ctx.getLevel().setBlock(displayPos, CTBlocks.ACC_DISPLAY_PANEL.get().defaultBlockState(), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(controllerPos);
        var display = (AccDisplayBlockEntity)ctx.getLevel().getBlockEntity(displayPos);
        var publish = AdvancedContraptionControllerBlockEntity.class.getDeclaredMethod("publishAccDisplayUpdates");
        publish.setAccessible(true);
        for(String type : List.of("acc_display_widget", "acc_hologram_widget")){
            var graph = graph(type);
            var widget = graph.nodes().stream().filter(node -> node.id().equals("widget")).findFirst().orElseThrow();
            widget.data().put("TargetData", new ControllerDiscoveryNode(
                    "acc_display::world::" + displayPos.asLong(), ControllerDiscoveryKind.DISPLAY, "world",
                    "createthrusters:acc_display_panel", "Display", null, displayPos).toTag());
            var active = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("activeGraph");
            active.setAccessible(true);active.set(controller, graph);
            var runtimeField = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");
            runtimeField.setAccessible(true);
            var runtime = (GraphRuntime)runtimeField.get(controller);
            runtime.clear();runtime.compile(graph);runtime.tick(graph, false);
            var players = List.of(UUID.randomUUID(), UUID.randomUUID());
            int clicks = 0;
            for(int pulse = 1; pulse <= 6; pulse++){
                UUID player = players.get(pulse % 2);
                runtime.enqueue("key:toggle:pressed", player);runtime.tick(graph, false);
                boolean expected = pulse % 2 == 1;
                ctx.assertTrue(runtime.liveOutput("flip", "value").asBoolean() == expected,
                        type + " camera consumer changed flip-flop state");
                ctx.assertTrue(runtime.liveInput("widget", "visible").asBoolean() == expected,
                        type + " visibility did not receive the boolean");
                publish.invoke(controller);
                if(expected){
                    ctx.assertTrue(display.value("widget", "visible").asBoolean(), "Visible widget was not published");
                    runtime.withInteractionPlayer(player, () -> runtime.enqueueHudInteraction(
                            "widget", "button", AdvancedGraphDocument.Value.bool(true)));
                    runtime.tick(graph, false);
                    ctx.assertTrue(runtime.liveOutput("counter", "value").asNumber() == ++clicks,
                            "Widget button stopped executing after visibility changed");
                    runtime.withInteractionPlayer(player, () -> runtime.enqueueHudInteraction(
                            "widget", "button", AdvancedGraphDocument.Value.bool(false)));
                    runtime.tick(graph, false);
                }
                ctx.assertTrue(runtime.diagnostics().isEmpty(), "Widget chain reported runtime errors");
            }
        }
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "widget_boolean")
    public static void hudSamplingKeepsExecutedBooleansWithoutChangingPersonalState(GameTestHelper ctx){
        var graph = graph("advanced_hud_element");
        var runtime = new GraphRuntime(null, true);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        runtime.compile(graph);runtime.tick(graph, false);
        for(int pulse = 1; pulse <= 6; pulse++){
            runtime.enqueue("key:toggle:pressed", first);runtime.tick(graph, false);
            boolean expected = pulse % 2 == 1;
            ctx.assertTrue(runtime.liveOutput("flip", "value").asBoolean() == expected,
                    "HUD sampling overwrote the executed boolean");
            var snapshot = runtime.createShutdownSnapshot();
            ctx.assertTrue(runtime.playerHudValues(first).inputs().get("widget:visible").asBoolean() == expected,
                    "HUD lost the triggering player's boolean");
            ctx.assertTrue(!runtime.playerHudValues(second).inputs().get("widget:visible").asBoolean(),
                    "HUD leaked another player's state");
            ctx.assertTrue(snapshot.equals(runtime.createShutdownSnapshot()), "HUD observation changed runtime state");
        }
        ctx.succeed();
    }

    private static AdvancedGraphDocument graph(String type){
        var graph = new AdvancedGraphDocument();
        var key = add(graph, "key", "event_key");key.data().putString("BindingId", "toggle");
        CompoundTag outputs = new CompoundTag();outputs.putString("exec", "exec");outputs.putString("pressed", "boolean");
        key.data().put("DynamicOutputs", outputs);
        add(graph, "flip", "flip_flop");
        var widget = add(graph, "widget", type);
        CompoundTag button = new CompoundTag();button.putString("Type", "button");button.putString("InteractionId", "button");
        button.putString("ExecPort", "click");button.putString("ValuePort", "pressed");button.putInt("W", 60);button.putInt("H", 20);
        ListTag elements = new ListTag();elements.add(button);widget.data().put("WidgetElements", elements);
        AdvancedHudInteractions.synchronize(widget, graph.edges());
        add(graph, "branch", "branch");add(graph, "counter", "count");add(graph, "camera", "control_camera");
        edge(graph, "key", "exec", "flip", "exec");
        edge(graph, "flip", "value", "widget", "visible");
        edge(graph, "widget", "click", "branch", "exec");
        edge(graph, "widget", "pressed", "branch", "condition");
        edge(graph, "branch", "true", "counter", "exec");
        edge(graph, "counter", "exec", "camera", "exec");
        return graph;
    }

    private static AdvancedGraphDocument.Node add(AdvancedGraphDocument graph, String id, String type){
        var node = new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
        graph.nodes().add(node);return node;
    }

    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input){
        graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input, from, output, to, input));
    }
}
