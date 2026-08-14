package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Draw the Thruster
public class ThrusterRenderer implements BlockEntityRenderer<ThrusterBlockEntity> {
        /*--------------------------------------------------------##---------------------------------------------------------

        =======================================================================================================================
                                                               Constants
        =======================================================================================================================

        ------------------------------------------------------------##-----------------------------------------------------*/

        private static final ResourceLocation BEACON_BEAM_TEXTURE =
                        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");
        private static final float BEAM_WIDTH_SCALE = 1.5f;
        private static final float BLAZE_BURNER_SCALE = 0.62f;
        private static final double BLAZE_BURNER_BASE_OFFSET = -0.24D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster
    public ThrusterRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the thruster
    @Override
    public void render(ThrusterBlockEntity be, float partialTicks, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {

        if (!be.isClientEffectSourceValid()) {
            ThrusterSoundManager.stop(be);
            return;
        }

        BlockState state = be.getBlockState();
        boolean focusedMode = be.isFocusedMode();
        if (!focusedMode) {
            renderBlazeBurner(be, state, ms, buffer);
        }

        if (!be.isActive()) {
            return;
        }

        ThrusterSoundManager.touch(be);

        if (be.isParticleFiltered()) {
            return;
        }

                if (focusedMode) {
            renderFocusedBeam(be, ms, buffer);
            return;
        }

        float throttle = Mth.clamp(be.getAppliedThrottle(), 0.0f, 1.0f);
        int alpha = Mth.clamp(Math.round(64.0f + throttle * 191.0f), 0, 255);
        Vec3 effectDirection = be.getVisualEffectDirection().normalize();

        VertexConsumer translucentConsumer = buffer.getBuffer(RenderType.translucent());
        SuperByteBuffer flame = CachedBuffers.partial(flameFrame(be), state);
        rotateNorthFacingEffectBuffer(flame, effectDirection);

        ms.pushPose();
        flame.disableDiffuse().light(0xF000F0).color(255, 255, 255, alpha).renderInto(ms, translucentConsumer);
        ms.popPose();
    }

        /*--------------------------------------------------------##---------------------------------------------------------

        =======================================================================================================================
                                                               Functions
        =======================================================================================================================

        ------------------------------------------------------------##-----------------------------------------------------*/

        // Get the flame frame
        private static PartialModel flameFrame(ThrusterBlockEntity blockEntity) {
                PartialModel[] frames = blockEntity.isSuperheatedMode() || blockEntity.isSoulThruster()
                                ? CTPartialModels.THRUSTER_FLAME_SUPERHEATED_FRAMES
                                : CTPartialModels.THRUSTER_FLAME_FRAMES;
                Level level = blockEntity.getLevel();
                if (level == null || frames.length == 0) {
                        return blockEntity.isSuperheatedMode() || blockEntity.isSoulThruster()
                                        ? CTPartialModels.THRUSTER_FLAME_SUPERHEATED
                                        : CTPartialModels.THRUSTER_FLAME;
                }
                int frame = Mth.floor(AnimationTickHolder.getRenderTime(level)) % frames.length;
                return frames[frame];
        }

        // Draw the blaze burner
        private static void renderBlazeBurner(ThrusterBlockEntity blockEntity, BlockState state,
                                                                                  PoseStack ms, MultiBufferSource buffer) {
                Level level = blockEntity.getLevel();
                if (level == null) {
                        return;
                }

                boolean active = blockEntity.isActive();
                BlazeBurnerBlock.HeatLevel heatLevel = active
                                ? blockEntity.isSuperheatedMode()
                                        ? BlazeBurnerBlock.HeatLevel.SEETHING
                                        : BlazeBurnerBlock.HeatLevel.KINDLED
                                : BlazeBurnerBlock.HeatLevel.SMOULDERING;
                boolean heated = heatLevel.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING);
                Direction facing = blockEntity.getBlockDirection();
                Vec3 nozzleDirection = Vec3.atLowerCornerOf(facing.getNormal()).normalize();
                Vec3 baseAnchor = new Vec3(0.5D, 0.5D, 0.5D).add(nozzleDirection.scale(BLAZE_BURNER_BASE_OFFSET));
                Quaternionf orientation = rotationFromUp(nozzleDirection);
                float animation = heated ? 0.175f : 0.0f;
                float horizontalAngle = heated ? 0.0f : burnerLookAngle(blockEntity, baseAnchor, nozzleDirection, orientation);

                ms.pushPose();
                ms.translate(baseAnchor.x, baseAnchor.y, baseAnchor.z);
                ms.mulPose(orientation);
                ms.scale(BLAZE_BURNER_SCALE, BLAZE_BURNER_SCALE, BLAZE_BURNER_SCALE);
                ms.translate(-0.5D, 0.0D, -0.5D);
                BlazeBurnerRenderer.renderShared(ms, null, buffer, level, state, heatLevel,
                                animation, horizontalAngle, heated, false, null,
                                blockEntity.getBlockPos().hashCode());
                ms.popPose();
        }

        // Get the rotation from up
        private static Quaternionf rotationFromUp(Vec3 dir) {
                Vector3f target = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
                if (target.lengthSquared() < 1.0E-6f) {
                        return new Quaternionf();
                }
                return new Quaternionf().rotateTo(new Vector3f(0.0f, 1.0f, 0.0f), target.normalize());
        }

        // Get the burner look angle
        private static float burnerLookAngle(ThrusterBlockEntity blockEntity, Vec3 baseAnchor,
                                                                                 Vec3 nozzleDirection, Quaternionf orientation) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null || player.isInvisible()) {
                        return 0.0f;
                }

                Vec3 blockOrigin = Vec3.atLowerCornerOf(blockEntity.getBlockPos());
                Vec3 burnerCenter = blockOrigin.add(baseAnchor)
                                .add(nozzleDirection.scale(BLAZE_BURNER_SCALE * 0.5D));
                Vec3 toPlayer = new Vec3(player.getX(), player.getEyeY(), player.getZ()).subtract(burnerCenter);
                Vector3f localX = new Vector3f(1.0f, 0.0f, 0.0f).rotate(orientation);
                Vector3f localZ = new Vector3f(0.0f, 0.0f, 1.0f).rotate(orientation);
                double x = toPlayer.x * localX.x() + toPlayer.y * localX.y() + toPlayer.z * localX.z();
                double z = toPlayer.x * localZ.x() + toPlayer.y * localZ.y() + toPlayer.z * localZ.z();
                if (x * x + z * z < 1.0E-6D) {
                        return 0.0f;
                }
                return (float) Math.toRadians(Math.toDegrees(-Mth.atan2(z, x)) - 90.0D);
        }

        // Rotate the north facing effect buffer
        private static void rotateNorthFacingEffectBuffer(SuperByteBuffer buffer, Vec3 dir) {
                Quaternionf orientation = new Quaternionf().rotateTo(0.0f, 0.0f, -1.0f,
                                (float) dir.x, (float) dir.y, (float) dir.z);
                buffer.rotateCentered(orientation);
        }

        // Draw the focused beam
        private static void renderFocusedBeam(ThrusterBlockEntity blockEntity, PoseStack ms, MultiBufferSource buffer) {
                Vec3 dir = blockEntity.getVisualEffectDirection().normalize();
                if (dir.lengthSqr() < 1.0E-5D) {
                        return;
                }

                float beamLength = Mth.clamp((float) blockEntity.getForcedProcessingDistance(), 0.75f, 12.0f);
                int col = blockEntity.getBeamColor();
                float red = ((col >> 16) & 0xFF) / 255.0f;
                float green = ((col >> 8) & 0xFF) / 255.0f;
                float blue = (col & 0xFF) / 255.0f;
                float throttle = Mth.clamp(blockEntity.getAppliedThrottle(), 0.0f, 1.0f);
                float beamAlpha = Mth.clamp(blockEntity.getBeamMaxOpacity() * (0.15f + 0.85f * throttle), 0.0f, 1.0f);
                Level level = blockEntity.getLevel();
                float renderTime = level == null ? 0.0f : AnimationTickHolder.getRenderTime(level);
                VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucentEmissive(BEACON_BEAM_TEXTURE, true));

                ms.pushPose();

                ms.translate(0.5D, 0.5D, 0.5D);
                ms.mulPose(new Quaternionf().rotateTo(0.0f, 0.0f, 1.0f,
                                (float) dir.x, (float) dir.y, (float) dir.z));
                renderBeaconBeam(builder, ms, beamLength, red, green, blue, beamAlpha, renderTime);
                ms.popPose();
        }

        // Draw the beacon beam
        private static void renderBeaconBeam(VertexConsumer builder, PoseStack ms, float length,
                                             float red, float green, float blue, float alpha, float renderTime) {
                float start = 0.35f;
                float end = length + start;
                int segments = Math.max(4, Mth.ceil(length * 2.0f));
                float scroll = -renderTime * 0.025f;

                renderBeaconBeamLayer(builder, ms, start, end, segments, 0.115f * BEAM_WIDTH_SCALE,
                                red, green, blue, alpha, scroll, 1.0f);
                renderBeaconBeamLayer(builder, ms, start, end, segments, 0.32f * BEAM_WIDTH_SCALE,
                                red, green, blue, alpha, scroll * 0.65f, 0.38f);
        }

        // Draw the beacon beam layer
        private static void renderBeaconBeamLayer(VertexConsumer builder, PoseStack ms, float start, float end,
                                                  int segments, float radius, float red, float green, float blue,
                                                  float alpha, float scroll, float alphaScale) {
                for (int side = 0; side < 4; side++) {
                        float angle0 = (float) (Math.PI * 0.5D * side + Math.PI * 0.25D);
                        float angle1 = (float) (Math.PI * 0.5D * (side + 1) + Math.PI * 0.25D);
                        float x0 = Mth.cos(angle0) * radius;
                        float y0 = Mth.sin(angle0) * radius;
                        float x1 = Mth.cos(angle1) * radius;
                        float y1 = Mth.sin(angle1) * radius;

                        for (int segment = 0; segment < segments; segment++) {
                                float t0 = segment / (float) segments;
                                float t1 = (segment + 1) / (float) segments;
                                float z0 = Mth.lerp(t0, start, end);
                                float z1 = Mth.lerp(t1, start, end);
                                float alpha0 = alpha * alphaScale * beamDistanceAlpha(t0);
                                float alpha1 = alpha * alphaScale * beamDistanceAlpha(t1);
                                float v0 = scroll + z0 * 0.35f;
                                float v1 = scroll + z1 * 0.35f;
                                addBeamQuad(builder, ms, x0, y0, x1, y1, z0, z1,
                                                red, green, blue, alpha0, alpha1, v0, v1);
                        }
                }
        }

        // Get the beam distance alpha
        private static float beamDistanceAlpha(float distanceFraction) {
                return (float) Math.pow(Mth.clamp(1.0f - distanceFraction, 0.0f, 1.0f), 1.65D);
        }

        // Add the beam quad
        private static void addBeamQuad(VertexConsumer builder, PoseStack ms,
                                        float x0, float y0, float x1, float y1, float z0, float z1,
                                        float red, float green, float blue, float alpha0, float alpha1,
                                        float v0, float v1) {
                Matrix4f matrix = ms.last().pose();
                builder.addVertex(matrix, x0, y0, z0)
                                .setColor(red, green, blue, alpha0)
                                .setUv(0.0f, v0)
                                .setLight(0xF000F0)
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setNormal(0.0f, 1.0f, 0.0f);
                builder.addVertex(matrix, x1, y1, z0)
                                .setColor(red, green, blue, alpha0)
                                .setUv(1.0f, v0)
                                .setLight(0xF000F0)
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setNormal(0.0f, 1.0f, 0.0f);
                builder.addVertex(matrix, x1, y1, z1)
                                .setColor(red, green, blue, alpha1)
                                .setUv(1.0f, v1)
                                .setLight(0xF000F0)
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setNormal(0.0f, 1.0f, 0.0f);
                builder.addVertex(matrix, x0, y0, z1)
                                .setColor(red, green, blue, alpha1)
                                .setUv(0.0f, v1)
                                .setLight(0xF000F0)
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setNormal(0.0f, 1.0f, 0.0f);
        }

        // Check if this should render off screen
        @Override
        public boolean shouldRenderOffScreen(ThrusterBlockEntity blockEntity) {
                return true;
        }

        // Get the render bounding box
        @Override
        public @NotNull AABB getRenderBoundingBox(@NotNull ThrusterBlockEntity blockEntity) {
                return blockEntity.getRenderBoundingBox();
        }

        // Get the view distance
        @Override
        public int getViewDistance() {
                return 256;
        }
}
