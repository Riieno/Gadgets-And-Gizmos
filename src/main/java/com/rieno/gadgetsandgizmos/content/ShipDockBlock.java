package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.neoforge.network.ShipDockOpenPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

// Open and tick the docking point used by ships and shipping schedules
public class ShipDockBlock extends HorizontalDirectionalBlock implements IBE<ShipDockBlockEntity>, IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<ShipDockBlock> CODEC = simpleCodec(ShipDockBlock::new);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship dock block
    public ShipDockBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the codec
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // Get the state for placement
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle ship dock block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ShipDockBlockEntity dock) {
            PacketDistributor.sendToPlayer(serverPlayer, new ShipDockOpenPayload(
                    pos, SimulatedHelper.getContainingSubLevelId(dock),
                    dock.getDockName(), dock.canRefuel(), dock.canRestock(),
                    dock.canHandlePackages(), dock.isDoorControlEnabled(), dock.getDoorControlMask(),
                    dock.linkedConnectorReferences(), dock.refuelConnectorReferences(),
                    dock.restockConnectorReferences(), dock.packageConnectorReferences()));
        }
        return InteractionResult.CONSUME;
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!movedByPiston && state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof ShipDockBlockEntity dock) {
            if (!level.isClientSide) {
                dock.clearLinkedConnectorBindings();
                for (ItemStack stack : dock.bufferedItems()) {
                    net.minecraft.world.Containers.dropItemStack(
                            level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
            if (level.getServer() != null) {
                ShipDockRegistry.get(level.getServer()).remove(dock.getDockId());
            }
        }
        IBE.onRemove(state, level, pos, newState);
    }

    // Handle block placement
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ShipDockBlockEntity dock) {
            dock.applyLinkedConnectors(stack);
        }
    }

    // Get the block entity class
    @Override
    public Class<ShipDockBlockEntity> getBlockEntityClass() {
        return ShipDockBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ShipDockBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SHIP_DOCK.get();
    }

    // Create the block entity
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShipDockBlockEntity(pos, state);
    }
}
