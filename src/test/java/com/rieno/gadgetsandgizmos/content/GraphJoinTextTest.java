package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTextInputs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GraphJoinTextTest {
    @BeforeAll
    static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test
    void addedAndRemovedInputsReachGraphConsumers(){
        CompoundTag data = new CompoundTag();
        GraphTextInputs.add(data);
        CompoundTag defaults = new CompoundTag();
        defaults.put("a", textDefault("one "));
        defaults.put("b", textDefault("two "));
        defaults.put("text_3", textDefault("three"));
        data.put("Defaults", defaults);
        var join = new AdvancedGraphDocument.Node("join", "string_concat", "", 0, 0, data);
        assertEquals("string", AdvancedGraphCatalog.inputs(join).get("text_3"));
        assertTrue(GraphTextInputs.remove(join.data(), "b"));
        assertFalse(AdvancedGraphCatalog.inputs(join).containsKey("b"));
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        AtomicReference<String> result = new AtomicReference<>();
        when(controller.setGraphTargetData(any(), anySet(), any())).thenAnswer(call -> {
            Function<String, AdvancedGraphDocument.Value> values = call.getArgument(2);
            result.set(values.apply("text").asString());
            return true;
        });
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.nodes().add(join);
        graph.nodes().add(new AdvancedGraphDocument.Node("tick", "event_tick", "", 0, 0, new CompoundTag()));
        CompoundTag target = new CompoundTag(), ports = new CompoundTag();
        ports.putString("text", "string");
        target.put("DynamicInputs", ports);
        graph.nodes().add(new AdvancedGraphDocument.Node("target", "set_block_data", "", 0, 0, target));
        graph.edges().add(new AdvancedGraphDocument.Edge("exec", "tick", "exec", "target", "exec"));
        graph.edges().add(new AdvancedGraphDocument.Edge("value", "join", "value", "target", "text"));
        GraphRuntime runtime = new GraphRuntime(controller);
        runtime.tick(graph, false);
        assertEquals("one three", result.get());
        assertTrue(runtime.diagnostics().isEmpty());
        assertTrue(GraphTextInputs.remove(join.data(), "a"));
        runtime.compile(graph);
        when(level.getGameTime()).thenReturn(101L);
        runtime.tick(graph, false);
        assertEquals("three", result.get());
        assertTrue(runtime.diagnostics().isEmpty());
    }
    private static CompoundTag textDefault(String text){
        CompoundTag value = new CompoundTag(), payload = new CompoundTag();
        value.putString("Type", "string");
        payload.putString("Value", text);
        value.put("Payload", payload);
        return value;
    }
}
