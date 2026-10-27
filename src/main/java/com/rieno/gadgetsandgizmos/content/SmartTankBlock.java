package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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

// Provide Create fluid-tank connectivity for the double-capacity Smart Tank
public class SmartTankBlock extends FluidTankBlock {
    // Initialize the smart tank block
    public SmartTankBlock(BlockBehaviour.Properties properties) {
        super(properties, false);
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
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext ctx){
        if(ctx == CollisionContext.empty()) return super.getCollisionShape(state, level, pos, ctx);
        return super.getShape(state, level, pos, ctx);
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SMART_TANK.get();
    }

    // Get the block entity class
    @Override
    public Class<FluidTankBlockEntity> getBlockEntityClass() {
        return FluidTankBlockEntity.class;
    }
}
