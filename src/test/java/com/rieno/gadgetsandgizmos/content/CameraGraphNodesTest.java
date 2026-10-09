package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.content.advanced.CameraGraphNodes;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Verify Camera graph modes, unloaded sources and execution pulse routing
class CameraGraphNodesTest{
    // Initialize registries used by the ACC graph runtime
    @BeforeAll
    static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }
    // Preserve saved rates and connected wires when upgrading the port's units
    @Test
    void oldRateDefaultsAndWiresMigrateToSeconds(){
        var graph = new AdvancedGraphDocument();
        var camera = node("camera", "camera");
        camera.data().getCompound("Defaults").remove("rays_per_second");
        CompoundTag oldRate = new CompoundTag();
        oldRate.putString("Type", "number");
        oldRate.put("Payload", AdvancedGraphDocument.Value.number(3).payload());
        camera.data().getCompound("Defaults").put("rays_per_tick", oldRate);
        graph.nodes().add(camera);
        graph.nodes().add(node("rate", "constant_number"));
        graph.edges().add(new AdvancedGraphDocument.Edge("rate-wire", "rate", "value", "camera", "rays_per_tick"));
        var restored = AdvancedGraphDocument.fromTag(graph.toTag());
        var defaults = restored.nodes().getFirst().data().getCompound("Defaults");
        assertEquals(60, defaults.getCompound("rays_per_second").getCompound("Payload").getDouble("Value"));
        assertFalse(defaults.contains("rays_per_tick"));
        assertEquals("rate-wire", restored.edges().getFirst().id());
        assertEquals("rays_per_second", restored.edges().getFirst().toPort());
    }
    // Expose Manual inputs only while the mode requires them
    @Test
    void manualPortsFollowTheSelectedMode(){
        var node = node("camera", "camera");
        assertFalse(AdvancedGraphCatalog.inputs(node).containsKey("pan"));
        node.data().getCompound("Defaults").getCompound("mode").put("Payload", AdvancedGraphDocument.Value.string("Manual").payload());
        assertEquals("number", AdvancedGraphCatalog.inputs(node).get("pan"));
        assertEquals("number", AdvancedGraphCatalog.inputs(node).get("tilt"));
        node.data().getCompound("Defaults").getCompound("mode").put("Payload", AdvancedGraphDocument.Value.string("Sentry").payload());
        assertFalse(AdvancedGraphCatalog.inputs(node).containsKey("pan"));
        assertFalse(AdvancedGraphCatalog.inputs(node).containsKey("tilt"));
    }
    // Retain correct types when a camera is unloaded or a graph is simulated
    @Test
    void unavailableOutputsMatchTheNodeSchema(){
        AdvancedGraphCatalog.outputs(node("camera", "camera")).forEach((port, type) ->
                assertEquals(type, CameraGraphNodes.unavailable(port).type(), port));
        assertEquals(java.util.Set.of("target", "source", "name"),
                AdvancedGraphCatalog.inputs(node("feed", "acc_display_camera_source")).keySet());
        assertTrue(AdvancedGraphCatalog.outputs(node("control", "control_camera")).isEmpty());
    }
    // Execute a control request once per pulse and never while merely sampling nodes
    @Test
    void controlCameraRequiresAnExecutionPulse(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(node("tick", "event_tick"));
        graph.nodes().add(node("control", "control_camera"));
        GraphRuntime runtime = new GraphRuntime(controller);
        runtime.tick(graph, true);
        verify(controller, never()).controlGraphCamera(any(), any(), any());
        graph.edges().add(new AdvancedGraphDocument.Edge("pulse", "tick", "exec", "control", "exec"));
        graph.setRevision(graph.revision() + 1);
        assertTrue(AdvancedGraphValidator.validate(graph, false, false).valid());
        runtime.tick(graph, true);
        verify(controller, times(1)).controlGraphCamera(any(), any(), isNull());
    }
    // Keep controls active with no ray consumers and no runtime observer
    @Test
    void unconnectedCameraAppliesControlsWithoutPassiveSampling(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var camera = mock(CameraBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        when(controller.graphCamera(any(), any())).thenReturn(camera);
        var graph = new AdvancedGraphDocument();
        var node = node("camera", "camera");
        graph.nodes().add(node);
        var runtime = new GraphRuntime(controller);
        assertTrue(runtime.needsRegularTick(graph, false));
        runtime.tick(graph, false);
        verify(camera).configure(eq(64.0D), eq(20.0D), any(),
                eq(com.rieno.gadgetsandgizmos.lib.view.ViewRig.Mode.LOCKED),
                eq(com.rieno.gadgetsandgizmos.lib.view.ViewRig.Orientation.LOCAL), eq(0.0D), eq(0.0D));
        node.data().getCompound("Defaults").getCompound("mode")
                .put("Payload", AdvancedGraphDocument.Value.string("Sentry").payload());
        graph.setRevision(graph.revision() + 1);
        runtime.compile(graph);
        runtime.tick(graph, false);
        verify(camera).configure(eq(64.0D), eq(20.0D), any(),
                eq(com.rieno.gadgetsandgizmos.lib.view.ViewRig.Mode.SENTRY), any(), eq(0.0D), eq(0.0D));
        verify(camera, never()).graphValue(any());
    }
    // Forward connected scalar values while keeping simulation free of block mutations
    @Test
    void manualCameraReadsScalarConnectionsWithoutRayConsumers(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var camera = mock(CameraBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        when(controller.graphCamera(any(), any())).thenReturn(camera);
        var graph = new AdvancedGraphDocument();
        var node = node("camera", "camera");
        node.data().getCompound("Defaults").getCompound("mode")
                .put("Payload", AdvancedGraphDocument.Value.string("Manual").payload());
        var pan = node("pan", "constant_number");
        var tilt = node("tilt", "constant_number");
        pan.data().putDouble("Value", 120);
        tilt.data().putDouble("Value", -80);
        graph.nodes().addAll(java.util.List.of(node, pan, tilt));
        graph.edges().add(new AdvancedGraphDocument.Edge("pan", "pan", "value", "camera", "pan"));
        graph.edges().add(new AdvancedGraphDocument.Edge("tilt", "tilt", "value", "camera", "tilt"));
        new GraphRuntime(controller, true).tick(graph, false);
        verifyNoInteractions(camera);
        new GraphRuntime(controller).tick(graph, false);
        verify(camera).configure(eq(64.0D), eq(20.0D), any(),
                eq(com.rieno.gadgetsandgizmos.lib.view.ViewRig.Mode.MANUAL), any(), eq(120.0D), eq(-80.0D));
    }
    // Build the shared client/server node defaults
    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }
}
