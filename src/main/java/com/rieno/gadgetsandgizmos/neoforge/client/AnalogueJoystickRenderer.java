package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

// Draw the Analogue Joystick
public class AnalogueJoystickRenderer extends SafeBlockEntityRenderer<AnalogueJoystickBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick
    public AnalogueJoystickRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the analogue joystick
    @Override
    protected void renderSafe(AnalogueJoystickBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource bufferSource, int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.cutoutMipped());
        SuperByteBuffer handle = CachedBuffers.partial(CTPartialModels.ANALOGUE_JOYSTICK_HANDLE, state);
        double renderLocalX = be.getVisualLocalX(partialTicks);
        double renderLocalZ = be.getVisualLocalZ(partialTicks);
        AttachFace face = state.getValue(AnalogueJoystickBlock.FACE);
        if (face == AttachFace.CEILING) {
            renderLocalX = -renderLocalX;
            renderLocalZ = -renderLocalZ;
        }

        ms.pushPose();
        applyFacing(ms, state);
        ms.translate(0.5D, 0.3125D, 0.5D);

        ms.mulPose(Axis.ZP.rotationDegrees((float) (renderLocalX * be.getMaxTiltDegrees())));
        ms.mulPose(Axis.XP.rotationDegrees((float) (-renderLocalZ * be.getMaxTiltDegrees())));
        ms.translate(-0.5D, -0.3125D, -0.5D);
        handle.light(light).renderInto(ms, vertexConsumer);
        ms.popPose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the facing
    private static void applyFacing(PoseStack ms, BlockState state) {
        float yRotation = AnalogueJoystickBlock.getYRotationDegrees(state);
        ms.translate(0.5D, 0.0D, 0.5D);
        ms.mulPose(Axis.YP.rotationDegrees(yRotation));
        ms.mulPose(Axis.XP.rotationDegrees(AnalogueJoystickBlock.getXRotationDegrees(state)));
        ms.translate(-0.5D, 0.0D, -0.5D);
    }
}
