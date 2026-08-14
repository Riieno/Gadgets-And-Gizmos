package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlock;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.ControllerEmbeddedMount;
import com.rieno.gadgetsandgizmos.content.PortableAdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.PortableAnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannel;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.control.CustomKeyEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.RenderTypeHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

// Draw the controller's levers, buttons and labels from its live channel state
public class AnalogueContraptionControllerRenderer extends SafeBlockEntityRenderer<AnalogueContraptionControllerBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String DIRECT_TARGET_OPTION_PREFIX = "::opt:";
    private static final String DIRECT_TARGET_PROPERTY_PREFIX = "::prop:";
    private static final float THROTTLE_MAX_TILT = 38.0f;
    private static final float JOYSTICK_MAX_TILT = 24.0f;
    private static final double BUTTON_PRESS_DEPTH = 1.0D / 16.0D;
    private static final float YAW_STICK_WEIGHT = 0.65f;
    private static final float THROTTLE_PIVOT_X = 6.0f;
    private static final float THROTTLE_PIVOT_Y = 1.0f;
    private static final float THROTTLE_PIVOT_Z = 4.5f;
    private static final float JOYSTICK_PIVOT_X = 11.5f;
    private static final float JOYSTICK_PIVOT_Y = 1.0f;
    private static final float JOYSTICK_PIVOT_Z = 4.5f;
    private static final float CLOCK_PIVOT_X = 4.0f;
    private static final float CLOCK_PIVOT_Y = 4.0f;
    private static final float CLOCK_PIVOT_Z = 9.0f;
    private static final float OMETER_PANEL_PIVOT_X = 8.0f;
    private static final float OMETER_PANEL_PIVOT_Y = 3.0f;
    private static final float OMETER_PANEL_PIVOT_Z = 8.0f;
    private static final float OMETER_LEFT_DIGIT_X = 1.99f;
    private static final float OMETER_RIGHT_DIGIT_X = 6.99f;
    private static final float OMETER_DIGIT_Y = 8.25f;
    private static final float OMETER_DIGIT_Z = 6.12f;
    private static final float OMETER_DIGIT_SCALE = 0.025f;
    private static final float OMETER_DIGIT_HEIGHT = 3.0f;
    private static final float OMETER_DIGIT_FRONT_OFFSET = 0.002f;
    private static final int OMETER_DIGIT_BRIGHT_COLOR = 0xFFFFA12B;
    private static final String[] DIGIT_TEXT = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final String[] CLOCK_TEXT = createClockTextCache();
    private static final Quaternionf OMETER_PANEL_ROTATION = Axis.XP.rotationDegrees(45.0f);
    private static final Quaternionf CLOCK_FACE_ROTATION_X = Axis.XP.rotationDegrees(-45.0f);
    private static final Quaternionf FACE_BACK_ROTATION = Axis.YP.rotationDegrees(180.0f);
    private static final Quaternionf[][] FACING_ROTATIONS = createFacingRotations();
    private static final List<PartialModel> BUTTON_MODELS = List.of(
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_1,
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_2,
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_3,
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_4,
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_5,
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_6,
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_7,
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_8,
            CTPartialModels.ANALOGUE_CONTROLLER_BUTTON_9
    );
    private static final List<PartialModel> ADVANCED_BUTTON_MODELS = List.of(
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_1,
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_2,
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_3,
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_4,
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_5,
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_6,
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_7,
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_8,
            CTPartialModels.ADVANCED_CONTROLLER_BUTTON_9
    );
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked embedded render caches
    private final Map<AnalogueContraptionControllerBlockEntity, EmbeddedRenderCache> embeddedRenderCaches =
            Collections.synchronizedMap(new WeakHashMap<>());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue contraption controller
    public AnalogueContraptionControllerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the analogue contraption controller
    @Override
    protected void renderSafe(AnalogueContraptionControllerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource bufferSource, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (be instanceof AdvancedContraptionControllerBlockEntity advanced
                && advanced.displaysShipInitializationProgressFor(Minecraft.getInstance().player)) {
            renderShipInitProgress(advanced, ms, bufferSource);
        }
        renderEmbeddedBlock(be, ms, bufferSource, light, overlay);

        int animatedPartLight = light;
        boolean portable = be instanceof PortableAnalogueContraptionControllerBlockEntity
                || be instanceof PortableAdvancedContraptionControllerBlockEntity;
        ms.pushPose();
        applyFacing(ms, state);
        try {
            if (!portable) {
                boolean advanced = be instanceof AdvancedContraptionControllerBlockEntity;
                if (state.getValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB)) {
                    VertexConsumer cutout = bufferSource.getBuffer(RenderType.cutoutMipped());
                    PartialModel body = advanced
                            ? CTPartialModels.ADVANCED_CONTRAPTION_CONTROLLER_BODY
                            : CTPartialModels.ANALOGUE_CONTRAPTION_CONTROLLER_BODY;
                    renderPartial(body, state, ms, cutout, animatedPartLight, overlay);
                }
                if (!advanced) {
                    VertexConsumer translucent = bufferSource.getBuffer(RenderType.translucent());
                    renderPlacedOmeterShells(ms, translucent, state, animatedPartLight, overlay);
                    renderPlacedOutputOmeters(be, ms, bufferSource);
                }
                return;
            }

            VertexConsumer cutout = bufferSource.getBuffer(RenderType.cutoutMipped());
            AnimationState animationState = resolveAnimationState(be);
            boolean advanced = be instanceof AdvancedContraptionControllerBlockEntity;
            PartialModel throttle = advanced ? CTPartialModels.ADVANCED_CONTROLLER_THROTTLE : CTPartialModels.ANALOGUE_CONTROLLER_THROTTLE;
            PartialModel joystick = advanced ? CTPartialModels.ADVANCED_CONTROLLER_JOYSTICK : CTPartialModels.ANALOGUE_CONTROLLER_JOYSTICK;
            PartialModel clock = advanced ? CTPartialModels.ADVANCED_CONTROLLER_CLOCK : CTPartialModels.ANALOGUE_CONTROLLER_CLOCK;
            List<PartialModel> buttons = advanced ? ADVANCED_BUTTON_MODELS : BUTTON_MODELS;

            renderRotatedPart(ms, cutout, state, animatedPartLight, overlay, throttle,
                THROTTLE_PIVOT_X, THROTTLE_PIVOT_Y, THROTTLE_PIVOT_Z,
                Axis.XP.rotationDegrees(animationState.throttleValue() * THROTTLE_MAX_TILT));

            renderJoystick(ms, cutout, state, animatedPartLight, overlay, joystick,
                    animationState.stickX(), animationState.stickZ());
            renderPartial(clock, state, ms, cutout, animatedPartLight, overlay);
            renderButtons(buttons, animationState.buttonPressed(), ms, cutout, state, animatedPartLight, overlay);
            renderClockText(be, ms, bufferSource, animatedPartLight);
        } finally {
            ms.popPose();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the ship init progress
    private void renderShipInitProgress(
            AdvancedContraptionControllerBlockEntity blockEntity,
            PoseStack poseStack,
            MultiBufferSource buffer
    ) {
        double offsetX = 0.5D;
        double offsetY = 2.5D;
        double offsetZ = 0.5D;
        SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);
        if (subLevel != null && subLevel.getPlot().getBoundingBox() != null) {
            var bounds = subLevel.getPlot().getBoundingBox();
            offsetX = (bounds.minX() + bounds.maxX() + 1.0D) * 0.5D
                    - blockEntity.getBlockPos().getX();
            offsetY = bounds.maxY() + 2.0D - blockEntity.getBlockPos().getY();
            offsetZ = (bounds.minZ() + bounds.maxZ() + 1.0D) * 0.5D
                    - blockEntity.getBlockPos().getZ();
        }

        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, offsetZ);
        Quaternionf parentRotation = new Matrix3f(poseStack.last().pose())
                .getNormalizedRotation(new Quaternionf());
        poseStack.mulPose(parentRotation.invert());
        poseStack.mulPose(Minecraft.getInstance()
                .getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Font font = Minecraft.getInstance().font;
        int percent = Mth.clamp(blockEntity.getShipInitializationProgressPercent(), 0, 100);
        int filled = Mth.clamp((percent + 2) / 5, 0, 20);
        String heading = "Initializing ship controls: " + percent + "%";
        String progressBar = "[" + "=".repeat(filled) + "-".repeat(20 - filled) + "]";
        String status = blockEntity.getShipInitializationProgressStatus();
        if (status == null) {
            status = "";
        } else if (status.length() > 64) {
            status = status.substring(0, 64);
        }
        drawCenteredWorldText(font, heading, -12.0F, 0xFFFFAA00, poseStack, buffer);
        drawCenteredWorldText(font, progressBar, 0.0F, 0xFF67E86B, poseStack, buffer);
        if (!status.isBlank()) {
            drawCenteredWorldText(font, status, 12.0F, 0xFFFFFFFF, poseStack, buffer);
        }
        poseStack.popPose();
    }

    // Draw the centered world text
    private static void drawCenteredWorldText(
            Font font,
            String text,
            float y,
            int col,
            PoseStack poseStack,
            MultiBufferSource buffer
    ) {
        float x = -font.width(text) * 0.5F;
        Matrix4f pose = poseStack.last().pose();
        font.drawInBatch(text, x, y, col, false, pose, buffer,
                Font.DisplayMode.SEE_THROUGH, 0x80000000, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, y, col, false, pose, buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    // Check if this should render off screen
    @Override
    public boolean shouldRenderOffScreen(AnalogueContraptionControllerBlockEntity blockEntity) {
        return blockEntity instanceof AdvancedContraptionControllerBlockEntity advanced
                && advanced.displaysShipInitializationProgressFor(Minecraft.getInstance().player);
    }

    // Get the view distance
    @Override
    public int getViewDistance() {
        return 256;
    }

    // Draw the embedded block
    private void renderEmbeddedBlock(AnalogueContraptionControllerBlockEntity be, PoseStack ms,
                                     MultiBufferSource bufferSource, int light, int overlay) {
        BlockState embeddedState = be.getEmbeddedBlockState();
        if (embeddedState == null) {
            embeddedRenderCaches.remove(be);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(embeddedState);
        BlockEntity preview = be.getEmbeddedBlockEntityPreview();
        if (EmbeddedCopycatsKineticRenderer.render(
                preview, embeddedState, ms, bufferSource, light, overlay)) {
            embeddedRenderCaches.remove(be);
            return;
        }
        int surroundingsHash = surroundingsHash(be);
        EmbeddedRenderCache cache = embeddedRenderCaches.get(be);
        if (cache == null || !cache.matches(embeddedState, preview, model, surroundingsHash)) {
            cache = buildEmbeddedRenderCache(be, embeddedState, preview, model, surroundingsHash);
            if (cache != null) {
                embeddedRenderCaches.put(be, cache);
            }
        }

        if (cache == null) {
            minecraft.getBlockRenderer().renderSingleBlock(
                    embeddedState, ms, bufferSource, light, overlay, embeddedModelData(be, embeddedState), null);
            return;
        }

        PoseStack.Pose pose = ms.last();
        for (EmbeddedRenderLayer layer : cache.layers()) {
            VertexConsumer consumer = bufferSource.getBuffer(layer.renderType());
            for (BakedQuad quad : layer.quads()) {
                float red = quad.isTinted() ? cache.red() : 1.0F;
                float green = quad.isTinted() ? cache.green() : 1.0F;
                float blue = quad.isTinted() ? cache.blue() : 1.0F;
                consumer.putBulkData(pose, quad, red, green, blue, 1.0F, light, overlay);
            }
        }
    }

    // Build the embedded render cache
    private EmbeddedRenderCache buildEmbeddedRenderCache(AnalogueContraptionControllerBlockEntity be,
                                                          BlockState embeddedState, BlockEntity preview,
                                                          BakedModel model, int surroundingsHash) {
        if (embeddedState.getRenderShape() != RenderShape.MODEL) {
            return null;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ModelData modelData = embeddedModelData(be, embeddedState);
            RandomSource random = RandomSource.create(42L);
            List<EmbeddedRenderLayer> layers = new ArrayList<>();
            for (RenderType blockRenderType : model.getRenderTypes(embeddedState, random, modelData)) {
                List<BakedQuad> quads = new ArrayList<>();
                for (Direction dir : Direction.values()) {
                    random.setSeed(42L);
                    quads.addAll(model.getQuads(embeddedState, dir, random, modelData, blockRenderType));
                }
                random.setSeed(42L);
                quads.addAll(model.getQuads(embeddedState, null, random, modelData, blockRenderType));
                if (!quads.isEmpty()) {
                    layers.add(new EmbeddedRenderLayer(
                            RenderTypeHelper.getEntityRenderType(blockRenderType, false),
                            List.copyOf(quads)));
                }
            }

            int col = minecraft.getBlockColors().getColor(embeddedState, null, null, 0);
            float red = Mth.clamp((col >> 16 & 0xFF) / 255.0F, 0.0F, 1.0F);
            float green = Mth.clamp((col >> 8 & 0xFF) / 255.0F, 0.0F, 1.0F);
            float blue = Mth.clamp((col & 0xFF) / 255.0F, 0.0F, 1.0F);
            return new EmbeddedRenderCache(
                    embeddedState,
                    preview,
                    model,
                    surroundingsHash,
                    red,
                    green,
                    blue,
                    List.copyOf(layers));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // Get the surroundings hash
    private static int surroundingsHash(AnalogueContraptionControllerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return 0;
        }
        BlockPos pos = be.getBlockPos();
        int hash = 1;
        for (Direction dir : Direction.values()) {
            hash = 31 * hash + level.getBlockState(pos.relative(dir)).hashCode();
        }
        return hash;
    }

    // Get the embedded model data
    private ModelData embeddedModelData(AnalogueContraptionControllerBlockEntity be, BlockState embeddedState) {
        Level level = be.getLevel();
        BlockEntity embeddedBlockEntity = be.getEmbeddedBlockEntityPreview();
        if (level != null && embeddedBlockEntity != null) {
            try {
                BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(embeddedState);
                BlockAndTintGetter renderLevel = new EmbeddedBlockRenderView(
                        level,
                        be.getBlockPos(),
                        embeddedState,
                        embeddedBlockEntity);
                return model.getModelData(
                        renderLevel,
                        be.getBlockPos(),
                        embeddedState,
                        embeddedBlockEntity.getModelData());
            } catch (RuntimeException ignored) {
            }
        }
        BlockState material = be.getEmbeddedSlabMaterial();
        return material == null
                ? ModelData.EMPTY
                : ModelData.builder().with(CopycatModel.MATERIAL_PROPERTY, material).build();
    }

    // Store the embedded block render view
    private record EmbeddedBlockRenderView(Level delegate, BlockPos embeddedPos, BlockState embeddedState,
                                           BlockEntity embeddedBlockEntity) implements BlockAndTintGetter {
        // Get the block entity
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return embeddedPos.equals(pos) ? embeddedBlockEntity : delegate.getBlockEntity(pos);
        }

        // Get the block state
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return embeddedPos.equals(pos) ? embeddedState : delegate.getBlockState(pos);
        }

        // Get the fluid state
        @Override
        public FluidState getFluidState(BlockPos pos) {
            return embeddedPos.equals(pos) ? embeddedState.getFluidState() : delegate.getFluidState(pos);
        }

        // Get the shade
        @Override
        public float getShade(Direction dir, boolean shade) {
            return delegate.getShade(dir, shade);
        }

        // Get the light engine
        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        // Get the block tint
        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return delegate.getBlockTint(pos, colorResolver);
        }

        // Get the height
        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        // Get the min build height
        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }
    }

    // Store the embedded render layer
    private record EmbeddedRenderLayer(RenderType renderType, List<BakedQuad> quads) {
    }

    // Store the embedded render cache
    private record EmbeddedRenderCache(BlockState state, BlockEntity preview, BakedModel model,
                                       int surroundingsHash, float red, float green, float blue,
                                       List<EmbeddedRenderLayer> layers) {
        // Check if this matches the value
        private boolean matches(BlockState state, BlockEntity preview, BakedModel model, int surroundingsHash) {
            return this.state == state
                    && this.preview == preview
                    && this.model == model
                    && this.surroundingsHash == surroundingsHash;
        }
    }

    // Draw the placed output ometers
    private void renderPlacedOutputOmeters(AnalogueContraptionControllerBlockEntity be, PoseStack ms,
                                           MultiBufferSource bufferSource) {
        int signal = getPlacedOutputSignal(be);
        ms.pushPose();
        rotateAroundPivot(ms, OMETER_PANEL_PIVOT_X, OMETER_PANEL_PIVOT_Y, OMETER_PANEL_PIVOT_Z);
        ms.mulPose(OMETER_PANEL_ROTATION);
        unrotateAroundPivot(ms, OMETER_PANEL_PIVOT_X, OMETER_PANEL_PIVOT_Y, OMETER_PANEL_PIVOT_Z);
        renderOmeterDigit(DIGIT_TEXT[signal / 10], OMETER_RIGHT_DIGIT_X, ms, bufferSource);
        renderOmeterDigit(DIGIT_TEXT[signal % 10], OMETER_LEFT_DIGIT_X, ms, bufferSource);
        ms.popPose();
    }

    // Get the placed output signal
    private int getPlacedOutputSignal(AnalogueContraptionControllerBlockEntity be) {
        return Mth.clamp(be.getOmeterOutputSignal(), 0, 15);
    }

    // Draw the placed ometer shells
    private void renderPlacedOmeterShells(PoseStack ms, VertexConsumer consumer, BlockState state,
                                          int light, int overlay) {
        renderPartial(CTPartialModels.ANALOGUE_CONTRAPTION_CONTROLLER_OMETER_1,
                state, ms, consumer, light, overlay);
        renderPartial(CTPartialModels.ANALOGUE_CONTRAPTION_CONTROLLER_OMETER_0,
                state, ms, consumer, light, overlay);
    }

    // Draw the ometer digit
    private void renderOmeterDigit(String digit, float localX, PoseStack ms, MultiBufferSource bufferSource) {
        ms.pushPose();
        ms.translate(localX / 16.0D, OMETER_DIGIT_Y / 16.0D, OMETER_DIGIT_Z / 16.0D);
        ms.mulPose(FACE_BACK_ROTATION);
        ms.translate(0.0D, 0.0D, OMETER_DIGIT_FRONT_OFFSET);
        ms.scale(OMETER_DIGIT_SCALE, -OMETER_DIGIT_SCALE, OMETER_DIGIT_SCALE);
        renderOmeterDigitFace(digit, ms, bufferSource);
        ms.popPose();
    }

    // Draw the ometer digit face
    private void renderOmeterDigitFace(String digit, PoseStack ms, MultiBufferSource bufferSource) {
        Font font = Minecraft.getInstance().font;
        int width = font.width(digit);
        float x = width / -2.0f;
        float y = -OMETER_DIGIT_HEIGHT;
        Matrix4f pose = ms.last().pose();
        font.drawInBatch(digit, x, y, OMETER_DIGIT_BRIGHT_COLOR, false, pose, bufferSource,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    // Draw the buttons
    private void renderButtons(List<PartialModel> models, boolean[] buttonPressed, PoseStack ms, VertexConsumer consumer,
                               BlockState state, int light, int overlay) {
        for (int idx = 0; idx < models.size(); idx++) {
            boolean pressed = idx < buttonPressed.length && buttonPressed[idx];
            ms.pushPose();
            if (pressed) {
                ms.translate(0.0D, -BUTTON_PRESS_DEPTH, 0.0D);
            }
            renderPartial(models.get(idx), state, ms, consumer, light, overlay);
            ms.popPose();
        }
    }

    // Render a controller joystick
    private void renderJoystick(PoseStack ms, VertexConsumer consumer, BlockState state, int light, int overlay,
                                PartialModel model, float stickX, float stickZ) {
        ms.pushPose();
        rotateAroundPivot(ms, JOYSTICK_PIVOT_X, JOYSTICK_PIVOT_Y, JOYSTICK_PIVOT_Z);
        ms.mulPose(Axis.ZP.rotationDegrees(stickX * JOYSTICK_MAX_TILT));
        ms.mulPose(Axis.XP.rotationDegrees(stickZ * JOYSTICK_MAX_TILT));
        unrotateAroundPivot(ms, JOYSTICK_PIVOT_X, JOYSTICK_PIVOT_Y, JOYSTICK_PIVOT_Z);
        renderPartial(model, state, ms, consumer, light, overlay);
        ms.popPose();
    }

    // Draw the rotated part
    private void renderRotatedPart(PoseStack ms, VertexConsumer consumer, BlockState state, int light, int overlay,
                                   PartialModel model, float pivotX, float pivotY, float pivotZ,
                                   org.joml.Quaternionf rotation) {
        ms.pushPose();
        rotateAroundPivot(ms, pivotX, pivotY, pivotZ);
        ms.mulPose(rotation);
        unrotateAroundPivot(ms, pivotX, pivotY, pivotZ);
        renderPartial(model, state, ms, consumer, light, overlay);
        ms.popPose();
    }

    // Draw the partial
    private void renderPartial(PartialModel model, BlockState state, PoseStack ms, VertexConsumer consumer,
                               int light, int overlay) {
        CachedBuffers.partial(model, state)
                .light(light)
                .overlay(overlay)
                .renderInto(ms, consumer);
    }

    // Draw the clock text
    private void renderClockText(AnalogueContraptionControllerBlockEntity be, PoseStack ms, MultiBufferSource bufferSource,
                                 int light) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        String text = formatClockText(level, be.getBlockPos().asLong());
        int textWidth = font.width(text);

        ms.pushPose();
        ms.translate(4.98D / 16.0D, 4.78D / 16.0D, 9.18D / 16.0D);
        ms.mulPose(CLOCK_FACE_ROTATION_X);
        ms.mulPose(FACE_BACK_ROTATION);
        ms.translate(0.0D, 0.0D, 0.002D);

        float baseScale = 0.00625f;
        float maxFaceWidthPx = 34.0f;
        float widthScale = textWidth <= 0 ? 1.0f : Math.min(1.0f, maxFaceWidthPx / textWidth);
        float scale = baseScale * widthScale;
        ms.scale(scale, -scale, scale);

        Matrix4f pose = ms.last().pose();
        font.drawInBatch(text, -textWidth / 2.0f, -4.0f, 0xFFBCE7FF, false, pose, bufferSource,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        ms.popPose();
    }

    // Format the clock text
    private String formatClockText(Level level, long positionSeed) {
        long dayTime = Math.floorMod(level.getDayTime(), 24000L);
        int hours24 = (int) ((dayTime / 1000L + 6L) % 24L);
        int minutes = (int) ((dayTime % 1000L) * 60L / 1000L);
        String base = CLOCK_TEXT[hours24 * 60 + minutes];
        if (level.dimensionType().natural()) {
            return base;
        }
        return glitchClockText(base, level.getGameTime(), positionSeed);
    }

    // Create the clock text cache
    private static String[] createClockTextCache() {
        String[] values = new String[24 * 60];
        for (int hours24 = 0; hours24 < 24; hours24++) {
            int hours12 = hours24 % 12;
            if (hours12 == 0) {
                hours12 = 12;
            }
            String suffix = hours24 >= 12 ? " PM" : " AM";
            for (int minutes = 0; minutes < 60; minutes++) {
                values[hours24 * 60 + minutes] = twoDigits(hours12) + ":" + twoDigits(minutes) + suffix;
            }
        }
        return values;
    }

    // Get the two digits
    private static String twoDigits(int val) {
        return val < 10 ? "0" + val : Integer.toString(val);
    }

    // Get the glitch clock text
    private String glitchClockText(String base, long gameTime, long positionSeed) {
        String palette = "0123456789#?:*";
        long seed = (gameTime * 341873128712L) ^ (positionSeed * 132897987541L);
        StringBuilder builder = new StringBuilder(base);

        for (int idx = 0; idx < builder.length(); idx++) {
            char current = builder.charAt(idx);
            if (current == ' ') {
                continue;
            }
            long noise = seed + (idx * 0x9E3779B97F4A7C15L);
            if ((noise & 3L) == 0L) {
                int glyphIndex = (int) Math.floorMod(noise >> 3, palette.length());
                builder.setCharAt(idx, palette.charAt(glyphIndex));
            }
        }

        return builder.toString();
    }

    // Resolve the animation state
    private AnimationState resolveAnimationState(AnalogueContraptionControllerBlockEntity be) {
        float throttleValue = 0.0f;
        float pitchValue = 0.0f;
        float rollValue = 0.0f;
        float yawValue = 0.0f;
        boolean[] buttonPressed = new boolean[BUTTON_MODELS.size()];

        for (AnalogueControlChannel channelDef : AnalogueControlChannel.values()) {
            String channelId = channelDef.id();
            AnalogueChannel channel = be.getChannel(channelId);
            if (channel == null) {
                continue;
            }

            float magnitude = (float) Mth.clamp(channel.getUnsignedValue(), 0.0D, 1.0D);
            if (magnitude <= 0.001f) {
                continue;
            }

            String preset = be.getBindingPreset(channelId);
            ControllerDirectTargetReference inputTarget = be.getInputTarget(channelId);
            boolean keyDriven = be.getKeyBinding(channelId) >= 0 && inputTarget == null && channel.isPressed();
            if (keyDriven) {
                buttonPressed[pickButtonIndex(channelId)] = true;
            }

            AnimationRouting routing = determineRouting(inputTarget, preset);
            if (routing == AnimationRouting.NONE) {
                continue;
            }

            String mappedForPreset = mapChannelIdByPreset(channelId, preset);
            AxisAccumulation axis = axisContribution(mappedForPreset, magnitude);
            if (routing == AnimationRouting.THROTTLE) {
                throttleValue = clampAbsMax(throttleValue, axis.throttle());
            } else {
                pitchValue = clampAbsMax(pitchValue, axis.pitch());
                rollValue = clampAbsMax(rollValue, axis.roll());
                yawValue = clampAbsMax(yawValue, axis.yaw());
            }
        }

        for (CustomKeyEntry entry : be.getCustomKeyEntries()) {
            if (entry == null || !be.isCustomEntryActive(entry.id())) {
                continue;
            }
            if (entry.keyCode >= 0 && entry.inputTarget == null && be.isCustomEntryPressed(entry.id())) {
                buttonPressed[pickButtonIndex(entry.id())] = true;
            }

            String channelId = resolveMappedChannelId(entry.id(), entry.inputTarget);
            AnimationRouting routing = determineRouting(entry.inputTarget, entry.bindingPreset);
            if (routing == AnimationRouting.NONE) {
                continue;
            }

            String mappedForPreset = mapChannelIdByPreset(channelId, entry.bindingPreset);
            AxisAccumulation axis = axisContribution(mappedForPreset, 1.0f);
            if (routing == AnimationRouting.THROTTLE) {
                throttleValue = clampAbsMax(throttleValue, axis.throttle());
            } else {
                pitchValue = clampAbsMax(pitchValue, axis.pitch());
                rollValue = clampAbsMax(rollValue, axis.roll());
                yawValue = clampAbsMax(yawValue, axis.yaw());
            }
        }

        float stickX = Mth.clamp(rollValue + yawValue * YAW_STICK_WEIGHT, -1.0f, 1.0f);
        float stickZ = Mth.clamp(-pitchValue, -1.0f, 1.0f);
        if (be instanceof AdvancedContraptionControllerBlockEntity advanced) {
            stickX = Mth.clamp(stickX + (float) advanced.getMouseInputValue("mouse_x"), -1.0f, 1.0f);
            stickZ = Mth.clamp(stickZ + (float) advanced.getMouseInputValue("mouse_y"), -1.0f, 1.0f);
            if (advanced.isMouseInputActive("left_click")) buttonPressed[0] = true;
            if (advanced.isMouseInputActive("right_click")) buttonPressed[1] = true;
            if (advanced.isMouseInputActive("middle_click")) buttonPressed[2] = true;
        }
        return new AnimationState(Mth.clamp(throttleValue, -1.0f, 1.0f), stickX, stickZ, buttonPressed);
    }

    // Get the determine routing
    private static AnimationRouting determineRouting(ControllerDirectTargetReference inputTarget, String preset) {
        String presetId = preset == null ? "none" : preset.toLowerCase(Locale.ROOT);
        return switch (presetId) {
            case "pitch", "roll", "yaw" -> AnimationRouting.JOYSTICK;
            case "throttle" -> AnimationRouting.THROTTLE;
            default -> routingFromInputTarget(inputTarget);
        };
    }

    // Get the routing from input target
    private static AnimationRouting routingFromInputTarget(ControllerDirectTargetReference inputTarget) {
        if (inputTarget == null) {
            return AnimationRouting.NONE;
        }
        String typeId = inputTarget.targetTypeId() == null ? "" : inputTarget.targetTypeId().toLowerCase(Locale.ROOT);
        if (typeId.contains("joystick")) {
            return AnimationRouting.JOYSTICK;
        }
        if (typeId.contains("throttle") || typeId.contains("analog_lever") || typeId.contains("analog_transmission")) {
            return AnimationRouting.THROTTLE;
        }
        return AnimationRouting.NONE;
    }

    // Get the axis contribution
    private static AxisAccumulation axisContribution(String channelId, float magnitude) {
        AnalogueControlChannel channel = AnalogueControlChannel.byId(channelId);
        if (channel == null) {
            return AxisAccumulation.ZERO;
        }
        return switch (channel) {
            case PITCH_UP -> new AxisAccumulation(0.0f, magnitude, 0.0f, 0.0f);
            case PITCH_DOWN -> new AxisAccumulation(0.0f, -magnitude, 0.0f, 0.0f);
            case ROLL_LEFT -> new AxisAccumulation(0.0f, 0.0f, -magnitude, 0.0f);
            case ROLL_RIGHT -> new AxisAccumulation(0.0f, 0.0f, magnitude, 0.0f);
            case YAW_LEFT -> new AxisAccumulation(0.0f, 0.0f, 0.0f, -magnitude);
            case YAW_RIGHT -> new AxisAccumulation(0.0f, 0.0f, 0.0f, magnitude);
            case THROTTLE_UP -> new AxisAccumulation(magnitude, 0.0f, 0.0f, 0.0f);
            case THROTTLE_DOWN -> new AxisAccumulation(-magnitude, 0.0f, 0.0f, 0.0f);
            default -> AxisAccumulation.ZERO;
        };
    }

    // Resolve the mapped channel id
    private static String resolveMappedChannelId(String fallbackChannelId, ControllerDirectTargetReference target) {
        if (target == null || target.targetId() == null) {
            return fallbackChannelId;
        }
        String targetId = target.targetId();
        int optionIndex = targetId.indexOf(DIRECT_TARGET_OPTION_PREFIX);
        if (optionIndex < 0) {
            return fallbackChannelId;
        }
        String optionChannel = targetId.substring(optionIndex + DIRECT_TARGET_OPTION_PREFIX.length()).trim();
        int propertyIndex = optionChannel.indexOf(DIRECT_TARGET_PROPERTY_PREFIX);
        if (propertyIndex >= 0) {
            optionChannel = optionChannel.substring(0, propertyIndex).trim();
        }
        return optionChannel.isEmpty() ? fallbackChannelId : optionChannel;
    }

    // Map the channel id by preset
    private static String mapChannelIdByPreset(String channelId, String preset) {
        if (channelId == null || preset == null || preset.isBlank()) {
            return channelId;
        }
        String presetId = preset.toLowerCase(Locale.ROOT);
        if (!isDirectionalPolarityChannel(channelId)) {
            return channelId;
        }

        boolean positive = isPositivePolarityChannel(channelId);
        return switch (presetId) {
            case "pitch" -> positive ? AnalogueControlChannel.PITCH_UP.id() : AnalogueControlChannel.PITCH_DOWN.id();
            case "roll" -> positive ? AnalogueControlChannel.ROLL_RIGHT.id() : AnalogueControlChannel.ROLL_LEFT.id();
            case "yaw" -> positive ? AnalogueControlChannel.YAW_RIGHT.id() : AnalogueControlChannel.YAW_LEFT.id();
            case "throttle" -> positive ? AnalogueControlChannel.THROTTLE_UP.id() : AnalogueControlChannel.THROTTLE_DOWN.id();
            default -> channelId;
        };
    }

    // Check if this is a directional polarity channel
    private static boolean isDirectionalPolarityChannel(String channelId) {
        return isPositivePolarityChannel(channelId) || isNegativePolarityChannel(channelId);
    }

    // Check if this is a positive polarity channel
    private static boolean isPositivePolarityChannel(String channelId) {
        AnalogueControlChannel channel = AnalogueControlChannel.byId(channelId);
        return channel == AnalogueControlChannel.PITCH_UP
                || channel == AnalogueControlChannel.ROLL_RIGHT
                || channel == AnalogueControlChannel.YAW_RIGHT
                || channel == AnalogueControlChannel.THROTTLE_UP
                || channel == AnalogueControlChannel.STRAFE_RIGHT
                || channel == AnalogueControlChannel.LIFT_UP;
    }

    // Check if this is a negative polarity channel
    private static boolean isNegativePolarityChannel(String channelId) {
        AnalogueControlChannel channel = AnalogueControlChannel.byId(channelId);
        return channel == AnalogueControlChannel.PITCH_DOWN
                || channel == AnalogueControlChannel.ROLL_LEFT
                || channel == AnalogueControlChannel.YAW_LEFT
                || channel == AnalogueControlChannel.THROTTLE_DOWN
                || channel == AnalogueControlChannel.STRAFE_LEFT
                || channel == AnalogueControlChannel.LIFT_DOWN;
    }

    // Clamp the abs max
    private static float clampAbsMax(float current, float candidate) {
        return Math.abs(candidate) > Math.abs(current) ? Mth.clamp(candidate, -1.0f, 1.0f) : current;
    }

    // Get the pick button index
    private static int pickButtonIndex(String sourceId) {
        int bound = Math.max(1, BUTTON_MODELS.size());
        return Math.floorMod(sourceId == null ? 0 : sourceId.hashCode(), bound);
    }

    // Rotate around the joystick pivot
    private static void rotateAroundPivot(PoseStack ms, float x, float y, float z) {
        ms.translate(x / 16.0D, y / 16.0D, z / 16.0D);
    }

    // Unrotate around the pivot
    private static void unrotateAroundPivot(PoseStack ms, float x, float y, float z) {
        ms.translate(-x / 16.0D, -y / 16.0D, -z / 16.0D);
    }

    // Apply the facing
    private static void applyFacing(PoseStack ms, BlockState state) {
        Direction facing = state.getValue(AnalogueContraptionControllerBlock.FACING);
        Direction zAxis = facing.getAxis().isVertical()
                ? state.getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                : Direction.UP;
        ms.translate(0.5D, 0.5D, 0.5D);
        ms.mulPose(FACING_ROTATIONS[facing.ordinal()][zAxis.ordinal()]);
        ms.translate(-0.5D, -0.5D, -0.5D);
        if (state.hasProperty(AnalogueContraptionControllerBlock.EMBEDDED_SLAB)
                && state.getValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB)) {
            ms.translate(0.0D,
                    state.getValue(AnalogueContraptionControllerBlock.MOUNT_OFFSET)
                            / (double) ControllerEmbeddedMount.PIXELS_PER_BLOCK,
                    0.0D);
        }
    }

    // Create the facing rotations
    private static Quaternionf[][] createFacingRotations() {
        Direction[] directions = Direction.values();
        Quaternionf[][] rotations = new Quaternionf[directions.length][directions.length];
        for (Direction facing : directions) {
            for (Direction zAxis : directions) {
                rotations[facing.ordinal()][zAxis.ordinal()] = facing.getAxis() == zAxis.getAxis()
                        ? new Quaternionf()
                        : rotationFromBasis(cross(facing, zAxis), facing, zAxis);
            }
        }
        return rotations;
    }

    // Get the rotation from basis
    private static Quaternionf rotationFromBasis(Direction xAxis, Direction yAxis, Direction zAxis) {
        return new Matrix3f()
                .m00(xAxis.getStepX()).m01(xAxis.getStepY()).m02(xAxis.getStepZ())
                .m10(yAxis.getStepX()).m11(yAxis.getStepY()).m12(yAxis.getStepZ())
                .m20(zAxis.getStepX()).m21(zAxis.getStepY()).m22(zAxis.getStepZ())
                .getNormalizedRotation(new Quaternionf());
    }

    // Get the cross
    private static Direction cross(Direction first, Direction second) {
        int x = first.getStepY() * second.getStepZ() - first.getStepZ() * second.getStepY();
        int y = first.getStepZ() * second.getStepX() - first.getStepX() * second.getStepZ();
        int z = first.getStepX() * second.getStepY() - first.getStepY() * second.getStepX();
        for (Direction dir : Direction.values()) {
            if (dir.getStepX() == x && dir.getStepY() == y && dir.getStepZ() == z) {
                return dir;
            }
        }
        return Direction.EAST;
    }

    // Define the animation routing values
    private enum AnimationRouting {
        NONE,
        JOYSTICK,
        THROTTLE
    }

    // Store the axis accumulation
    private record AxisAccumulation(float throttle, float pitch, float roll, float yaw) {
        private static final AxisAccumulation ZERO = new AxisAccumulation(0.0f, 0.0f, 0.0f, 0.0f);
    }

    // Store animation state
    private record AnimationState(float throttleValue, float stickX, float stickZ, boolean[] buttonPressed) {
    }

}
