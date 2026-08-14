package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.FuelOxidizerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Draw the Fuel Oxidizer
public class FuelOxidizerRenderer extends KineticBlockEntityRenderer<FuelOxidizerBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float COG_FORWARD_OFFSET = 1.0F / 16.0F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the fuel oxidizer
    public FuelOxidizerRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the fuel oxidizer
    @Override
    protected void renderSafe(FuelOxidizerBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int light, int overlay) {
        Direction dir = be.getBlockState().getValue(BlockStateProperties.FACING);
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutoutMipped());

        SuperByteBuffer cog = CachedBuffers.partialFacing(CTPartialModels.FUEL_OXIDIZER_COG, be.getBlockState(), dir.getOpposite())
                .translate(dir.getStepX() * COG_FORWARD_OFFSET,
                        dir.getStepY() * COG_FORWARD_OFFSET,
                        dir.getStepZ() * COG_FORWARD_OFFSET);
        float angle = getAngleForBe(be, be.getBlockPos(), dir.getAxis());

        kineticRotationTransform(cog, be, dir.getAxis(), angle, light)
                .renderInto(poseStack, consumer);
    }
}
