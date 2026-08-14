package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.neoforge.client.PortableContraptionControllerItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Draw the Portable Contraption Controller held item
@Mixin(PlayerItemInHandLayer.class)
public abstract class PortableContraptionControllerHeldItemLayerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Hide the items while holding portable controller
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void ct$hideItemsWhileHoldingPortableController(LivingEntity entity, ItemStack stack,
                                                            ItemDisplayContext context, HumanoidArm arm,
                                                            PoseStack poseStack, MultiBufferSource bufferSource,
                                                            int packedLight, CallbackInfo ci) {
        if (PortableContraptionControllerItemRenderer.isThirdPersonPoseActive(entity)) {
            ci.cancel();
        }
    }
}
