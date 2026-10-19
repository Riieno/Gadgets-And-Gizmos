package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmStrengthUiTest{
    @Test void everyScmStrengthSliderIsUnsignedWhileDirectionAmountsStaySigned() throws Exception{
        var screen = mock(AdvancedContraptionControllerScreen.class, CALLS_REAL_METHODS);
        var range = AdvancedContraptionControllerScreen.class.getDeclaredMethod("numberRange", AdvancedGraphDocument.Node.class, String.class);
        range.setAccessible(true);
        int checked = 0;
        for(var type : AdvancedGraphCatalog.registry().definitions()){
            if(!AdvancedGraphCatalog.isShipControlType(type.id()) || !type.inputs().containsKey("strength")) continue;
            var node = new AdvancedGraphDocument.Node(type.id(), type.id(), "", 0, 0, new CompoundTag());
            assertArrayEquals(new double[]{0, 1}, (double[])range.invoke(screen, node, "strength"));
            checked++;
        }
        assertTrue(checked >= 5);
        var direction = new AdvancedGraphDocument.Node("yaw", "ship_yaw", "", 0, 0, new CompoundTag());
        assertArrayEquals(new double[]{-1, 1}, (double[])range.invoke(screen, direction, "amount"));
    }
}
