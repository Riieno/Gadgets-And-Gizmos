package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BearingGraphControlTest{
    @BeforeAll
    static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void vectorBearingLimitAndModeReachTheRegistryAndTheirSetters(){
        var bearing = mock(VectorBearingBlockEntity.class);
        when(bearing.graphWritableData()).thenCallRealMethod();
        when(bearing.graphReadableData()).thenCallRealMethod();
        when(bearing.graphWritableOptions()).thenCallRealMethod();
        when(bearing.writeGraphData(anyString(), any())).thenCallRealMethod();
        assertEquals("number", BlockEntityDataAdapterRegistry.writableData(bearing).get("max_tilt"));
        assertEquals("string", BlockEntityDataAdapterRegistry.writableData(bearing).get("control_mode"));
        assertEquals(List.of("auto", "computer", "redstone"),
                BlockEntityDataAdapterRegistry.writableOptions(bearing).get("control_mode"));
        assertEquals("boolean", BlockEntityDataAdapterRegistry.readableData(bearing).get("keep_stable"));
        assertTrue(bearing.writeGraphData("max_tilt", AdvancedGraphDocument.Value.number(30)));
        assertTrue(bearing.writeGraphData("control_mode", AdvancedGraphDocument.Value.string("computer")));
        verify(bearing).setMaxTiltDegrees(30);
        verify(bearing).setControlMode(VectorBearingBlockEntity.ControlMode.COMPUTER);
    }

    @Test
    void otherBearingRangesRemainWritable(){
        var thruster = mock(ThrusterBearingBlockEntity.class);
        when(thruster.graphWritableData()).thenCallRealMethod();
        assertEquals("number", BlockEntityDataAdapterRegistry.writableData(thruster).get("min_angle"));
        assertEquals("number", BlockEntityDataAdapterRegistry.writableData(thruster).get("max_angle"));
        var aileron = mock(AileronBearingBlockEntity.class);
        when(aileron.graphWritableData()).thenCallRealMethod();
        var ports = BlockEntityDataAdapterRegistry.writableData(aileron);
        for(String port : List.of("primary_min", "primary_max", "secondary_min", "secondary_max"))
            assertEquals("number", ports.get(port));
    }
}
