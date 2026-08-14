package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep controller placement compatible with Bits N Bobs cogwheel chains
@Pseudo
@Mixin(targets = "com.kipti.bnb.content.kinetics.cogwheel_chain.placement.CogwheelChainPlacementInteraction", remap = false)
public abstract class BitsNBobsCogwheelChainPlacementInteractionMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the allow gantry belt wheel use
    @Inject(method = "onRightClick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ct$allowGantryBeltWheelUse(InputEvent.InteractionKeyMappingTriggered event,
                                                   CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        if (!isHoldingBeltConnector(player.getMainHandItem()) && !isHoldingBeltConnector(player.getOffhandItem())) {
            return;
        }

        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult)
                || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockState targetedState = level.getBlockState(blockHitResult.getBlockPos());
        if (targetedState.getBlock() instanceof PhysicsGantryBeltWheelBlock) {
            cir.setReturnValue(false);
        }
    }

    // Check if the player is holding a belt connector
    private static boolean isHoldingBeltConnector(ItemStack stack) {
        return stack.getItem() instanceof BeltConnectorItem;
    }
}
