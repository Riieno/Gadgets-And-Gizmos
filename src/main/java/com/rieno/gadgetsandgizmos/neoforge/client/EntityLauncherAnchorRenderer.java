package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorBlock;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorAim;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// Draw the Entity Launcher Anchor
public class EntityLauncherAnchorRenderer extends KineticBlockEntityRenderer<EntityLauncherAnchorBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher anchor
    public EntityLauncherAnchorRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the entity launcher anchor
    @Override
    protected void renderSafe(EntityLauncherAnchorBlockEntity be, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }

        BlockState state = be.getBlockState();
        Vec3 targetFrame = toAnchorRenderFrame(be, be.getRenderTargetWorldPosition());
        Vec3 barrelTargetFrame = toAnchorRenderFrame(be, be.getBarrelAimTargetWorldPosition());
        Vec3 pivotFrame = barrelPivotFrame(be.getBlockPos(), state);
        Aim aim = aim(state, pivotFrame, barrelTargetFrame);
        Vec3 dir = aim.direction();
        renderBase(state, dir, poseStack, buffer, light);
        renderBarrel(state, aim.rotation(), poseStack, buffer, light);
        renderCog(be, state, poseStack, buffer, light);

        RopeStrandHolderBehavior ropeHolder = be.getRopeHolder();
        boolean renderedRealRope = false;
        if (ropeHolder != null && ropeHolder.getClientStrand() != null) {
            RopeStrandRenderer.render(be, ropeHolder, partialTicks, poseStack, buffer);
            renderedRealRope = true;
            if (!be.hasAnchorTarget()) {
                return;
            }
        }

        Vec3 localOrigin = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 muzzleFrame = be.getAttachmentLocalPosition();
        if (targetFrame == null) {
            targetFrame = muzzleFrame;
        }
        Vec3 clawRopeEndFrame = targetFrame.subtract(dir.scale(0.44));
        if (!renderedRealRope && !be.hasEntityTarget()) {
            UUID ropeId = stableRopeId(be);
            List<Vec3> ropePath = EntityLauncherRopePhysics
                    .getPath(ropeId, be.getLevel(), muzzleFrame, clawRopeEndFrame, false)
                    .stream()
                    .map(point -> point.subtract(localOrigin))
                    .toList();
            EntityLauncherRopeRenderer.renderPolyline(poseStack, buffer, ropePath, light);
        }

        if (be.hasMountedClaw()) {
            return;
        }

        poseStack.pushPose();

        Vec3 clawLocal = targetFrame.subtract(localOrigin);
        poseStack.translate(clawLocal.x, clawLocal.y, clawLocal.z);
        poseStack.mulPose(aim.rotation());

        poseStack.translate(-0.5, -0.5, -0.5);
        CachedBuffers.partial(CTPartialModels.ENTITY_LAUNCHER_CLAW, Blocks.AIR.defaultBlockState())
                .light(light)
                .renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
        poseStack.popPose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Convert the entity launcher anchor to anchor render frame
    private static Vec3 toAnchorRenderFrame(EntityLauncherAnchorBlockEntity be, Vec3 worldPosition) {
        if (worldPosition == null || be.getLevel() == null) {
            return worldPosition;
        }
        Vec3 projectedWorld = SimulatedHelper.projectOutOfSubLevels(be.getLevel(), worldPosition);
        Object subLevel = SimulatedHelper.getContainingSubLevel(be);
        if (subLevel == null) {
            return projectedWorld;
        }
        Vec3 local = SimulatedHelper.toContainingLocalPosition(subLevel, projectedWorld);
        return local == null ? projectedWorld : local;
    }

    // Get the aim
    private static Aim aim(BlockState state, Vec3 pivotFrame, Vec3 targetFrame) {
        Vec3 targetDirection = null;
        if (targetFrame != null) {
            Vec3 delta = targetFrame.subtract(pivotFrame);
            if (delta.lengthSqr() > 1.0E-6) {
                targetDirection = delta.normalize();
            }
        }

        EntityLauncherAnchorAim.AimFrame frame = EntityLauncherAnchorAim.solve(mountNormal(state), targetDirection);
        return new Aim(frame.direction(),
                rotationFromUpAndForward(toVector(frame.up()), toVector(frame.direction())));
    }

    // Draw the barrel
    private static void renderBarrel(BlockState state, Quaternionf rotation, PoseStack poseStack,
                                     MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        Vec3 pivotLocal = barrelPivotLocal(state);
        poseStack.translate(pivotLocal.x, pivotLocal.y, pivotLocal.z);
        poseStack.mulPose(rotation);
        poseStack.translate(-0.5, -0.5, -0.5);
        CachedBuffers.partial(CTPartialModels.ENTITY_LAUNCHER_CANNON_BARREL, state)
                .light(light)
                .renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
        poseStack.popPose();
    }

    // Draw the base
    private static void renderBase(BlockState state, Vec3 aimDirection, PoseStack poseStack,
                                   MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(mountedRotation(state, aimDirection));
        poseStack.translate(-0.5, -0.5, -0.5);
        CachedBuffers.partial(CTPartialModels.ENTITY_LAUNCHER_BASE, state)
                .light(light)
                .renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
        poseStack.popPose();
    }

    // Draw the cog
    private static void renderCog(EntityLauncherAnchorBlockEntity be, BlockState state, PoseStack poseStack,
                                  MultiBufferSource buffer, int light) {
        Direction facing = state.hasProperty(EntityLauncherAnchorBlock.FACING)
                ? state.getValue(EntityLauncherAnchorBlock.FACING).getOpposite()
                : Direction.SOUTH;
        SuperByteBuffer cog = CachedBuffers.partialFacingVertical(SimPartialModels.SWIVEL_BEARING_COG, state, facing);
        Direction.Axis axis = state.hasProperty(EntityLauncherAnchorBlock.FACING)
                ? state.getValue(EntityLauncherAnchorBlock.FACING).getAxis()
                : Direction.Axis.Z;
        float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis);
        KineticBlockEntityRenderer.kineticRotationTransform(cog, be, axis, angle, light)
                .renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    // Get the barrel pivot frame
    private static Vec3 barrelPivotFrame(net.minecraft.core.BlockPos pos, BlockState state) {
        return Vec3.atLowerCornerOf(pos).add(barrelPivotLocal(state));
    }

    // Get the barrel pivot local
    private static Vec3 barrelPivotLocal(BlockState state) {
        return EntityLauncherAnchorAim.barrelPivotLocal(mountNormal(state));
    }

    // Get the mount normal
    private static Direction mountNormal(BlockState state) {
        Direction facing = state.hasProperty(EntityLauncherAnchorBlock.FACING)
                ? state.getValue(EntityLauncherAnchorBlock.FACING)
                : Direction.NORTH;
        return facing;
    }

    // Get the mounted rotation
    private static Quaternionf mountedRotation(BlockState state, Vec3 aimDirection) {
        Direction normal = mountNormal(state);
        Direction forward = normal.getAxis().isVertical()
                ? horizontalForward(normal, aimDirection)
                : Direction.UP;
        return rotationFromUpAndForward(normal, forward);
    }

    // Get the horizontal forward
    private static Direction horizontalForward(Direction normal, Vec3 aimDirection) {
        if (aimDirection != null) {
            Vec3 projected = new Vec3(aimDirection.x, 0.0D, aimDirection.z);
            if (projected.lengthSqr() > 1.0E-6D) {
                Direction nearest = Direction.getNearest(projected.x, 0.0D, projected.z);
                if (nearest.getAxis().isHorizontal()) {
                    return nearest;
                }
            }
        }
        return normal == Direction.DOWN ? Direction.SOUTH : Direction.NORTH;
    }

    // Get the rotation from up and forward
    private static Quaternionf rotationFromUpAndForward(Direction up, Direction forward) {
        return rotationFromUpAndForward(directionVector(up), directionVector(forward));
    }

    // Get the rotation from up and forward
    private static Quaternionf rotationFromUpAndForward(Vector3f upVector, Vector3f forwardVector) {
        upVector = new Vector3f(upVector).normalize();
        forwardVector = new Vector3f(forwardVector).normalize();
        Quaternionf alignment = new Quaternionf().rotateTo(new Vector3f(0.0f, 1.0f, 0.0f), upVector);
        Vector3f currentForward = new Vector3f(0.0f, 0.0f, -1.0f).rotate(alignment).normalize();
        float dot = Math.max(-1.0f, Math.min(1.0f, currentForward.dot(forwardVector)));
        float angle = (float) Math.acos(dot);
        Vector3f cross = new Vector3f(currentForward).cross(forwardVector);
        if (cross.dot(upVector) < 0.0f) {
            angle = -angle;
        }
        return new Quaternionf().rotateAxis(angle, upVector.x, upVector.y, upVector.z).mul(alignment);
    }

    // Get the direction vector
    private static Vector3f directionVector(Direction dir) {
        return new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ()).normalize();
    }

    // Convert the entity launcher anchor to vector
    private static Vector3f toVector(Vec3 vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    // Get the stable rope id
    private static UUID stableRopeId(EntityLauncherAnchorBlockEntity be) {
        String key = be.getLevel().dimension().location() + ":" + be.getBlockPos().asLong();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    // Get the rotation from negative z
    private static Quaternionf rotationFromNegativeZ(Vec3 dir) {
        Vector3f from = new Vector3f(0.0f, 0.0f, -1.0f);
        Vector3f to = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (to.lengthSquared() < 1.0E-6f) {
            return new Quaternionf();
        }
        Quaternionf quaternion = new Quaternionf().rotateTo(from, to.normalize());
        if (Float.isNaN(quaternion.x) || Float.isNaN(quaternion.y) || Float.isNaN(quaternion.z) || Float.isNaN(quaternion.w)) {
            return new Quaternionf();
        }
        return quaternion;
    }

    // Check if this should render off screen
    @Override
    public boolean shouldRenderOffScreen(EntityLauncherAnchorBlockEntity blockEntity) {
        return true;
    }

    // Get the view distance
    @Override
    public int getViewDistance() {
        return 256;
    }

    // Store the aim
    private record Aim(Vec3 direction, Quaternionf rotation) {
    }
}
