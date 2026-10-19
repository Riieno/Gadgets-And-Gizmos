package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphPulseFlowTest{
    @Test void loopPublishesEveryIndexIgnoresBusyPulsesAndCanStartAgain(){
        var f = new Fixture();
        var loop = f.add("loop", "bounded_loop");
        literal(loop, "iterations", AdvancedGraphDocument.Value.number(3));
        f.counter("through"); f.counter("body"); f.counter("complete");
        f.edge("go", "exec", "loop", "exec");
        f.edge("loop", "exec", "through", "exec");
        f.edge("loop", "body", "body", "exec");
        f.edge("loop", "complete", "complete", "exec");
        f.edge("loop", "body", "loop", "exec");
        f.pulse();
        assertEquals(1, f.value("through", "value"));
        assertEquals(1, f.value("body", "value"));
        assertEquals(0, f.value("complete", "value"));
        assertEquals(0, f.value("loop", "index"));
        f.clock.incrementAndGet(); f.pulse();
        assertEquals(1, f.value("through", "value"));
        assertEquals(2, f.value("body", "value"));
        assertEquals(1, f.value("loop", "index"));
        f.clock.incrementAndGet(); f.tick();
        assertEquals(3, f.value("body", "value"));
        assertEquals(1, f.value("complete", "value"));
        assertEquals(2, f.value("loop", "index"));
        f.clock.incrementAndGet(); f.pulse();
        assertEquals(2, f.value("through", "value"));
        assertEquals(4, f.value("body", "value"));
        assertEquals(0, f.value("loop", "index"));
        assertTrue(f.runtime.diagnostics().isEmpty(), f.runtime.diagnostics().toString());
    }

    @Test void emptyLoopPassesThroughAndCompletesWithoutBodyPulses(){
        var f = new Fixture();
        var loop = f.add("loop", "bounded_loop");
        literal(loop, "iterations", AdvancedGraphDocument.Value.number(0));
        f.counter("through");f.counter("body");f.counter("complete");
        f.edge("go", "exec", "loop", "exec");
        f.edge("loop", "exec", "through", "exec");
        f.edge("loop", "body", "body", "exec");
        f.edge("loop", "complete", "complete", "exec");
        f.pulse();f.clock.incrementAndGet();f.tick();
        assertEquals(1, f.value("through", "value"));
        assertEquals(0, f.value("body", "value"));
        assertEquals(1, f.value("complete", "value"));
    }

    @Test void aRunningLoopResumesWithTheSameIndexAndRemainingWorkAfterASnapshot(){
        var f = new Fixture();
        var loop = f.add("loop", "bounded_loop");
        literal(loop, "iterations", AdvancedGraphDocument.Value.number(3));
        f.counter("body");f.counter("complete");
        f.edge("go", "exec", "loop", "exec");
        f.edge("loop", "body", "body", "exec");
        f.edge("loop", "complete", "complete", "exec");
        f.pulse();
        CompoundTag snapshot = f.runtime.createShutdownSnapshot();
        var resumed = new GraphRuntime(f.controller, true);
        resumed.compile(f.graph);
        resumed.restoreShutdownSnapshot(snapshot);
        f.clock.incrementAndGet();resumed.tick(f.graph, false);
        assertEquals(1, resumed.liveOutput("loop", "index").asNumber());
        f.clock.incrementAndGet();resumed.tick(f.graph, false);
        assertEquals(3, resumed.liveOutput("body", "value").asNumber());
        assertEquals(1, resumed.liveOutput("complete", "value").asNumber());
        assertTrue(resumed.diagnostics().isEmpty(), resumed.diagnostics().toString());
    }

    @Test void flipModesKeepTheirCadenceAndPreserveExecutionPassthrough(){
        for(String mode : List.of("Every Pulse", "Every Second Pulse", "Every Nth Pulse")){
            var f = new Fixture();
            var flip = f.add("flip", "flip_flop");
            literal(flip, "flip_on", AdvancedGraphDocument.Value.string(mode));
            literal(flip, "skip_pulses", AdvancedGraphDocument.Value.number(2));
            f.counter("through");
            f.edge("go", "exec", "flip", "exec");
            f.edge("flip", "exec", "through", "exec");
            int period = mode.equals("Every Pulse") ? 1 : mode.equals("Every Second Pulse") ? 2 : 3;
            for(int pulse = 1; pulse <= period * 3; pulse++){
                f.pulse();
                assertEquals((pulse / period) % 2 == 1, f.runtime.liveOutput("flip", "value").asBoolean(), mode);
                assertEquals(pulse, f.value("through", "value"), mode);
                f.clock.incrementAndGet();
            }
            assertTrue(f.runtime.diagnostics().isEmpty(), f.runtime.diagnostics().toString());
        }
    }

    @Test void pulseIfEvaluatesEveryTickAndStopsWhenTheComparisonIsFalse(){
        var f = new Fixture();
        var pulse = f.add("condition", "pulse_if");
        var source = f.add("source", "constant_number");
        source.data().putDouble("Value", 5);
        literal(pulse, "b", AdvancedGraphDocument.Value.number(3));
        literal(pulse, "operator", AdvancedGraphDocument.Value.string(">"));
        f.counter("matched");
        f.edge("source", "value", "condition", "a");
        f.edge("condition", "exec", "matched", "exec");
        f.tick();f.clock.incrementAndGet();f.tick();
        assertEquals(2, f.value("matched", "value"));
        source.data().putDouble("Value", 1);
        f.clock.incrementAndGet();f.tick();
        assertEquals(2, f.value("matched", "value"));
        assertTrue(f.runtime.diagnostics().isEmpty(), f.runtime.diagnostics().toString());
    }

    @Test void pulseIfMatchesNumberTextBooleanAndStructuredInputs(){
        CompoundTag entries = new CompoundTag();
        entries.put("answer", AdvancedGraphDocument.Value.number(42).toTag());
        for(var value : List.of(AdvancedGraphDocument.Value.number(5), AdvancedGraphDocument.Value.string("ready"),
                AdvancedGraphDocument.Value.bool(false), AdvancedGraphDocument.Value.list(entries), AdvancedGraphDocument.Value.map(entries))){
            var f = new Fixture();
            var pulse = f.add("condition", "pulse_if");
            literal(pulse, "a", value);literal(pulse, "b", value);
            f.counter("matched");f.edge("condition", "exec", "matched", "exec");
            f.tick();
            assertEquals("any", AdvancedGraphCatalog.inputs(pulse).get("a"));
            assertEquals(value.type(), AdvancedGraphCatalog.inputs(pulse).get("b"));
            assertEquals(1, f.value("matched", "value"), value.type());
            literal(pulse, "operator", AdvancedGraphDocument.Value.string("!="));
            f.clock.incrementAndGet();f.tick();
            assertEquals(1, f.value("matched", "value"));
        }
    }

    @Test void nthFlipShowsItsCountOnlyWhenSelectedAndOldNodesKeepEveryPulse(){
        var flip = new AdvancedGraphDocument.Node("flip", "flip_flop", "", 0, 0, new CompoundTag());
        assertFalse(AdvancedGraphCatalog.inputs(flip).containsKey("skip_pulses"));
        literal(flip, "flip_on", AdvancedGraphDocument.Value.string("Every Nth Pulse"));
        assertEquals("number", AdvancedGraphCatalog.inputs(flip).get("skip_pulses"));
        literal(flip, "skip_pulses", AdvancedGraphDocument.Value.number(4));
        literal(flip, "flip_on", AdvancedGraphDocument.Value.string("Every Pulse"));
        assertFalse(AdvancedGraphCatalog.inputs(flip).containsKey("skip_pulses"));
        assertEquals(4, AdvancedGraphDocument.Value.fromTag(flip.data().getCompound("Defaults").getCompound("skip_pulses")).asNumber());
    }

    @Test void appliedLoopPublishesToAnOpenEditorInLiveAndSimulatedRuntimes(){
        for(boolean simulation : new boolean[]{false, true}){
            var f = new Fixture(simulation);
            var staleSource = f.graph.copy();
            var loop = f.add("loop", "bounded_loop");literal(loop, "iterations", AdvancedGraphDocument.Value.number(3));
            f.counter("through");f.counter("body");f.counter("complete");
            f.edge("go", "exec", "loop", "exec");f.edge("loop", "exec", "through", "exec");
            f.edge("loop", "body", "body", "exec");f.edge("loop", "complete", "complete", "exec");
            var preview = new GraphRuntime(null, true);
            preview.beginPreviewSample(f.graph, staleSource, Map.of(), Map.of());
            assertEquals(0, preview.previewOutput(f.graph, loop, "index").asNumber());
            f.pulse();assertEquals(1, f.value("through", "value"));
            for(int index = 0; index < 3; index++){
                if(index > 0){ f.clock.incrementAndGet();f.tick(); }
                preview.beginPreviewSample(f.graph, f.graph.copy(), Map.of(), f.runtime.liveOutputs());
                assertEquals(index, preview.previewOutput(f.graph, loop, "index").asNumber());
                assertEquals(index + 1, f.value("body", "value"));
            }
            assertEquals(1, f.value("complete", "value"));
            assertTrue(f.runtime.executionPulses().containsKey("loop:complete>complete:exec"));
            assertTrue(f.runtime.diagnostics().isEmpty(), f.runtime.diagnostics().toString());
        }
    }

    private static void literal(AdvancedGraphDocument.Node node, String port, AdvancedGraphDocument.Value value){
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.put(port, value.toTag());node.data().put("Defaults", defaults);
    }

    private static final class Fixture{
        final AdvancedGraphDocument graph = new AdvancedGraphDocument();
        final AtomicLong clock = new AtomicLong();
        final AdvancedContraptionControllerBlockEntity controller = mock(AdvancedContraptionControllerBlockEntity.class);
        final GraphRuntime runtime;

        Fixture(){ this(true); }
        Fixture(boolean simulation){
            Level level = mock(Level.class);
            when(level.getGameTime()).thenAnswer(call -> clock.get());
            when(controller.getLevel()).thenReturn(level);
            when(controller.getActiveGraph()).thenReturn(graph);
            runtime = new GraphRuntime(controller, simulation);
            add("go", "event_trigger").data().putString("Event", "go");
        }

        AdvancedGraphDocument.Node add(String id, String type){
            var node = new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
            graph.nodes().add(node);return node;
        }
        void counter(String id){ add(id, "count"); }
        void edge(String from, String output, String to, String input){
            graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input, from, output, to, input));
        }
        void pulse(){ runtime.enqueue("go");tick(); }
        void tick(){ runtime.tick(graph, false); }
        double value(String id, String port){ return runtime.liveOutputs().getOrDefault(id + ":" + port, AdvancedGraphDocument.Value.number(0)).asNumber(); }
    }
}
