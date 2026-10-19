package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

// Reuse Create vault geometry and connectivity for a large FE multiblock
public class SmartBatteryBlock extends SmartVaultBlock {
    // Initialize the smart battery block
    public SmartBatteryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ItemVaultBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SMART_BATTERY.get();
    }

    // Get the block entity class
    @Override
    public Class<ItemVaultBlockEntity> getBlockEntityClass() {
        return ItemVaultBlockEntity.class;
    }
}
