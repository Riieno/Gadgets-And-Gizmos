package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancedHudButtonInteractionTest{
    @Test void eachButtonValueChangePulsesAndReleasesItsBooleanOutput(){
        for(String type : java.util.List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            Fixture fixture = new Fixture(type);
            UUID player = UUID.randomUUID();
            fixture.runtime.tick(fixture.graph, false);
            for(int click = 1; click <= 3; click++){
                fixture.time.set(click * 10L);
                assertTrue(fixture.runtime.withInteractionPlayer(player, () ->
                        fixture.runtime.enqueueHudButtonInteraction("hud", "button", fixture.time.get())));
                fixture.runtime.tick(fixture.graph, false);
                assertTrue(fixture.runtime.liveOutput("hud", "pressed").asBoolean());
                assertEquals(click * 2 - 1, fixture.runtime.liveOutput("counter", "value").asNumber());
                fixture.time.addAndGet(2);
                fixture.runtime.tick(fixture.graph, false);
                assertFalse(fixture.runtime.liveOutput("hud", "pressed").asBoolean());
                assertEquals(click * 2, fixture.runtime.liveOutput("counter", "value").asNumber(),
                        "Both press and release must pulse when their value changes");
            }
            assertTrue(fixture.runtime.diagnostics().isEmpty());
        }
    }

    @Test void mouseReleaseRearmsBooleanLogicAndPersonalHudState(){
        Fixture fixture = new Fixture("advanced_hud_element");
        fixture.graph.nodes().add(new AdvancedGraphDocument.Node("changed", "event_value_change", "", 0, 0, new CompoundTag()));
        fixture.graph.nodes().add(new AdvancedGraphDocument.Node("branch", "branch", "", 0, 0, new CompoundTag()));
        fixture.graph.edges().clear();
        fixture.graph.edges().add(edge("hud", "pressed", "changed", "value"));
        fixture.graph.edges().add(edge("hud", "pressed", "branch", "condition"));
        fixture.graph.edges().add(edge("changed", "exec", "branch", "exec"));
        fixture.graph.edges().add(edge("branch", "true", "counter", "exec"));
        UUID player = UUID.randomUUID();
        fixture.runtime.tick(fixture.graph, false);
        for(int click = 1; click <= 3; click++){
            fixture.time.set(click * 10L);
            assertTrue(fixture.runtime.withInteractionPlayer(player, () ->
                    fixture.controller.handleHudElementInteraction("hud", "button", AdvancedGraphDocument.Value.bool(true))));
            assertTrue(fixture.runtime.withInteractionPlayer(player, () ->
                    fixture.controller.handleHudElementInteraction("hud", "button", AdvancedGraphDocument.Value.bool(false))));
            fixture.runtime.tick(fixture.graph, false);
            assertTrue(fixture.runtime.playerHudValues(player).outputs().get("hud:pressed").asBoolean());
            assertEquals(click, fixture.runtime.liveOutput("counter", "value").asNumber());
            fixture.time.incrementAndGet();
            fixture.runtime.tick(fixture.graph, false);
            assertFalse(fixture.runtime.playerHudValues(player).outputs().get("hud:pressed").asBoolean());
            assertEquals(click, fixture.runtime.liveOutput("counter", "value").asNumber());
            fixture.time.incrementAndGet();
            fixture.runtime.tick(fixture.graph, false);
            assertFalse(fixture.runtime.liveOutput("hud", "pressed").asBoolean());
        }
        assertTrue(fixture.runtime.diagnostics().isEmpty());
    }

    @Test void everyControlPulsesOnlyWhenItsValueChanges(){
        for(String nodeType : java.util.List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            for(String type : java.util.List.of("button", "toggle", "slider", "text_input")){
                Fixture fixture = new Fixture(nodeType);
                var node = fixture.graph.nodes().getFirst();
                node.data().getList("WidgetElements", 10).getCompound(0).putString("Type", type);
                AdvancedHudInteractions.synchronize(node, fixture.graph.edges());
                var first = "slider".equals(type) ? AdvancedGraphDocument.Value.number(0.4)
                        : "text_input".equals(type) ? AdvancedGraphDocument.Value.string("Typed") : AdvancedGraphDocument.Value.bool(true);
                var second = "slider".equals(type) ? AdvancedGraphDocument.Value.number(0.7)
                        : "text_input".equals(type) ? AdvancedGraphDocument.Value.string("") : AdvancedGraphDocument.Value.bool(false);
                fixture.runtime.tick(fixture.graph, false);
                int changes = 0;
                AdvancedGraphDocument.Value previous = null;
                for(var value : java.util.List.of(first, first, second, second, first)){
                    fixture.time.incrementAndGet();
                    fixture.runtime.enqueueHudInteraction("hud", "button", value);
                    fixture.runtime.tick(fixture.graph, false);
                    if(!value.equals(previous)) changes++;
                    assertEquals(changes, fixture.runtime.liveOutput("counter", "value").asNumber(), nodeType + "/" + type);
                    assertEquals(value, fixture.runtime.liveOutput("hud", "pressed"));
                    previous = value;
                }
            }
        }
    }

    private static AdvancedGraphDocument.Edge edge(String from, String fromPort, String to, String toPort){
        return new AdvancedGraphDocument.Edge(from + ":" + to + ":" + toPort, from, fromPort, to, toPort);
    }

    private static final class Fixture{
        final AtomicLong time = new AtomicLong();
        final AdvancedGraphDocument graph = new AdvancedGraphDocument();
        final GraphRuntime runtime;
        final AdvancedContraptionControllerBlockEntity controller;

        Fixture(String type){
            Level level = mock(Level.class);
            when(level.getGameTime()).thenAnswer(ignored -> time.get());
            controller = mock(AdvancedContraptionControllerBlockEntity.class);
            when(controller.getLevel()).thenReturn(level);
            runtime = new GraphRuntime(controller, true);
            doCallRealMethod().when(controller).handleHudElementInteraction(anyString(), anyString(), any());
            doCallRealMethod().when(controller).handleHudButtonInteraction(anyString(), anyString());
            try{
                var runtimeField = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");
                runtimeField.setAccessible(true); runtimeField.set(controller, runtime);
                var graphField = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("activeGraph");
                graphField.setAccessible(true); graphField.set(controller, graph);
            }catch(ReflectiveOperationException err){ throw new AssertionError(err); }
            CompoundTag data = new CompoundTag();
            CompoundTag button = new CompoundTag();
            button.putString("Type", "button");
            button.putString("InteractionId", "button");
            button.putString("ValuePort", "pressed");
            button.putString("ExecPort", "click");
            ListTag elements = new ListTag(); elements.add(button);
            data.put("WidgetElements", elements);
            var hud = new AdvancedGraphDocument.Node("hud", type, "", 0, 0, data);
            AdvancedHudInteractions.synchronize(hud, graph.edges());
            graph.nodes().add(hud);
            CompoundTag counter = new CompoundTag();
            CompoundTag defaults = new CompoundTag();
            defaults.put("step", AdvancedGraphDocument.Value.number(1).toTag());
            counter.put("Defaults", defaults);
            graph.nodes().add(new AdvancedGraphDocument.Node("counter", "count", "", 0, 0, counter));
            graph.edges().add(new AdvancedGraphDocument.Edge("click", "hud", button.getString("ExecPort"), "counter", "exec"));
        }
    }
}

