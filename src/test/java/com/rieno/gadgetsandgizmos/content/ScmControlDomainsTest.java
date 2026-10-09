package com.rieno.gadgetsandgizmos.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScmControlDomainsTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test
    void independentDriveChainsRetainActuatorPriorityOrder() throws Exception{
        Class<?> optionType = Class.forName(ShipControlModuleRuntime.class.getName() + "$KineticControlOption");
        var ctor = optionType.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var unitMethod = optionType.getDeclaredMethod("unit");
        unitMethod.setAccessible(true);
        var domains = ShipControlModuleRuntime.class.getDeclaredMethod("kineticControlDomains", List.class);
        domains.setAccessible(true);
        List<Object> options = new ArrayList<>();
        UUID ship = UUID.randomUUID();
        for(int idx : new int[]{7, 3, 1, 6, 2, 5, 0, 4}){
            var unit = new ShipControlMap.PropulsionUnit(idx, ship, new BlockPos(idx, 0, 0), "test:block",
                    "scm:test", true, Vec3.ZERO, new Vec3(0, 1, 0), 0, 1, 0, 1, 1, List.of());
            options.add(ctor.newInstance(unit, null, Set.of(idx % 2 == 0 ? "left_drive" : "right_drive")));
        }
        List<?> result = (List<?>) domains.invoke(null, options);
        List<List<Integer>> indices = new ArrayList<>();
        for(Object group : result){
            List<Integer> values = new ArrayList<>();
            for(Object option : (List<?>) group){
                values.add(((ShipControlMap.PropulsionUnit) unitMethod.invoke(option)).index());
            }
            indices.add(values);
        }
        assertEquals(List.of(List.of(1, 3, 5, 7), List.of(0, 2, 4, 6)), indices);
    }
}
