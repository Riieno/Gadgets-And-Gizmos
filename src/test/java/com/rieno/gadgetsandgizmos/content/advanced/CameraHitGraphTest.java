package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraHitGraphTest{
    @Test void displayButtonCarriesItsPlayerIntoTheCameraInteractionInput(){
        var graph = new AdvancedGraphDocument();
        var camera = new AdvancedGraphDocument.Node("camera", "camera", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("camera", null));
        var button = new AdvancedGraphDocument.Node("display", "acc_display_widget", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("acc_display_widget", null));
        var element = new CompoundTag();
        element.putString("Type", "button"); element.putString("InteractionId", "use");
        element.putString("ValuePort", "pressed"); element.putString("ExecPort", "click");
        var elements = new ListTag(); elements.add(element);
        button.data().put("WidgetElements", elements);
        AdvancedHudInteractions.synchronize(button, graph.edges());
        graph.nodes().add(button); graph.nodes().add(camera);
        graph.edges().add(new AdvancedGraphDocument.Edge("click", "display", "click", "camera", "interact_with_hit"));
        assertEquals("exec", AdvancedGraphCatalog.inputs(camera).get("interact_with_hit"));
        assertEquals("Interact with Hit", camera.data().getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG).getString("interact_with_hit"));
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getLevel()).thenReturn(mock(Level.class));
        var runtime = new GraphRuntime(controller);
        runtime.tick(graph, false);
        var player = UUID.randomUUID();
        assertTrue(runtime.withInteractionPlayer(player, () -> runtime.enqueueHudButtonInteraction("display", "use", 10)));
        runtime.tick(graph, false);
        verify(controller).interactGraphCamera(eq(camera), any(), eq(player));
        assertTrue(runtime.diagnostics().isEmpty(), runtime.diagnostics().toString());
        assertTrue(runtime.needsRegularTick(graph), "Adding an execution input stopped passive camera updates");
    }
}
