package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.client.PortableContraptionControllerItemRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Pose the Portable Contraption Controller player
@Mixin(HumanoidModel.class)
public abstract class PortableContraptionControllerPlayerPoseMixin<T extends LivingEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the pose portable controller arms
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void ct$posePortableControllerArms(T entity, float limbSwing, float limbSwingAmount,
                                               float ageInTicks, float netHeadYaw, float headPitch,
                                               CallbackInfo ci) {
        if (!PortableContraptionControllerItemRenderer.isThirdPersonPoseActive(entity)) {
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        float crouchOffset = entity.isCrouching() ? 0.35F : 0.0F;
        model.rightArm.xRot = -1.15F + crouchOffset;
        model.leftArm.xRot = -1.15F + crouchOffset;
        model.rightArm.yRot = -0.1F;
        model.leftArm.yRot = 0.1F;
        model.rightArm.zRot = 0.0F;
        model.leftArm.zRot = 0.0F;
    }
}
