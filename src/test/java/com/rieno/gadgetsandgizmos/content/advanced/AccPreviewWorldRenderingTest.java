package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.client.render.WorldRenderControl;
import com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccPreviewWorldRenderingTest{
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void onlyLiveThreeDimensionalViewsResumeTheCoveredWorldPass() throws Exception{
        var screen = mock(AdvancedContraptionControllerScreen.class);
        when(screen.suppressWorldRendering()).thenCallRealMethod();
        assertFalse(WorldRenderControl.shouldRenderWorld(true, screen));
        for(String flag : new String[]{"scmGraphOverview", "scmConfigurationOpen", "scmBlockPickerOpen", "projectionRendering"}){
            set(screen, flag, true);
            assertTrue(WorldRenderControl.shouldRenderWorld(true, screen), flag);
            assertFalse(WorldRenderControl.shouldRenderWorld(false, screen));
            set(screen, flag, false);
            assertFalse(WorldRenderControl.shouldRenderWorld(true, screen));
        }
        set(screen, "graphTargetPickerOpen", true);
        assertFalse(WorldRenderControl.shouldRenderWorld(true, screen));
        set(screen, "graphTargetPickerBlockTab", true);
        assertTrue(WorldRenderControl.shouldRenderWorld(true, screen));
        set(screen, "graphTargetPickerOpen", false);
        assertFalse(WorldRenderControl.shouldRenderWorld(true, screen));
    }

    private static void set(AdvancedContraptionControllerScreen screen, String field, boolean value) throws Exception{
        var target = AdvancedContraptionControllerScreen.class.getDeclaredField(field);
        target.setAccessible(true);target.setBoolean(screen, value);
    }
}
