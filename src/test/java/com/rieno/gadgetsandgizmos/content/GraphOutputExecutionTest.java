package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GraphOutputExecutionTest {
    @BeforeAll
    static void bootstrap() { ControllerTestBootstrap.bootstrap(); }

    @Test
    void allThreeSetDataNodesReceiveTheSameInputInTheSameTick() {
        for (String flow : List.of("fanout", "chain", "parallel_execution", "sequenced_execution", "delay")) {
            var controller = mock(AdvancedContraptionControllerBlockEntity.class);
            var level = mock(Level.class);
            AtomicLong tick = new AtomicLong(100);
            when(level.getGameTime()).thenAnswer(call -> tick.get());
            when(controller.getLevel()).thenReturn(level);
            Map<String, Double> applied = new LinkedHashMap<>();
            Map<String, Long> appliedAt = new LinkedHashMap<>();
            when(controller.getGraphTargetData(any(), eq("throttle"))).thenAnswer(call ->
                    AdvancedGraphDocument.Value.number(applied.getOrDefault(
                            ((AdvancedGraphDocument.Node) call.getArgument(0)).id(), 0D)));
            when(controller.setGraphTargetData(any(), anySet(), any())).thenAnswer(call -> {
                AdvancedGraphDocument.Node target = call.getArgument(0);
                Function<String, AdvancedGraphDocument.Value> values = call.getArgument(2);
                applied.put(target.id(), values.apply("throttle").asNumber());
                appliedAt.put(target.id(), tick.get());
                return true;
            });
            AdvancedGraphDocument graph = new AdvancedGraphDocument();
            graph.nodes().add(node("update", "event_tick", new CompoundTag()));
            CompoundTag keyData = new CompoundTag(); keyData.putString("BindingId", "key_space");
            graph.nodes().add(node("key", "controller_channel_input", keyData));
            String execFrom = "update";
            if (flow.endsWith("_execution")) {
                CompoundTag ports = new CompoundTag();
                for (int i = 0; i < 3; i++) ports.putString("exec_" + i, "exec");
                CompoundTag data = new CompoundTag(); data.put("DynamicOutputs", ports);
                graph.nodes().add(node("flow", flow, data));
                edge(graph, "update", "exec", "flow", "exec");
                execFrom = "flow";
            } else if (flow.equals("delay")) {
                CompoundTag data = new CompoundTag(); data.putInt("Ticks", 1);
                graph.nodes().add(node("flow", "delay", data));
                edge(graph, "update", "exec", "flow", "exec");
                execFrom = "flow";
            }
            for (int i = 0; i < 3; i++) {
                CompoundTag ports = new CompoundTag(); ports.putString("throttle", "number");
                CompoundTag data = new CompoundTag(); data.put("DynamicInputs", ports);
                graph.nodes().add(node("thruster" + i, "set_block_data", data));
                edge(graph, "key", "value", "thruster" + i, "throttle");
                edge(graph, execFrom, flow.endsWith("_execution") ? "exec_" + i : "exec", "thruster" + i, "exec");
                if (flow.equals("chain")) execFrom = "thruster" + i;
            }
            GraphRuntime runtime = new GraphRuntime(controller);
            for (double throttle : new double[]{1, 0, 1, 0}) {
                when(controller.getGraphBindingValue("key_space")).thenReturn(throttle);
                tick.incrementAndGet();
                appliedAt.clear();
                runtime.tick(graph, false);
                if (flow.equals("delay") && appliedAt.isEmpty()) {
                    tick.incrementAndGet();
                    runtime.tick(graph, false);
                }
                assertEquals(Map.of("thruster0", throttle, "thruster1", throttle, "thruster2", throttle), applied, flow);
                assertEquals(Map.of("thruster0", tick.get(), "thruster1", tick.get(), "thruster2", tick.get()), appliedAt, flow);
                assertTrue(runtime.diagnostics().isEmpty(), () -> flow + ": " + runtime.diagnostics());
            }
        }
    }

    @Test
    void failedSetDataReportsFalseBeforeItsDownstreamBranchExecutes() {
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(controller.getGraphTargetData(any(), anyString())).thenReturn(AdvancedGraphDocument.Value.number(0));
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.nodes().add(node("update", "event_tick", new CompoundTag()));
        CompoundTag data = new CompoundTag();
        CompoundTag ports = new CompoundTag(); ports.putString("throttle", "number"); data.put("DynamicInputs", ports);
        CompoundTag encoded = new CompoundTag(); encoded.putString("Type", "number"); encoded.put("Payload", AdvancedGraphDocument.Value.number(1).payload());
        CompoundTag defaults = new CompoundTag(); defaults.put("throttle", encoded); data.put("Defaults", defaults);
        graph.nodes().add(node("write", "set_block_data", data));
        graph.nodes().add(node("branch", "branch", new CompoundTag()));
        CompoundTag output = new CompoundTag(); output.putString("BindingId", "failed"); output.putDouble("Value", 1);
        graph.nodes().add(node("failed", "controller_channel_output", output));
        edge(graph, "update", "exec", "write", "exec");
        edge(graph, "write", "exec", "branch", "exec");
        edge(graph, "write", "success", "branch", "condition");
        edge(graph, "branch", "false", "failed", "exec");
        new GraphRuntime(controller).tick(graph, false);
        verify(controller).setGraphBindingValues(Map.of("failed", 1D), false);
    }

    private static AdvancedGraphDocument.Node node(String id, String type, CompoundTag data) {
        return new AdvancedGraphDocument.Node(id, type, id, 0, 0, data);
    }

    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input) {
        graph.edges().add(new AdvancedGraphDocument.Edge("edge" + graph.edges().size(), from, output, to, input));
    }
}
