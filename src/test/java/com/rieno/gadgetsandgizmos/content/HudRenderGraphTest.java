package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.util.RevisionSnapshotCache;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HudRenderGraphTest{
    @Test void repeatedHudReadsReuseTopologyAndTheCompiledProgramWithoutGraphCopies() throws Exception{
        var graph = spy(new AdvancedGraphDocument());
        CompoundTag sourceData = new CompoundTag();
        sourceData.putDouble("Value", 0.75);
        var source = new AdvancedGraphDocument.Node("source", "constant_number", "", 0, 0, sourceData);
        var hud = new AdvancedGraphDocument.Node("hud", "advanced_hud_element", "", 0, 0, new CompoundTag());
        CompoundTag defaults = new CompoundTag();
        defaults.put("label", AdvancedGraphDocument.Value.string("Ship HUD").toTag());
        hud.data().put("Defaults", defaults);
        CompoundTag ports = new CompoundTag(); ports.putString("value", "number");
        hud.data().put("DynamicInputs", ports);
        graph.nodes().add(source); graph.nodes().add(hud);
        graph.edges().add(new AdvancedGraphDocument.Edge("wire", "source", "value", "hud", "value"));
        var controller = controller(graph);
        var runtime = new GraphRuntime(controller);
        field(controller, "graphRuntime", runtime);
        runtime.compile(graph);
        var layout = controller.activeGraphReadView();
        clearInvocations(graph);
        for(int frame = 0; frame < 1000; frame++){
            assertSame(layout, controller.activeGraphReadView());
            assertSame(hud, layout.node("hud"));
            assertEquals("Ship HUD", controller.previewActiveGraphInput(hud, "label").asString());
            assertEquals(0.75, controller.previewActiveGraphInput(hud, "value").asNumber());
        }
        verify(graph, never()).copy();
        verify(graph, never()).toTag();
        verify(graph, never()).programFingerprint();
        assertTrue(runtime.liveInputs().isEmpty(), "Rendering must not write runtime inputs");
        assertTrue(runtime.liveOutputs().isEmpty(), "Rendering must not fire runtime events");
    }

    @Test void graphEditsAndSameRevisionControllerUpdatesReplaceTheRenderView() throws Exception{
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("old", "hud_element", "", 0, 0, new CompoundTag()));
        var controller = controller(graph);
        var first = controller.activeGraphReadView();
        graph.nodes().clear();
        graph.nodes().add(new AdvancedGraphDocument.Node("new", "hud_element", "", 0, 0, new CompoundTag()));
        graph.setRevision(1);
        var updated = controller.activeGraphReadView();
        assertNotSame(first, updated);
        assertNull(updated.node("old"));
        assertSame(graph.nodes().getFirst(), updated.node("new"));
        var replacement = new AdvancedGraphDocument(); replacement.setRevision(1);
        field(controller, "activeGraph", replacement);
        assertNotSame(updated, controller.activeGraphReadView());
        assertTrue(controller.activeGraphReadView().nodes().isEmpty());
    }

    @Test void editingStillGetsAnIndependentDocument() throws Exception{
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("hud", "hud_element", "", 0, 0, new CompoundTag()));
        var controller = controller(graph);
        var edit = controller.getActiveGraph();
        edit.nodes().getFirst().data().putString("Label", "Edited");
        edit.nodes().clear();
        assertEquals(1, graph.nodes().size());
        assertFalse(controller.activeGraphReadView().node("hud").data().contains("Label"));
    }

    private static AdvancedContraptionControllerBlockEntity controller(AdvancedGraphDocument graph) throws Exception{
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        field(controller, "activeGraph", graph);
        field(controller, "activeGraphReadCache", new RevisionSnapshotCache<>());
        return controller;
    }

    private static void field(Object target, String name, Object val) throws Exception{
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, val);
    }
}
