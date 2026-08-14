package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundRopeKnotAttachPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineAttachPacket;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.google.common.cache.Cache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

// Keep the two-click zipline preview and placement request valid across moving levels
public final class PoweredZiplinePlacementHandler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double ROPE_SELECTION_RADIUS = 0.25D;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Selected target
    private static @Nullable Target selectedTarget;
    // Selected mode
    private static TargetMode selectedMode = TargetMode.NONE;
    // Resolved chain connection field
    private static @Nullable Field chainConnectionField;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the powered zipline placement handler
    private PoweredZiplinePlacementHandler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the client
    public static void clientTick() {
        if (CTItems.POWERED_ZIPLINE == null && CTBlocks.ROPE_KNOT == null) {
            selectedTarget = null;
            selectedMode = TargetMode.NONE;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.screen != null) {
            selectedTarget = null;
            selectedMode = TargetMode.NONE;
            return;
        }
        boolean ziplineMode = isHoldingPoweredZipline(player);
        boolean ropeKnotMode = !ziplineMode && player.isShiftKeyDown() && isHoldingRope(player);
        if (!ziplineMode && !ropeKnotMode) {
            selectedTarget = null;
            selectedMode = TargetMode.NONE;
            return;
        }

        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0D;
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(range));
        double bestDistance = minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : minecraft.hitResult.getLocation().distanceToSqr(from);

        Target ropeTarget = findRopeTarget(minecraft.level, from, to, bestDistance);
        selectedTarget = ropeTarget;
        selectedMode = selectedTarget == null ? TargetMode.NONE : ziplineMode ? TargetMode.ZIPLINE : TargetMode.ROPE_KNOT;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the interaction key mapping triggered event
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered evt) {
        if (!evt.isUseItem()) {
            return;
        }
        if (!tryAttachSelected()) {
            return;
        }
        evt.setSwingHand(false);
        evt.setCanceled(true);
    }

    // Handle the render world event
    public static void onRenderWorld(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || selectedTarget == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = evt.getCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 target = selectedTarget.targetPosition();
        AABB bounds = new AABB(
                target.x - 0.14D,
                target.y - 0.14D,
                target.z - 0.14D,
                target.x + 0.14D,
                target.y + 0.14D,
                target.z + 0.14D);
        PoseStack poseStack = evt.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        float red = selectedMode == TargetMode.ROPE_KNOT ? 0.85f : 0.15f;
        float green = selectedTarget.targetType() == ServerboundZiplineAttachPacket.TargetType.ROPE ? 0.85f : 0.55f;
        LevelRenderer.renderLineBox(poseStack, lines, bounds, red, green, 1.0f, 1.0f);
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    // Sample the zipline world position
    public static @Nullable Vec3 sampleZiplineWorldPosition(PoweredZiplineBlockEntity zipline, float partialTick) {
        if (zipline.getAttachedChainPos() != null) {
            return sampleChainPosition(zipline, partialTick);
        }
        if (zipline.getAttachedRopeUUID() != null) {
            return sampleRopePosition(zipline, partialTick);
        }
        return null;
    }

    // Sample the zipline world tangent
    public static @Nullable Vec3 sampleZiplineWorldTangent(PoweredZiplineBlockEntity zipline) {
        float pos = zipline.getPathPosition(1.0f);
        float length = Math.max(0.01f, zipline.getPathLength());
        float step = zipline.getAttachedChainPos() != null && zipline.getAttachedChainConnection() == null ? 1.0f : 0.15f;
        Vec3 before = sampleZiplineWorldPosAt(zipline, wrapOrClamp(zipline, pos - step, length));
        Vec3 after = sampleZiplineWorldPosAt(zipline, wrapOrClamp(zipline, pos + step, length));
        if (before == null || after == null) {
            return null;
        }
        Vec3 tangent = after.subtract(before);
        return tangent.lengthSqr() < 1.0E-6D ? null : tangent.normalize();
    }

    // Wrap the clamp
    private static float wrapOrClamp(PoweredZiplineBlockEntity zipline, float pos, float length) {
        if (zipline.getAttachedChainPos() != null && zipline.getAttachedChainConnection() == null) {
            return (pos % 360.0f + 360.0f) % 360.0f;
        }
        return Mth.clamp(pos, 0.0f, length);
    }

    // Sample the zipline world pos
    private static @Nullable Vec3 sampleZiplineWorldPosAt(PoweredZiplineBlockEntity zipline, float pos) {
        if (zipline.getAttachedChainPos() != null) {
            return sampleChainPositionAt(zipline, pos);
        }
        if (zipline.getAttachedRopeUUID() != null) {
            return sampleRopePositionAt(zipline, pos);
        }
        return null;
    }

    // Try to attach selected
    private static boolean tryAttachSelected() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || (CTItems.POWERED_ZIPLINE == null && CTBlocks.ROPE_KNOT == null)) {
            return false;
        }
        Target target = selectedTarget;
        TargetMode mode = selectedMode;
        if ((target == null || mode == TargetMode.NONE) && minecraft.level != null) {
            target = findImmediateTarget(minecraft, player);
            mode = target == null ? TargetMode.NONE : player.isShiftKeyDown() && isHoldingRope(player)
                    ? TargetMode.ROPE_KNOT
                    : TargetMode.ZIPLINE;
        }
        if (target == null) {
            return false;
        }
        if (mode == TargetMode.ROPE_KNOT) {
            if (CTBlocks.ROPE_KNOT == null || !player.isShiftKeyDown() || !isHoldingRope(player) || target.targetType() != ServerboundZiplineAttachPacket.TargetType.ROPE
                    || target.ropeUuid() == null) {
                return false;
            }
            PacketDistributor.sendToServer(new ServerboundRopeKnotAttachPacket(
                    target.ropeUuid(),
                    target.targetPosition(),
                    target.pathPosition()));
            player.swing(player.getMainHandItem().getItem() instanceof RopeItem
                    ? net.minecraft.world.InteractionHand.MAIN_HAND
                    : net.minecraft.world.InteractionHand.OFF_HAND);
            selectedTarget = target;
            selectedMode = TargetMode.ROPE_KNOT;
            return true;
        }
        if (mode != TargetMode.ZIPLINE || !isHoldingPoweredZipline(player)) {
            return false;
        }
        PacketDistributor.sendToServer(new ServerboundZiplineAttachPacket(
                target.targetType(),
                target.targetPosition(),
                target.chainPos() == null ? BlockPos.ZERO : target.chainPos(),
                target.chainConnection(),
                target.ropeUuid(),
                target.pathPosition()));
        player.swing(player.getMainHandItem().is(CTItems.POWERED_ZIPLINE.get())
                ? net.minecraft.world.InteractionHand.MAIN_HAND
                : net.minecraft.world.InteractionHand.OFF_HAND);
        selectedTarget = target;
        selectedMode = TargetMode.ZIPLINE;
        return true;
    }

    // Find the immediate target
    private static @Nullable Target findImmediateTarget(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.level == null) {
            return null;
        }
        boolean ziplineMode = isHoldingPoweredZipline(player);
        boolean ropeKnotMode = !ziplineMode && player.isShiftKeyDown() && isHoldingRope(player);
        if (!ziplineMode && !ropeKnotMode) {
            return null;
        }
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0D;
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(range));
        double maxDistanceSq = range * range;
        return findRopeTarget(minecraft.level, from, to, maxDistanceSq);
    }

    // Check if the player is holding the powered zipline
    private static boolean isHoldingPoweredZipline(LocalPlayer player) {
        return isPoweredZipline(player.getMainHandItem()) || isPoweredZipline(player.getOffhandItem());
    }

    // Check if the player is holding the rope
    private static boolean isHoldingRope(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof RopeItem || player.getOffhandItem().getItem() instanceof RopeItem;
    }

    // Check if the zipline is powered
    private static boolean isPoweredZipline(ItemStack stack) {
        if (CTItems.POWERED_ZIPLINE == null) {
            return false;
        }
        return stack.is(CTItems.POWERED_ZIPLINE.get());
    }

    // Find the chain target
    private static @Nullable Target findChainTarget(ClientLevel level, Vec3 from, Vec3 to, double maxDistanceSq) {
        Cache<BlockPos, java.util.List<ChainConveyorShape>> cache =
                ChainConveyorInteractionHandler.loadedChains.get((LevelAccessor) level);
        BlockPos bestLift = null;
        ChainConveyorShape bestShape = null;
        Vec3 bestHit = null;
        float bestPosition = 0.0f;
        BlockPos bestConnection = null;
        double bestDistance = maxDistanceSq;

        for (Map.Entry<BlockPos, java.util.List<ChainConveyorShape>> entry : cache.asMap().entrySet()) {
            BlockPos liftPos = entry.getKey();
            Vec3 liftVec = Vec3.atLowerCornerOf((Vec3i) liftPos);
            for (ChainConveyorShape shape : entry.getValue()) {
                Vec3 intersect = shape.intersect(from.subtract(liftVec), to.subtract(liftVec));
                if (intersect == null) {
                    continue;
                }
                Vec3 hit = intersect.add(liftVec);
                double distance = hit.distanceToSqr(from);
                if (distance > bestDistance) {
                    continue;
                }
                bestDistance = distance;
                bestLift = liftPos;
                bestShape = shape;
                bestHit = hit;
                bestPosition = shape.getChainPosition(intersect);
                bestConnection = readChainConnection(shape);
            }
        }

        if (bestLift == null || bestShape == null || bestHit == null) {
            return null;
        }
        return new Target(ServerboundZiplineAttachPacket.TargetType.CHAIN, bestHit, bestLift.immutable(),
                bestConnection == null ? null : bestConnection.immutable(), null, bestPosition);
    }

    // Read the chain connection
    private static @Nullable BlockPos readChainConnection(ChainConveyorShape shape) {
        if (!(shape instanceof ChainConveyorShape.ChainConveyorOBB)) {
            return null;
        }
        try {
            if (chainConnectionField == null) {
                chainConnectionField = ChainConveyorShape.ChainConveyorOBB.class.getDeclaredField("connection");
                chainConnectionField.setAccessible(true);
            }
            Object val = chainConnectionField.get(shape);
            return val instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Find the rope target
    private static @Nullable Target findRopeTarget(ClientLevel level, Vec3 from, Vec3 to, double maxDistanceSq) {
        ClientLevelRopeManager manager = ClientLevelRopeManager.getOrCreate(level);
        if (manager == null) {
            return null;
        }
        RopeRaycast best = null;
        for (ClientRopeStrand strand : manager.getAllStrands()) {
            ObjectArrayList<ClientRopePoint> points = strand.getPoints();
            float cumulative = 0.0f;
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = toRenderVec3(points.get(i));
                Vec3 b = toRenderVec3(points.get(i + 1));
                double segmentLength = a.distanceTo(b);
                if (segmentLength < 1.0E-5D) {
                    continue;
                }
                RopeRaycast hit = closestRaycast(from, to, a, b, cumulative, strand.getUuid(), maxDistanceSq);
                if (hit != null && (best == null || hit.rayDistanceSq() < best.rayDistanceSq())) {
                    best = hit;
                }
                cumulative += (float) segmentLength;
            }
        }
        if (best == null) {
            return null;
        }
        return new Target(ServerboundZiplineAttachPacket.TargetType.ROPE, best.position(), BlockPos.ZERO,
                null, best.ropeUuid(), best.pathPosition());
    }

    // Get the closest raycast
    private static @Nullable RopeRaycast closestRaycast(Vec3 rayStart, Vec3 rayEnd, Vec3 segmentStart, Vec3 segmentEnd,
                                                       float cumulative, UUID ropeUuid, double maxDistanceSq) {
        Vec3 ray = rayEnd.subtract(rayStart);
        Vec3 segment = segmentEnd.subtract(segmentStart);
        Vec3 between = rayStart.subtract(segmentStart);
        double a = ray.dot(ray);
        double b = ray.dot(segment);
        double c = segment.dot(segment);
        double d = ray.dot(between);
        double e = segment.dot(between);
        double denominator = a * c - b * b;
        double s = denominator < 1.0E-8D ? 0.0D : Mth.clamp((b * e - c * d) / denominator, 0.0D, 1.0D);
        double t = c < 1.0E-8D ? 0.0D : Mth.clamp((b * s + e) / c, 0.0D, 1.0D);
        s = a < 1.0E-8D ? 0.0D : Mth.clamp((b * t - d) / a, 0.0D, 1.0D);

        Vec3 rayPoint = rayStart.add(ray.scale(s));
        Vec3 ropePoint = segmentStart.add(segment.scale(t));
        if (a >= 1.0E-8D && c >= 1.0E-8D) {
            s = Mth.clamp(ropePoint.subtract(rayStart).dot(ray) / a, 0.0D, 1.0D);
            rayPoint = rayStart.add(ray.scale(s));
            t = Mth.clamp(rayPoint.subtract(segmentStart).dot(segment) / c, 0.0D, 1.0D);
            ropePoint = segmentStart.add(segment.scale(t));
            rayPoint = rayStart.add(ray.scale(Mth.clamp(ropePoint.subtract(rayStart).dot(ray) / a, 0.0D, 1.0D)));
        }
        if (rayPoint.distanceToSqr(ropePoint) > ROPE_SELECTION_RADIUS * ROPE_SELECTION_RADIUS) {
            return null;
        }
        double rayDistanceSq = rayPoint.distanceToSqr(rayStart);
        if (rayDistanceSq > maxDistanceSq) {
            return null;
        }
        return new RopeRaycast(ropePoint, ropeUuid, cumulative + (float) (segment.length() * t), rayDistanceSq);
    }

    // Sample the chain position
    private static @Nullable Vec3 sampleChainPosition(PoweredZiplineBlockEntity zipline, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockPos chainPos = zipline.getAttachedChainPos();
        if (chainPos == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(minecraft.level, chainPos);
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) {
            return null;
        }
        chain.prepareStats();
        float pos = zipline.getPathPosition(partialTick);
        return sampleChainPositionAt(zipline, pos);
    }

    // Sample the chain position
    private static @Nullable Vec3 sampleChainPositionAt(PoweredZiplineBlockEntity zipline, float pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockPos chainPos = zipline.getAttachedChainPos();
        if (chainPos == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(minecraft.level, chainPos);
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) {
            return null;
        }
        chain.prepareStats();
        BlockPos connection = zipline.getAttachedChainConnection();
        if (connection != null) {
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(connection);
            if (stats != null) {
                Vec3 diff = stats.end().subtract(stats.start()).normalize();
                return stats.start().add(diff.scale(Math.min(stats.chainLength(), pos)));
            }
        }
        return Vec3.atBottomCenterOf(chainPos).add(VecHelper.rotate(new Vec3(0.0D, 0.25D, 1.0D), pos, Direction.Axis.Y));
    }

    // Sample the rope position
    private static @Nullable Vec3 sampleRopePosition(PoweredZiplineBlockEntity zipline, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || zipline.getAttachedRopeUUID() == null) {
            return null;
        }
        ClientLevelRopeManager manager = ClientLevelRopeManager.getOrCreate(minecraft.level);
        ClientRopeStrand strand = manager == null ? null : manager.getStrand(zipline.getAttachedRopeUUID());
        if (strand == null) {
            return null;
        }
        return sampleClientRope(strand, zipline.getPathPosition(partialTick));
    }

    // Sample the rope position
    private static @Nullable Vec3 sampleRopePositionAt(PoweredZiplineBlockEntity zipline, float pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || zipline.getAttachedRopeUUID() == null) {
            return null;
        }
        ClientLevelRopeManager manager = ClientLevelRopeManager.getOrCreate(minecraft.level);
        ClientRopeStrand strand = manager == null ? null : manager.getStrand(zipline.getAttachedRopeUUID());
        if (strand == null) {
            return null;
        }
        return sampleClientRope(strand, pos);
    }

    // Sample the client rope
    private static @Nullable Vec3 sampleClientRope(ClientRopeStrand strand, float pos) {
        ObjectArrayList<ClientRopePoint> points = strand.getPoints();
        float cumulative = 0.0f;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = toRenderVec3(points.get(i));
            Vec3 b = toRenderVec3(points.get(i + 1));
            float segmentLength = (float) a.distanceTo(b);
            if (pos <= cumulative + segmentLength) {
                double local = segmentLength <= 1.0E-5f ? 0.0D : (pos - cumulative) / segmentLength;
                return a.lerp(b, Mth.clamp(local, 0.0D, 1.0D));
            }
            cumulative += segmentLength;
        }
        return points.isEmpty() ? null : toRenderVec3(points.getLast());
    }

    // Convert a rope point to render coordinates
    private static Vec3 toRenderVec3(ClientRopePoint point) {
        Vector3d vector = point.renderPos(1.0f, new Vector3d());
        return new Vec3(vector.x, vector.y, vector.z);
    }

    // Store the target
    private record Target(ServerboundZiplineAttachPacket.TargetType targetType, Vec3 targetPosition,
                          @Nullable BlockPos chainPos, @Nullable BlockPos chainConnection,
                          @Nullable UUID ropeUuid, float pathPosition) {
    }

    // Store the rope raycast
    private record RopeRaycast(Vec3 position, UUID ropeUuid, float pathPosition, double rayDistanceSq) {
    }

    // Define the target mode values
    private enum TargetMode {
        NONE,
        ZIPLINE,
        ROPE_KNOT
    }
}
