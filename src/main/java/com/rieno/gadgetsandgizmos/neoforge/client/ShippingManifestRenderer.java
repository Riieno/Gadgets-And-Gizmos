package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlock;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

// Draw the Shipping Manifest
public final class ShippingManifestRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float TEXT_SCALE = 0.0055F;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int WRAP_WIDTH = 88;
    private static final float CONTENT_TOP_Y = -52.0F;
    private static final float LIST_LEFT_X = -44.0F;
    private static final float FOOTER_Y = 52.0F;
    private static final int RENDER_DISTANCE_CHUNKS = 4;
    private static final int CACHE_REFRESH_TICKS = 20;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cached level
    private static ClientLevel cachedLevel;
    // Cached chunk x
    private static int cachedChunkX = Integer.MIN_VALUE;
    // Cached chunk z
    private static int cachedChunkZ = Integer.MIN_VALUE;
    // Next cache refresh time
    private static long nextCacheRefreshTime;
    // Cached manifests
    private static List<ShippingManifestBlockEntity> cachedManifests = List.of();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping manifest
    private ShippingManifestRenderer() {
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
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }

        boolean renderContents = CTConfigs.CLIENT.renderShippingManifestContents.get();
        if (!renderContents) {
            cachedLevel = null;
            cachedManifests = List.of();
            return;
        }

        BlockPos center = minecraft.player.blockPosition();
        refreshManifestCacheIfNeeded(level, center);
        if (cachedManifests.isEmpty()) {
            return;
        }

        Vec3 cameraPos = evt.getCamera().getPosition();
        PoseStack poseStack = evt.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        boolean drewText = false;
        for (ShippingManifestBlockEntity manifest : cachedManifests) {
            if (manifest.isRemoved() || manifest.getLevel() != level
                    || !Vec3.atCenterOf(manifest.getBlockPos()).closerThan(cameraPos, 64.0D)) {
                continue;
            }
            syncModelVariant(manifest, true);
            int light = manifest.isManifestGlowing()
                    ? FULL_BRIGHT
                    : LevelRenderer.getLightColor(level, manifest.getBlockPos());
            renderManifest(manifest, poseStack, bufferSource, cameraPos, light);
            drewText = true;
        }
        if (drewText) {
            bufferSource.endBatch();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Refresh the manifest cache if needed
    private static void refreshManifestCacheIfNeeded(ClientLevel level, BlockPos center) {
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        long gameTime = level.getGameTime();
        if (level == cachedLevel
                && chunkX == cachedChunkX
                && chunkZ == cachedChunkZ
                && gameTime < nextCacheRefreshTime) {
            return;
        }

        List<ShippingManifestBlockEntity> manifests = new ArrayList<>();
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getLoadedWorldBlockEntities(
                level, center, RENDER_DISTANCE_CHUNKS)) {
            if (blockEntity instanceof ShippingManifestBlockEntity manifest && !manifest.isRemoved()) {
                manifests.add(manifest);
            }
        }
        cachedLevel = level;
        cachedChunkX = chunkX;
        cachedChunkZ = chunkZ;
        nextCacheRefreshTime = gameTime + CACHE_REFRESH_TICKS;
        cachedManifests = manifests;
    }

    // Sync the model variant
    private static void syncModelVariant(ShippingManifestBlockEntity manifest, boolean renderContents) {
        if (manifest.getLevel() == null) {
            return;
        }
        if (manifest.hasFluidHandler()) {
            return;
        }
        BlockState state = manifest.getBlockState();
        if (!state.hasProperty(ShippingManifestBlock.SHOW_BLANK)) {
            return;
        }
        boolean hasVisibleContents = !manifest.getDisplayEntries().isEmpty();
        boolean shouldShowBlank = renderContents && (manifest.hasFluidHandler() || hasVisibleContents);
        if (state.getValue(ShippingManifestBlock.SHOW_BLANK) == shouldShowBlank) {
            return;
        }
        manifest.getLevel().setBlock(manifest.getBlockPos(),
                state.setValue(ShippingManifestBlock.SHOW_BLANK, shouldShowBlank),
                Block.UPDATE_CLIENTS);
    }

    // Draw the manifest
    private static void renderManifest(ShippingManifestBlockEntity manifest, PoseStack poseStack,
                                       MultiBufferSource bufferSource, Vec3 cameraPos, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<ShippingManifestBlockEntity.DisplayEntry> entries = manifest.getDisplayEntries();
        int textColor = manifest.getWorldTextColor();
        poseStack.pushPose();
        BlockPos pos = manifest.getBlockPos();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
        applyBlockStateRotation(poseStack, manifest.getBlockState());
        poseStack.translate(0.5D, 1.25D / 16.0D, 0.505D);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        if (entries.isEmpty()) {
            drawCentered(Component.translatable("gui.createthrusters.shipping_manifest.empty").getString(),
                    0.0F, textColor, font, poseStack, bufferSource, light);
            poseStack.popPose();
            return;
        }

        int offset = manifest.getClientScrollOffset();
        int end = Math.min(entries.size(), offset + ShippingManifestBlockEntity.DISPLAY_ROWS);

        float cursorY = CONTENT_TOP_Y;
        for (int idx = offset; idx < end; idx++) {
            ShippingManifestBlockEntity.DisplayEntry entry = entries.get(idx);
            Component line = entry.stack().isEmpty()
                    ? Component.literal(entry.text())
                    : Component.literal(entry.amount() + "x ").append(entry.stack().getHoverName());
            List<FormattedCharSequence> wrapped = font.split(line, WRAP_WIDTH);
            if (wrapped.isEmpty()) {
                cursorY += font.lineHeight;
                continue;
            }
            drawWrappedLeft(wrapped, LIST_LEFT_X, cursorY, textColor, font, poseStack, bufferSource, light);
            cursorY += wrapped.size() * font.lineHeight;
        }

        String posText = Component.translatable("createthrusters.shipping_manifest.display.position",
                offset + 1, end, entries.size()).getString();
        drawCentered(posText, FOOTER_Y, textColor, font, poseStack, bufferSource, light);
        poseStack.popPose();
    }

    // Draw the wrapped left
    private static void drawWrappedLeft(List<FormattedCharSequence> lines, float x, float y, int col, Font font,
                                        PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            font.drawInBatch(lines.get(lineIndex), x, y + lineIndex * font.lineHeight, col, false, poseStack.last().pose(),
                    bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        }
    }

    // Draw the centered
    private static void drawCentered(String text, float y, int col, Font font, PoseStack poseStack,
                                     MultiBufferSource bufferSource, int light) {
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, y, col, false, poseStack.last().pose(),
                bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }

    // Apply the block state rotation
    private static void applyBlockStateRotation(PoseStack poseStack, BlockState state) {
        AttachFace face = state.getValue(ShippingManifestBlock.FACE);
        int xRotation = switch (face) {
            case WALL -> 90;
            case CEILING -> 180;
            case FLOOR -> 0;
        };
        int yRotation = switch (face) {
            case CEILING -> switch (state.getValue(ShippingManifestBlock.FACING)) {
                case NORTH -> 180;
                case EAST -> 270;
                case WEST -> 90;
                default -> 0;
            };
            default -> switch (state.getValue(ShippingManifestBlock.FACING)) {
                case SOUTH -> 180;
                case EAST -> 90;
                case WEST -> 270;
                default -> 0;
            };
        };

        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRotation));
        poseStack.mulPose(Axis.XP.rotationDegrees(-xRotation));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }
}
