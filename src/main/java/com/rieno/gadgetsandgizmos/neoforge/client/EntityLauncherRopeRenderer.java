package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

// Draw the Entity Launcher Rope
public final class EntityLauncherRopeRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher rope
    private EntityLauncherRopeRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the entity launcher rope
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 fromLocal, Vec3 toLocal, int packedLight) {
        renderPolyline(poseStack, bufferSource, List.of(fromLocal, toLocal), packedLight);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the polyline
    public static void renderPolyline(PoseStack poseStack, MultiBufferSource bufferSource, List<Vec3> points, int packedLight) {
        if (points.size() < 2) {
            return;
        }

        SuperByteBuffer middle = CachedBuffers.partialFacing(SimPartialModels.ROPE, Blocks.AIR.defaultBlockState(), Direction.NORTH);
        SuperByteBuffer knot = CachedBuffers.partialFacing(SimPartialModels.ROPE_KNOT, Blocks.AIR.defaultBlockState(), Direction.NORTH);
        VertexConsumer solid = bufferSource.getBuffer(RenderType.solid());

        Vec3 prev = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Vec3 next = points.get(i);
            Vec3 piece = next.subtract(prev);
            double pieceLength = piece.length();
            if (pieceLength < 1.0E-3) {
                prev = next;
                continue;
            }

            Quaternionf orientation = rotationFromUp(piece.normalize());
            poseStack.pushPose();
            poseStack.translate(prev.x, prev.y, prev.z);
            poseStack.mulPose(orientation);
            poseStack.translate(-0.5, -0.5, -0.5);

            if (i > 1) {
                knot.light(packedLight).renderInto(poseStack, solid);
            }

            poseStack.translate(0.0, 0.5, 0.0);
            poseStack.scale(1.0f, (float) pieceLength, 1.0f);
            middle.light(packedLight).renderInto(poseStack, solid);
            poseStack.popPose();

            prev = next;
        }
    }

    // Get the rotation from up
    private static Quaternionf rotationFromUp(Vec3 dir) {
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f target = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (target.lengthSquared() < 1.0E-6f) {
            return new Quaternionf();
        }
        target.normalize();
        Quaternionf quaternion = new Quaternionf().rotateTo(up, target);
        if (Float.isNaN(quaternion.x) || Float.isNaN(quaternion.y) || Float.isNaN(quaternion.z) || Float.isNaN(quaternion.w)) {
            return new Quaternionf();
        }
        return quaternion;
    }
}
