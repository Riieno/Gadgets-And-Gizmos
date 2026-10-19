package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphWidgetBooleanPropagationTest{
    @Test void staleSavedPlayerScopesCannotFreezeAWorldWidget(){
        Fixture fixture = new Fixture("acc_display_widget", "visible", true);
        UUID player = UUID.randomUUID();
        fixture.runtime.compile(fixture.graph);
        CompoundTag snapshot = new CompoundTag();
        CompoundTag shared = new CompoundTag();shared.put("flip:value", AdvancedGraphDocument.Value.bool(false).toTag());
        snapshot.put("State", shared);
        CompoundTag values = new CompoundTag();values.put("flip:value", AdvancedGraphDocument.Value.bool(true).toTag());
        CompoundTag players = new CompoundTag();players.put(player.toString(), values);snapshot.put("PlayerState", players);
        fixture.runtime.restoreShutdownSnapshot(snapshot);
        for(int pulse = 1; pulse <= 4; pulse++){
            fixture.pulse(player);
            assertEquals(pulse % 2 == 1, fixture.runtime.liveOutput("flip", "value").asBoolean());
            assertEquals(pulse % 2 == 1, fixture.runtime.liveInput("widget", "visible").asBoolean());
        }
    }

    @Test void connectingAndDisconnectingCameraConsumersPreservesRunningState(){
        Fixture fixture = new Fixture("acc_display_widget", "visible", false);
        var players = List.of(UUID.randomUUID(), UUID.randomUUID());
        fixture.runtime.tick(fixture.graph, false);
        for(int pulse = 1; pulse <= 8; pulse++){
            if(pulse == 3){
                fixture.add("camera", "control_camera");
                fixture.edge("counter", "exec", "camera", "exec");
            }
            if(pulse == 6) fixture.graph.edges().removeIf(edge -> edge.toNode().equals("camera"));
            fixture.pulse(players.get(pulse % 2));
            assertEquals(pulse % 2 == 1, fixture.runtime.liveOutput("flip", "value").asBoolean());
            assertEquals(pulse % 2 == 1, fixture.runtime.liveInput("widget", "visible").asBoolean());
        }
        assertTrue(fixture.runtime.diagnostics().isEmpty());
    }

    @Test void cameraConsumersCannotChangeWorldWidgetBooleanState(){
        for(String type : List.of("acc_display_widget", "acc_hologram_widget")){
            for(String port : List.of("visible", "value", "enabled")){
                for(boolean camera : List.of(false, true)){
                    Fixture fixture = new Fixture(type, port, camera);
                    UUID player = UUID.randomUUID();
                    fixture.runtime.tick(fixture.graph, false);
                    for(int pulse = 1; pulse <= 4; pulse++){
                        fixture.pulse(player);
                        boolean expected = pulse % 2 == 1;
                        assertEquals(expected, fixture.runtime.liveOutput("flip", "value").asBoolean(), type + "/" + port + "/camera=" + camera);
                        assertEquals(expected, fixture.runtime.liveInput("widget", port).asBoolean());
                        fixture.runtime.beginPreviewSample(fixture.graph);
                        assertEquals(expected, fixture.runtime.previewInput(fixture.graph, fixture.widget, port).asBoolean());
                        fixture.click(player);
                        assertEquals(pulse, fixture.runtime.liveOutput("counter", "value").asNumber());
                    }
                    assertTrue(fixture.runtime.diagnostics().isEmpty(), fixture.runtime.diagnostics().toString());
                    if(camera) verify(fixture.controller, times(4)).controlGraphCamera(any(), any(), eq(player));
                }
            }
        }
    }

    @Test void hudSamplingPreservesTheLatestExecutedBooleanAndEachPlayersOwnState(){
        for(String port : List.of("visible", "value", "enabled")){
            Fixture fixture = new Fixture("advanced_hud_element", port, true);
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            fixture.runtime.tick(fixture.graph, false);
            for(int pulse = 1; pulse <= 4; pulse++){
                fixture.pulse(first);
                boolean expected = pulse % 2 == 1;
                assertEquals(expected, fixture.runtime.liveOutput("flip", "value").asBoolean(), port);
                assertEquals(expected, fixture.runtime.liveInput("widget", port).asBoolean());
                var snapshot = fixture.runtime.createShutdownSnapshot();
                assertEquals(expected, fixture.runtime.playerHudValues(first).inputs().get("widget:" + port).asBoolean());
                assertFalse(fixture.runtime.playerHudValues(second).inputs().get("widget:" + port).asBoolean());
                assertEquals(snapshot, fixture.runtime.createShutdownSnapshot());
                fixture.click(first);
                assertEquals(pulse, fixture.runtime.liveOutput("counter", "value").asNumber());
            }
            verify(fixture.controller, times(4)).controlGraphCamera(any(), any(), eq(first));
        }
    }

    @Test void automaticEventsDoNotReuseAnotherPlayersCachedBoolean(){
        Fixture fixture = new Fixture("advanced_hud_element", "visible", true);
        fixture.graph.edges().removeIf(edge -> edge.fromNode().equals("widget") && edge.fromPort().equals("click"));
        fixture.add("tick", "event_tick");
        fixture.add("probe", "branch");
        fixture.add("changed", "event_value_change");
        fixture.add("branch", "branch");
        fixture.edge("tick", "exec", "probe", "exec");
        fixture.edge("flip", "value", "probe", "condition");
        fixture.edge("widget", "pressed", "changed", "value");
        fixture.edge("changed", "exec", "branch", "exec");
        fixture.edge("flip", "value", "branch", "condition");
        fixture.edge("branch", "true", "pressed", "exec");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        fixture.runtime.tick(fixture.graph, false);
        fixture.pulse(first);
        fixture.pulse(second);
        fixture.pulse(second);
        for(int click = 1; click <= 3; click++){
            fixture.click(first);
            assertEquals(click, fixture.runtime.liveOutput("counter", "value").asNumber());
            fixture.time.addAndGet(2);
            fixture.runtime.tick(fixture.graph, false);
        }
        verify(fixture.controller, times(3)).controlGraphCamera(any(), any(), eq(first));
        assertTrue(fixture.runtime.diagnostics().isEmpty());
    }

    private static final class Fixture{
        final AdvancedGraphDocument graph = new AdvancedGraphDocument();
        final AtomicLong time = new AtomicLong();
        final AdvancedContraptionControllerBlockEntity controller = mock(AdvancedContraptionControllerBlockEntity.class);
        final GraphRuntime runtime;
        final AdvancedGraphDocument.Node widget;

        Fixture(String type, String port, boolean camera){
            Level level = mock(Level.class);
            when(level.getGameTime()).thenAnswer(ignored -> time.get());
            when(controller.getLevel()).thenReturn(level);
            runtime = new GraphRuntime(controller);
            var key = add("key", "event_key");
            key.data().putString("BindingId", "toggle");
            CompoundTag outputs = new CompoundTag();
            outputs.putString("exec", "exec");outputs.putString("pressed", "boolean");
            key.data().put("DynamicOutputs", outputs);
            add("flip", "flip_flop");
            widget = add("widget", type);
            CompoundTag inputs = widget.data().getCompound("DynamicInputs");
            if(port.equals("enabled")) inputs.putString(port, "boolean");
            widget.data().put("DynamicInputs", inputs);
            CompoundTag button = new CompoundTag();
            button.putString("Type", "button");
            button.putString("InteractionId", "button");
            button.putString("ValuePort", "pressed");
            button.putString("ExecPort", "click");
            ListTag elements = new ListTag();elements.add(button);
            widget.data().put("WidgetElements", elements);
            AdvancedHudInteractions.synchronize(widget, graph.edges());
            add("counter", "count");
            add("pressed", "branch");
            edge("key", "exec", "flip", "exec");
            edge("flip", "value", "widget", port);
            edge("widget", "click", "pressed", "exec");
            edge("widget", "pressed", "pressed", "condition");
            edge("pressed", "true", "counter", "exec");
            if(camera){
                add("camera", "control_camera");
                edge("counter", "exec", "camera", "exec");
            }
        }

        AdvancedGraphDocument.Node add(String id, String type){
            var node = new AdvancedGraphDocument.Node(id, type, "", 0, 0,
                    AdvancedGraphNodeFactory.createDefaultData(type, null));
            graph.nodes().add(node);
            return node;
        }

        void edge(String from, String output, String to, String input){
            graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input, from, output, to, input));
        }

        void pulse(UUID player){
            time.incrementAndGet();
            runtime.enqueue("key:toggle:pressed", player);
            runtime.tick(graph, false);
        }

        void click(UUID player){
            time.addAndGet(3);
            runtime.tick(graph, false);
            assertTrue(runtime.withInteractionPlayer(player,
                    () -> runtime.enqueueHudButtonInteraction("widget", "button", time.get())));
            runtime.tick(graph, false);
        }
    }
}
