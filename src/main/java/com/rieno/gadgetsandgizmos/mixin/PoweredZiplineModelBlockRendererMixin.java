package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ZiplineRidingController;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Draw Powered Zipline models
@Mixin(ModelBlockRenderer.class)
public abstract class PoweredZiplineModelBlockRendererMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation ROPE_CONNECTOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("simulated", "block/rope_winch/winch");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Hide the zipline connector with ao
    @Redirect(
            method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/BakedModel;getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;"))
    private List<BakedQuad> createthrusters$hideZiplineConnectorWithAO(BakedModel model, @Nullable BlockState state,
            @Nullable Direction direction, RandomSource random, ModelData modelData, @Nullable RenderType renderType) {
        return createthrusters$filterConnectorQuads(state, model.getQuads(state, direction, random, modelData, renderType));
    }

    // Hide the zipline connector without ao
    @Redirect(
            method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/BakedModel;getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;"))
    private List<BakedQuad> createthrusters$hideZiplineConnectorWithoutAO(BakedModel model, @Nullable BlockState state,
            @Nullable Direction direction, RandomSource random, ModelData modelData, @Nullable RenderType renderType) {
        return createthrusters$filterConnectorQuads(state, model.getQuads(state, direction, random, modelData, renderType));
    }

    // Filter the connector quads
    private static List<BakedQuad> createthrusters$filterConnectorQuads(@Nullable BlockState state, List<BakedQuad> quads) {
        if (state == null || !state.is(CTBlocks.POWERED_ZIPLINE.get()) || quads.isEmpty()) {
            return quads;
        }

        ZiplineRidingController controller = ZiplineRidingController.getInstance();
        if (controller == null || !controller.shouldCullFirstPersonConnector()) {
            return quads;
        }

        List<BakedQuad> filtered = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            if (!ROPE_CONNECTOR_TEXTURE.equals(quad.getSprite().contents().name())) {
                filtered.add(quad);
            }
        }
        return filtered;
    }
}
