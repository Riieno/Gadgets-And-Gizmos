package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.LecternPortableContraptionController;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Read comparator output from a portable controller lectern
@Mixin(ComparatorBlock.class)
public class ComparatorBlockPortableLecternMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the portable lectern side signal
    @WrapOperation(method = "getInputSignal",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getAnalogOutputSignal(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I"))
    private int createthrusters$getPortableLecternSideSignal(BlockState instance, Level level, BlockPos pos,
                                                             Operation<Integer> original,
                                                             @Local(ordinal = 0) Direction dir) {
        int signal = LecternPortableContraptionController.getComparatorOutput(level, pos, dir);
        if (signal >= 0) {
            return signal;
        }
        return original.call(instance, level, pos);
    }
}
