package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

// Anchor and tick the kinetic rope assembly used by an Entity Launcher
public class EntityLauncherAnchorBlock extends DirectionalKineticBlock
        implements IBE<EntityLauncherAnchorBlockEntity>, ICogWheel, IWrenchable,
        RopeHolderBlock<EntityLauncherAnchorBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher anchor block
    public EntityLauncherAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle entity launcher anchor block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        EntityLauncherAnchorBlockEntity anchor = getAnchor(level, pos);
        if (!level.isClientSide() && anchor != null) {
            anchor.tryMount(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // Handle entity launcher anchor block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide() && stack.is(SimTags.Items.DESTROYS_ROPE)) {
            ItemInteractionResult res = RopeHolderBlock.shearRope(this, level, pos, (ServerPlayer) player);
            EntityLauncherAnchorBlockEntity anchor = getAnchor(level, pos);
            if (res.consumesAction() && anchor != null) {
                anchor.releaseAllTargets();
            }
            return res;
        }
        EntityLauncherAnchorBlockEntity anchor = getAnchor(level, pos);
        if (!level.isClientSide() && anchor != null) {
            anchor.tryMount(player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the block entity class
    @Override
    public Class<EntityLauncherAnchorBlockEntity> getBlockEntityClass() {
        return EntityLauncherAnchorBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends EntityLauncherAnchorBlockEntity> getBlockEntityType() {
        return CTBlockEntities.ENTITY_LAUNCHER_ANCHOR.get();
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && state.getBlock() != newState.getBlock()) {
            EntityLauncherAnchorBlockEntity anchor = getAnchor(level, pos);
            if (anchor != null) {
                anchor.onDestroyed();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Get the anchor
    @Nullable
    private static EntityLauncherAnchorBlockEntity getAnchor(Level level, BlockPos pos) {
        return SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos, EntityLauncherAnchorBlockEntity.class);
    }

    // Get the ticker
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (!level.isClientSide() && blockEntityType == CTBlockEntities.ENTITY_LAUNCHER_ANCHOR.get()) {
            return (tickerLevel, pos, tickerState, blockEntity) ->
                    EntityLauncherAnchorBlockEntity.tick(tickerLevel, pos, tickerState,
                            (EntityLauncherAnchorBlockEntity) blockEntity);
        }
        return null;
    }

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Check if this is a small cog
    @Override
    public boolean isSmallCog() {
        return true;
    }

    // Check if this is a large cog
    @Override
    public boolean isLargeCog() {
        return false;
    }

    // Check if this is a dedicated cog wheel
    @Override
    public boolean isDedicatedCogWheel() {
        return true;
    }

    // Get the render shape
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // Get the clone item stack
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return CTItems.ENTITY_LAUNCHER == null ? ItemStack.EMPTY : new ItemStack(CTItems.ENTITY_LAUNCHER.get());
    }
}
