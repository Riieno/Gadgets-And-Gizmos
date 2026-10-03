package com.rieno.gadgetsandgizmos.content.tablet;

import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockEntity;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAccessRegistry;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationRuntime;
import com.rieno.gadgetsandgizmos.config.TabletAppsServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Apply shipping-manifest insert rules and server transfer limits
final class ManifestContainerPolicies implements ContainerAccessRegistry.Provider{
    static void register(){
        ContainerAccessRegistry.register(new ManifestContainerPolicies());
        ContainerAutomationRuntime.setTransferLimit(() -> TabletAppsServerConfig.TRANSFER.get());
    }

    @Override public boolean canOpen(ServerPlayer player, Level level, BlockPos pos){
        return true;
    }

    @Override public boolean canInsert(Level level, BlockPos pos, ItemStack stack){
        return ShippingManifestBlockEntity.allowsAttachedContainerItem(level, pos, stack);
    }

    @Override public boolean canExtract(Level level, BlockPos pos, ItemStack stack){
        return true;
    }
}
