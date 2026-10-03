package com.rieno.gadgetsandgizmos.content;

import net.minecraft.SharedConstants;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThrusterBlockTest {
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
    void delegatesUpgradeInsertion(){
        ThrusterBlockEntity thruster = mock(ThrusterBlockEntity.class);
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        ItemStack heldStack = new ItemStack(Items.BONE);
        when(thruster.tryInsertUpgradeItem(player, heldStack)).thenReturn(true);

        ItemInteractionResult result = ThrusterBlock.insertUpgrade(level, thruster, player, heldStack);

        assertEquals(ItemInteractionResult.SUCCESS, result);
        verify(thruster).tryInsertUpgradeItem(player, heldStack);
    }
}
