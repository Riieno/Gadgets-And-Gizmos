package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.graph.GraphInputVisibility;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GearboxGraphControlTest{
    @BeforeAll
    static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void setDataShowsOnlyTheSelectedModesGearboxControls(){
        var gearbox = mock(BiDirectionalGearboxBlockEntity.class);
        when(gearbox.getOperationModeName()).thenReturn("passthrough");
        when(gearbox.graphWritableData()).thenCallRealMethod();
        when(gearbox.graphWritableInputConditions()).thenCallRealMethod();
        var node = new AdvancedGraphDocument.Node("set", "set_block_data", "", 0, 0, new CompoundTag());
        CompoundTag ports = new CompoundTag();
        gearbox.graphWritableData().forEach(ports::putString);
        node.data().put("DynamicInputs", ports);
        GraphInputVisibility.configure(node.data(), "", gearbox.graphWritableInputConditions());
        assertTrue(AdvancedGraphCatalog.inputs(node).containsKey("north_south_output_speed_percent"));
        assertFalse(AdvancedGraphCatalog.inputs(node).containsKey("north_max_angle"));
        CompoundTag defaults = new CompoundTag();
        defaults.put("operation_mode", AdvancedGraphDocument.Value.string("servo").toTag());
        node.data().put("Defaults", defaults);
        assertTrue(AdvancedGraphCatalog.inputs(node).containsKey("north_max_angle"));
        assertEquals("string", AdvancedGraphCatalog.inputs(node).get("north_direction"));
        assertFalse(AdvancedGraphCatalog.inputs(node).containsKey("north_target_speed"));
        assertFalse(AdvancedGraphCatalog.inputs(node).containsKey("north_output_inverted"));
        assertFalse(AdvancedGraphCatalog.inputs(node).containsKey("north_south_output_speed_percent"));
    }

    @Test
    void dropdownDirectionsAndLegacyNumbersReachTheirFace(){
        var gearbox = mock(BiDirectionalGearboxBlockEntity.class);
        when(gearbox.writeGraphData(anyString(), any())).thenCallRealMethod();
        when(gearbox.graphWritableOptions()).thenCallRealMethod();
        assertEquals(List.of("Clockwise", "Counter Clockwise"), gearbox.graphWritableOptions().get("west_direction"));
        assertTrue(gearbox.writeGraphData("west_direction", AdvancedGraphDocument.Value.string("Counter Clockwise")));
        assertTrue(gearbox.writeGraphData("east_direction", AdvancedGraphDocument.Value.string("Clockwise")));
        assertTrue(gearbox.writeGraphData("north_direction", AdvancedGraphDocument.Value.number(-1)));
        verify(gearbox).setFaceOutputInverted(Direction.WEST, true);
        verify(gearbox).setFaceOutputInverted(Direction.EAST, false);
        verify(gearbox).setFaceOutputInverted(Direction.NORTH, true);
        gearbox.writeGraphData("south_max_angle", AdvancedGraphDocument.Value.number(30));
        verify(gearbox).setFaceMaxAngle(Direction.SOUTH, 30);
    }

    @Test
    void lanePercentagesRemainIndependentAndReachTheDrivenOutput() throws Exception{
        var gearbox = mock(BiDirectionalGearboxBlockEntity.class);
        when(gearbox.getSecondaryLaneAxis()).thenReturn(Direction.Axis.X);
        when(gearbox.getLaneOutputSpeedPercent(any())).thenCallRealMethod();
        doCallRealMethod().when(gearbox).setLaneOutputSpeedPercent(any(), anyDouble());
        gearbox.setLaneOutputSpeedPercent(Direction.Axis.Z, 100);
        gearbox.setLaneOutputSpeedPercent(Direction.Axis.X, 100);
        gearbox.setLaneOutputSpeedPercent(Direction.Axis.Z, 25);
        assertEquals(25, gearbox.getLaneOutputSpeedPercent(Direction.Axis.Z), 0.001);
        assertEquals(100, gearbox.getLaneOutputSpeedPercent(Direction.Axis.X), 0.001);
        when(gearbox.getFaceTargetSpeed(any())).thenReturn(Double.NaN);
        when(gearbox.getNorthSouthSpeed()).thenReturn(128.0f);
        when(gearbox.getEastWestSpeed()).thenReturn(-128.0f);
        var method = BiDirectionalGearboxBlockEntity.class.getDeclaredMethod("getFaceOutputMultiplier", Direction.class);
        method.setAccessible(true);
        assertEquals(0.25f, (float) method.invoke(gearbox, Direction.NORTH), 0.001);
        assertEquals(1.0f, (float) method.invoke(gearbox, Direction.EAST), 0.001);
        gearbox.setLaneOutputSpeedPercent(Direction.Axis.X, 0);
        assertEquals(0.0f, (float) method.invoke(gearbox, Direction.EAST), 0.001);
        assertEquals(0.25f, (float) method.invoke(gearbox, Direction.NORTH), 0.001);
    }

    @Test
    void eachServoTargetIsClampedToItsOwnLimit() throws Exception{
        var gearbox = mock(BiDirectionalGearboxBlockEntity.class);
        when(gearbox.getManualAngle(Direction.NORTH)).thenReturn(75.0);
        when(gearbox.getFaceMaxAngle(Direction.NORTH)).thenReturn(30.0);
        when(gearbox.getManualAngle(Direction.SOUTH)).thenReturn(-75.0);
        when(gearbox.getFaceMaxAngle(Direction.SOUTH)).thenReturn(15.0);
        when(gearbox.getManualAngle(Direction.EAST)).thenReturn(Double.NaN);
        when(gearbox.getFaceMaxAngle(Direction.EAST)).thenReturn(0.0);
        when(gearbox.getFaceAngle(any())).thenCallRealMethod();
        var method = BiDirectionalGearboxBlockEntity.class.getDeclaredMethod("setServoFaceAngle", Direction.class, double.class);
        method.setAccessible(true);
        for(Direction face : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST)) method.invoke(gearbox, face, 90.0);
        assertEquals(30.0, gearbox.getFaceAngle(Direction.NORTH));
        assertEquals(-15.0, gearbox.getFaceAngle(Direction.SOUTH));
        assertEquals(0.0, gearbox.getFaceAngle(Direction.EAST));
    }
}
