package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.LecternPortableContraptionController;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Handle Lectern Portable Contraption Controller
@Mixin(LecternBlock.class)
public class LecternPortableContraptionControllerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the portable controller item on lectern
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void createthrusters$usePortableControllerItemOnLectern(ItemStack stack, BlockState state, Level level,
                                                                    BlockPos pos, Player player, InteractionHand hand,
                                                                    BlockHitResult hit,
                                                                    CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (LecternPortableContraptionController.isPortableLectern(level, pos)) {
            InteractionResult res = LecternPortableContraptionController.usePortableLectern(
                    state, level, pos, player);
            if (res != InteractionResult.PASS) {
                cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide));
            }
            return;
        }

        if (stack.getItem() instanceof PortableContraptionControllerItem
                && LecternPortableContraptionController.placeOnLectern(stack, state, level, pos, player)) {
            cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide));
        }
    }

    // Handle the portable controller lectern
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void createthrusters$usePortableControllerLectern(BlockState state, Level level, BlockPos pos,
                                                              Player player, BlockHitResult hit,
                                                              CallbackInfoReturnable<InteractionResult> cir) {
        if (!LecternPortableContraptionController.isPortableLectern(level, pos)) {
            return;
        }
        InteractionResult res = LecternPortableContraptionController.usePortableLectern(state, level, pos, player);
        if (res != InteractionResult.PASS) {
            cir.setReturnValue(res);
        }
    }

    // Handle the portable controller lectern analog output
    @Inject(method = "getAnalogOutputSignal", at = @At("HEAD"), cancellable = true)
    private void createthrusters$getPortableControllerLecternAnalogOutput(BlockState state, Level level, BlockPos pos,
                                                                          CallbackInfoReturnable<Integer> cir) {
        int signal = LecternPortableContraptionController.getMaxComparatorOutput(level, pos);
        if (signal >= 0) {
            cir.setReturnValue(signal);
        }
    }

    // Handle the drop portable controller
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void createthrusters$dropPortableController(BlockState state, Level level, BlockPos pos,
                                                        BlockState newState, boolean isMoving, CallbackInfo ci) {
        Block block = state.getBlock();
        if (isMoving || newState.is(block)) {
            return;
        }
        LecternPortableContraptionController.dropController(level, pos);
    }
}
