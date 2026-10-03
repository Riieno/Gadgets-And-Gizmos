package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Draw the short travel span while the carriage crosses between rope connectors.
public final class PoweredZiplineHandoffRenderer {
    private static final Map<PoweredZiplineBlockEntity, Span> SPANS = new WeakHashMap<>();

    private PoweredZiplineHandoffRenderer() {}

    public static void record(PoweredZiplineBlockEntity zipline) {
        List<Vec3> points = zipline.getHandoffSplinePoints();
        if (points.isEmpty()) SPANS.remove(zipline);
        else SPANS.put(zipline, new Span(Minecraft.getInstance().level, points, System.nanoTime()));
    }

    public static void clear() {
        SPANS.clear();
    }

    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SPANS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        long now = System.nanoTime();
        SPANS.entrySet().removeIf(entry -> entry.getKey().isRemoved()
                || now - entry.getValue().seenAt() > 1_000_000_000L);
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (Span span : SPANS.values()) {
            if (span.level() != minecraft.level) continue;
            List<Vec3> points = span.points();
            for (int index = 1; index < points.size(); index++) {
                Vec3 a = points.get(index - 1);
                Vec3 b = points.get(index);
                lines.addVertex(pose.last().pose(), (float) a.x, (float) a.y, (float) a.z)
                        .setColor(154, 118, 77, 255).setNormal(0.0F, 1.0F, 0.0F);
                lines.addVertex(pose.last().pose(), (float) b.x, (float) b.y, (float) b.z)
                        .setColor(154, 118, 77, 255).setNormal(0.0F, 1.0F, 0.0F);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private record Span(net.minecraft.world.level.Level level, List<Vec3> points, long seenAt) {}
}
