package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.neoforge.network.ScmPathDebugPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

// Renders the selected live SCM route. Data arrives only after /gizmos scm_path_debug is enabled.
public final class ScmPathDebugRenderer {
    private static final long SNAPSHOT_TIMEOUT_TICKS = 60L;
    private static final double MAX_RENDER_DISTANCE = 288.0D;
    private static final Map<String, Snapshot> SNAPSHOTS = new LinkedHashMap<>();

    private ScmPathDebugRenderer() {
    }

    public static void apply(ScmPathDebugPayload payload) {
        if (!payload.enabled()) {
            SNAPSHOTS.clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long receivedAt = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        String key = payload.controllerPos().asLong() + ":" + payload.commandKey();
        SNAPSHOTS.put(key, new Snapshot(payload, receivedAt));
    }

    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || SNAPSHOTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            SNAPSHOTS.clear();
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        SNAPSHOTS.entrySet().removeIf(entry -> gameTime - entry.getValue().receivedAt() > SNAPSHOT_TIMEOUT_TICKS);
        if (SNAPSHOTS.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (Snapshot snapshot : SNAPSHOTS.values()) {
            ScmPathDebugPayload payload = snapshot.payload();
            if (!payload.position().closerThan(camera, MAX_RENDER_DISTANCE)) {
                continue;
            }
            renderSnapshot(poseStack, lines, payload);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void renderSnapshot(PoseStack poseStack, VertexConsumer lines,
                                       ScmPathDebugPayload payload) {
        for (ScmPathDebugPayload.PathSegment segment : payload.exploredSegments()) {
            addLine(poseStack, lines, segment.from(), segment.to(),
                    segment.clear() ? 0.20F : 0.95F,
                    segment.clear() ? 0.55F : 0.12F,
                    segment.clear() ? 0.75F : 0.10F,
                    segment.clear() ? 0.30F : 0.52F);
        }
        Vec3 previous = payload.position();
        for (int index = 0; index < payload.waypoints().size(); index++) {
            ScmPathDebugPayload.PathPoint waypoint = payload.waypoints().get(index);
            boolean active = index == payload.activeWaypointIndex();
            if (active) {
                addLine(poseStack, lines, previous, waypoint.position(),
                        payload.currentSegmentClear() ? 0.10F : 1.00F,
                        payload.currentSegmentClear() ? 0.90F : 0.12F,
                        payload.currentSegmentClear() ? 1.00F : 0.10F, 1.0F);
            } else if (waypoint.reverse()) {
                addLine(poseStack, lines, previous, waypoint.position(), 1.0F, 0.55F, 0.08F, 0.95F);
            } else {
                addLine(poseStack, lines, previous, waypoint.position(), 0.20F, 1.0F, 0.25F, 0.95F);
            }
            renderMarker(poseStack, lines, waypoint.position(), active
                    ? 0.28D : 0.16D, waypoint.reverse() ? 1.0F : 0.20F,
                    waypoint.reverse() ? 0.55F : 1.0F, waypoint.reverse() ? 0.08F : 0.25F);
            previous = waypoint.position();
        }

        renderMarker(poseStack, lines, payload.position(), 0.24D, 0.10F, 0.75F, 1.0F);
        renderMarker(poseStack, lines, payload.target(), 0.32D, 0.85F, 0.20F, 1.0F);
    }

    private static void renderMarker(PoseStack poseStack, VertexConsumer lines, Vec3 position,
                                     double radius, float red, float green, float blue) {
        AABB box = new AABB(position.x - radius, position.y - radius, position.z - radius,
                position.x + radius, position.y + radius, position.z + radius);
        LevelRenderer.renderLineBox(poseStack, lines, box, red, green, blue, 1.0F);
    }

    private static void addLine(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to,
                                float red, float green, float blue, float alpha) {
        Vec3 normal = to.subtract(from);
        if (normal.lengthSqr() < 1.0E-8D) {
            normal = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            normal = normal.normalize();
        }
        Matrix4f matrix = poseStack.last().pose();
        consumer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, alpha)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, alpha)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record Snapshot(ScmPathDebugPayload payload, long receivedAt) {
    }
}
