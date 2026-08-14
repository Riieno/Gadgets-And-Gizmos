package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelClientRenderApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllSpecialTextures;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem;
import dev.simulated_team.simulated.index.SimRenderTypes;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.BindableTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// Draw the CT Physics Staff Overlay
public final class CTPhysicsStaffOverlayRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int HOVER_OUTLINE_COLOR = 0xBFBFBF;
    private static final Method GET_LOCKS_METHOD = findGetLocksMethod();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT physics staff overlay
    private CTPhysicsStaffOverlayRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the render world event
    public static void onRenderWorld(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }

        if (!isWorldReadyForPhysicsStaffOverlay(minecraft)) {
            return;
        }

        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = evt.getCamera().getPosition();
        renderAllLocks(bufferSource, evt.getPoseStack(), level, cameraPos);
        bufferSource.endBatch(SimRenderTypes.lock());

        BlockPos hoverBlockPos = updateHoverPos(minecraft, player);
        if (hoverBlockPos != null) {
            Outliner.getInstance()
                    .showCluster("physicsStaffSelection", List.of(hoverBlockPos))
                    .colored(HOVER_OUTLINE_COLOR)
                    .disableLineNormals()
                    .lineWidth(0.03125f)
                    .withFaceTexture((BindableTexture) AllSpecialTextures.CHECKERED);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the hover pos
    private static BlockPos updateHoverPos(Minecraft minecraft, LocalPlayer player) {
        PhysicsStaffClientHandler.ClientDragSession dragSession = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.getDragSession();
        if (dragSession != null) {
            Vector3dc localAnchor = dragSession.dragLocalAnchor();
            return BlockPos.containing(localAnchor.x(), localAnchor.y(), localAnchor.z());
        }

        HitResult hit = pickWithSubLevelPoses(minecraft, player);
        if (!(hit instanceof BlockHitResult blockHitResult) || blockHitResult.getType() == HitResult.Type.MISS) {
            return null;
        }

        return SableLevelApi.containing(minecraft.level, hit.getLocation()) == null ? null : blockHitResult.getBlockPos();
    }

    // Check if the world is ready for the physics staff overlay
    private static boolean isWorldReadyForPhysicsStaffOverlay(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        return player != null
                && level != null
                && !player.isRemoved()
                && minecraft.getConnection() != null
                && player.connection != null;
    }

    // Get the pick with sublevel poses
    private static HitResult pickWithSubLevelPoses(Minecraft minecraft, LocalPlayer player) {
        float partialTicks = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        ClientLevel level = minecraft.level;
        if (level == null) {
            return player.pick(PhysicsStaffItem.RANGE, partialTicks, false);
        }
        return SubLevelClientRenderApi.withPoses(level, partialTicks,
                () -> player.pick(PhysicsStaffItem.RANGE, partialTicks, false));
    }

    // Draw all physics staff locks
    private static void renderAllLocks(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, Level level, Vec3 cameraPos) {
        List<UUID> locks = getLocks(level);
        if (locks.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        for (UUID lock : locks) {
            SubLevel subLevel = container.getSubLevel(lock);
            if (!(subLevel instanceof ClientSubLevel clientSubLevel)) {
                continue;
            }

            Vec3 renderPos = SubLevelClientRenderApi.renderPosition(clientSubLevel,
                    minecraft.getTimer().getGameTimeDeltaPartialTick(true));

            poseStack.pushPose();
            poseStack.translate(renderPos.x() - cameraPos.x(), renderPos.y() - cameraPos.y(), renderPos.z() - cameraPos.z());
            poseStack.mulPose(new Quaternionf((Quaternionfc) minecraft.getEntityRenderDispatcher().cameraOrientation()));

            VertexConsumer buffer = bufferSource.getBuffer(SimRenderTypes.lock());
            PoseStack.Pose pose = poseStack.last();
            buffer.addVertex(pose, -0.5f, -0.5f, 0.0f).setColor(-1).setUv(0.0f, 1.0f).setLight(0xF000F0);
            buffer.addVertex(pose, -0.5f, 0.5f, 0.0f).setColor(-1).setUv(0.0f, 0.0f).setLight(0xF000F0);
            buffer.addVertex(pose, 0.5f, 0.5f, 0.0f).setColor(-1).setUv(1.0f, 0.0f).setLight(0xF000F0);
            buffer.addVertex(pose, 0.5f, -0.5f, 0.0f).setColor(-1).setUv(1.0f, 1.0f).setLight(0xF000F0);
            poseStack.popPose();
        }
    }

    // Get the locks
    @SuppressWarnings("unchecked")
    private static List<UUID> getLocks(Level level) {
        if (GET_LOCKS_METHOD == null) {
            return Collections.emptyList();
        }

        try {
            Object res = GET_LOCKS_METHOD.invoke(SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER, level);
            return res instanceof List<?> list ? (List<UUID>) list : Collections.emptyList();
        } catch (ReflectiveOperationException err) {
            return Collections.emptyList();
        }
    }

    // Find the get locks method
    private static Method findGetLocksMethod() {
        try {
            Method method = PhysicsStaffClientHandler.class.getDeclaredMethod("getLocks", Level.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException err) {
            return null;
        }
    }

}
