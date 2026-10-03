package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvancedGraphOutputDeltaTest{
    @Test
    void writesAnExplicitZeroOnce(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var node = new AdvancedGraphDocument.Node("zero-output", "direct_target_output", "", 0, 0,
                new CompoundTag());
        String port = GraphSignalRange.REDSTONE_SIGNAL_PORT;
        var zero = AdvancedGraphDocument.Value.number(0);
        Map<String, AdvancedGraphDocument.Value> desired = Map.of(port, zero);
        when(controller.getGraphTargetData(node, port)).thenReturn(zero);

        assertEquals(Set.of(port), AdvancedGraphOutputDelta.changedPorts(controller, node, desired));
        AdvancedGraphOutputDelta.recordApplied(controller, node, Set.of(port), desired);
        assertEquals(Set.of(), AdvancedGraphOutputDelta.changedPorts(controller, node, desired));
    }
}
