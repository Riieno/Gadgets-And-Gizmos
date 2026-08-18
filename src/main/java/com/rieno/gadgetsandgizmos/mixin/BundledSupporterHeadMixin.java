package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Render marked player heads from bundled supporter textures
@Mixin(BlockEntityWithoutLevelRenderer.class)
public abstract class BundledSupporterHeadMixin {
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Vanilla skull models rebuilt after resource reloads
    @Shadow
    private Map<SkullBlock.Type, SkullModelBase> skullModels;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           Functions
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Draw a marked item from its packaged supporter texture
    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void createthrusters$renderBundledSupporterHead(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay,
            CallbackInfo ci) {
        ResourceLocation texture = BundledSupporterHeadStack.texture(stack);
        if (texture == null) {
            return;
        }

        SkullModelBase model = skullModels.get(SkullBlock.Types.PLAYER);
        if (model == null) {
            return;
        }
        SkullBlockRenderer.renderSkull(
                null,
                180.0F,
                0.0F,
                poseStack,
                buffer,
                light,
                model,
                RenderType.entityTranslucent(texture));
        ci.cancel();
    }
}
