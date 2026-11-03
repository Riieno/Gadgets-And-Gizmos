package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingPistonMath;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.lib.geometry.CardinalRotation;
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
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

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
            renderDetails(be, partialTicks, ms, buffer, light);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

<<<<<<< Updated upstream
=======
    // Draw the SCM preview partials
    static boolean renderPreview(BlockEntity entity, BlockState state, PoseStack ms,
                                 MultiBufferSource buffer, int light) {
        if (!(entity instanceof VectorBearingBlockEntity bearing)) {
            return false;
        }
        renderShaft(bearing, ms, buffer, light);
        renderDetails(bearing, AnimationTickHolder.getPartialTicks(), ms, buffer, light);
        return true;
    }

    // Draw the bearing plate and pistons
    private static void renderDetails(VectorBearingBlockEntity be, float partialTicks, PoseStack ms,
                                      MultiBufferSource buffer, int light) {
        Direction facing = be.getBearingFacing();
        Vector3f baseNormal = directionVector(facing);
        boolean mounted = be.isMountedAssemblyPresent();
        Quaternionf tilt = headTilt(be, partialTicks);
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

    // Share the physical head orientation between the instanced and fallback renderers
    static Quaternionf headTilt(VectorBearingBlockEntity be, float partialTicks){
        if(!be.isMountedAssemblyPresent()) return new Quaternionf();
        Quaternionf tilt = mountedTilt(be, partialTicks);
        return tilt == null ? appliedTilt(be, partialTicks, be.getBearingFacing(),
                directionVector(be.getBearingFacing())) : tilt;
    }

>>>>>>> Stashed changes
    // Get the mounted tilt
    private static Quaternionf mountedTilt(VectorBearingBlockEntity be, float partialTicks) {
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
        childOrientation.mul(CardinalRotation.between(be.getMountedHeadFacing(), be.getBearingFacing())
                .quaternion().conjugate()).normalize();

        return new Quaternionf((float) childOrientation.x, (float) childOrientation.y,
                (float) childOrientation.z, (float) childOrientation.w).normalize();
    }

    // Get the applied tilt
    private static Quaternionf appliedTilt(VectorBearingBlockEntity be, float partialTicks, Direction facing,
                                           Vector3f baseNormal) {
        Vec3 dir = be.getInterpolatedHeadDirection(partialTicks);
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
    private static Quaterniond interpolatedOrientation(SubLevel subLevel, float partialTicks) {
        if (subLevel instanceof ClientSubLevel clientSubLevel) {
            return new Quaterniond(clientSubLevel.renderPose(partialTicks).orientation()).normalize();
        }
        return new Quaterniond(subLevel.lastPose().orientation())
                .slerp(new Quaterniond(subLevel.logicalPose().orientation()), partialTicks)
                .normalize();
    }

    // Draw the pistons
    private static void renderPistons(VectorBearingBlockEntity be, Direction facing, Quaternionf tilt, PoseStack ms,
                                      MultiBufferSource buffer, int light) {
        for(int idx = 0; idx < 4; idx++){
            for(int part = 0; part < 2; part++){
                ms.pushPose();
                if(posePiston(ms, facing, tilt, idx, part == 1)){
                    CachedBuffers.partial(part == 0 ? CTPartialModels.VECTOR_BEARING_PISTON_HEAD
                            : CTPartialModels.VECTOR_BEARING_PISTON_POLE, be.getBlockState())
                            .light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
                }
                ms.popPose();
            }
        }
    }

    // Position one model part with the same transforms in both rendering paths
    static boolean posePiston(PoseStack ms, Direction facing, Quaternionf tilt, int idx, boolean pole){
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vector3f transformed = tilt.transform(new Vector3f((float)normal.x, (float)normal.y, (float)normal.z));
        Vec3 tilted = new Vec3(transformed.x, transformed.y, transformed.z);
        double dot = normal.dot(tilted);
        if(Math.abs(dot) < 1.0E-6D) return false;
        Vec3 pos = VecHelper.rotate(new Vec3(0.36875D, 0, 0), -90.0D * idx, Direction.Axis.Y);
        if(facing.getAxis().isHorizontal()){
            pos = VecHelper.rotate(pos, AngleHelper.horizontalAngle(facing), Direction.Axis.Z);
            pos = VecHelper.rotate(pos, -90.0D + AngleHelper.verticalAngle(facing), Direction.Axis.X);
        }
        pos = pos.add(normal.scale(VectorBearingPistonMath.extension(pos.dot(tilted), dot)));
        ms.translate(pos.x + 0.5D, pos.y + 0.5D, pos.z + 0.5D);
        int side = idx;
        if(facing == Direction.DOWN){
            ms.mulPose((idx % 2 == 0 ? Axis.XP : Axis.ZP).rotationDegrees(180));
        }
        if(facing.getAxis().isHorizontal()){
            ms.mulPose(Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(facing.getOpposite())));
            ms.mulPose(Axis.XP.rotationDegrees(-90.0F + AngleHelper.verticalAngle(facing)));
            side = 2 - side;
        }
        if(pole) ms.translate(0, 0.03125D, 0);
        ms.mulPose(Axis.YP.rotationDegrees(-90.0F * side));
        return true;
    }

    // Draw the shaft
    private static void renderShaft(VectorBearingBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
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


    // Draw the SCM preview partials
    static boolean renderPreview(BlockEntity entity, BlockState state, PoseStack ms,
                                 MultiBufferSource buffer, int light) {
        if (!(entity instanceof VectorBearingBlockEntity bearing)) {
            return false;
        }
        renderShaft(bearing, ms, buffer, light);
        renderDetails(bearing, AnimationTickHolder.getPartialTicks(), ms, buffer, light);
        return true;
    }


    // Draw the bearing plate and pistons
    private static void renderDetails(VectorBearingBlockEntity be, float partialTicks, PoseStack ms,
                                      MultiBufferSource buffer, int light) {
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
}
