package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.neoforge.client.PlayerMannequinPose;
import com.rieno.gadgetsandgizmos.neoforge.client.PlayerMannequinRenderer.MannequinRenderPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep arbitrary saved mannequin poses while using the unmodified vanilla player model geometry.
@Mixin(PlayerModel.class)
public abstract class PlayerMannequinPlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void ct$poseMannequin(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof MannequinRenderPlayer proxy) {
            PlayerMannequinPose.apply((PlayerModel<?>) (Object) this, proxy.mannequin(), ageInTicks);
        }
    }
}
