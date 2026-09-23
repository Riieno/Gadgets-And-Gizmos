package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;

// Provide Create item-vault connectivity for the higher-capacity Smart Vault
public class SmartVaultBlock extends ItemVaultBlock {
    // Initialize the smart vault block
    public SmartVaultBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // Inherit the axis from an adjacent custom vault when not placing precisely
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getPlayer() == null || !ctx.getPlayer().isShiftKeyDown()) {
            BlockState adjacent = ctx.getLevel().getBlockState(
                    ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite()));
            if (adjacent.getBlock() == this && adjacent.hasProperty(HORIZONTAL_AXIS)) {
                return defaultBlockState().setValue(HORIZONTAL_AXIS, adjacent.getValue(HORIZONTAL_AXIS));
            }
        }
        return defaultBlockState().setValue(HORIZONTAL_AXIS, ctx.getHorizontalDirection().getAxis());
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ItemVaultBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SMART_VAULT.get();
    }

    // Get the block entity class
    @Override
    public Class<ItemVaultBlockEntity> getBlockEntityClass() {
        return ItemVaultBlockEntity.class;
    }
}
