package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.physics.EntityTelemetryApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GraphTrackingNodesTest {
    @BeforeAll
    static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test
    void shipTrackerWorksWithoutLocalScmAndUsesConfiguredInputs(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        when(controller.getShipTrackingValue(eq("tracker"), eq("All"), eq(128.0D), anyString()))
                .thenAnswer(call -> GraphRuntime.defaultShipTrackingValue(call.getArgument(3)));
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("tracker", "ship_tracker", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("ship_tracker", AdvancedGraphNodeFactory.Context.EMPTY)));
        assertTrue(AdvancedGraphValidator.validate(graph, false, false).valid());
        GraphRuntime runtime = new GraphRuntime(controller);
        runtime.tick(graph, true);
        verify(controller, atLeastOnce()).getShipTrackingValue(eq("tracker"), eq("All"), eq(128.0D), anyString());
        assertTrue(runtime.diagnostics().isEmpty());
    }

    @Test
    void gogglesKeepExistingPortsAndExposeTypedTelemetry(){
        var node = new AdvancedGraphDocument.Node("goggles", "portable_tracker", "", 0, 0, new CompoundTag());
        var outputs = AdvancedGraphCatalog.outputs(node);
        assertEquals("map", outputs.get("player_facing"));
        assertEquals("map", outputs.get("looking_at"));
        assertEquals("string", outputs.get("wearer_uuid"));
        EntityTelemetryApi.ports().forEach((port, type) -> assertEquals(type, outputs.get(port), port));
        assertEquals("map", GraphRuntime.defaultShipTrackingValue("coordinates").type());
        assertEquals("list", GraphRuntime.defaultShipTrackingValue("ships").type());
        assertEquals("boolean", GraphRuntime.defaultShipTrackingValue("available").type());
    }
}
