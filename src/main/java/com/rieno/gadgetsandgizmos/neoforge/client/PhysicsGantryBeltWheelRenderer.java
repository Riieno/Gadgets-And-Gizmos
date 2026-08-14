package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

// Draw the Physics Gantry Belt Wheel
public class PhysicsGantryBeltWheelRenderer extends KineticBlockEntityRenderer<PhysicsGantryBeltWheelBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float BELT_LOOP_RADIUS = 0.28f;
    private static final float BELT_HALF_WIDTH_NEAR = 0.125f;
    private static final float BELT_HALF_WIDTH_FAR = 0.09375f;
    private static final ResourceLocation BELT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "create", "textures/block/belt.png");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry belt wheel
    public PhysicsGantryBeltWheelRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the rotated model
    @Override
    protected SuperByteBuffer getRotatedModel(PhysicsGantryBeltWheelBlockEntity be, BlockState state) {
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);

        return CachedBuffers.partialFacingVertical((PartialModel) AllPartialModels.SHAFTLESS_COGWHEEL, state,
            Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the physics gantry belt wheel
    @Override
    protected void renderSafe(PhysicsGantryBeltWheelBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        boolean flywheelActive = dev.engine_room.flywheel.api.visualization.VisualizationManager.supportsVisualization(be.getLevel());
        if (!flywheelActive) {

            BlockState renderedState = getRenderedBlockState(be);
            CTFlywheelVisuals.renderRotatingBufferWhite(be, getRotatedModel(be, renderedState), ms,
                    buffer.getBuffer(getRenderType(be, renderedState)), light);
        }

        BlockState state = be.getBlockState();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);

        if (!flywheelActive) {
            float angle = getAngleForBE(be, be.getBlockPos(), axis) / 180.0f * (float) Math.PI;
            for (Direction shaftFacing : net.createmod.catnip.data.Iterate.directionsInAxis(axis)) {
                SuperByteBuffer shaft = CachedBuffers.partialFacing((PartialModel) AllPartialModels.SHAFT_HALF, state, shaftFacing);
                CTFlywheelVisuals.kineticRotationTransformWhite(shaft, be, axis, angle, light)
                    .renderInto(ms, buffer.getBuffer(RenderType.solid()));
            }
        }

        if (!be.shouldRenderLinkFromThisEndpoint()) {
            return;
        }

        PhysicsGantryBeltWheelBlockEntity linkedWheel = be.resolveLinkedWheel();
        if (linkedWheel == null) {
            return;
        }

        Vec3 startWorld = be.getWorldAnchorPosition();
        Vec3 endWorld = linkedWheel.getWorldAnchorPosition();
        double distance = startWorld.distanceTo(endWorld);
        if (distance < 0.01D) {
            return;
        }

        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 startFrame = be.getAnchorPositionInRenderFrameOf(be);
        Vec3 endFrame = linkedWheel.getAnchorPositionInRenderFrameOf(be);
        Vec3 start = startFrame.subtract(origin);
        Vec3 end = endFrame.subtract(origin);
        if (!isRenderableLocalEndpoint(start) || !isRenderableLocalEndpoint(end)
                || start.distanceToSqr(end) > 96.0D * 96.0D) {
            return;
        }

        renderLoopBelt(
                be,
                ms,
                buffer,
                start,
                end,
                state.getValue(BlockStateProperties.AXIS),
                linkedWheel.getBlockState().getValue(BlockStateProperties.AXIS),
                startWorld,
                endWorld,
                overlay);
    }

    // Get the render bounding box
    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull PhysicsGantryBeltWheelBlockEntity be) {
        if (!be.shouldRenderLinkFromThisEndpoint()) {
            return super.getRenderBoundingBox(be);
        }
        Vec3 start = be.getWorldAnchorPosition();
        Vec3 end = be.getLinkedWorldAnchorPosition();
        if (start == null || end == null) {
            return super.getRenderBoundingBox(be);
        }
        return new AABB(
                Math.min(start.x, end.x) - 1.0D,
                Math.min(start.y, end.y) - 1.0D,
                Math.min(start.z, end.z) - 1.0D,
                Math.max(start.x, end.x) + 1.0D,
                Math.max(start.y, end.y) + 1.0D,
                Math.max(start.z, end.z) + 1.0D);
    }

    // Check if this should render off screen
    @Override
    public boolean shouldRenderOffScreen(PhysicsGantryBeltWheelBlockEntity blockEntity) {
        return true;
    }

    // Get the rendered angle for a block entity
    private static float getAngleForBE(KineticBlockEntity be, BlockPos pos, Direction.Axis axis) {
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, pos, axis);
        return (time * be.getSpeed() * 3.0f / 20.0f + offset) % 360.0f;
    }

    // Check if this is renderable local endpoint
    private static boolean isRenderableLocalEndpoint(Vec3 point) {
        return point != null
                && Double.isFinite(point.x)
                && Double.isFinite(point.y)
                && Double.isFinite(point.z)
                && Math.abs(point.x) < 128.0D
                && Math.abs(point.y) < 128.0D
                && Math.abs(point.z) < 128.0D;
    }

    // Draw the loop belt
    private static void renderLoopBelt(PhysicsGantryBeltWheelBlockEntity be, PoseStack ms, MultiBufferSource buffer,
                                       Vec3 start, Vec3 end, Direction.Axis startAxis,
                                       Direction.Axis endAxis, Vec3 startWorld, Vec3 endWorld, int overlay) {
        Vec3 startAxisVector = axisVector(startAxis);
        Vec3 endAxisVector = axisVector(endAxis);

        Vec3 centerDelta = end.subtract(start);
        if (centerDelta.lengthSqr() <= 1.0E-6D) {
            return;
        }
        Vec3 line = centerDelta.normalize();
        Vec3 startSide = beltSide(startAxisVector, line);
        Vec3 endSide = beltSide(endAxisVector, line);
        if (startSide.dot(endSide) < 0.0D) {
            endSide = endSide.scale(-1.0D);
        }
        Vec3 startArcDirection = beltArcDirection(startAxisVector, line, startSide);
        Vec3 endArcDirection = beltArcDirection(endAxisVector, line, endSide);

        float distance = (float) centerDelta.length();
        float radius = Math.min(BELT_LOOP_RADIUS, Math.max(0.08f, distance * 0.45f));

        Vec3 aTop = start.add(startSide.scale(radius));
        Vec3 bTop = end.add(endSide.scale(radius));
        Vec3 bBottom = end.subtract(endSide.scale(radius));
        Vec3 aBottom = start.subtract(startSide.scale(radius));

        List<Vec3> loopPoints = new ArrayList<>();
        loopPoints.add(aTop);
        loopPoints.add(bTop);
        appendArc(loopPoints, end, endSide, endArcDirection, radius, 0.0f, (float) Math.PI,
                Mth.clamp((int) (radius * 56.0f), 12, 32));
        loopPoints.add(bBottom);
        loopPoints.add(aBottom);
        appendArc(loopPoints, start, startSide, startArcDirection, radius, (float) Math.PI,
                (float) (Math.PI * 2.0), Mth.clamp((int) (radius * 56.0f), 12, 32));

        float animation = 0.0f;
        float speed = be.getSpeed();
        float absSpeed = Math.abs(speed);
        float directionSign = speed >= 0.0f ? 1.0f : -1.0f;
        if (absSpeed > 1.0E-4f) {
            float time = AnimationTickHolder.getRenderTime(be.getLevel()) / (360.0f / absSpeed);
            time %= 1.0f;
            if (time < 0.0f) {
                time += 1.0f;
            }
            animation = (time - 0.5f) * directionSign;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 mid = startWorld.lerp(endWorld, 0.5D);
        boolean far = minecraft.level == be.getLevel()
                && !minecraft.getBlockEntityRenderDispatcher().camera.getPosition().closerThan(mid, 48.0D);

        VertexConsumer beltConsumer = buffer.getBuffer(RenderTypes.chain(BELT_TEXTURE));
        Vec3 worldOrigin = Vec3.atLowerCornerOf(be.getBlockPos());
        float uvCursor = animation;

        for (int i = 1; i < loopPoints.size(); i++) {
            Vec3 from = loopPoints.get(i - 1);
            Vec3 to = loopPoints.get(i);
            Vec3 tangent = to.subtract(from);
            if (tangent.lengthSqr() <= 1.0E-8D) {
                continue;
            }

            float segmentLength = (float) tangent.length();
            Vec3 fromWorld = from.add(worldOrigin);
            Vec3 toWorld = to.add(worldOrigin);
            int lightFrom = LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(fromWorld));
            int lightTo = LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(toWorld));
            Vec3 segmentCenter = from.add(to).scale(0.5D);
            Vec3 axisHint = segmentCenter.distanceToSqr(start) <= segmentCenter.distanceToSqr(end)
                    ? startAxisVector
                    : endAxisVector;
            renderBeltSegment(ms, beltConsumer, from, to, axisHint, uvCursor, uvCursor + segmentLength,
                    lightFrom, lightTo, far, overlay);
            uvCursor += segmentLength * directionSign;
        }
    }

    // Get the axis vector
    private static Vec3 axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(1.0D, 0.0D, 0.0D);
            case Y -> new Vec3(0.0D, 1.0D, 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, 1.0D);
        };
    }

    // Get the belt side
    private static Vec3 beltSide(Vec3 axis, Vec3 line) {
        Vec3 side = axis.cross(line);
        if (side.lengthSqr() <= 1.0E-8D) {
            Vec3 fallback = Math.abs(axis.y) < 0.9D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
            side = axis.cross(fallback);
        }
        return side.normalize();
    }

    // Get the belt arc direction
    private static Vec3 beltArcDirection(Vec3 axis, Vec3 line, Vec3 side) {
        Vec3 projectedLine = line.subtract(axis.scale(line.dot(axis)));
        if (projectedLine.lengthSqr() <= 1.0E-8D) {
            projectedLine = side.cross(axis);
        }
        return projectedLine.normalize();
    }

    // Add the arc
    private static void appendArc(List<Vec3> points, Vec3 center, Vec3 side, Vec3 line, float radius,
                                  float startAngle, float endAngle, int segments) {
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float angle = Mth.lerp(t, startAngle, endAngle);
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            Vec3 point = center
                    .add(side.scale(radius * c))
                    .add(line.scale(radius * s));
            points.add(point);
        }
    }

    // Draw the belt segment
    private static void renderBeltSegment(PoseStack ms, VertexConsumer consumer, Vec3 from, Vec3 to, Vec3 axisVec,
                                          float minV, float maxV, int lightFrom, int lightTo, boolean far,
                                          int overlay) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() <= 1.0E-10D) {
            return;
        }

        Vec3 dir = delta.normalize();
        Vec3 side = axisVec.cross(dir);
        if (side.lengthSqr() <= 1.0E-10D) {
            side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() <= 1.0E-10D) {
            side = dir.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 up = dir.cross(side).normalize();

        float halfWidth = far ? BELT_HALF_WIDTH_FAR : BELT_HALF_WIDTH_NEAR;
        float halfThickness = halfWidth * 0.75f;
        Vec3 center = from.add(delta.scale(0.5D));
        Vec3 along = dir.scale(delta.length() * 0.5D);
        Vec3 across = side.scale(halfWidth);
        Vec3 vertical = up.scale(halfThickness);

        Vec3 p000 = center.subtract(along).subtract(across).subtract(vertical);
        Vec3 p001 = center.subtract(along).subtract(across).add(vertical);
        Vec3 p010 = center.subtract(along).add(across).subtract(vertical);
        Vec3 p011 = center.subtract(along).add(across).add(vertical);
        Vec3 p100 = center.add(along).subtract(across).subtract(vertical);
        Vec3 p101 = center.add(along).subtract(across).add(vertical);
        Vec3 p110 = center.add(along).add(across).subtract(vertical);
        Vec3 p111 = center.add(along).add(across).add(vertical);

        float uvStart = minV;
        float uvEnd = maxV;
        float widthU = 0.5f;

        renderFace(ms, consumer, p001, p101, p111, p011, uvStart, uvEnd, 0.0f, widthU, lightFrom, lightTo, overlay);
        renderFace(ms, consumer, p000, p100, p110, p010, uvStart, uvEnd, 0.0f, widthU, lightFrom, lightTo, overlay);
        renderFace(ms, consumer, p000, p100, p101, p001, uvStart, uvEnd, 0.0f, widthU, lightFrom, lightTo, overlay);
        renderFace(ms, consumer, p010, p110, p111, p011, uvStart, uvEnd, 0.0f, widthU, lightFrom, lightTo, overlay);
        renderFace(ms, consumer, p000, p001, p011, p010, 0.0f, widthU, 0.0f, widthU, lightFrom, lightTo, overlay);
        renderFace(ms, consumer, p100, p110, p111, p101, 0.0f, widthU, 0.0f, widthU, lightFrom, lightTo, overlay);
    }

    // Draw the face
    private static void renderFace(PoseStack ms, VertexConsumer consumer,
                                   Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                                   float minU, float maxU, float minV, float maxV,
                                   int lightFrom, int lightTo, int overlay) {
        Matrix4f pose = ms.last().pose();
        PoseStack.Pose normalPose = ms.last();
        Vec3 edge1 = b.subtract(a);
        Vec3 edge2 = d.subtract(a);
        Vec3 normal = edge1.cross(edge2).normalize();
        if (normal.lengthSqr() <= 1.0E-10D) {
            normal = new Vec3(0.0D, 1.0D, 0.0D);
        }
        Vec3 backNormal = new Vec3(-normal.x, -normal.y, -normal.z);

        addVertex(pose, normalPose, consumer, a, minU, minV, normal, lightFrom, overlay);
        addVertex(pose, normalPose, consumer, b, maxU, minV, normal, lightFrom, overlay);
        addVertex(pose, normalPose, consumer, c, maxU, maxV, normal, lightTo, overlay);
        addVertex(pose, normalPose, consumer, d, minU, maxV, normal, lightTo, overlay);

        addVertex(pose, normalPose, consumer, d, minU, maxV, backNormal, lightTo, overlay);
        addVertex(pose, normalPose, consumer, c, maxU, maxV, backNormal, lightTo, overlay);
        addVertex(pose, normalPose, consumer, b, maxU, minV, backNormal, lightFrom, overlay);
        addVertex(pose, normalPose, consumer, a, minU, minV, backNormal, lightFrom, overlay);
    }

    // Add the vertex
    private static void addVertex(Matrix4f pose, PoseStack.Pose normalPose, VertexConsumer consumer,
                                  Vec3 pos, float u, float v, Vec3 normal, int light, int overlay) {
        consumer.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                .setUv(u, v)
                .setOverlay(overlay == OverlayTexture.NO_OVERLAY ? OverlayTexture.NO_OVERLAY : overlay)
                .setLight(light)
                .setNormal(normalPose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
