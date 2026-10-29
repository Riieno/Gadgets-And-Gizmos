package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Provide Create item-vault connectivity for the higher-capacity Smart Vault
public class SmartVaultBlock extends ItemVaultBlock {
    // Initialize the smart vault block
    public SmartVaultBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext ctx){
        return SmartContainerTablets.wrench(ctx);
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit){
        if(SmartContainerTablets.interact(level, pos, player, hit)) return InteractionResult.sidedSuccess(level.isClientSide);
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit){
        if(SmartContainerTablets.interact(level, pos, player, hand, hit)) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext ctx){
        return SmartContainerTablets.selectionShape(level, pos, super.getShape(state, level, pos, ctx));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext ctx){
        return super.getShape(state, level, pos, ctx);
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
