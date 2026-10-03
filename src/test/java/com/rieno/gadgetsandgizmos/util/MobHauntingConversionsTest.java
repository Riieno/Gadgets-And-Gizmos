package com.rieno.gadgetsandgizmos.util;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MobHauntingConversionsTest {
    @BeforeAll
    static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(MockedStatic<LoadingModList> loader = mockStatic(LoadingModList.class)){
            LoadingModList mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    @Test
    void preservesEveryBuiltInNetherAndEndConversion(){
        assertEquals(EntityType.WITHER_SKELETON, MobHauntingConversions.getConversionType(EntityType.SKELETON));
        assertEquals(EntityType.WITHER_SKELETON, MobHauntingConversions.getConversionType(EntityType.STRAY));
        assertEquals(EntityType.WITHER_SKELETON, MobHauntingConversions.getConversionType(EntityType.BOGGED));
        assertEquals(EntityType.MAGMA_CUBE, MobHauntingConversions.getConversionType(EntityType.SLIME));
        assertEquals(EntityType.ZOMBIFIED_PIGLIN, MobHauntingConversions.getConversionType(EntityType.ZOMBIE));
        assertEquals(EntityType.PIGLIN_BRUTE, MobHauntingConversions.getConversionType(EntityType.HUSK));
        assertEquals(EntityType.ZOMBIFIED_PIGLIN, MobHauntingConversions.getConversionType(EntityType.DROWNED));
        assertEquals(EntityType.HOGLIN, MobHauntingConversions.getConversionType(EntityType.PIG));
        assertEquals(EntityType.ENDERMITE, MobHauntingConversions.getConversionType(EntityType.SILVERFISH));
        assertEquals(EntityType.SKELETON_HORSE, MobHauntingConversions.getConversionType(EntityType.HORSE));
        assertEquals(EntityType.PIGLIN, MobHauntingConversions.getConversionType(EntityType.VILLAGER));
        assertEquals(EntityType.ENDERMAN, MobHauntingConversions.getConversionType(EntityType.PIGLIN));
    }

    @Test
    void includesTheBundledServerConfigDefaults(){
        assertNotNull(MobHauntingConversionsTest.class.getResourceAsStream(
                "/createthrusters/default-mob-haunting.json"));
    }
}
