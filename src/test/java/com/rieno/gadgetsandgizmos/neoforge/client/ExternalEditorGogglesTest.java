package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.ClientViewOwnership;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalEditorGogglesTest{
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.neoforged.fml.ModList.of(java.util.List.of(), java.util.List.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void externalEditorDoesNotReceiveGogglesHudOrProjectedCameraInput(){
        try(var ownership = mockStatic(ClientViewOwnership.class)){
            ownership.when(ClientViewOwnership::isReserved).thenReturn(true);
            var event = mock(RenderGuiEvent.Post.class);
            CTPhysicsGogglesClient.onRenderGui(event);verifyNoInteractions(event);
            assertFalse(AccDisplayCameraProjection.interact(null, .5, .5, 0));
            assertDoesNotThrow(() -> AccDisplayCameraProjection.render(null, null, 640, 360, null, null, null, 0));
        }
    }
}
