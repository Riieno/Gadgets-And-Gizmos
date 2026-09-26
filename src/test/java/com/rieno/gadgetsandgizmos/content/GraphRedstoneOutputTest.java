package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.GraphSignalRange;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GraphRedstoneOutputTest {
    private static final String SIGNAL = GraphSignalRange.REDSTONE_SIGNAL_PORT;
    private static final double[][] CASES = {
            {-100, 0}, {-0.75, 0}, {0, 0}, {0.05, 1}, {0.1, 2},
            {0.5, 8}, {0.75, 11}, {1, 15}, {2, 2}, {7.5, 8}, {14.6, 15},
            {15, 15}, {100, 15}, {Double.NaN, 0},
            {Double.NEGATIVE_INFINITY, 0}, {Double.POSITIVE_INFINITY, 15}
    };

    @BeforeAll
    static void bootstrap() { ControllerTestBootstrap.bootstrap(); }

    @Test
    void everyRedstoneOutputUsesTheSameRangeAndNearestInteger() {
        for (String type : List.of("direct_target_output", "linker_face_output",
                "local_redstone_output", "wireless_frequency_output")) {
            for (double[] sample : CASES) {
                var controller = mock(AdvancedContraptionControllerBlockEntity.class);
                var level = mock(Level.class);
                when(controller.getLevel()).thenReturn(level);
                when(controller.getGraphTargetData(any(), anyString()))
                        .thenReturn(AdvancedGraphDocument.Value.number(-1));
                List<Double> directWrites = new ArrayList<>();
                when(controller.setGraphTargetData(any(), anySet(), any())).thenAnswer(call -> {
                    Function<String, AdvancedGraphDocument.Value> values = call.getArgument(2);
                    directWrites.add(values.apply("direct_signal").asNumber());
                    return true;
                });
                CompoundTag output = new CompoundTag();
                output.putString("BindingId", "redstone");
                output.putDouble("Value", sample[0]);
                AdvancedGraphDocument graph = graph(node("output", type, output));
                var runtime = new GraphRuntime(controller);
                runtime.tick(graph, false);
                String context = type + " input " + sample[0];
                assertTrue(runtime.diagnostics().isEmpty(), () -> context + ": " + runtime.diagnostics());
                @SuppressWarnings("unchecked")
                var outputs = org.mockito.ArgumentCaptor.forClass(Map.class);
                verify(controller).setGraphBindingValues(outputs.capture(), eq(false));
                double normalized = (Double) outputs.getValue().get("redstone");
                assertTrue(Double.isFinite(normalized) && normalized >= 0 && normalized <= 1, context);
                assertEquals((int) sample[1], Math.round(normalized * 15), context);
                if (type.equals("direct_target_output") || type.equals("linker_face_output")) {
                    assertEquals(1, directWrites.size(), context);
                    assertEquals((int) sample[1], Math.round(directWrites.getFirst() * 15), context);
                }
            }
        }
    }

    @Test
    void setDataRedstoneIsOptionalAndCanAccompanyDataWrites() {
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getLevel()).thenReturn(mock(Level.class));
        when(controller.getGraphTargetData(any(), anyString())).thenReturn(AdvancedGraphDocument.Value.number(-1));
        CompoundTag data = new CompoundTag();
        var empty = node("output", "set_block_data", data);
        assertEquals("number", AdvancedGraphCatalog.inputs(empty).get(SIGNAL));
        new GraphRuntime(controller).tick(graph(empty), false);
        verify(controller, never()).setGraphTargetData(any(), anySet(), any());

        CompoundTag dynamic = new CompoundTag();
        dynamic.putString("throttle", "number");
        data.put("DynamicInputs", dynamic);
        CompoundTag defaults = new CompoundTag();
        defaults.put("throttle", numberTag(0.25));
        defaults.put(SIGNAL, numberTag(0.75));
        data.put("Defaults", defaults);
        when(controller.setGraphTargetData(any(), anySet(), any())).thenAnswer(call -> {
            Set<String> ports = call.getArgument(1);
            Function<String, AdvancedGraphDocument.Value> values = call.getArgument(2);
            assertEquals(Set.of("throttle", SIGNAL), ports);
            assertEquals(0.25, values.apply("throttle").asNumber());
            assertEquals(0.75, values.apply(SIGNAL).asNumber());
            return true;
        });
        new GraphRuntime(controller).tick(graph(node("output", "set_block_data", data)), false);
        verify(controller).setGraphTargetData(any(), eq(Set.of("throttle", SIGNAL)), any());
    }

    @Test
    void wiredRedstoneInputParticipatesInRuntimeAndDeletionReset() throws Exception {
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getLevel()).thenReturn(mock(Level.class));
        when(controller.getGraphBindingValue("key")).thenReturn(0.75);
        when(controller.getGraphTargetData(any(), anyString())).thenReturn(AdvancedGraphDocument.Value.number(-1));
        when(controller.setGraphTargetData(any(), anySet(), any())).thenAnswer(call -> {
            Function<String, AdvancedGraphDocument.Value> values = call.getArgument(2);
            assertEquals(0.75, values.apply(SIGNAL).asNumber());
            return true;
        });
        var output = node("output", "set_block_data", new CompoundTag());
        AdvancedGraphDocument graph = graph(output);
        CompoundTag key = new CompoundTag(); key.putString("BindingId", "key");
        graph.nodes().add(node("key", "controller_channel_input", key));
        graph.edges().add(new AdvancedGraphDocument.Edge("signal", "key", "value", "output", SIGNAL));
        new GraphRuntime(controller).tick(graph, false);
        verify(controller).setGraphTargetData(any(), eq(Set.of(SIGNAL)), any());
        assertEquals(Set.of(SIGNAL), AdvancedContraptionControllerBlockEntity.graphTargetResetPorts(graph, output));

        CompoundTag defaults = new CompoundTag();
        defaults.put(SIGNAL, numberTag(0.75));
        output.data().put("Defaults", defaults);
        var reset = AdvancedContraptionControllerBlockEntity.class.getDeclaredMethod(
                "graphTargetResetValue", AdvancedGraphDocument.Node.class, String.class);
        reset.setAccessible(true);
        var real = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        assertEquals(0, ((AdvancedGraphDocument.Value) reset.invoke(real, output, SIGNAL)).asNumber());
    }

    private static CompoundTag numberTag(double value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", "number");
        tag.put("Payload", AdvancedGraphDocument.Value.number(value).payload());
        return tag;
    }

    private static AdvancedGraphDocument graph(AdvancedGraphDocument.Node output) {
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(node("update", "event_tick", new CompoundTag()));
        graph.nodes().add(output);
        graph.edges().add(new AdvancedGraphDocument.Edge("exec", "update", "exec", output.id(), "exec"));
        return graph;
    }

    private static AdvancedGraphDocument.Node node(String id, String type, CompoundTag data) {
        return new AdvancedGraphDocument.Node(id, type, id, 0, 0, data);
    }
}
