package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.neoforge.client.PlayerMannequinRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// World entities must enter the dispatcher already identified as players, including for shader hooks.
@Mixin(LevelRenderer.class)
public abstract class PlayerMannequinLevelRenderMixin {
    @ModifyVariable(
            method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Entity ct$renderMannequinAsPlayer(Entity entity) {
        return entity instanceof PlayerMannequinEntity mannequin
                ? PlayerMannequinRenderer.renderPlayerFor(mannequin)
                : entity;
    }
}
