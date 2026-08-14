package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Draw the CT Gantry Anchor Debug
public final class CTGantryAnchorDebugRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether CT gantry anchor debug is enabled
    private static boolean enabled;
    // Last anchor count
    private static int lastAnchorCount;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT gantry anchor debug
    private CTGantryAnchorDebugRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is enabled
    public static boolean isEnabled() {
        return enabled;
    }

    // Get the last anchor count
    public static int getLastAnchorCount() {
        return lastAnchorCount;
    }

    // Toggle the gantry anchor debug overlay
    public static boolean toggle() {
        enabled = !enabled;
        if (!enabled) {
            lastAnchorCount = 0;
        }
        return enabled;
    }

    // Set the enabled
    public static void setEnabled(boolean val) {
        enabled = val;
        if (!enabled) {
            lastAnchorCount = 0;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the render world event
    public static void onRenderWorld(RenderLevelStageEvent evt) {
        if (!enabled || evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            lastAnchorCount = 0;
            return;
        }

        List<AnchorDebugPoint> points = collectAnchorPoints(level, minecraft.player.blockPosition(),
                Math.max(2, minecraft.options.renderDistance().get()));
        lastAnchorCount = points.size();
        if (points.isEmpty()) {
            return;
        }

        Camera camera = evt.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = evt.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (AnchorDebugPoint point : points) {
            AABB bounds = new AABB(
                    point.anchor().x - 0.125D,
                    point.anchor().y - 0.125D,
                    point.anchor().z - 0.125D,
                    point.anchor().x + 0.125D,
                    point.anchor().y + 0.125D,
                    point.anchor().z + 0.125D);
            LevelRenderer.renderLineBox(
                    poseStack,
                    lines,
                    bounds.minX,
                    bounds.minY,
                    bounds.minZ,
                    bounds.maxX,
                    bounds.maxY,
                    bounds.maxZ,
                    0.95f,
                    0.25f,
                    0.10f,
                    1.0f);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    // Collect the anchor points
    private static List<AnchorDebugPoint> collectAnchorPoints(ClientLevel level, BlockPos center, int chunkRadius) {
        Set<BlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BlockEntity> blockEntities = new ArrayList<>();

        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getLoadedWorldBlockEntities(level, center, chunkRadius)) {
            if (blockEntity != null && !blockEntity.isRemoved() && seen.add(blockEntity)) {
                blockEntities.add(blockEntity);
            }
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            try {
                for (Object subLevel : container.getAllSubLevels()) {
                    for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                        if (blockEntity != null && !blockEntity.isRemoved() && seen.add(blockEntity)) {
                            blockEntities.add(blockEntity);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        List<AnchorDebugPoint> points = new ArrayList<>();
        for (BlockEntity blockEntity : blockEntities) {
            if (!(blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage)) {
                continue;
            }
            if (!carriage.hasActiveAttachmentAnchor()) {
                continue;
            }
            AnchorDebugPoint point = toAnchorPoint(level, carriage);
            if (point != null) {
                points.add(point);
            }
        }
        return points;
    }

    // Convert the CT gantry anchor debug to anchor point
    private static AnchorDebugPoint toAnchorPoint(ClientLevel level, PhysicsGantryCarriageBlockEntity carriage) {
        BlockPos shaftPos = carriage.getAttachedShaftPos();
        Direction shaftDirection = carriage.getAttachedShaftDirection();
        Direction carriageFacing = carriage.getAttachedCarriageFacing();
        if (shaftPos == null || shaftDirection == null || carriageFacing == null) {
            return null;
        }

        UUID subLevelId = carriage.getAttachedSubLevelId();
        PhysicsGantryShaftBlockEntity shaft = SimulatedHelper.findBlockEntity(level, subLevelId, shaftPos,
                PhysicsGantryShaftBlockEntity.class);
        if (shaft == null) {
            shaft = SimulatedHelper.findBlockEntityIncludingSubLevels(level, shaftPos, PhysicsGantryShaftBlockEntity.class);
        }

        Vec3 shaftDirectionLocal = Vec3.atLowerCornerOf(shaftDirection.getNormal());
        Vec3 carriageFacingLocal = Vec3.atLowerCornerOf(carriageFacing.getNormal());
        Vec3 localAnchor = Vec3.atCenterOf(shaftPos)
                .add(shaftDirectionLocal.x * carriage.getAttachedShaftProgress(),
                        shaftDirectionLocal.y * carriage.getAttachedShaftProgress(),
                        shaftDirectionLocal.z * carriage.getAttachedShaftProgress())
                .add(carriageFacingLocal.x * 0.5D,
                        carriageFacingLocal.y * 0.5D,
                        carriageFacingLocal.z * 0.5D);

        Vec3 worldAnchor = shaft == null ? localAnchor : SimulatedHelper.toContainingWorldPosition(shaft, localAnchor);
        return new AnchorDebugPoint(worldAnchor);
    }

    // Store the anchor debug point
    private record AnchorDebugPoint(Vec3 anchor) {
    }
}
