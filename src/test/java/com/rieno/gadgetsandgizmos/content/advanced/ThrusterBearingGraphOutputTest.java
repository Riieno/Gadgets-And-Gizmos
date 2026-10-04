package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThrusterBearingGraphOutputTest {
    @Test
    void faceOutputSendsChangesToNegativeSwivelValues() {
        AdvancedContraptionControllerBlockEntity controller =
                mock(AdvancedContraptionControllerBlockEntity.class);
        AdvancedGraphDocument.Node node = new AdvancedGraphDocument.Node(
                "bearing-face-output", "linker_face_output", "", 0, 0, new CompoundTag());
        when(controller.getGraphTargetData(node, "direct_signal"))
                .thenReturn(AdvancedGraphDocument.Value.number(0));

        String rawPort = AdvancedContraptionControllerBlockEntity.GRAPH_RAW_DIRECT_SIGNAL_PORT;
        Map<String, AdvancedGraphDocument.Value> negative = Map.of(
                "direct_signal", AdvancedGraphDocument.Value.number(0),
                rawPort, AdvancedGraphDocument.Value.number(-0.5));
        assertEquals(Set.of("direct_signal"),
                AdvancedGraphOutputDelta.changedPorts(controller, node, negative));
        AdvancedGraphOutputDelta.recordApplied(controller, node, Set.of("direct_signal"), negative);
        assertEquals(Set.of(), AdvancedGraphOutputDelta.changedPorts(controller, node, negative));

        Map<String, AdvancedGraphDocument.Value> moreNegative = Map.of(
                "direct_signal", AdvancedGraphDocument.Value.number(0),
                rawPort, AdvancedGraphDocument.Value.number(-0.75));
        assertEquals(Set.of("direct_signal"),
                AdvancedGraphOutputDelta.changedPorts(controller, node, moreNegative));
    }
}
