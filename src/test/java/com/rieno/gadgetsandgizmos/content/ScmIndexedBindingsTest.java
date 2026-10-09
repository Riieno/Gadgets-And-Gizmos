package com.rieno.gadgetsandgizmos.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmIndexedBindingsTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test
    void indexedMatchingPreservesAdaptersFacesAndBodyIdentity() throws Exception{
        var runtime = new ShipControlModuleRuntime(mock(AdvancedContraptionControllerBlockEntity.class));
        var indexMethod = ShipControlModuleRuntime.class.getDeclaredMethod("configurationIndex", Collection.class);
        var matchMethod = ShipControlModuleRuntime.class.getDeclaredMethod("configurationReferenceMatchesUnit",
                ScmConfigurationProfile.UnitReference.class, ShipControlMap.PropulsionUnit.class);
        var indexedMethod = ShipControlModuleRuntime.class.getDeclaredMethod("configurationIndexMatchesUnit",
                com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockIndex.class, ShipControlMap.PropulsionUnit.class);
        indexMethod.setAccessible(true);
        matchMethod.setAccessible(true);
        indexedMethod.setAccessible(true);
        Random random = new Random(43L);
        UUID[] ships = {UUID.randomUUID(), UUID.randomUUID(), null};
        for(int trial = 0; trial < 30; trial++){
            List<ScmConfigurationProfile.UnitReference> refs = new ArrayList<>();
            for(int idx = 0; idx < 100; idx++){
                refs.add(new ScmConfigurationProfile.UnitReference(ships[random.nextInt(3)],
                        new BlockPos(random.nextInt(30), 0, 0), random.nextBoolean() ? "" : "scm:test",
                        random.nextBoolean() ? null : Direction.values()[random.nextInt(6)]));
            }
            Object index = indexMethod.invoke(null, refs);
            for(int idx = 0; idx < 100; idx++){
                String adapter = random.nextBoolean() ? "scm:test" : "scm:test:face_"
                        + Direction.values()[random.nextInt(6)].getSerializedName();
                var unit = new ShipControlMap.PropulsionUnit(idx, ships[random.nextInt(3)],
                        new BlockPos(random.nextInt(30), 0, 0), "test:block", adapter,
                        true, Vec3.ZERO, new Vec3(0, 1, 0), 0, 1, 0, 1, 1, List.of());
                boolean expected = false;
                for(var reference : refs){
                    if((boolean) matchMethod.invoke(runtime, reference, unit)){
                        expected = true;
                        break;
                    }
                }
                assertEquals(expected, indexedMethod.invoke(runtime, index, unit));
            }
        }
    }
}
