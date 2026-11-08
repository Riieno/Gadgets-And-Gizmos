package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.ScissorPistonArmBlock;
import com.rieno.gadgetsandgizmos.content.ScissorPistonLinkBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelClientRenderApi;
import com.rieno.gadgetsandgizmos.lib.geometry.ScissorLinkageLayout;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Draw every scissor arm from the moving head's shared attachment frame
public class ScissorPistonArmRenderer extends SafeBlockEntityRenderer<ScissorPistonLinkBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ScissorLinkageLayout.Profile ARM_PROFILE = ScissorPistonArmBlock.ARM_PROFILE;

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
    protected void renderSafe(ScissorPistonLinkBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        var parent = be.getParentPiston();
        var head = SableLevelApi.containing(be);
        if(parent == null || head == null || parent.getMountedSubLevelHandle() != head
                || !be.getBlockPos().equals(parent.getMountedLocalPos())) return;
        Vec3 base = SubLevelClientRenderApi.relativePosition(SableLevelApi.containing(parent),
                parent.getBlockPos().getCenter(), head, partialTicks);
        Vec3 axis = be.getBlockPos().getCenter().subtract(base);
        double extension = axis.length();
        if(!Double.isFinite(extension) || extension <= .001) return;
        var layout = new ScissorLinkageLayout(Math.max(2, parent.getEffectiveMaxRange() + 1), extension);
        float sectionLength = (float)layout.sectionLength();
        float sideSpan = (float)layout.sideSpan();
        float scale = (float)layout.modelScale(ARM_PROFILE);
        Vec3 dir = axis.scale(1 / extension);
        var facing = parent.getPistonFacing();
        Vector3f nominalDir = new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Quaternionf baseRotation = new Quaternionf().rotationTo(nominalDir,
                new Vector3f((float)dir.x, (float)dir.y, (float)dir.z))
                .mul(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), nominalDir));
        BlockState state = be.getBlockState();

        for (int link = 0; link < layout.sectionCount(); link++) {
            float centerOffset = (float)-layout.distanceFromHead(link);
            renderArm(ms, buffer, state, light, baseRotation, centerOffset, sectionLength, sideSpan, scale, false);
            renderArm(ms, buffer, state, light, baseRotation, centerOffset, sectionLength, sideSpan, scale, true);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

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
        ms.translate(offsetWest ? -ARM_PROFILE.lateralOffset() : 0, centerOffset + ARM_PROFILE.headOffset(), 0);
        ms.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), armDirection));
        ms.mulPose(new Quaternionf().rotationY((float) Math.PI / 2.0F));
        ms.scale(scale, scale, scale);
        ms.translate(-0.5D, -0.5D, -0.5D);
        arm.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

}
