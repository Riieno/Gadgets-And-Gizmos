package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.neoforge.client.PlayerMannequinRenderer.MannequinRenderPlayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep the real player geometry and animation, but draw its opaque skin before its alpha-blended outer skin.
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerMannequinLayerRenderMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void ct$solidMannequinBase(LivingEntity entity, boolean bodyVisible, boolean translucent,
                                        boolean glowing, CallbackInfoReturnable<RenderType> result) {
        if (entity instanceof MannequinRenderPlayer player && bodyVisible) {
            result.setReturnValue(RenderType.entitySolid(player.getSkin().texture()));
        }
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    )
    private void ct$renderMannequinLayers(EntityModel<?> model, PoseStack poseStack, VertexConsumer baseBuffer,
                                          int packedLight, int packedOverlay, int color, Operation<Void> original,
                                          LivingEntity entity, float entityYaw, float partialTicks,
                                          PoseStack renderPoseStack, MultiBufferSource buffers, int renderLight) {
        if (!(entity instanceof MannequinRenderPlayer player)
                || !(model instanceof PlayerModel<?> playerModel)
                || entity.isInvisible()) {
            original.call(model, poseStack, baseBuffer, packedLight, packedOverlay, color);
            return;
        }

        ModelPart[] outer = {playerModel.hat, playerModel.jacket, playerModel.leftSleeve,
                playerModel.rightSleeve, playerModel.leftPants, playerModel.rightPants};
        ModelPart[] base = {playerModel.head, playerModel.body, playerModel.leftArm,
                playerModel.rightArm, playerModel.leftLeg, playerModel.rightLeg};

        // The original PlayerModel render retains the vanilla transforms, pose and slim/wide geometry.
        boolean[] outerVisible = ct$visibility(outer);
        try {
            ct$setVisible(outer, false);
            original.call(model, poseStack, baseBuffer, packedLight, packedOverlay, color);
        } finally {
            ct$restoreVisibility(outer, outerVisible);
        }

        boolean[] baseVisible = ct$visibility(base);
        try {
            ct$setVisible(base, false);
            VertexConsumer overlayBuffer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
            original.call(model, poseStack, overlayBuffer, packedLight, packedOverlay, color);
        } finally {
            ct$restoreVisibility(base, baseVisible);
        }
    }

    private static boolean[] ct$visibility(ModelPart[] parts) {
        boolean[] visible = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) visible[i] = parts[i].visible;
        return visible;
    }

    private static void ct$setVisible(ModelPart[] parts, boolean visible) {
        for (ModelPart part : parts) part.visible = visible;
    }

    private static void ct$restoreVisibility(ModelPart[] parts, boolean[] visible) {
        for (int i = 0; i < parts.length; i++) parts[i].visible = visible[i];
    }
}
