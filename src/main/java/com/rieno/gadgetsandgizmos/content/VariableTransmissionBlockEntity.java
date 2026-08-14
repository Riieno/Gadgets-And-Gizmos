package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Apply the current redstone ratio to one side of a split shaft
public class VariableTransmissionBlockEntity extends SplitShaftBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the variable transmission
    public VariableTransmissionBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.VARIABLE_TRANSMISSION.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the rotation speed modifier
    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (!hasSource()) {
            return 1.0f;
        }

        Direction sourceFacing = getSourceFacing();
        if (sourceFacing == null || face.getAxis() != sourceFacing.getAxis()) {
            return 0.0f;
        }

        if (face == sourceFacing) {
            return 1.0f;
        }

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof VariableTransmissionBlock)) {
            return 1.0f;
        }

        int signal = Mth.clamp(state.getValue(VariableTransmissionBlock.POWER), 0, 15);
        return signal / 15.0f;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the variable transmission
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof VariableTransmissionBlock block) || !hasAdjacentCreateRedstoneLink()) {
            return;
        }
        int livePower = VariableTransmissionBlock.readNeighborPower(level, worldPosition);
        if (state.getValue(VariableTransmissionBlock.POWER) != livePower) {
            block.applyPower(level, worldPosition, state, livePower);
        }
    }

    // Check if this has adjacent create redstone link
    private boolean hasAdjacentCreateRedstoneLink() {
        if (level == null) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            BlockPos neighbourPos = worldPosition.relative(dir);
            BlockState neighbourState = level.getBlockState(neighbourPos);
            if (!(neighbourState.getBlock() instanceof RedstoneLinkBlock)) {
                continue;
            }
            if (!neighbourState.getValue(RedstoneLinkBlock.RECEIVER)
                    || neighbourState.getValue(RedstoneLinkBlock.FACING) != dir.getOpposite()) {
                continue;
            }
            BlockEntity neighbour = level.getBlockEntity(neighbourPos);
            if (neighbour instanceof RedstoneLinkBlockEntity) {
                return true;
            }
        }
        return false;
    }
}
