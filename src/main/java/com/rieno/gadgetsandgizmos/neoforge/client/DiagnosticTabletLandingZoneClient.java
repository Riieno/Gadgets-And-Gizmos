package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.Optional;

// Handle files dropped onto the tablet and send only supported bounded uploads to the server
public final class DiagnosticTabletLandingZoneClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet landing zone client
    private DiagnosticTabletLandingZoneClient() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the render world event
    public static void onRenderWorld(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Context ctx = context();
        if (ctx == null || ctx.zones().isEmpty()) return;
        Vec3 camera = evt.getCamera().getPosition();
        PoseStack pose = evt.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        int[][] edges = {
                {0, 1}, {1, 3}, {3, 2}, {2, 0},
                {4, 5}, {5, 7}, {7, 6}, {6, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (CompoundTag zone : ctx.zones()) {
            Vec3[] corners = globalCorners(ctx.dock(), zone);
            boolean selected = zone.getBoolean("Selected") || zone.getBoolean("Draft");
            for (int[] edge : edges) addLine(pose, lines, corners[edge[0]], corners[edge[1]], selected);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the mouse scrolling event
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent evt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (evt.isCanceled() || minecraft.player == null || !minecraft.player.isShiftKeyDown()
                || evt.getScrollDeltaY() == 0.0D) return;
        Context ctx = context();
        if (ctx == null || ctx.selectedZone() == null) return;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 end = eye.add(minecraft.player.getLookAngle().scale(96.0D));
        Vec3 localEye = SimulatedHelper.toContainingLocalPosition(ctx.dock(), eye);
        Vec3 localEnd = SimulatedHelper.toContainingLocalPosition(ctx.dock(), end);
        AABB box = localBox(ctx.selectedZone()).inflate(0.015D);
        Optional<Vec3> hit = box.clip(localEye, localEnd);
        if (hit.isEmpty()) return;
        Direction face = closestFace(hit.get(), box);
        int dir = evt.getScrollDeltaY() > 0.0D ? -1 : 1;
        int amount = switch (face) {
            case WEST, DOWN, NORTH -> -dir;
            default -> dir;
        };
        PacketDistributor.sendToServer(new DiagnosticTabletActionPayload(false, ctx.hand(),
                BlockPos.ZERO, null, ctx.state().tabletId(),
                DiagnosticTabletData.appId("scm"), "landing", "landing_adjust",
                face.getName() + "|" + amount));
        evt.setCanceled(true);
    }

    // Get the context
    private static Context context() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        InteractionHand hand = null;
        ItemStack tablet = minecraft.player.getMainHandItem();
        if (tablet.getItem() instanceof DiagnosticTabletItem) hand = InteractionHand.MAIN_HAND;
        else {
            tablet = minecraft.player.getOffhandItem();
            if (tablet.getItem() instanceof DiagnosticTabletItem) hand = InteractionHand.OFF_HAND;
        }
        if (hand == null) return null;
        DiagnosticTabletData.State state = DiagnosticTabletData.read(tablet);
        if (!DiagnosticTabletData.appId("scm").equals(state.app()) || state.tabletId() == null) return null;
        CompoundTag snapshot = DiagnosticTabletClientAppData.get(
                DiagnosticTabletData.appId("scm"), false, state.tabletId(), null, null);
        ListTag targets = snapshot.getList("Targets", Tag.TAG_COMPOUND);
        CompoundTag selected = null;
        for (int idx = 0; idx < targets.size(); idx++) {
            if (targets.getCompound(idx).getBoolean("Selected")) {
                selected = targets.getCompound(idx);
                break;
            }
        }
        if (selected == null || !selected.contains("Pos")) return null;
        BlockPos dockPos = BlockPos.of(selected.getLong("Pos"));
        java.util.UUID subLevel = selected.hasUUID("SubLevel") ? selected.getUUID("SubLevel") : null;
        BlockEntity dock = SimulatedHelper.findLoadedBlockEntityExact(minecraft.level, subLevel, dockPos);
        if (dock == null) return null;
        java.util.List<CompoundTag> zones = new java.util.ArrayList<>();
        CompoundTag selectedZone = null;
        ListTag stored = snapshot.getList("LandingZones", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < stored.size(); idx++) {
            CompoundTag zone = stored.getCompound(idx).copy();
            zones.add(zone);
            if (zone.getBoolean("Selected")) selectedZone = zone;
        }
        if (snapshot.contains("LandingDraftStart", Tag.TAG_LONG)
                && minecraft.hitResult instanceof BlockHitResult hit) {
            BlockPos first = BlockPos.of(snapshot.getLong("LandingDraftStart"));
            Vec3 local = SimulatedHelper.toContainingLocalPosition(dock, hit.getLocation());
            BlockPos second = BlockPos.containing(local);
            CompoundTag preview = new CompoundTag();
            preview.putLong("Min", new BlockPos(Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ())).asLong());
            preview.putLong("Max", new BlockPos(Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ())).asLong());
            preview.putBoolean("Draft", true);
            preview.putBoolean("Selected", true);
            zones.add(preview);
        }
        if (selectedZone == null && !zones.isEmpty()) selectedZone = zones.getFirst();
        return new Context(hand, state, dock, java.util.List.copyOf(zones), selectedZone);
    }

    // Get the local box
    private static AABB localBox(CompoundTag zone) {
        BlockPos min = BlockPos.of(zone.getLong("Min"));
        BlockPos max = BlockPos.of(zone.getLong("Max"));
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    // Get the global corners
    private static Vec3[] globalCorners(BlockEntity dock, CompoundTag zone) {
        AABB box = localBox(zone);
        Vec3[] local = {
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ)
        };
        Vec3[] global = new Vec3[local.length];
        for (int idx = 0; idx < local.length; idx++) {
            global[idx] = SimulatedHelper.toGlobalWorldPosition(dock, local[idx]);
        }
        return global;
    }

    // Get the closest face
    private static Direction closestFace(Vec3 point, AABB box) {
        double[] distances = {
                Math.abs(point.x - box.minX), Math.abs(point.x - box.maxX),
                Math.abs(point.y - box.minY), Math.abs(point.y - box.maxY),
                Math.abs(point.z - box.minZ), Math.abs(point.z - box.maxZ)
        };
        Direction[] faces = {Direction.WEST, Direction.EAST, Direction.DOWN,
                Direction.UP, Direction.NORTH, Direction.SOUTH};
        int closest = 0;
        for (int idx = 1; idx < distances.length; idx++) {
            if (distances[idx] < distances[closest]) closest = idx;
        }
        return faces[closest];
    }

    // Add the line
    private static void addLine(PoseStack pose, VertexConsumer consumer, Vec3 from, Vec3 to,
                                boolean selected) {
        Matrix4f matrix = pose.last().pose();
        Vec3 normal = to.subtract(from).normalize();
        consumer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(selected ? 0.18F : 0.55F, selected ? 0.82F : 0.62F,
                        selected ? 1.0F : 0.72F, 0.95F)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(selected ? 0.18F : 0.55F, selected ? 0.82F : 0.62F,
                        selected ? 1.0F : 0.72F, 0.95F)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    // Pass the operation context
    private record Context(InteractionHand hand, DiagnosticTabletData.State state,
                           BlockEntity dock, java.util.List<CompoundTag> zones,
                           CompoundTag selectedZone) {
    }
}
