package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.ScissorPistonArmBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Draw the Scissor Piston Arm
public class ScissorPistonArmRenderer extends SafeBlockEntityRenderer<ScissorPistonArmBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float PIXEL = 1.0F / 16.0F;
    private static final float HEADWARD_OFFSET = 4.0F / 16.0F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston arm
    public ScissorPistonArmRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the scissor piston arm
    @Override
    protected void renderSafe(ScissorPistonArmBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        int links = be.getArmLinkCount();
        float extension = Math.max(0.0F, (float) be.getInterpolatedExtension(partialTicks));
        if (extension <= 0.001F) {
            return;
        }

        float sectionLength = Mth.clamp(extension / links, 0.0F, 1.0F);
        float sideSpan = (float) Math.sqrt(Math.max(0.0F, 1.0F - sectionLength * sectionLength));
        float scale = Mth.lerp(sectionLength, 0.9F, 1.0F);
        int cellDistance = be.getCellDistanceFromHead();
        Quaternionf baseRotation = rotationFromUp(be.getPistonFacing());
        BlockState state = be.getBlockState();

        for (int link = 0; link < links; link++) {
            float distanceFromHead = extension - (link + 0.5F) * sectionLength;
            if (cellForDistance(distanceFromHead) != cellDistance) {
                continue;
            }
            float centerOffset = cellDistance - distanceFromHead;
            renderArm(ms, buffer, state, light, baseRotation, centerOffset, sectionLength, sideSpan, scale, false);
            renderArm(ms, buffer, state, light, baseRotation, centerOffset, sectionLength, sideSpan, scale, true);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the cell for distance
    private static int cellForDistance(float distanceFromHead) {
        return Math.max(1, Math.round(distanceFromHead));
    }

    // Draw the arm
    private static void renderArm(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                                  Quaternionf baseRotation, float centerOffset, float sectionLength,
                                  float sideSpan, float scale, boolean offsetWest) {
        float side = offsetWest ? -sideSpan : sideSpan;
        Vector3f armDirection = new Vector3f(side, sectionLength, 0.0F);
        if (armDirection.lengthSquared() < 1.0E-6F) {
            armDirection.set(0.0F, 1.0F, 0.0F);
        } else {
            armDirection.normalize();
        }

        SuperByteBuffer arm = CachedBuffers.partial(CTPartialModels.SCISSOR_PISTON_ARM, state);
        ms.pushPose();
        ms.translate(0.5D, 0.5D, 0.5D);
        ms.mulPose(new Quaternionf(baseRotation));
        ms.translate(offsetWest ? -PIXEL : 0.0F, centerOffset + HEADWARD_OFFSET, 0.0F);
        ms.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), armDirection));
        ms.mulPose(new Quaternionf().rotationY((float) Math.PI / 2.0F));
        ms.scale(scale, scale, scale);
        ms.translate(-0.5D, -0.5D, -0.5D);
        arm.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

    // Get the rotation from up
    private static Quaternionf rotationFromUp(Direction dir) {
        return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), directionVector(dir));
    }

    // Get the direction vector
    private static Vector3f directionVector(Direction dir) {
        return new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }
}
