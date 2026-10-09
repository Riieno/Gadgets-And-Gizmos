package com.rieno.gadgetsandgizmos.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmFleetRoutingTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test
    void largeMixedFleetRebindsImmediatelyAfterAnEditAndReload() throws Exception{
        UUID ship = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        var profile = ScmConfigurationProfile.empty();
        Set<ScmConfigurationProfile.UnitReference> refs = new LinkedHashSet<>();
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        for(int idx = 0; idx < 1024; idx++){
            BlockPos pos = new BlockPos(idx, 0, 0);
            String adapter = idx % 2 == 0 ? "thruster" : "scm:test:face_north";
            refs.add(new ScmConfigurationProfile.UnitReference(ship, pos,
                    idx % 2 == 0 ? adapter : "", idx % 2 == 0 ? null : Direction.NORTH));
            units.add(new ShipControlMap.PropulsionUnit(idx, ship, pos, "test:block", adapter,
                    true, Vec3.ZERO, new Vec3(0, 1, 0), 0, 1, 0, 1, 1, List.of()));
        }
        var map = new ShipControlMap(mapId, "test:level", ship, BlockPos.ZERO, Vec3.ZERO, units, 0L);
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getScmConfigurationProfile()).thenReturn(profile);
        var runtime = new ShipControlModuleRuntime(controller);
        var selected = ShipControlModuleRuntime.class.getDeclaredMethod("isAccelerationControlUnit",
                ShipControlMap.class, ShipControlMap.PropulsionUnit.class);
        selected.setAccessible(true);
        profile.replace(mapId, List.of(new ScmConfigurationProfile.Group("drive", "Drive", refs)),
                Map.of(ScmConfigurationProfile.ACCELERATION_ACTION, "drive"), Set.of());
        for(var unit : units) assertTrue((boolean) selected.invoke(runtime, map, unit));
        var retained = refs.iterator().next();
        profile.replace(mapId, List.of(new ScmConfigurationProfile.Group("drive", "Drive", Set.of(retained))),
                Map.of("ship_accelerate", "drive"), Set.of());
        for(var unit : units){
            assertEquals(retained.blockPosition().equals(unit.blockPosition()), selected.invoke(runtime, map, unit));
        }
        when(controller.getScmConfigurationProfile()).thenReturn(ScmConfigurationProfile.fromTag(profile.toTag()));
        for(var unit : units){
            assertEquals(retained.blockPosition().equals(unit.blockPosition()), selected.invoke(runtime, map, unit));
        }
        profile.replace(mapId, List.of(), Map.of(), Set.of());
        assertTrue(profile.accelerationControlUnits().isEmpty());
        assertTrue(profile.groupedUnits().isEmpty());
        assertTrue(profile.ikJointUnits().isEmpty());
    }
}
