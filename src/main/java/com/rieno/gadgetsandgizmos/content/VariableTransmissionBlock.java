package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

// Read neighboring redstone power to set an encased shaft speed ratio
public class VariableTransmissionBlock extends AbstractEncasedShaftBlock implements IBE<VariableTransmissionBlockEntity>, IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the variable transmission block
    public VariableTransmissionBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
        super.createBlockStateDefinition(builder);
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return super.getStateForPlacement(ctx)
                .setValue(POWER, ctx.getLevel().getBestNeighborSignal(ctx.getClickedPos()));
    }

    // Handle the neighboring block change
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);

        if (level.isClientSide) {
            return;
        }

        int prev = state.getValue(POWER);
        int updated = readNeighborPower(level, pos);
        if (prev != updated) {
            applyPower(level, pos, state, updated);
        }
    }

    // Read the neighbor power
    static int readNeighborPower(Level level, BlockPos pos) {
        return level == null || pos == null ? 0 : level.getBestNeighborSignal(pos);
    }

    // Apply the power
    void applyPower(Level level, BlockPos pos, BlockState state, int updatedPower) {
        if (level == null || pos == null || state == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(15, updatedPower));
        if (state.getValue(POWER) == clamped) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        KineticBlockEntity kinetic = be instanceof KineticBlockEntity kineticBlockEntity
                ? kineticBlockEntity
                : null;
        if (kinetic != null && !level.isClientSide) {
            kinetic.detachKinetics();
        }
        level.setBlock(pos, state.setValue(POWER, clamped), 2);
        if (kinetic != null && !level.isClientSide && !kinetic.isRemoved()) {
            kinetic.attachKinetics();
            kinetic.setChanged();
            kinetic.sendData();
        }
    }

    // Get the block entity class
    @Override
    public Class<VariableTransmissionBlockEntity> getBlockEntityClass() {
        return VariableTransmissionBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends VariableTransmissionBlockEntity> getBlockEntityType() {
        return CTBlockEntities.VARIABLE_TRANSMISSION.get();
    }

    // Hide the stress impact
    @Override
    public boolean hideStressImpact() {
        return true;
    }
}
