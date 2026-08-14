package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlock;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Ignore glue between a gantry shaft and its attached carriage
@Mixin(SimAssemblyContraption.class)
public class SimAssemblyContraptionPhysicsGantryGlueMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the ignore glue between moved shaft and attached carriage
    @Inject(method = "checkAndCacheGlue", at = @At("HEAD"), cancellable = true)
    private void createthrusters$ignoreGlueBetweenMovedShaftAndAttachedCarriage(LevelAccessor levelAccessor,
                                                                                BlockPos blockPos,
                                                                                BlockPos offsetDir,
                                                                                CallbackInfoReturnable<Boolean> cir) {
        if (!(levelAccessor instanceof Level level) || blockPos == null || offsetDir == null) {
            return;
        }

        BlockPos targetPos = blockPos.offset(offsetDir);
        if (isAttachedPhysicsGantryShaftCarriagePair(level, blockPos, targetPos)
                || isAttachedPhysicsGantryShaftCarriagePair(level, targetPos, blockPos)) {
            cir.setReturnValue(false);
        }
    }

    // Check if this is attached physics gantry shaft carriage pair
    private static boolean isAttachedPhysicsGantryShaftCarriagePair(Level level, BlockPos shaftPos, BlockPos carriagePos) {
        BlockState shaftState = level.getBlockState(shaftPos);
        if (shaftState.getBlock() != com.rieno.gadgetsandgizmos.registry.CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return false;
        }

        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, carriagePos);
        if (!(blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage) || !carriage.isAssembledToSubLevel()) {
            return false;
        }

        return carriage.isAttachedToShaftBlock(shaftPos, shaftState.getValue(PhysicsGantryShaftBlock.FACING));
    }
}
