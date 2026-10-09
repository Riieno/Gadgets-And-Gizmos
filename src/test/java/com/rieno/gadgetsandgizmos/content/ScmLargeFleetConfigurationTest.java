package com.rieno.gadgetsandgizmos.content;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScmLargeFleetConfigurationTest {
    @BeforeAll
    static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test
    void savesAndLoadsThousandsOfControlUnits(){
        UUID ship = UUID.randomUUID();
        Set<ScmConfigurationProfile.UnitReference> units = new LinkedHashSet<>();
        for(int idx = 0; idx < 8192; idx++){
            units.add(new ScmConfigurationProfile.UnitReference(ship, new BlockPos(idx, 0, 0), "test:thruster", null));
        }
        ScmConfigurationProfile.Group group = new ScmConfigurationProfile.Group("lift", "Lift", units);
        assertEquals(units, ScmConfigurationProfile.Group.fromTag(group.toTag()).units());
    }

    @Test
    void directCommandsOnlyReplaceFacesOfTheSameBlock(){
        UUID ship = UUID.randomUUID();
        var direct = new ScmConfigurationProfile.UnitReference(ship, new BlockPos(1, 0, 0), "test:thruster", null);
        var sameBlock = new ScmConfigurationProfile.UnitReference(ship, new BlockPos(1, 0, 0), "", Direction.NORTH);
        var otherBlock = new ScmConfigurationProfile.UnitReference(ship, new BlockPos(2, 0, 0), "", Direction.NORTH);
        var group = new ScmConfigurationProfile.Group("lift", "Lift", Set.of(direct, sameBlock, otherBlock));
        assertEquals(Set.of(direct, otherBlock), group.units());
    }
}
