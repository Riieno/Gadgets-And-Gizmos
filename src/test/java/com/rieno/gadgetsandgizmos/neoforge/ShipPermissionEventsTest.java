package com.rieno.gadgetsandgizmos.neoforge;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Verify the permission event leaves world wrench use intact and gates claimed ships
class ShipPermissionEventsTest {
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
    void wrenchWorksOnUnclaimedWorldAndSubLevelBlocks(){
        PlayerInteractEvent.RightClickBlock evt = mock(PlayerInteractEvent.RightClickBlock.class);
        ServerPlayer player = mock(ServerPlayer.class);
        Level level = mock(Level.class);
        BlockPos pos = BlockPos.ZERO;
        when(evt.getEntity()).thenReturn(player);
        when(evt.getLevel()).thenReturn(level);
        when(evt.getPos()).thenReturn(pos);

        UUID subLevelId = UUID.randomUUID();
        ShipPermissionManager manager = mock(ShipPermissionManager.class);
        try(MockedStatic<SableLevelApi> subLevels = mockStatic(SableLevelApi.class);
            MockedStatic<ShipPermissionManager> managers = mockStatic(ShipPermissionManager.class)){
            subLevels.when(() -> SableLevelApi.containingId(level, pos)).thenReturn(null);
            managers.when(() -> ShipPermissionManager.get(null)).thenReturn(manager);
            ShipPermissionEvents.rightClick(evt);
            subLevels.when(() -> SableLevelApi.containingId(level, pos)).thenReturn(subLevelId);
            ShipPermissionEvents.rightClick(evt);
        }

        verify(evt, never()).setUseItem(any());
        verify(evt, never()).setUseBlock(any());
        verify(evt, never()).setCanceled(true);
    }

    @Test
    void claimedShipWrenchNeedsInteractionOrDestroy(){
        PlayerInteractEvent.RightClickBlock evt = mock(PlayerInteractEvent.RightClickBlock.class);
        ServerPlayer player = mock(ServerPlayer.class);
        Level level = mock(Level.class);
        ItemStack stack = mock(ItemStack.class);
        ShipPermissionManager manager = mock(ShipPermissionManager.class);
        UUID shipId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        BlockPos pos = BlockPos.ZERO;
        when(evt.getEntity()).thenReturn(player);
        when(evt.getLevel()).thenReturn(level);
        when(evt.getPos()).thenReturn(pos);
        when(evt.getItemStack()).thenReturn(stack);
        when(stack.getItem()).thenReturn(mock(WrenchItem.class));
        when(player.getUUID()).thenReturn(playerId);
        when(manager.isClaimed(shipId)).thenReturn(true);
        when(manager.allows(shipId, playerId, ShipPermission.INTERACT)).thenReturn(true);

        try(MockedStatic<SableLevelApi> subLevels = mockStatic(SableLevelApi.class);
            MockedStatic<ShipPermissionManager> managers = mockStatic(ShipPermissionManager.class)){
            subLevels.when(() -> SableLevelApi.containingId(level, pos)).thenReturn(shipId);
            managers.when(() -> ShipPermissionManager.get(null)).thenReturn(manager);
            ShipPermissionEvents.rightClick(evt);
            verify(evt, never()).setUseBlock(any());
            verify(evt, never()).setUseItem(any());

            when(player.isShiftKeyDown()).thenReturn(true);
            ShipPermissionEvents.rightClick(evt);
            verify(evt).setCancellationResult(InteractionResult.FAIL);
            verify(evt).setCanceled(true);
        }
    }
}
