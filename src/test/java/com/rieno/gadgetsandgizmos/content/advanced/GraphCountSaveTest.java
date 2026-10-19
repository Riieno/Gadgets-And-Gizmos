package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphCountSaveTest{
    @Test void legacyDirectionsMigrateInBothRootAndFunctionGraphs(){
        var graph = new AdvancedGraphDocument();
        for(String direction : List.of("increment", "decrement")){
            var counter = node(direction, "count");
            var defaults = counter.data().getCompound("Defaults");
            defaults.remove("decrement");defaults.remove("remember_count");
            defaults.put("step", AdvancedGraphDocument.Value.number(2.5).toTag());
            counter.data().putString(AdvancedGraphCatalog.COUNT_DIRECTION_TAG, direction);
            graph.nodes().add(counter);
            var function = new AdvancedGraphDocument.FunctionGraph(direction, direction);
            function.nodes().add(counter);graph.functions().add(function);
        }
        var loaded = AdvancedGraphDocument.fromTag(graph.toTag());
        for(int idx = 0; idx < 2; idx++){
            for(var counter : List.of(loaded.nodes().get(idx), loaded.functions().get(idx).nodes().getFirst())){
                assertEquals(idx == 1, literal(counter, "decrement").asBoolean());
                assertEquals(2.5, literal(counter, "step").asNumber());
                assertFalse(literal(counter, "remember_count").asBoolean());
                assertFalse(counter.data().contains(AdvancedGraphCatalog.COUNT_DIRECTION_TAG));
            }
        }
        loaded.nodes().getFirst().data().putString(AdvancedGraphCatalog.COUNT_DIRECTION_TAG, "decrement");
        assertFalse(literal(AdvancedGraphDocument.fromTag(loaded.toTag()).nodes().getFirst(), "decrement").asBoolean());
    }

    @Test void remembersOnlyOptedInCountersAndKeepsCountingAfterApply(){
        var fixture = new Fixture();
        set(fixture.counter, "remember_count", AdvancedGraphDocument.Value.bool(true));
        fixture.pulse();fixture.pulse();fixture.pulse();
        fixture.apply();assertEquals(3, fixture.value());
        fixture.pulse();assertEquals(4, fixture.value());
        set(fixture.counter, "remember_count", AdvancedGraphDocument.Value.bool(false));
        fixture.apply();assertEquals(0, fixture.value());
        fixture.pulse();assertEquals(1, fixture.value());
    }

    @Test void enablingRememberOnAnExistingCounterRetainsItsCurrentCount(){
        var fixture = new Fixture();
        fixture.pulse();fixture.pulse();
        set(fixture.counter, "remember_count", AdvancedGraphDocument.Value.bool(true));
        fixture.apply();assertEquals(2, fixture.value());
        set(fixture.counter, "decrement", AdvancedGraphDocument.Value.bool(true));
        fixture.pulse();assertEquals(1, fixture.value());
        set(fixture.counter, "reset", AdvancedGraphDocument.Value.bool(true));
        fixture.pulse();assertEquals(0, fixture.value());
        fixture.apply();assertEquals(0, fixture.value());
    }

    @Test void wiredDirectionAndRememberFlagsOverrideInlineBooleans(){
        var fixture = new Fixture();
        var flag = node("flag", "constant_boolean");flag.data().putBoolean("Value", true);
        fixture.graph.nodes().add(flag);
        fixture.graph.edges().add(new AdvancedGraphDocument.Edge("direction", "flag", "value", "counter", "decrement"));
        fixture.graph.edges().add(new AdvancedGraphDocument.Edge("remember", "flag", "value", "counter", "remember_count"));
        fixture.pulse();fixture.pulse();assertEquals(-2, fixture.value());
        fixture.apply();assertEquals(-2, fixture.value());
        fixture.pulse();assertEquals(-3, fixture.value());
    }

    @Test void deletedCountStateDoesNotLeakIntoARecreatedNode(){
        var fixture = new Fixture();set(fixture.counter, "remember_count", AdvancedGraphDocument.Value.bool(true));
        fixture.pulse();fixture.graph.nodes().remove(fixture.counter);fixture.graph.edges().clear();
        fixture.apply();fixture.graph.nodes().add(fixture.counter);
        fixture.apply();assertEquals(0, fixture.value());
    }

    @Test void rememberedCountersAndVariablesStaySeparateForEachPlayer(){
        var fixture = new Fixture();set(fixture.counter, "remember_count", AdvancedGraphDocument.Value.bool(true));
        fixture.graph.variables().put("personal", AdvancedGraphDocument.Value.number(0));
        var setter = node("set_personal", "variable_set");setter.data().putString("Variable", "personal");
        var getter = node("get_personal", "variable_get");getter.data().putString("Variable", "personal");
        var hud = node("hud", "hud_element");
        CompoundTag button = new CompoundTag();button.putString("Type", "button");button.putString("InteractionId", "click");
        button.putString("ExecPort", "click");button.putString("ValuePort", "pressed");
        var elements = new net.minecraft.nbt.ListTag();elements.add(button);hud.data().put("WidgetElements", elements);
        AdvancedHudInteractions.synchronize(hud, fixture.graph.edges());
        fixture.graph.nodes().addAll(List.of(setter, getter, hud));
        fixture.graph.edges().add(new AdvancedGraphDocument.Edge("click", "hud", "click", "counter", "exec"));
        fixture.graph.edges().add(new AdvancedGraphDocument.Edge("store", "counter", "exec", "set_personal", "exec"));
        fixture.graph.edges().add(new AdvancedGraphDocument.Edge("count", "counter", "value", "set_personal", "value"));
        fixture.graph.edges().add(new AdvancedGraphDocument.Edge("display", "get_personal", "value", "hud", "value"));
        var first = java.util.UUID.randomUUID();var second = java.util.UUID.randomUUID();
        CompoundTag snapshot = new CompoundTag();CompoundTag history = new CompoundTag();
        for(var player : List.of(first, second)){
            CompoundTag values = new CompoundTag();
            var value = AdvancedGraphDocument.Value.number(player.equals(first) ? 2 : 1);
            values.put("counter:value", value.toTag());values.put("variable:personal", value.toTag());
            history.put(player.toString(), values);
        }
        snapshot.put("PlayerState", history);
        fixture.runtime.compile(fixture.graph);fixture.runtime.restoreShutdownSnapshot(snapshot);
        assertEquals(2, fixture.runtime.previewVariables(first).get("personal").asNumber());
        assertEquals(1, fixture.runtime.previewVariables(second).get("personal").asNumber());
        fixture.apply();
        assertEquals(2, fixture.runtime.previewVariables(first).get("personal").asNumber());
        assertEquals(1, fixture.runtime.previewVariables(second).get("personal").asNumber());
        var players = fixture.runtime.createShutdownSnapshot().getCompound("PlayerState");
        assertEquals(2, AdvancedGraphDocument.Value.fromTag(players.getCompound(first.toString()).getCompound("counter:value")).asNumber());
        assertEquals(1, AdvancedGraphDocument.Value.fromTag(players.getCompound(second.toString()).getCompound("counter:value")).asNumber());
    }

    @Test void percentageIsAvailableInTheAddonAndSavedGraphsUseNumericPorts(){
        var graph = new AdvancedGraphDocument();var percentage = node("percentage", "createthrusters:percentage");
        set(percentage, "a", AdvancedGraphDocument.Value.number(50));set(percentage, "b", AdvancedGraphDocument.Value.number(200));
        graph.nodes().add(percentage);graph = AdvancedGraphDocument.fromTag(graph.toTag());
        percentage = graph.nodes().getFirst();var runtime = new GraphRuntime(null);runtime.beginPreviewSample(graph);
        assertEquals(Map.of("a", "number", "b", "number"), AdvancedGraphCatalog.inputs(percentage));
        assertEquals(25, runtime.previewOutput(graph, percentage, "value").asNumber());
        assertTrue(runtime.previewOutput(graph, percentage, "valid").asBoolean());
    }

    private static AdvancedGraphDocument.Value literal(AdvancedGraphDocument.Node node, String port){
        return AdvancedGraphDocument.Value.fromTag(node.data().getCompound("Defaults").getCompound(port));
    }
    private static void set(AdvancedGraphDocument.Node node, String port, AdvancedGraphDocument.Value value){
        CompoundTag defaults = node.data().getCompound("Defaults");defaults.put(port, value.toTag());node.data().put("Defaults", defaults);
    }
    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
    private static final class Fixture{
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        AdvancedGraphDocument.Node counter = node("counter", "count");
        final GraphRuntime runtime;
        Fixture(){
            var controller = mock(AdvancedContraptionControllerBlockEntity.class);var level = mock(Level.class);
            when(controller.getLevel()).thenReturn(level);
            runtime = new GraphRuntime(controller, true);
            var event = node("pulse", "event_trigger");event.data().putString("Event", "go");
            graph.nodes().addAll(List.of(event, counter));
            graph.edges().add(new AdvancedGraphDocument.Edge("pulse", "pulse", "exec", "counter", "exec"));
        }
        void pulse(){ runtime.enqueue("go");runtime.tick(graph, false); }
        void apply(){
            graph = AdvancedGraphDocument.fromTag(graph.toTag());
            runtime.clearForGraphSave(graph);runtime.compile(graph);
            counter = graph.nodes().stream().filter(node -> node.id().equals("counter")).findFirst().orElse(counter);
        }
        double value(){ runtime.beginPreviewSample(graph);return runtime.previewOutput(graph, counter, "value").asNumber(); }
    }
}
