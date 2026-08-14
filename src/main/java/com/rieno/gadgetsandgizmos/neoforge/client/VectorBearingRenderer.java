package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingPistonMath;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.OrientationMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Draw the Vector Bearing
public class VectorBearingRenderer extends KineticBlockEntityRenderer<VectorBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vector bearing
    public VectorBearingRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the vector bearing
    @Override
    protected void renderSafe(VectorBearingBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            renderShaft(be, ms, buffer, light);
        }

        Direction facing = be.getBearingFacing();
        Vector3f baseNormal = directionVector(facing);
        boolean mounted = be.isMountedAssemblyPresent();
        Quaternionf tilt = mounted ? mountedTilt(be, partialTicks) : null;
        if (mounted && tilt == null) {
            tilt = appliedTilt(be, partialTicks, facing, baseNormal);
        }
        if (mounted) {
            renderPistons(be, facing, tilt, ms, buffer, light);
            return;
        }

        Quaternionf baseRotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), baseNormal);

        SuperByteBuffer plate = CachedBuffers.partial(CTPartialModels.VECTOR_BEARING_PLATE, be.getBlockState());
        plate.rotateCentered(baseRotation);
        plate.light(light);
        plate.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        renderPistons(be, facing, new Quaternionf(), ms, buffer, light);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the mounted tilt
    private Quaternionf mountedTilt(VectorBearingBlockEntity be, float partialTicks) {
        Level level = be.getLevel();
        if (level == null || be.getMountedSubLevelId() == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel child = container == null ? null : container.getSubLevel(be.getMountedSubLevelId());
        if (child == null || child.isRemoved()) {
            return null;
        }

        Quaterniond childOrientation = interpolatedOrientation(child, partialTicks);
        Object containingSubLevel = SimulatedHelper.getContainingSubLevel(be);
        if (containingSubLevel instanceof SubLevel parent) {
            Quaterniond parentOrientation = interpolatedOrientation(parent, partialTicks);
            childOrientation = parentOrientation.invert().mul(childOrientation).normalize();
        }

        return new Quaternionf((float) childOrientation.x, (float) childOrientation.y,
                (float) childOrientation.z, (float) childOrientation.w).normalize();
    }

    // Get the applied tilt
    private Quaternionf appliedTilt(VectorBearingBlockEntity be, float partialTicks, Direction facing,
                                    Vector3f baseNormal) {
        Vec3 dir = OrientationMath.directionFromAngles(
                Math.toRadians(be.getInterpolatedAppliedXDegrees(partialTicks)),
                Math.toRadians(be.getInterpolatedAppliedZDegrees(partialTicks)));
        Vector3f localTarget = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (localTarget.lengthSquared() < 1.0E-6F) {
            localTarget.set(0.0F, 1.0F, 0.0F);
        } else {
            localTarget.normalize();
        }

        Quaternionf baseRotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), baseNormal);
        Vector3f target = baseRotation.transform(new Vector3f(localTarget)).normalize();
        return new Quaternionf().rotationTo(new Vector3f(baseNormal), target);
    }

    // Get the interpolated orientation
    private Quaterniond interpolatedOrientation(SubLevel subLevel, float partialTicks) {
        if (subLevel instanceof ClientSubLevel clientSubLevel) {
            return new Quaterniond(clientSubLevel.renderPose(partialTicks).orientation()).normalize();
        }
        return new Quaterniond(subLevel.lastPose().orientation())
                .slerp(new Quaterniond(subLevel.logicalPose().orientation()), partialTicks)
                .normalize();
    }

    // Draw the pistons
    private void renderPistons(VectorBearingBlockEntity be, Direction facing, Quaternionf tilt, PoseStack ms,
                               MultiBufferSource buffer, int light) {
        BlockState state = be.getBlockState();
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vector3f transformedNormal = tilt.transform(new Vector3f(
                (float) normal.x, (float) normal.y, (float) normal.z));
        Vec3 tiltedNormal = new Vec3(transformedNormal.x(), transformedNormal.y(), transformedNormal.z());
        double normalDot = normal.dot(tiltedNormal);
        if (Math.abs(normalDot) < 1.0E-6D) {
            return;
        }

        for (int i = 0; i < 4; i++) {
            SuperByteBuffer head = CachedBuffers.partial(CTPartialModels.VECTOR_BEARING_PISTON_HEAD, state);
            SuperByteBuffer pole = CachedBuffers.partial(CTPartialModels.VECTOR_BEARING_PISTON_POLE, state);
            Vec3 translatedPos = VecHelper.rotate(new Vec3(0.36875D, 0.0D, 0.0D), -90.0D * i,
                    Direction.Axis.Y);

            if (facing.getAxis().isHorizontal()) {
                translatedPos = VecHelper.rotate(translatedPos, AngleHelper.horizontalAngle(facing), Direction.Axis.Z);
                translatedPos = VecHelper.rotate(translatedPos, -90.0D + AngleHelper.verticalAngle(facing),
                        Direction.Axis.X);
            }

            double extension = VectorBearingPistonMath.extension(
                    translatedPos.dot(tiltedNormal), normalDot);
            translatedPos = translatedPos.add(normal.scale(extension));
            head.translate(translatedPos);
            head.translate(0.5F, 0.5F, 0.5F);
            pole.translate(translatedPos);
            pole.translate(0.5F, 0.5F, 0.5F);
            int side = i;
            if (facing == Direction.DOWN) {
                if (i % 2 == 0) {
                    head.rotate(AngleHelper.rad(180.0D), Direction.EAST);
                    pole.rotate(AngleHelper.rad(180.0D), Direction.EAST);
                } else {
                    head.rotate(AngleHelper.rad(180.0D), Direction.SOUTH);
                    pole.rotate(AngleHelper.rad(180.0D), Direction.SOUTH);
                }
            }

            if (facing.getAxis().isHorizontal()) {
                head.rotate(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
                pole.rotate(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
                head.rotate(AngleHelper.rad(-90.0D + AngleHelper.verticalAngle(facing)), Direction.EAST);
                pole.rotate(AngleHelper.rad(-90.0D + AngleHelper.verticalAngle(facing)), Direction.EAST);
                side = 2 - side;
            }

            pole.translate(0.0D, 0.03125D, 0.0D);
            head.rotate(AngleHelper.rad(-90.0D * side), Direction.UP);
            pole.rotate(AngleHelper.rad(-90.0D * side), Direction.UP);
            head.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
            pole.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    // Draw the shaft
    private void renderShaft(VectorBearingBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        Direction shaftDirection = be.getBearingFacing().getOpposite();
        SuperByteBuffer shaft = CachedBuffers.partialFacing((PartialModel) AllPartialModels.SHAFT_HALF,
                be.getBlockState(), shaftDirection);
        float angle = getAngleForBe(be, be.getBlockPos(), shaftDirection.getAxis());
        CTFlywheelVisuals.kineticRotationTransformWhite(shaft, be, shaftDirection.getAxis(), angle, light);
        shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    // Get the direction vector
    private static Vector3f directionVector(Direction dir) {
        return new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }
}
