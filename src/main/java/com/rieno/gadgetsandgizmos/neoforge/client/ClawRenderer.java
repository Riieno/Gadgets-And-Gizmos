package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ClawBlock;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.client.render.AreaHighlightRenderTypes;
import com.rieno.gadgetsandgizmos.particle.ClawMarkerPulseParticleOptions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Draw the Claw
public class ClawRenderer extends SafeBlockEntityRenderer<ClawBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float MAX_JAW_ANGLE = 27.5f;
    private static final double PIVOT_X = 8.0 / 16.0;
    private static final double PIVOT_Y = 5.0 / 16.0;
    private static final double PIVOT_Z = 8.0 / 16.0;
    private static final float MARKER_MIN_RADIUS = 0.25f;
    private static final float MARKER_MAX_RADIUS = 3.0f;
    private static final float MARKER_MAX_DISTANCE = 64.0f;
    private static final float MARKER_RING_Y_OFFSET = 0.08f;
    private static final long PARTICLE_EMITTER_INTERVAL_NANOS = 15_000_000L;
    private static final double CONE_MAX_TRACE_DISTANCE = 256.0;
    private static final double CONE_HALF_ANGLE_RADIANS = Math.toRadians(10.0);
    private static final int CONE_RING_SAMPLES = 14;
    private static final int CONE_RADIAL_STEPS = 3;
    private static final Map<Long, Long> LAST_PARTICLE_EMIT_NS = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw
    public ClawRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the claw
    @Override
    protected void renderSafe(ClawBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource bufferSource, int light, int overlay) {
        if (VisualizationManager.supportsVisualization((LevelAccessor) be.getLevel())) {

            renderMarkerVisualization(be, partialTicks, ms, bufferSource, light, overlay);
            return;
        }

        float openAngle = (float) be.clawAngle.getValue(partialTicks) * MAX_JAW_ANGLE;
        float xRot = ClawBlock.getXRotationDegrees(be.getBlockState().getValue(ClawBlock.FACING));
        float yRot = ClawBlock.getYRotationDegrees(be.getBlockState().getValue(ClawBlock.FACING));

        SuperByteBuffer jawLeft = CachedBuffers.partial(CTPartialModels.CLAW_JAW_LEFT, be.getBlockState());
        SuperByteBuffer jawRight = CachedBuffers.partial(CTPartialModels.CLAW_JAW_RIGHT, be.getBlockState());

        ms.pushPose();

        ms.translate(0.5D, 0.5D, 0.5D);
        ms.mulPose(Axis.YP.rotationDegrees(yRot));
        ms.mulPose(Axis.XP.rotationDegrees(xRot));
        ms.translate(-0.5D, -0.5D, -0.5D);

        ms.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        ms.mulPose(Axis.XP.rotationDegrees(-openAngle));
        ms.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        jawLeft.light(light).renderInto(ms, bufferSource.getBuffer(RenderType.cutoutMipped()));
        ms.popPose();

        ms.pushPose();

        ms.translate(0.5D, 0.5D, 0.5D);
        ms.mulPose(Axis.YP.rotationDegrees(yRot));
        ms.mulPose(Axis.XP.rotationDegrees(xRot));
        ms.translate(-0.5D, -0.5D, -0.5D);

        ms.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        ms.mulPose(Axis.XP.rotationDegrees(openAngle));
        ms.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        jawRight.light(light).renderInto(ms, bufferSource.getBuffer(RenderType.cutoutMipped()));
        ms.popPose();

        renderMarkerVisualization(be, partialTicks, ms, bufferSource, light, overlay);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the marker visualization
    private void renderMarkerVisualization(ClawBlockEntity be, float partialTicks, PoseStack ms,
                                           MultiBufferSource bufferSource, int light, int overlay) {

        ClawMarkerRenderState.clearAll();
    }

    // Emit the cone intersection particles
    private void emitConeIntersectionParticles(ClawBlockEntity be, Level level, Vec3 clawWorldCenter, float signalFactor) {
        if (!level.isClientSide) {
            return;
        }

        long key = be.getBlockPos().asLong();
        long now = System.nanoTime();
        long last = LAST_PARTICLE_EMIT_NS.getOrDefault(key, 0L);
        if (now - last < PARTICLE_EMITTER_INTERVAL_NANOS) {
            return;
        }
        LAST_PARTICLE_EMIT_NS.put(key, now);

        if (LAST_PARTICLE_EMIT_NS.size() > 1024) {
            LAST_PARTICLE_EMIT_NS.entrySet().removeIf(entry -> now - entry.getValue() > 5_000_000_000L);
        }

        Vec3 apex = clawWorldCenter.add(0.0, -1.1, 0.0);
        ConeHit hit = coneTraceDown(level, apex, CONE_MAX_TRACE_DISTANCE);
        if (hit == null) {
            return;
        }

        Vec3 projectedHit = hit.position;
        double distanceToHit = Math.sqrt(SimulatedHelper.distanceSquaredWithSubLevels(level, clawWorldCenter, projectedHit));
        double normalizedDistance = Math.max(0.0, Math.min(1.0, distanceToHit / CONE_MAX_TRACE_DISTANCE));
        double coneRadiusAtHit = distanceToHit * Math.tan(CONE_HALF_ANGLE_RADIANS);
        double emitterRadius = Math.max(0.12, Math.min(3.25, 0.12 + Math.pow(normalizedDistance, 1.55) * 3.40 + coneRadiusAtHit * 0.10));
        int count = Math.max(4, 4 + Math.round(signalFactor * 12.0f) + (int) Math.floor(normalizedDistance * 6.0));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double radial = Math.sqrt(random.nextDouble()) * emitterRadius;
            double px = projectedHit.x + Math.cos(theta) * radial;
            double py = projectedHit.y + 0.02;
            double pz = projectedHit.z + Math.sin(theta) * radial;
            level.addParticle(new ClawMarkerPulseParticleOptions(0.15f, 0.95f, 0.9f), px, py, pz, 0.0, 0.0, 0.0);
        }
    }

    // Get the cone trace down
    private ConeHit coneTraceDown(Level level, Vec3 apex, double maxDistance) {
        AABB searchBounds = new AABB(apex, apex.add(0.0, -maxDistance, 0.0)).inflate(3.0);
        ConeHit best = traceRay(level, apex, new Vec3(0.0, -1.0, 0.0), maxDistance);
        double coneSlope = Math.tan(CONE_HALF_ANGLE_RADIANS);
        java.util.List<Object> intersectingSubLevels = SimulatedHelper.getIntersectingSubLevels(level, searchBounds);

        for (int radialStep = 1; radialStep <= CONE_RADIAL_STEPS; radialStep++) {
            double radialScale = coneSlope * (radialStep / (double) CONE_RADIAL_STEPS);
            for (int i = 0; i < CONE_RING_SAMPLES; i++) {
                double angle = (Math.PI * 2.0 * i) / CONE_RING_SAMPLES;
                Vec3 dir = new Vec3(Math.cos(angle) * radialScale, -1.0, Math.sin(angle) * radialScale).normalize();
                ConeHit sample = traceRay(level, apex, dir, maxDistance);
                for (Object subLevel : intersectingSubLevels) {
                    ConeHit subLevelSample = traceRayInSubLevel(subLevel, level, apex, dir, maxDistance);
                    if (subLevelSample != null && (sample == null || subLevelSample.distance < sample.distance)) {
                        sample = subLevelSample;
                    }
                }
                if (sample == null) {
                    continue;
                }
                if (best == null || sample.distance < best.distance) {
                    best = sample;
                }
            }
        }

        return best;
    }

    // Get the trace ray in sublevel
    private ConeHit traceRayInSubLevel(Object subLevel, Level rootLevel, Vec3 startWorld, Vec3 dir, double maxDistance) {
        Level subLevelWorld = getSubLevelLevel(subLevel);
        if (subLevelWorld == null) {
            return null;
        }

        Vec3 startLocal = SimulatedHelper.toContainingLocalPosition(subLevel, startWorld);
        Vec3 endLocal = startLocal.add(dir.scale(maxDistance));
        BlockHitResult hit = subLevelWorld.clip(new ClipContext(startLocal, endLocal, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        Vec3 worldHit = SimulatedHelper.toContainingWorldPosition(subLevel, hit.getLocation());
        double distance = Math.sqrt(SimulatedHelper.distanceSquaredWithSubLevels(rootLevel, startWorld, worldHit));
        return new ConeHit(worldHit, distance);
    }

    // Resolve the containing level
    private Level getSubLevelLevel(Object subLevel) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object res = subLevel.getClass().getMethod("getLevel").invoke(subLevel);
            return res instanceof Level level ? level : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Get the trace ray
    private ConeHit traceRay(Level level, Vec3 start, Vec3 dir, double maxDistance) {
        Vec3 end = start.add(dir.scale(maxDistance));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        double distance = start.distanceTo(hit.getLocation());
        return new ConeHit(hit.getLocation(), distance);
    }

    // Store the cone hit
    private record ConeHit(Vec3 position, double distance) {
    }

    // Get the raycast down piercing blocks
    @org.jetbrains.annotations.Nullable
    private Vec3 raycastDownPiercingBlocks(Level level, Vec3 start, Vec3 end) {

        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return null;
    }

    // Calculate the dynamic radius
    private float calcDynamicRadius(double distanceToHit) {

        if (distanceToHit <= 1.0) {
            return MARKER_MIN_RADIUS;
        }
        if (distanceToHit >= MARKER_MAX_DISTANCE) {
            return MARKER_MAX_RADIUS;
        }

        float t = (float) Math.min(1.0, distanceToHit / MARKER_MAX_DISTANCE);
        return MARKER_MIN_RADIUS + (MARKER_MAX_RADIUS - MARKER_MIN_RADIUS) * t;
    }

        // Draw the marker decal
        private void renderMarkerDecal(PoseStack ms, MultiBufferSource bufferSource, int light, float radius, float signalFactor) {
        VertexConsumer buffer = bufferSource.getBuffer(AreaHighlightRenderTypes.clawMarker());
        Matrix4f matrix = ms.last().pose();

        float halfSize = radius;
        float y = MARKER_RING_Y_OFFSET;
        float r = 0.15f;
        float g = 0.95f;
        float b = 0.9f;
        float a = 0.20f + (0.65f * signalFactor);

        buffer.addVertex(matrix, -halfSize, y, -halfSize)
            .setColor(r, g, b, a)
            .setUv(0.0f, 0.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0f, 1.0f, 0.0f);
        buffer.addVertex(matrix, -halfSize, y, halfSize)
            .setColor(r, g, b, a)
            .setUv(0.0f, 1.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0f, 1.0f, 0.0f);
        buffer.addVertex(matrix, halfSize, y, halfSize)
            .setColor(r, g, b, a)
            .setUv(1.0f, 1.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0f, 1.0f, 0.0f);
        buffer.addVertex(matrix, halfSize, y, -halfSize)
            .setColor(r, g, b, a)
            .setUv(1.0f, 0.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0f, 1.0f, 0.0f);
        }

}
