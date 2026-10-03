package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.neoforge.client.PlayerMannequinRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Let mannequins enter the same single dispatcher pass as other client players.
@Mixin(value = EntityRenderDispatcher.class, priority = 1500)
public abstract class PlayerMannequinDispatchMixin {
    @ModifyVariable(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
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
