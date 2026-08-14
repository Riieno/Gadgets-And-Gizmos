package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import com.rieno.gadgetsandgizmos.content.EntityLauncherClawEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

// Draw the Entity Launcher Claw
public class EntityLauncherClawRenderer extends EntityRenderer<EntityLauncherClawEntity> {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher claw
    public EntityLauncherClawRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the entity launcher claw
    @Override
    public void render(EntityLauncherClawEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Entity owner = entity.getOwner();
        Vec3 renderOrigin = entity.getPosition(partialTick);
        Vec3 clawFrame = toEntityRenderFrame(entity, entity.getAttachmentPos(partialTick), partialTick);
        Vec3 clawDirection = getClawDirection(entity, partialTick);

        Vec3 clawRenderFrame = clawFrame;
        if (owner != null) {
            Vec3 ownerWorld = entity.getMountedLauncherOrigin();
            if (ownerWorld == null && owner instanceof Player player) {
                InteractionHand hand = entity.isLaunchedFromMainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                if (isLocalFirstPerson(player)) {
                    ownerWorld = getFirstPersonFocusPos(partialTick);
                } else {
                    ownerWorld = EntityLauncherItem.handRopeWorldPos(player, hand, partialTick);
                }
            } else if (ownerWorld == null) {
                ownerWorld = owner.getPosition(partialTick).add(0.0, owner.getBbHeight() * 0.65, 0.0);
            }

            Vec3 ownerFrame = toEntityRenderFrame(entity, ownerWorld, partialTick);
            Vec3 clawEndFrame = clawFrame.subtract(clawDirection.scale(0.44));
            List<Vec3> ropePath = EntityLauncherRopePhysics.getPath(entity, ownerFrame, clawEndFrame).stream()
                    .map(point -> point.subtract(renderOrigin))
                    .toList();
            EntityLauncherRopeRenderer.renderPolyline(poseStack, bufferSource, ropePath, packedLight);
        }

        poseStack.pushPose();
        Vec3 clawRenderOffset = clawRenderFrame.subtract(renderOrigin);
        poseStack.translate(clawRenderOffset.x, clawRenderOffset.y, clawRenderOffset.z);
        poseStack.mulPose(rotationFromNegativeZ(clawDirection));

        poseStack.translate(-0.5, -0.5, -0.5);
        CachedBuffers.partial(CTPartialModels.ENTITY_LAUNCHER_CLAW, Blocks.AIR.defaultBlockState())
                .light(packedLight)
                .renderInto(poseStack, bufferSource.getBuffer(RenderType.cutoutMipped()));
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the texture location
    @Override
    public ResourceLocation getTextureLocation(EntityLauncherClawEntity entity) {
        return ResourceLocation.withDefaultNamespace("missing");
    }

    // Check if this should render
    @Override
    public boolean shouldRender(EntityLauncherClawEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        return true;
    }

    // Get the claw direction
    private static Vec3 getClawDirection(EntityLauncherClawEntity entity, float partialTick) {
        Vec3 movement = entity.getDeltaMovement();
        if (movement.lengthSqr() > 1.0E-5) {
            return movement.normalize();
        }
        float yaw = entity.getYRot();
        float pitch = entity.getXRot();
        float yawRad = -yaw * ((float) Math.PI / 180.0f) - (float) Math.PI;
        float pitchRad = -pitch * ((float) Math.PI / 180.0f);
        float horizontal = (float) Math.cos(pitchRad);
        return new Vec3(
                Math.sin(yawRad) * horizontal,
                Math.sin(pitchRad),
                Math.cos(yawRad) * horizontal).normalize();
    }

    // Convert the entity launcher claw to entity render frame
    private static Vec3 toEntityRenderFrame(EntityLauncherClawEntity entity, Vec3 pos, float partialTick) {
        if (pos == null) {
            return entity.getPosition(partialTick);
        }

        Vec3 global = SimulatedHelper.projectOutOfSubLevels(entity.level(), pos);
        Object renderSubLevel = SimulatedHelper.getContainingSubLevel(entity.level(), entity.getAttachmentPos(partialTick));
        if (renderSubLevel == null) {
            return global;
        }

        Vec3 local = SimulatedHelper.toContainingLocalPosition(renderSubLevel, global);
        return local == null ? global : local;
    }

    // Get the rotation from negative z
    private static Quaternionf rotationFromNegativeZ(Vec3 dir) {
        Vector3f from = new Vector3f(0.0f, 0.0f, -1.0f);
        Vector3f to = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (to.lengthSquared() < 1.0E-6f) {
            return new Quaternionf();
        }
        return new Quaternionf().rotateTo(from, to.normalize());
    }

    // Check if this is the local first-person view
    private static boolean isLocalFirstPerson(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == player && minecraft.options.getCameraType().isFirstPerson();
    }

    // Get the first person focus pos
    private static Vec3 getFirstPersonFocusPos(float partialTick) {
        try {
            GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
            Camera camera = gameRenderer.getMainCamera();
            Vector3d focusPoint = new Vector3d(EntityLauncherItemRenderer.focusPos);
            Quaternionf orientation = new Quaternionf(camera.rotation());
            orientation.transformInverse(focusPoint);

            Vector4f clip = new Vector4f((float) focusPoint.x, (float) focusPoint.y, (float) focusPoint.z, 1.0f);
                float fov = Minecraft.getInstance().options.fov().get().floatValue();
                Matrix4f actualProjMat = new Matrix4f(gameRenderer.getProjectionMatrix(fov));
            actualProjMat.invert(new Matrix4f()).transform(clip);
            new Matrix4f(EntityLauncherItemRenderer.itemProjMat).transform(clip);

            Vec3 cameraPosition = camera.getPosition();
            focusPoint.set(clip.x, clip.y, clip.z);
            orientation.transform(focusPoint);
            focusPoint.mul(100.0 / fov);
            focusPoint.add(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            return new Vec3(focusPoint.x, focusPoint.y, focusPoint.z);
        } catch (Exception ignored) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) {
                return Vec3.ZERO;
            }
            return EntityLauncherItem.handRopeWorldPos(player, InteractionHand.MAIN_HAND, partialTick);
        }
    }
}
