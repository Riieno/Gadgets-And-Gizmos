package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphValues;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancedMathGraphTest{
    @BeforeAll
    static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test
    void registeredSuiteSurvivesSerializationAndProducesTypedValues(){
        var graph = new AdvancedGraphDocument();
        var scale = node("scale", "createthrusters:vector_scale");
        defaults(scale).put("vector", encoded(GraphRuntime.fromLibraryValue(MathGraphValues.vector(new Vector3(1, -2, 3)))));
        defaults(scale).put("scalar", encoded(AdvancedGraphDocument.Value.number(2)));
        graph.nodes().add(scale);
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        assertTrue(AdvancedGraphValidator.validate(graph, true, true).valid());
        var runtime = new GraphRuntime(null, true);
        assertEquals(new Vector3(2, -4, 6), MathGraphValues.vector(GraphRuntime.toLibraryValue(runtime.previewOutput(graph, graph.nodes().getFirst(), "value"))));
    }

    @Test
    void existingRotationIdsExposeNamedOutputsAndCompatibleMapKeys(){
        var graph = new AdvancedGraphDocument();
        var convert = node("angles", "quaternion_to_tait_bryan");
        defaults(convert).put("quaternion", encoded(GraphRuntime.fromLibraryValue(MathGraphValues.quaternion(
                Quaternion.fromAxisAngle(new Vector3(0, 0, 1), 0.6)))));
        graph.nodes().add(convert);
        var runtime = new GraphRuntime(null, true);
        assertEquals("number", AdvancedGraphCatalog.outputs(convert).get("yaw"));
        assertEquals(0.6, runtime.previewOutput(graph, convert, "yaw").asNumber(), 1.0E-12);
        var map = GraphRuntime.toLibraryValue(runtime.previewOutput(graph, convert, "tait_bryan"));
        assertEquals(0.6, MathGraphValues.component(map, "yaw", 0), 1.0E-12);
        assertEquals(0.6, MathGraphValues.component(map, "z", 0), 1.0E-12);
    }

    @Test
    void controllerExecAdvancesIntegratorOnceAndOutputReadsDoNotAdvance(){
        for(String id : new String[]{"linear_integrator", "rigid_body_integrator"}){
            var controller = mock(AdvancedContraptionControllerBlockEntity.class);
            var level = mock(Level.class);
            var tick = new AtomicLong(1);
            when(controller.getLevel()).thenReturn(level);
            when(level.getGameTime()).thenAnswer(call -> tick.get());
            var graph = new AdvancedGraphDocument();
            var integrator = node("motion", "createthrusters:" + id);
            defaults(integrator).put("velocity", encoded(GraphRuntime.fromLibraryValue(MathGraphValues.vector(new Vector3(2, 0, 0)))));
            defaults(integrator).put("delta_time", encoded(AdvancedGraphDocument.Value.number(1)));
            graph.nodes().add(node("tick", "event_tick"));
            graph.nodes().add(integrator);
            graph.edges().add(new AdvancedGraphDocument.Edge("exec", "tick", "exec", "motion", "exec"));
            var runtime = new GraphRuntime(controller);
            runtime.tick(graph, true);
            for(int idx = 0; idx < 3; idx++){
                assertEquals(2, MathGraphValues.vector(GraphRuntime.toLibraryValue(runtime.previewOutput(graph, integrator, "position"))).x(), 1.0E-12);
            }
            tick.incrementAndGet();
            runtime.tick(graph, true);
            assertEquals(4, MathGraphValues.vector(GraphRuntime.toLibraryValue(runtime.previewOutput(graph, integrator, "position"))).x(), 1.0E-12);
            assertTrue(runtime.diagnostics().isEmpty(), () -> runtime.diagnostics().toString());
        }
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    private static CompoundTag encoded(AdvancedGraphDocument.Value val){
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", val.type());
        tag.put("Payload", val.payload().copy());
        return tag;
    }

    private static CompoundTag defaults(AdvancedGraphDocument.Node node){
        return node.data().getCompound("Defaults");
    }
}
