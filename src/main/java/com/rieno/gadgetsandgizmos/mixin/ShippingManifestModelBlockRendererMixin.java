package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlock;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Draw Shipping Manifest models
@Mixin(ModelBlockRenderer.class)
public abstract class ShippingManifestModelBlockRendererMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Select the shipping manifest model
    @ModifyVariable(
            method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private BakedModel createthrusters$selectShippingManifestModel(BakedModel model, BlockAndTintGetter level,
            BakedModel originalModel, BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, RandomSource random, long seed,
            int overlay, ModelData modelData, RenderType renderType) {
        if (CTConfigs.CLIENT.renderShippingManifestContents.get()
                || !state.is(CTBlocks.SHIPPING_MANIFEST.get())
                || !state.getValue(ShippingManifestBlock.SHOW_BLANK)) {
            return model;
        }
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(
                state.setValue(ShippingManifestBlock.SHOW_BLANK, false));
    }
}
