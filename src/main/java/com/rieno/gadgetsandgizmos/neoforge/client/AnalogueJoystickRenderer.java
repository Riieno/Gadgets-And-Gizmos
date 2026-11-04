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
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
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
        double renderLocalX = be.getVisualLocalX(partialTicks);
        double renderLocalZ = be.getVisualLocalZ(partialTicks);
        AttachFace face = state.getValue(AnalogueJoystickBlock.FACE);
        if (face == AttachFace.CEILING) {
            renderLocalX = -renderLocalX;
            renderLocalZ = -renderLocalZ;
        }

        renderPartial(CTPartialModels.ANALOGUE_JOYSTICK_STICK, 1.0f, state, renderLocalX, renderLocalZ,
                be.getMaxTiltDegrees(), ms, vertexConsumer, light);
        renderPartial(CTPartialModels.ANALOGUE_JOYSTICK_SKIRT, 0.5f, state, renderLocalX, renderLocalZ,
                be.getMaxTiltDegrees(), ms, vertexConsumer, light);
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

    // Draw one moving joystick partial
    private static void renderPartial(PartialModel model, float angleScale, BlockState state,
                                      double renderLocalX, double renderLocalZ, float maxTiltDegrees,
                                      PoseStack ms, VertexConsumer vertexConsumer, int light) {
        SuperByteBuffer partial = CachedBuffers.partial(model, state);
        ms.pushPose();
        applyFacing(ms, state);
        ms.translate(0.5D, 0.125D, 0.5D);
        ms.mulPose(Axis.ZP.rotationDegrees((float) (renderLocalX * maxTiltDegrees * angleScale)));
        ms.mulPose(Axis.XP.rotationDegrees((float) (-renderLocalZ * maxTiltDegrees * angleScale)));
        ms.translate(-0.5D, -0.125D, -0.5D);
        partial.light(light).renderInto(ms, vertexConsumer);
        ms.popPose();
    }
}
