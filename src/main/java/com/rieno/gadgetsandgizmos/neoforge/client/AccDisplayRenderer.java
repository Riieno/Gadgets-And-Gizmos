package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlock;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.display.DisplayWidgetProjection;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Draw every ACC display mode from the same synced frame and connected panel layout
public class AccDisplayRenderer implements BlockEntityRenderer<AccDisplayBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int MAX_PLOT_SAMPLES = 48;
    private static final double DISPLAY_SURFACE_OFFSET = 0.01D;
    private static final float CONTENT_Z = -0.20F;
    private static final float LINE_Z = -0.30F;
    private static final float OVERLAY_Z = -0.40F;
    private static final float FOREGROUND_Z = -0.50F;
    private static final float TEXT_Z = -0.60F;
    private static final float TASKBAR_BASE_Z = -0.70F;
    private static final float TASKBAR_SELECTION_Z = -0.80F;
    private static final float TASKBAR_ACCENT_Z = -0.90F;
    private static final float TASKBAR_TEXT_Z = -1.00F;
    private static final float COMPUTED_BASE_Z = -0.24F;
    private static final float COMPUTED_WIDGET_Z = -0.30F;
    private static final float COMPUTED_OVERLAY_Z = -0.40F;
    private static final float COMPUTED_TEXT_Z = -0.50F;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Font
    private final Font font;
    // Tracked plot samples
    private final Map<AccDisplayBlockEntity, Map<String, ArrayDeque<Double>>> plotSamples =
            Collections.synchronizedMap(new WeakHashMap<>());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display
    public AccDisplayRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the ACC display
    @Override
    public void render(AccDisplayBlockEntity blockEntity, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        // ------------------------------------DISPLAY CHECKS------------------------------------
        if (!blockEntity.isNetworkRoot()) {
            return;
        }
        CompoundTag frame = blockEntity.displayFrame();
        String state = frame.getString("State");
        if (state.isBlank() || "unbound".equals(state)) {
            return;
        }
        int blocksWide = blockEntity.networkWidth();
        int blocksHigh = blockEntity.networkHeight();
        int pixelWidth = blockEntity.surfacePixelWidth();
        int pixelHeight = blockEntity.surfacePixelHeight();
        Direction facing = blockEntity.getBlockState().getValue(AccDisplayBlock.FACING);
        Direction right = blockEntity.screenRight();
        AccDisplayBlock displayBlock = (AccDisplayBlock) blockEntity.getBlockState().getBlock();
        double verticalCenter = verticalCenter(displayBlock.displayType(),
                blockEntity.getBlockState().getValue(AccDisplayBlock.Y_ALIGNMENT));

        poseStack.pushPose();
        poseStack.translate(
                0.5D + right.getStepX() * (blocksWide - 1) * 0.5D, verticalCenter - (blocksHigh - 1) * 0.5D, 0.5D + right.getStepZ() * (blocksWide - 1) * 0.5D);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poseStack.translate(0.0D, 0.0D, renderPlane(displayBlock.displayType(), blockEntity.getBlockState().getValue(AccDisplayBlock.Z_ALIGNMENT)));
        poseStack.scale(-1.0F / AccDisplayBlockEntity.PIXELS_PER_BLOCK, -1.0F / AccDisplayBlockEntity.PIXELS_PER_BLOCK, 1.0F / AccDisplayBlockEntity.PIXELS_PER_BLOCK);
        poseStack.translate(-pixelWidth * 0.5F, -pixelHeight * 0.5F, 0.0F);

        // ------------------------------------CONTENT LAYOUT------------------------------------
        ListTag sources = frame.getList("Sources", Tag.TAG_COMPOUND);
        int contentAreaHeight = sources.size() > 1
                ? Math.max(1, pixelHeight - AccDisplayBlockEntity.TASKBAR_PIXELS)
                : pixelHeight;
        ContentLayout contentLayout = contentLayout(frame, pixelWidth, contentAreaHeight);
        boolean transformedContent = contentLayout != null
                && ("external".equals(state) || "graph".equals(state)
                || "crn".equals(state));
        int renderWidth = pixelWidth;
        int renderHeight = contentAreaHeight;
        if (transformedContent) {
            renderWidth = contentLayout.width();
            renderHeight = contentLayout.height();
            poseStack.pushPose();
            poseStack.translate(contentLayout.x() + renderWidth * 0.5F,
                    contentLayout.y() + renderHeight * 0.5F, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(contentLayout.rotation()));
            poseStack.scale(contentLayout.scale(), contentLayout.scale(), 1.0F);
            poseStack.translate(-renderWidth * 0.5F, -renderHeight * 0.5F, 0.0F);
        }
        // ------------------------------------DISPLAY CONTENT------------------------------------
        if ("initializing".equals(state)) {
            renderInit(frame, renderWidth, renderHeight, poseStack, buffer);
        } else if ("unavailable".equals(state)) {
            fillAtZ(0, 0, renderWidth, renderHeight, CONTENT_Z,
                    0xFF101419, poseStack, buffer);
            drawCentered(frame.getString("Message"), renderWidth,
                    renderHeight / 2, 0xFFB7C0C8, poseStack, buffer);
        } else if ("display_link".equals(state)) {
            renderDisplayLink(frame, renderWidth, renderHeight, poseStack, buffer);
        } else if ("external".equals(state)) {
            CompoundTag external = frame.getCompound("External");
            if (!"diagnostic_tablet".equals(external.getString("Format"))
                    || !DiagnosticTabletGuiProjection.renderAccDisplay(
                    blockEntity.diagnosticTabletSource(), renderWidth, renderHeight,
                    partialTick, poseStack, buffer)) {
                renderExternal(external, renderWidth, renderHeight, poseStack, buffer);
            }
        } else if ("crn".equals(state)) {
            renderCrn(frame, renderWidth, renderHeight, poseStack, buffer);
        } else if ("graph".equals(state)) {
            String mode = frame.getString("Mode");
            if (!("graph".equals(mode) || "plotter".equals(mode))
                    || !AccDisplayGuiProjection.render(blockEntity, mode,
                    renderWidth, renderHeight, partialTick, poseStack, buffer)) {
                renderGraphFrame(blockEntity, renderWidth, renderHeight, poseStack, buffer);
            }
        }
        if (transformedContent) {
            poseStack.popPose();
        }
        // ------------------------------------SOURCE SELECTOR------------------------------------
        if (sources.size() > 1) {
            renderTaskbar(frame, sources, pixelWidth, contentAreaHeight, poseStack, buffer);
        }
        poseStack.popPose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the taskbar
    private void renderTaskbar(CompoundTag frame, ListTag sources, int width, int top,
                               PoseStack poseStack, MultiBufferSource buffer) {
        int bottom = top + AccDisplayBlockEntity.TASKBAR_PIXELS;
        fillAtZ(0, top, width, bottom, TASKBAR_BASE_Z,
                0xF20C1319, poseStack, buffer);
        fillAtZ(0, top, width, top + 1, TASKBAR_SELECTION_Z,
                0xFF3B5666, poseStack, buffer);
        String active = frame.getString("ActiveSource");
        for (int idx = 0; idx < sources.size(); idx++) {
            CompoundTag src = sources.getCompound(idx);
            int left = idx * width / sources.size();
            int right = (idx + 1) * width / sources.size();
            boolean selected = active.equals(src.getString("Id"));
            if (selected) {
                fillAtZ(left, top + 1, right, bottom, TASKBAR_SELECTION_Z,
                        0xFF244858, poseStack, buffer);
                fillAtZ(left, top, right, top + 2, TASKBAR_ACCENT_Z,
                        0xFF63C6E8, poseStack, buffer);
            }
            String label = trim(src.getString("Label"),
                    Math.max(1, (right - left - 4) / Math.max(1, font.width("W"))));
            drawTextAtZ(label, left + Math.max(2, (right - left - font.width(label)) / 2),
                    top + 3, TASKBAR_TEXT_Z, selected ? 0xFFFFFFFF : 0xFFB7C0C8,
                    poseStack, buffer);
        }
    }

    // Get the content layout
    private static ContentLayout contentLayout(CompoundTag frame, int width, int height) {
        if (!frame.contains("ContentNode", Tag.TAG_STRING)) {
            return null;
        }
        float x = (float) (frame.getDouble("ContentX") / 320.0D * width);
        float y = (float) (frame.getDouble("ContentY") / 180.0D * height);
        int contentWidth = Math.max(1,
                (int) Math.round(frame.getDouble("ContentWidth") / 320.0D * width));
        int contentHeight = Math.max(1,
                (int) Math.round(frame.getDouble("ContentHeight") / 180.0D * height));
        float scale = (float) Mth.clamp(frame.getDouble("ContentScale"), 0.01D, 100.0D);
        float rotation = (float) frame.getDouble("ContentRotation");
        return new ContentLayout(x, y, contentWidth, contentHeight, scale, rotation);
    }

    // Draw the display link
    private void renderDisplayLink(CompoundTag frame, int width, int height,
                                   PoseStack poseStack, MultiBufferSource buffer) {
        renderDisplayText(frame.getList("Lines", Tag.TAG_STRING),
                0, width, height, 0xFFFFFFFF, poseStack, buffer);
    }

    // Draw the CRN
    private void renderCrn(CompoundTag frame, int width, int height,
                           PoseStack poseStack, MultiBufferSource buffer) {
        // -----------------------------------------------------DISPLAY DATA-----------------------------------------------------
        CompoundTag data = frame.getCompound("CrnData");
        String mode = frame.getString("Mode");
        String ship = usable(data.getString("ShipName"), "Unnamed Ship");
        String current = usable(data.getString("CurrentStop"), "At sea");
        String target = usable(data.getString("TargetStop"), data.getString("Status"));
        String next = usable(data.getString("NextStop"), "End of route");
        String status = usable(data.getString("Status"), "Awaiting shipping schedule");
        String eta = formatEta(data.getLong("EtaSeconds"));
        String staticText = frame.getString("Text");
        // -----------------------------------------------------TEXT LINES-----------------------------------------------------
        ListTag dataLines = frame.getList("DataLines", Tag.TAG_STRING);
        // ------------------------------------EXTERNAL PAYLOAD------------------------------------
        CompoundTag externalPayload = frame.getCompound("ExternalPayload");
        if (!dataLines.isEmpty()) {
            current = displayLine(dataLines, 0, current);
            target = displayLine(dataLines, 1, current);
            next = displayLine(dataLines, 2, target);
            status = displayLine(dataLines, 3, status);
            if (staticText.isBlank()) {
                StringBuilder joined = new StringBuilder();
                for (int idx = 0; idx < dataLines.size(); idx++) {
                    if (idx > 0) joined.append('\n');
                    joined.append(dataLines.getString(idx));
                }
                staticText = joined.toString();
            }
        }
        if (!externalPayload.isEmpty()
                && !AdvancedGraphCatalog.isCrnStaticTextMode(mode)) {
            crnHeader(ship, crnCategory(mode), width, poseStack, buffer);
            poseStack.pushPose();
            poseStack.translate(0.0F, 14.0F, 0.0F);
            renderExternal(externalPayload, width, Math.max(1, height - 26),
                    poseStack, buffer);
            poseStack.popPose();
            crnFooter(eta + "  •  " + status, width, height, poseStack, buffer);
            return;
        }

        // -----------------------------------------------------DISPLAY MODE-----------------------------------------------------
        switch (mode) {
            case "passenger_information/running_text" -> {
                crnHeader(ship, "PASSENGER INFORMATION", width, poseStack, buffer);
                drawCentered(trim(status + "  •  Next: " + target,
                                Math.max(12, width / 5)),
                        width, Math.max(18, height / 2), 0xFFFFFFFF, poseStack, buffer);
            }
            case "train_destination/simple" -> {
                crnHeader(ship, "DESTINATION", width, poseStack, buffer);
                drawCrnHeadline(target, width, height, 0xFFFFFFFF, poseStack, buffer);
            }
            case "train_destination/extended" -> {
                crnHeader(ship, "DESTINATION", width, poseStack, buffer);
                drawCrnHeadline(target, width, height - 14, 0xFFFFFFFF, poseStack, buffer);
                drawCentered("Next: " + next, width, Math.max(20, height - 15),
                        0xFF8FD8F4, poseStack, buffer);
            }
            case "train_destination/detailed" -> {
                crnHeader(ship, "TRAIN DESTINATION", width, poseStack, buffer);
                crnRow("FROM", current, 18, width, poseStack, buffer);
                crnRow("TO", target, 31, width, poseStack, buffer);
                crnRow("NEXT", next, 44, width, poseStack, buffer);
                crnFooter(eta + "  •  " + status, width, height, poseStack, buffer);
            }
            case "platform/running_text" -> {
                crnHeader(ship, "PLATFORM INFORMATION", width, poseStack, buffer);
                drawCentered(trim(target + "  •  " + eta,
                                Math.max(12, width / 5)),
                        width, Math.max(18, height / 2), 0xFFFFFFFF, poseStack, buffer);
            }
            case "platform/table" -> {
                crnHeader(ship, "PLATFORM", width, poseStack, buffer);
                crnTableHeader(width, 17, poseStack, buffer);
                crnTableRow(current, "Now", 29, width, poseStack, buffer);
                crnTableRow(target, eta, 42, width, poseStack, buffer);
                crnTableRow(next, "Next", 55, width, poseStack, buffer);
            }
            case "platform/focus" -> {
                crnHeader(ship, "NEXT SERVICE", width, poseStack, buffer);
                drawCrnHeadline(target, width, height - 18, 0xFFFFFFFF, poseStack, buffer);
                crnFooter(eta + "  •  " + status, width, height, poseStack, buffer);
            }
            case "departure_board/table" -> {
                crnHeader(usable(data.getString("ScheduleTitle"), ship),
                        "DEPARTURES", width, poseStack, buffer);
                crnTableHeader(width, 17, poseStack, buffer);
                crnTableRow(target, eta, 29, width, poseStack, buffer);
                crnTableRow(next, "Following", 42, width, poseStack, buffer);
                crnFooter(data.getInt("RemainingStops") + " stops remaining",
                        width, height, poseStack, buffer);
            }
            case "static_text/simple_text" -> drawCrnHeadline(
                    usable(staticText, status), width, height,
                    0xFFFFFFFF, poseStack, buffer);
            case "static_text/rich_text" -> {
                String[] lines = usable(staticText, status).split("\\R", -1);
                int y = Math.max(5, (height - lines.length * (font.lineHeight + 2)) / 2);
                for (String line : lines) {
                    drawCentered(trim(line, Math.max(8, width / 5)), width, y,
                            y == Math.max(5, (height - lines.length * (font.lineHeight + 2)) / 2)
                                    ? 0xFF8FD8F4 : 0xFFFFFFFF,
                            poseStack, buffer);
                    y += font.lineHeight + 2;
                    if (y >= height - font.lineHeight) break;
                }
            }
            case "passenger_information/detailed_with_schedule" ->
                    renderCrnPassengerSchedule(data, ship, current, target, next,
                            status, eta, width, height, poseStack, buffer);
            default -> renderCrnPassengerSchedule(data, ship, current, target, next,
                    status, eta, width, height, poseStack, buffer);
        }
    }

    // Get the display line
    private static String displayLine(ListTag lines, int idx, String fallback) {
        if (idx < 0 || idx >= lines.size()) return fallback;
        String val = lines.getString(idx);
        return val == null || val.isBlank() ? fallback : val;
    }

    // Get the CRN category
    private static String crnCategory(String mode) {
        return switch (mode) {
            case "train_destination/simple", "train_destination/extended" -> "DESTINATION";
            case "train_destination/detailed" -> "TRAIN DESTINATION";
            case "platform/running_text" -> "PLATFORM INFORMATION";
            case "platform/table" -> "PLATFORM";
            case "platform/focus" -> "NEXT SERVICE";
            case "departure_board/table" -> "DEPARTURES";
            default -> "PASSENGER INFORMATION";
        };
    }

    // Draw the CRN passenger schedule
    private void renderCrnPassengerSchedule(
            CompoundTag data, String ship, String current, String target,
            String next, String status, String eta, int width, int height,
            PoseStack poseStack, MultiBufferSource buffer
    ) {
        crnHeader(ship, "PASSENGER INFORMATION", width, poseStack, buffer);
        crnRow("CURRENT", current, 18, width, poseStack, buffer);
        crnRow("DESTINATION", target, 31, width, poseStack, buffer);
        crnRow("NEXT", next, 44, width, poseStack, buffer);
        crnProgress(data.getDouble("ProgressPercent") / 100.0D,
                6, Math.max(48, height - 22), width - 6, poseStack, buffer);
        crnFooter(eta + "  •  " + status, width, height, poseStack, buffer);
    }

    // Handle the CRN header
    private void crnHeader(String title, String category, int width,
                           PoseStack poseStack, MultiBufferSource buffer) {
        fill(0, 0, width, 14, 0xFF123449, poseStack, buffer);
        drawText(trim(title, Math.max(6, width / 7)), 5, 3,
                0xFFFFFFFF, poseStack, buffer);
        String shownCategory = trim(category, Math.max(5, width / 8));
        drawText(shownCategory, Math.max(5, width - font.width(shownCategory) - 5), 3,
                0xFF8FD8F4, poseStack, buffer);
    }

    // Handle the CRN row
    private void crnRow(String label, String val, int y, int width,
                        PoseStack poseStack, MultiBufferSource buffer) {
        drawText(label, 6, y, 0xFF72BBD7, poseStack, buffer);
        int valueX = Math.min(width / 3, Math.max(6, font.width(label) + 12));
        drawText(trim(val, Math.max(6, (width - valueX - 6) / 5)),
                valueX, y, 0xFFFFFFFF, poseStack, buffer);
    }

    // Handle the CRN table header
    private void crnTableHeader(int width, int y,
                                PoseStack poseStack, MultiBufferSource buffer) {
        fill(4, y - 2, width - 4, y + 10, 0xFF14232D, poseStack, buffer);
        drawText("DESTINATION", 7, y, 0xFF72BBD7, poseStack, buffer);
        drawText("TIME", Math.max(7, width - 31), y,
                0xFF72BBD7, poseStack, buffer);
    }

    // Handle the CRN table row
    private void crnTableRow(String destination, String time, int y, int width,
                             PoseStack poseStack, MultiBufferSource buffer) {
        line(5, y + font.lineHeight + 1, width - 5, y + font.lineHeight + 1,
                1.0F, 0xFF233844, poseStack, buffer);
        drawText(trim(destination, Math.max(6, (width - 45) / 5)), 7, y,
                0xFFFFFFFF, poseStack, buffer);
        String shownTime = trim(time, 10);
        drawText(shownTime, Math.max(7, width - font.width(shownTime) - 7), y,
                0xFFF1C75B, poseStack, buffer);
    }

    // Handle the CRN footer
    private void crnFooter(String text, int width, int height,
                           PoseStack poseStack, MultiBufferSource buffer) {
        int y = Math.max(15, height - 13);
        fill(0, y - 2, width, height, 0xFF101B22, poseStack, buffer);
        drawCentered(trim(text, Math.max(8, width / 5)), width, y,
                0xFFB8CAD3, poseStack, buffer);
    }

    // Draw the CRN headline
    private void drawCrnHeadline(String text, int width, int height, int col,
                                 PoseStack poseStack, MultiBufferSource buffer) {
        String shown = trim(text, Math.max(8, width / 4));
        float scale = Math.max(0.55F, Math.min(2.0F,
                (width - 10.0F) / Math.max(1, font.width(shown))));
        drawScaledText(shown, width * 0.5F, Math.max(18, height * 0.52F),
                col, scale, true, poseStack, buffer);
    }

    // Handle the CRN progress
    private static void crnProgress(double progress, int left, int y, int right,
                                    PoseStack poseStack, MultiBufferSource buffer) {
        int safeRight = Math.max(left + 1, right);
        roundedFillAtZ(left, y, safeRight, y + 5,
                CONTENT_Z, 0xFF263945, 2, poseStack, buffer);
        int filled = left + (int) Math.round((safeRight - left)
                * Mth.clamp(progress, 0.0D, 1.0D));
        roundedFillAtZ(left, y, filled, y + 5,
                OVERLAY_Z, 0xFF47B9E8, 2, poseStack, buffer);
    }

    // Format the eta
    private static String formatEta(long seconds) {
        if (seconds < 0L) return "ETA --";
        long minutes = seconds / 60L;
        long remaining = seconds % 60L;
        return String.format(java.util.Locale.ROOT, "ETA %d:%02d", minutes, remaining);
    }

    // Get the usable
    private static String usable(String val, String fallback) {
        return val == null || val.isBlank() ? fallback : val;
    }

    // Draw the external
    private void renderExternal(CompoundTag frame, int width, int height,
                                PoseStack poseStack, MultiBufferSource buffer) {
        if ("crn".equals(frame.getString("Format"))) {
            renderCrn(frame, width, height, poseStack, buffer);
            return;
        }
        if ("computed".equals(frame.getString("Format"))) {
            renderComputed(frame, width, height, poseStack, buffer);
            return;
        }
        if ("text".equals(frame.getString("Format"))) {
            renderDisplayText(frame.getList("Lines", Tag.TAG_STRING),
                    0, width, height, 0xFFFFFFFF, poseStack, buffer);
            return;
        }
        if ("terminal".equals(frame.getString("Format"))
                && renderComputerCraftTerminal(frame, width, height, poseStack, buffer)) {
            return;
        }
        ListTag lines = frame.getList("Lines", Tag.TAG_STRING);
        int columns = Math.max(1, frame.getInt("Width"));
        int rows = Math.max(1, frame.getInt("Height"));
        float cellWidth = width / (float) columns;
        float cellHeight = height / (float) rows;
        ListTag foreground = frame.getList("Foreground", Tag.TAG_STRING);
        ListTag background = frame.getList("Background", Tag.TAG_STRING);
        for (int row = 0; row < Math.min(rows, lines.size()); row++) {
            String line = lines.getString(row);
            String fg = row < foreground.size() ? foreground.getString(row) : "0".repeat(columns);
            String bg = row < background.size() ? background.getString(row) : "f".repeat(columns);
            if (cellWidth < 4.0F) {
                drawText(trim(line, columns), 1, Math.round(row * cellHeight),
                        0xFFFFFFFF, poseStack, buffer);
                continue;
            }
            for (int column = 0; column < Math.min(columns, line.length()); column++) {
                float left = column * cellWidth;
                float top = row * cellHeight;
                fill(left, top, left + cellWidth, top + cellHeight,
                        terminalColor(frame, colorAt(bg, column, 'f')), poseStack, buffer);
                char character = line.charAt(column);
                if (character != ' ') {
                    String glyph = String.valueOf(character);
                    float glyphScale = Math.min(1.0F, Math.min(
                            Math.max(0.1F, cellWidth - 0.5F) / Math.max(1, font.width(glyph)),
                            Math.max(0.1F, cellHeight) / Math.max(1, font.lineHeight)));
                    poseStack.pushPose();
                    poseStack.translate(left, top, 0.0F);
                    poseStack.scale(glyphScale, glyphScale, 1.0F);
                    drawText(glyph, 0, 0,
                            terminalColor(frame, colorAt(fg, column, '0')), poseStack, buffer);
                    poseStack.popPose();
                }
            }
        }
    }

    // Draw the display text
    private void renderDisplayText(ListTag lines, int top, int width, int height,
                                   int col, PoseStack poseStack,
                                   MultiBufferSource buffer) {
        if (lines.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        int longest = 1;
        for (int idx = 0; idx < lines.size(); idx++) {
            longest = Math.max(longest, font.width(lines.getString(idx)));
        }
        float widthScale = Math.max(0.05F, (width - 12.0F) / longest);
        float heightScale = Math.max(0.05F,
                (height - 10.0F) / (lines.size() * (font.lineHeight + 2.0F)));
        float scale = Mth.clamp(Math.min(widthScale, heightScale), 0.25F, 6.0F);
        float lineHeight = (font.lineHeight + 2.0F) * scale;
        float y = top + Math.max(4.0F, (height - lines.size() * lineHeight) * 0.5F);
        for (int idx = 0; idx < lines.size(); idx++) {
            String line = lines.getString(idx);
            if (!line.isBlank()) {
                drawScaledText(line, width * 0.5F, y + lineHeight * 0.5F,
                        col, scale, true, poseStack, buffer);
            }
            y += lineHeight;
            if (y > top + height) {
                break;
            }
        }
    }

    // Draw the ComputerCraft terminal
    private static boolean renderComputerCraftTerminal(
            CompoundTag frame, int width, int height,
            PoseStack poseStack, MultiBufferSource buffer) {
        try {
            Class<?> renderer = Class.forName(
                    "com.rieno.gadgetsandgizmos.compat.computercraft.client.AccDisplayComputerCraftRenderer");
            Object res = renderer.getMethod("render", CompoundTag.class, int.class, int.class,
                            PoseStack.class, MultiBufferSource.class)
                    .invoke(null, frame, width, height, poseStack, buffer);
            return res instanceof Boolean rendered && rendered;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    // Draw the computed
    private void renderComputed(CompoundTag frame, int width, int height,
                                PoseStack poseStack, MultiBufferSource buffer) {
        int sourceWidth = Math.max(1, frame.getInt("Width"));
        int sourceHeight = Math.max(1, frame.getInt("Height"));
        double scaleX = width / (double) sourceWidth;
        double scaleY = height / (double) sourceHeight;
        ListTag widgets = frame.getList("Widgets", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < widgets.size(); idx++) {
            CompoundTag widget = widgets.getCompound(idx);
            CompoundTag properties = widget.getCompound("Properties");
            int x = (int) Math.round(widget.getInt("X") * scaleX);
            int y = (int) Math.round(widget.getInt("Y") * scaleY);
            int widgetWidth = Math.max(1, (int) Math.round(widget.getInt("W") * scaleX));
            int widgetHeight = Math.max(1, (int) Math.round(widget.getInt("H") * scaleY));
            int col = opaque(widget.getInt("Color"), 0xFF4DA7D1);
            String type = widget.getString("Type");
            switch (type) {
                case "button" -> {
                    drawComputedPanelAt(x, y, widgetWidth, widgetHeight,
                            0xE61B2732, col, 1, 3, poseStack, buffer);
                    drawComputedText(properties.getString("label"), x, y,
                            widgetWidth, widgetHeight, col, properties.getString("alignment"),
                            scaleY, poseStack, buffer);
                }
                case "slider" -> {
                    double amount = rangeAmount(properties, "minimum", "maximum");
                    int trackHeight = Math.max(2, widgetHeight / 4);
                    int trackY = y + (widgetHeight - trackHeight) / 2;
                    roundedFillAtZ(x, trackY, x + widgetWidth, trackY + trackHeight,
                            COMPUTED_BASE_Z, 0xFF263640, trackHeight / 2, poseStack, buffer);
                    roundedFillAtZ(x, trackY, x + (int) Math.round(widgetWidth * amount),
                            trackY + trackHeight, COMPUTED_WIDGET_Z, col,
                            trackHeight / 2, poseStack, buffer);
                }
                case "progress" -> {
                    double maximum = properties.getDouble("maximum");
                    double amount = maximum > 0.0D
                            ? Mth.clamp(properties.getDouble("value") / maximum, 0.0D, 1.0D) : 0.0D;
                    roundedFillAtZ(x, y, x + widgetWidth, y + widgetHeight,
                            COMPUTED_BASE_Z, 0xFF263640, 3, poseStack, buffer);
                    roundedFillAtZ(x, y, x + (int) Math.round(widgetWidth * amount),
                            y + widgetHeight, COMPUTED_WIDGET_Z, col, 3, poseStack, buffer);
                }
                case "clock" -> drawComputedText(
                        java.time.LocalTime.now().withNano(0).toString(), x, y,
                        widgetWidth, widgetHeight, col, properties.getString("alignment"),
                        scaleY, poseStack, buffer);
                default -> drawComputedText(properties.getString("text"), x, y,
                        widgetWidth, widgetHeight, col, properties.getString("alignment"),
                        scaleY, poseStack, buffer);
            }
        }
    }

    // Get the range amount
    private static double rangeAmount(CompoundTag properties, String minimumKey, String maximumKey) {
        double minimum = properties.getDouble(minimumKey);
        double maximum = properties.getDouble(maximumKey);
        if (!(maximum > minimum)) maximum = minimum + 1.0D;
        return Mth.clamp((properties.getDouble("value") - minimum) / (maximum - minimum),
                0.0D, 1.0D);
    }

    // Draw the computed text
    private void drawComputedText(String text, int x, int y, int width, int height,
                                  int col, String alignment, double displayScale,
                                  PoseStack poseStack, MultiBufferSource buffer) {
        if (text == null || text.isBlank()) return;
        float scale = (float) Math.max(0.1D, Math.min(displayScale,
                height / (double) Math.max(1, font.lineHeight)));
        float drawX = switch (alignment == null ? "" : alignment.toLowerCase(java.util.Locale.ROOT)) {
            case "right" -> x + width - font.width(text) * scale;
            case "center" -> x + (width - font.width(text) * scale) * 0.5F;
            default -> x;
        };
        float drawY = y + (height - font.lineHeight * scale) * 0.5F;
        poseStack.pushPose();
        poseStack.translate(drawX, drawY, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        drawTextAtZ(text, 0, 0, COMPUTED_TEXT_Z, col, poseStack, buffer);
        poseStack.popPose();
    }

    // Draw the computed panel
    private static void drawComputedPanelAt(int x, int y, int width, int height,
                                            int background, int border, int borderWidth, int radius,
                                            PoseStack poseStack, MultiBufferSource buffer) {
        roundedFillAtZ(x, y, x + width, y + height,
                COMPUTED_WIDGET_Z, border, radius, poseStack, buffer);
        if (width > borderWidth * 2 && height > borderWidth * 2) {
            roundedFillAtZ(x + borderWidth, y + borderWidth,
                    x + width - borderWidth, y + height - borderWidth,
                    COMPUTED_OVERLAY_Z, background, Math.max(0, radius - borderWidth),
                    poseStack, buffer);
        }
    }

    // Get the opaque
    private static int opaque(int col, int fallback) {
        if (col == 0) return fallback;
        return (col & 0xFF000000) == 0 ? col | 0xFF000000 : col;
    }

    // Get the color
    private static char colorAt(String colors, int idx, char fallback) {
        return colors != null && idx >= 0 && idx < colors.length()
                ? colors.charAt(idx) : fallback;
    }

    // Get the terminal color
    private static int terminalColor(char col) {
        return switch (Character.toLowerCase(col)) {
            case '0' -> 0xFFF0F0F0;
            case '1' -> 0xFFF2B233;
            case '2' -> 0xFFE57FD8;
            case '3' -> 0xFF99B2F2;
            case '4' -> 0xFFDEDE6C;
            case '5' -> 0xFF7FCC19;
            case '6' -> 0xFFF2B2CC;
            case '7' -> 0xFF4C4C4C;
            case '8' -> 0xFF999999;
            case '9' -> 0xFF4C99B2;
            case 'a' -> 0xFFB266E5;
            case 'b' -> 0xFF3366CC;
            case 'c' -> 0xFF7F664C;
            case 'd' -> 0xFF57A64E;
            case 'e' -> 0xFFCC4C4C;
            default -> 0xFF111111;
        };
    }

    // Get the terminal color
    private static int terminalColor(CompoundTag frame, char col) {
        int[] palette = frame.getIntArray("Palette");
        int digit = Character.digit(col, 16);
        int paletteIndex = digit < 0 ? -1 : 15 - digit;
        if (paletteIndex >= 0 && paletteIndex < palette.length) {
            return palette[paletteIndex] | 0xFF000000;
        }
        return terminalColor(col);
    }

    // Get the vertical center
    private static double verticalCenter(AccDisplayBlock.DisplayType type,
                                         AccDisplayBlock.Alignment alignment) {
        if (type != AccDisplayBlock.DisplayType.HALF_PANEL
                && type != AccDisplayBlock.DisplayType.SLAB) {
            return 0.5D;
        }
        return switch (alignment) {
            case NEGATIVE -> 0.25D;
            case CENTER -> 0.5D;
            case POSITIVE -> 0.75D;
        };
    }

    // Draw the plane
    private static double renderPlane(AccDisplayBlock.DisplayType type,
                                      AccDisplayBlock.Alignment alignment) {
        double outward = switch (type) {
            case BLOCK, SLAB -> -0.502D;
            case BOARD -> switch (alignment) {
                case NEGATIVE -> -0.502D;
                case CENTER -> -0.3145D;
                case POSITIVE -> -0.002D;
            };
            case PANEL, HALF_PANEL -> switch (alignment) {
                case NEGATIVE -> -0.502D;
                case CENTER -> -0.09575D;
                case POSITIVE -> 0.3105D;
            };
        };
        return outward - DISPLAY_SURFACE_OFFSET;
    }

    // Draw the init
    private void renderInit(CompoundTag frame, int width, int height,
                                      PoseStack poseStack, MultiBufferSource buffer) {
        int percent = Mth.clamp(frame.getInt("Percent"), 0, 100);
        String status = frame.getString("Status");
        drawCentered("Initializing ship controls: " + percent + "%", width,
                height / 2 - 18, 0xFFFFAA00, poseStack, buffer);
        int barLeft = Math.max(8, width / 12);
        int barRight = Math.min(width - 8, width - width / 12);
        int barTop = height / 2 - 5;
        fillAtZ(barLeft, barTop, barRight, barTop + 9,
                CONTENT_Z, 0xFF1D2B36, poseStack, buffer);
        fillAtZ(barLeft + 1, barTop + 1,
                barLeft + 1 + (int) Math.round((barRight - barLeft - 2) * percent / 100.0D),
                barTop + 8, OVERLAY_Z, 0xFF43C867, poseStack, buffer);
        if (!status.isBlank()) {
            drawCentered(trim(status, Math.max(24, width / 6)), width,
                    height / 2 + 10, 0xFFFFFFFF, poseStack, buffer);
        }
    }

    // Draw the graph frame
    private void renderGraphFrame(AccDisplayBlockEntity blockEntity, int width, int height,
                                  PoseStack poseStack, MultiBufferSource buffer) {
        AdvancedGraphDocument graph = blockEntity.graph();
        String mode = blockEntity.displayFrame().getString("Mode");
        if ("graph".equals(mode)) {
            renderNodeGraph(blockEntity, graph, width, height, poseStack, buffer);
            return;
        }
        if ("plotter".equals(mode)) {
            renderPlotter(blockEntity, graph, width, height, poseStack, buffer);
            return;
        }
        renderWidgets(blockEntity, graph, width, height, poseStack, buffer);
    }

    // Draw the widgets
    private void renderWidgets(AccDisplayBlockEntity blockEntity, AdvancedGraphDocument graph,
                               int width, int height, PoseStack poseStack,
                               MultiBufferSource buffer) {
        boolean rendered = false;
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if ((!"acc_display_widget".equals(node.type())
                    && !"acc_hologram_widget".equals(node.type())
                    && !"advanced_hud_element".equals(node.type()))
                    || !blockEntity.value(node.id(), "visible").asBoolean()
                    || !blockEntity.targetsThisDisplay(node)) {
                continue;
            }
            int canvasWidth = Math.max(1, node.data().getInt("WidgetWidth"));
            int canvasHeight = Math.max(1, node.data().getInt("WidgetHeight"));
            if (canvasWidth == 1) canvasWidth = 320;
            if (canvasHeight == 1) canvasHeight = 180;
            double scaleX = width / (double) canvasWidth;
            double scaleY = height / (double) canvasHeight;
            ListTag elements = node.data().getList("WidgetElements", Tag.TAG_COMPOUND);
            for (int idx = 0; idx < elements.size(); idx++) {
                CompoundTag elm = AdvancedHudElementBinding.resolvedCopy(
                        elements.getCompound(idx),
                        port -> blockEntity.value(node.id(), port));
                if (elm.contains("Visible", Tag.TAG_BYTE) && !elm.getBoolean("Visible")) {
                    continue;
                }
                int x = (int) Math.round(elm.getInt("X") * scaleX);
                int y = (int) Math.round(elm.getInt("Y") * scaleY);
                int elementWidth = Math.max(1, (int) Math.round(elm.getInt("W") * scaleX));
                int elementHeight = Math.max(1, (int) Math.round(elm.getInt("H") * scaleY));
                String type = elm.getString("Type");
                double configuredScale = elm.contains("Scale", Tag.TAG_DOUBLE)
                        ? elm.getDouble("Scale") : 1.0D;
                float rotation = (float) elm.getDouble("Rotation");
                boolean hologram = "acc_hologram_widget".equals(node.type());
                if (!hologram) {
                    DisplayWidgetProjection.Bounds bounded = DisplayWidgetProjection.fit(
                            x, y, elementWidth, elementHeight, width, height);
                    x = bounded.x();
                    y = bounded.y();
                    elementWidth = bounded.width();
                    elementHeight = bounded.height();
                    configuredScale = Math.min(configuredScale,
                            DisplayWidgetProjection.maximumScale(
                                    bounded, width, height, rotation));
                }
                float elementScale = (float) Mth.clamp(configuredScale, 0.01D, 100.0D);
                poseStack.pushPose();
                poseStack.translate(x + elementWidth * 0.5F, y + elementHeight * 0.5F, 0.0F);
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));
                poseStack.scale(elementScale, elementScale, 1.0F);
                poseStack.translate(-elementWidth * 0.5F, -elementHeight * 0.5F, 0.0F);
                if ("image".equals(type)) {
                    String src = blockEntity.value(node.id(), "image").asString();
                    if (src.isBlank()) {
                        src = elm.getString("Texture");
                    }
                    ResourceLocation texture = AdvancedHudImageClient.resolveTexture(src);
                    if (texture == null) {
                        fill(0, 0, elementWidth, elementHeight,
                                0x66374756, poseStack, buffer);
                    } else {
                        texturedQuad(texture, 0, 0, elementWidth, elementHeight,
                                CONTENT_Z, poseStack, buffer);
                    }
                    poseStack.popPose();
                    rendered = true;
                    continue;
                }
                drawProceduralWidget(blockEntity, node, elm, type,
                        elementWidth, elementHeight,
                        Math.max(0.01D, Math.min(scaleX, scaleY)), !hologram,
                        poseStack, buffer);
                poseStack.popPose();
                rendered = true;
            }
        }
        if (!rendered) {
            drawCentered("Add an ACC Display Widget node to the active graph",
                    width, height / 2, 0xFFB7C0C8, poseStack, buffer);
        }
    }

    // Draw the procedural widget
    private void drawProceduralWidget(
            AccDisplayBlockEntity blockEntity,
            AdvancedGraphDocument.Node node,
            CompoundTag elm,
            String type,
            int width,
            int height,
            double canvasScale,
            boolean constrainToSurface,
            PoseStack poseStack,
            MultiBufferSource buffer
    ) {
        // -----------------------------------------------------WIDGET STYLE-----------------------------------------------------
        int textColor = AdvancedHudElementStyle.color(
                elm, "Color", AdvancedHudElementStyle.DEFAULT_TEXT_COLOR);
        int background = AdvancedHudElementStyle.color(
                elm, "BackgroundColor", AdvancedHudElementStyle.DEFAULT_WIDGET_BACKGROUND_COLOR);
        int accent = AdvancedHudElementStyle.color(
                elm, "AccentColor", AdvancedHudElementStyle.DEFAULT_WIDGET_ACCENT_COLOR);
        int track = AdvancedHudElementStyle.color(
                elm, "TrackColor", AdvancedHudElementStyle.DEFAULT_WIDGET_TRACK_COLOR);
        int border = AdvancedHudElementStyle.color(elm, "BorderColor", 0xFF527185);
        int borderWidth = Math.max(0, (int) Math.round(
                AdvancedHudElementStyle.borderWidth(elm) * canvasScale));
        int radius = Math.max(0, (int) Math.round(
                AdvancedHudElementStyle.borderRadius(elm) * canvasScale));
        float fontScale = (float) Math.max(0.05D,
                AdvancedHudElementStyle.fontSize(elm) / (double) font.lineHeight * canvasScale);
        // ------------------------------------SURFACE BOUNDS------------------------------------
        if (constrainToSurface) {
            fontScale = Math.min(fontScale, Math.max(0.05F, height / (float) font.lineHeight));
        }
        String valuePort = elm.getString("ValuePort");
        if (valuePort.isBlank()) {
            valuePort = elm.getString("Port");
        }
        // -----------------------------------------------------WIDGET VALUE-----------------------------------------------------
        AdvancedGraphDocument.Value current = blockEntity.value(node.id(), valuePort);
        boolean interactionPulse = blockEntity.isWidgetInteractionPulsing(node.id(), elm.getString("InteractionId"));
        String text = widgetText(blockEntity, node, elm);

        // ------------------------------------WIDGET RENDERING------------------------------------
        switch (type) {
            case "box" -> drawPanel(width, height,
                    AdvancedHudElementStyle.color(elm, "Color", 0xAA1A2732),
                    border, borderWidth, radius, poseStack, buffer);
            case "button" -> {
                drawPanel(width, height, interactionPulse ? accent : background,
                        border, borderWidth, radius, poseStack, buffer);
                drawScaledText(fittedText(text.isBlank() ? "Button" : text,
                                Math.max(1, width - 4), fontScale), width * 0.5F, height * 0.5F,
                        textColor, fontScale, true, poseStack, buffer);
            }
            case "toggle" -> {
                int toggleWidth = Math.min(width, Math.max(12, height * 2));
                int toggleX = Math.max(0, width - toggleWidth);
                int toggleSurface = current.asBoolean() || interactionPulse
                        ? accent : track;
                int labelColor = readableTextColor(textColor, toggleSurface);
                roundedFillAtZ(toggleX, 0, width, height, CONTENT_Z, toggleSurface,
                        Math.min(radius, height / 2),
                        poseStack, buffer);
                int knobSize = Math.max(2, height - Math.max(2, height / 5));
                int knobX = current.asBoolean()
                        ? width - knobSize - Math.max(1, height / 10)
                        : toggleX + Math.max(1, height / 10);
                roundedFillAtZ(knobX, (height - knobSize) / 2,
                        knobX + knobSize, (height + knobSize) / 2,
                        OVERLAY_Z, readableTextColor(0xFFFFFFFF, toggleSurface),
                        knobSize / 2, poseStack, buffer);
                if (!text.isBlank() && toggleX > 4) {
                    int labelWidth = Math.max(1, toggleX - 4);
                    String fitted = font.plainSubstrByWidth(text,
                            Math.max(1, (int) Math.floor(labelWidth / fontScale)));
                    drawScaledText(fitted, 0, height * 0.5F - font.lineHeight * fontScale * 0.5F, labelColor,
                            fontScale, false, poseStack, buffer);
                }
            }
            case "slider" -> {
                double amount = widgetAmount(elm, current.asNumber());
                int labelSpace = text.isBlank() ? 0 : Math.min(height / 2, font.lineHeight + 2);
                int trackHeight = Math.max(2, Math.min(6, height - labelSpace));
                int trackY = labelSpace + Math.max(0, (height - labelSpace - trackHeight) / 2);
                roundedFillAtZ(0, trackY, width, trackY + trackHeight,
                        CONTENT_Z, track, trackHeight / 2, poseStack, buffer);
                int fillWidth = (int) Math.round(width * amount);
                roundedFillAtZ(0, trackY, fillWidth, trackY + trackHeight,
                        OVERLAY_Z, interactionPulse ? 0xFFFFFFFF : accent,
                        trackHeight / 2, poseStack, buffer);
                int handle = Math.max(trackHeight + 2, Math.min(height - labelSpace, 10));
                int handleX = Mth.clamp(fillWidth - handle / 2, 0, Math.max(0, width - handle));
                roundedFillAtZ(handleX, trackY + trackHeight / 2 - handle / 2,
                        handleX + handle, trackY + trackHeight / 2 + (handle + 1) / 2,
                        FOREGROUND_Z, interactionPulse ? 0xFFFFFFFF : accent,
                        handle / 2, poseStack, buffer);
                if (!text.isBlank()) {
                    drawScaledText(fittedText(text, width, fontScale), 0, 0, textColor, fontScale,
                            false, poseStack, buffer);
                }
            }
            case "progress" -> {
                double amount = widgetAmount(elm, current.asNumber());
                drawPanel(width, height, track, border, borderWidth, radius, poseStack, buffer);
                int inset = Math.max(1, borderWidth);
                int available = Math.max(0, width - inset * 2);
                int fillWidth = (int) Math.round(available * amount);
                roundedFillAtZ(inset, inset, inset + fillWidth,
                        Math.max(inset, height - inset), FOREGROUND_Z,
                        accent, Math.max(0, radius - inset), poseStack, buffer);
                String label = text.isBlank()
                        ? Math.round(amount * 100.0D) + "%" : text;
                drawScaledText(fittedText(label, Math.max(1, width - inset * 2), fontScale),
                        width * 0.5F, height * 0.5F,
                        textColor, fontScale, true, poseStack, buffer);
            }
            case "text_input" -> {
                drawPanel(width, height, background, border, borderWidth, radius, poseStack, buffer);
                String shown = current.asString().isBlank() ? text : current.asString();
                drawScaledText(fittedText(shown, Math.max(1, width - borderWidth * 2 - 4), fontScale),
                        Math.max(2, borderWidth + 2), height * 0.5F,
                        textColor, fontScale, false, poseStack, buffer);
            }
            default -> drawScaledText(fittedText(text, width, fontScale), 0, 0, textColor,
                    fontScale, false, poseStack, buffer);
        }
    }

    // Get the widget amount
    private static double widgetAmount(CompoundTag elm, double val) {
        double minimum = elm.contains("Min") ? elm.getDouble("Min") : 0.0D;
        double maximum = elm.contains("Max") ? elm.getDouble("Max") : 1.0D;
        if (!(maximum > minimum)) {
            maximum = minimum + 1.0D;
        }
        return Mth.clamp((val - minimum) / (maximum - minimum), 0.0D, 1.0D);
    }

    // Get the readable text color
    private static int readableTextColor(int configuredColor, int surfaceColor) {
        int alpha = configuredColor >>> 24;
        int redDifference = Math.abs((configuredColor >>> 16 & 0xFF)
                - (surfaceColor >>> 16 & 0xFF));
        int greenDifference = Math.abs((configuredColor >>> 8 & 0xFF)
                - (surfaceColor >>> 8 & 0xFF));
        int blueDifference = Math.abs((configuredColor & 0xFF) - (surfaceColor & 0xFF));
        if (redDifference + greenDifference + blueDifference >= 96 && alpha >= 0x80) {
            return configuredColor;
        }
        int luminance = ((surfaceColor >>> 16 & 0xFF) * 299
                + (surfaceColor >>> 8 & 0xFF) * 587
                + (surfaceColor & 0xFF) * 114) / 1000;
        return luminance >= 144 ? 0xFF101820 : 0xFFF4FBFF;
    }

    // Get the fitted text
    private String fittedText(String text, int width, float scale) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return font.plainSubstrByWidth(text,
                Math.max(1, (int) Math.floor(width / Math.max(0.05F, scale))));
    }

    // Draw the scaled text
    private void drawScaledText(String text, float x, float y, int col, float scale,
                                boolean centered, PoseStack poseStack, MultiBufferSource buffer) {
        if (text == null || text.isBlank()) {
            return;
        }
        poseStack.pushPose();
        float drawX = centered ? x - font.width(text) * scale * 0.5F : x;
        float drawY = centered ? y - font.lineHeight * scale * 0.5F : y;
        poseStack.translate(drawX, drawY, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        drawText(text, 0, 0, col, poseStack, buffer);
        poseStack.popPose();
    }

    // Draw the panel
    private static void drawPanel(int width, int height, int background, int border,
                                  int borderWidth, int radius,
                                  PoseStack poseStack, MultiBufferSource buffer) {
        roundedFillAtZ(0, 0, width, height, CONTENT_Z,
                borderWidth > 0 ? border : background, radius, poseStack, buffer);
        if (borderWidth > 0 && width > borderWidth * 2 && height > borderWidth * 2) {
            roundedFillAtZ(borderWidth, borderWidth, width - borderWidth, height - borderWidth,
                    OVERLAY_Z, background, Math.max(0, radius - borderWidth), poseStack, buffer);
        }
    }

    // Handle the rounded fill
    private static void roundedFill(int left, int top, int right, int bottom, int col,
                                    int requestedRadius,
                                    PoseStack poseStack, MultiBufferSource buffer) {
        roundedFillAtZ(left, top, right, bottom, CONTENT_Z, col,
                requestedRadius, poseStack, buffer);
    }

    // Handle the rounded fill at z
    private static void roundedFillAtZ(int left, int top, int right, int bottom, float z,
                                       int col, int requestedRadius,
                                       PoseStack poseStack, MultiBufferSource buffer) {
        if (right <= left || bottom <= top) {
            return;
        }
        int radius = Math.max(0, Math.min(requestedRadius,
                Math.min((right - left) / 2, (bottom - top) / 2)));
        if (radius <= 1) {
            fillAtZ(left, top, right, bottom, z, col, poseStack, buffer);
            return;
        }
        fillAtZ(left + radius, top, right - radius, bottom, z, col, poseStack, buffer);
        fillAtZ(left, top + radius, right, bottom - radius, z, col, poseStack, buffer);
        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5D;
            int inset = (int) Math.ceil(radius - Math.sqrt(Math.max(0.0D,
                    radius * radius - dy * dy)));
            fillAtZ(left + inset, top + row, right - inset, top + row + 1,
                    z, col, poseStack, buffer);
            fillAtZ(left + inset, bottom - row - 1, right - inset, bottom - row,
                    z, col, poseStack, buffer);
        }
    }

    // Get the widget text
    private String widgetText(AccDisplayBlockEntity blockEntity,
                              AdvancedGraphDocument.Node node, CompoundTag elm) {
        String type = elm.getString("Type");
        String label = elm.getString("Text");
        AdvancedGraphDocument.Value val = blockEntity.value(node.id(),
                elm.getString("ValuePort").isBlank()
                        ? elm.getString("Port") : elm.getString("ValuePort"));
        return switch (type) {
            case "button" -> label.isBlank() ? "Button" : label;
            case "toggle" -> label.isBlank() ? "Toggle" : label;
            case "slider" -> slider(val.asNumber(), label);
            case "progress", "text_input" -> label;
            case "value" -> val.type().equals("string") ? val.asString()
                    : val.type().equals("boolean") ? Boolean.toString(val.asBoolean())
                    : String.format(java.util.Locale.ROOT, "%.3f", val.asNumber());
            default -> label;
        };
    }

    // Get the slider
    private static String slider(double val, String label) {
        double finiteValue = Double.isFinite(val) ? val : 0.0D;
        return (label.isBlank() ? "" : label + " ")
                + String.format(java.util.Locale.ROOT, "%.3f", finiteValue);
    }

    // Draw the node graph
    private void renderNodeGraph(AccDisplayBlockEntity blockEntity, AdvancedGraphDocument graph, int width, int height,
                                 PoseStack poseStack, MultiBufferSource buffer) {
        if (graph.nodes().isEmpty()) {
            drawCentered("Active graph is empty", width, height / 2,
                    0xFFB7C0C8, poseStack, buffer);
            return;
        }
        drawText("ACTIVE NODE GRAPH  " + graph.nodes().size() + " nodes / "
                + graph.edges().size() + " links", 6, 5, 0xFF75D6FF, poseStack, buffer);
        Map<String, PixelPoint> positions = new LinkedHashMap<>();
        double viewportZoom = Mth.clamp(graph.viewportZoom(), 0.05D, 1.75D);
        double displayScale = Math.max(0.05D, Math.min(
                Math.max(1, width - 16) / 320.0D,
                Math.max(1, height - 28) / 180.0D));
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            int x = Mth.clamp(8 + (int) Math.round(
                            (graph.viewportX() + node.x() * viewportZoom) * displayScale),
                    8, Math.max(8, width - 73));
            int y = Mth.clamp(20 + (int) Math.round(
                            (graph.viewportY() + node.y() * viewportZoom) * displayScale),
                    20, Math.max(20, height - 17));
            positions.put(node.id(), new PixelPoint(x, y));
        }
        for (AdvancedGraphDocument.Edge edge : graph.edges()) {
            PixelPoint from = positions.get(edge.fromNode());
            PixelPoint to = positions.get(edge.toNode());
            if (from != null && to != null) {
                line(from.x() + 34, from.y() + 6, to.x(), to.y() + 6,
                        1.5F, blockEntity.isExecutionPulsing(GraphRuntime.executionEdgeKey(edge)) ? 0xFFFFD35A : 0xFF56B4A0, poseStack, buffer);
            }
        }
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            PixelPoint pos = positions.get(node.id());
            int x = pos.x();
            int y = pos.y();
            String label = node.label().isBlank() ? node.type() : node.label();
            fillAtZ(x, y, Math.min(width - 5, x + 68), y + 13,
                    OVERLAY_Z, 0xEE243B4B, poseStack, buffer);
            drawText(trim(label, 11), x + 3, y + 2, 0xFFE7EDF3, poseStack, buffer);
        }
    }

    // Draw the plotter
    private void renderPlotter(AccDisplayBlockEntity blockEntity, AdvancedGraphDocument graph,
                               int width, int height, PoseStack poseStack,
                               MultiBufferSource buffer) {
        String contentNodeId = blockEntity.displayFrame().getString("ContentNode");
        AdvancedGraphDocument.Node src = graph.nodes().stream()
                .filter(node -> "acc_display_plotter".equals(node.type())
                        && (contentNodeId.isBlank() || contentNodeId.equals(node.id()))
                        && blockEntity.value(node.id(), "visible").asBoolean()
                        && blockEntity.targetsThisDisplay(node))
                .findFirst().orElse(null);
        double sample = src == null ? 0.0D : blockEntity.value(src.id(), "value").asNumber();
        Map<String, ArrayDeque<Double>> displaySamples = plotSamples.computeIfAbsent(
                blockEntity, ignored -> new LinkedHashMap<>());
        String sampleSeries = src == null ? "" : src.id();
        ArrayDeque<Double> samples = displaySamples.computeIfAbsent(
                sampleSeries, ignored -> new ArrayDeque<>());
        if (samples.isEmpty() || Double.compare(samples.peekLast(), sample) != 0) {
            samples.addLast(Double.isFinite(sample) ? sample : 0.0D);
            while (samples.size() > MAX_PLOT_SAMPLES) samples.removeFirst();
        }
        drawText("FUNCTION PLOTTER", 6, 5, 0xFF75D6FF, poseStack, buffer);
        drawText("value " + String.format(java.util.Locale.ROOT, "%.4f", sample),
                6, 18, 0xFFE7EDF3, poseStack, buffer);
        List<Double> values = new ArrayList<>(samples);
        if (values.isEmpty()) values = List.of(0.0D);
        double minimum = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0D);
        double maximum = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0D);
        double span = Math.max(1.0E-9D, maximum - minimum);
        int plotLeft = 7;
        int plotTop = 31;
        int plotRight = width - 7;
        int plotBottom = height - 17;
        fill(plotLeft, plotTop, plotRight, plotBottom, 0xFF111D25, poseStack, buffer);
        line(plotLeft, plotBottom - 1, plotRight, plotBottom - 1,
                1.0F, 0xFF617581, poseStack, buffer);
        line(plotLeft, plotTop, plotLeft, plotBottom,
                1.0F, 0xFF617581, poseStack, buffer);
        for (int idx = 1; idx < values.size(); idx++) {
            float x0 = plotLeft + (idx - 1) * (plotRight - plotLeft)
                    / (float) Math.max(1, values.size() - 1);
            float x1 = plotLeft + idx * (plotRight - plotLeft)
                    / (float) Math.max(1, values.size() - 1);
            float y0 = plotBottom - 2 - (float) ((values.get(idx - 1) - minimum) / span
                    * Math.max(1, plotBottom - plotTop - 4));
            float y1 = plotBottom - 2 - (float) ((values.get(idx) - minimum) / span
                    * Math.max(1, plotBottom - plotTop - 4));
            lineAtZ(x0, y0, x1, y1, 1.5F, OVERLAY_Z,
                    0xFF67E86B, poseStack, buffer);
        }
        drawText(String.format(java.util.Locale.ROOT, "min %.3f   max %.3f", minimum, maximum),
                6, height - 14, 0xFFB7C0C8, poseStack, buffer);
    }

    // Draw the centered
    private void drawCentered(String text, int width, int y, int col,
                              PoseStack poseStack, MultiBufferSource buffer) {
        drawText(text, (width - font.width(text)) / 2, y, col, poseStack, buffer);
    }

    // Draw the text
    private void drawText(String text, int x, int y, int col,
                          PoseStack poseStack, MultiBufferSource buffer) {
        drawTextAtZ(text, x, y, TEXT_Z, col, poseStack, buffer);
    }

    // Draw the text at z
    private void drawTextAtZ(String text, int x, int y, float z, int col,
                             PoseStack poseStack, MultiBufferSource buffer) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(0.0F, 0.0F, z);
        font.drawInBatch(text, x, y, col, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    // Fill the ACC display
    private static void fill(float left, float top, float right, float bottom, int col,
                             PoseStack poseStack, MultiBufferSource buffer) {
        fillAtZ(left, top, right, bottom, CONTENT_Z, col, poseStack, buffer);
    }

    // Fill the z
    private static void fillAtZ(float left, float top, float right, float bottom, float z,
                                int col, PoseStack poseStack, MultiBufferSource buffer) {
        if (right <= left || bottom <= top) {
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = buffer.getBuffer(RenderType.text(WHITE_TEXTURE));
        vertices.addVertex(matrix, left, top, z).setColor(col)
                .setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, left, bottom, z).setColor(col)
                .setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, bottom, z).setColor(col)
                .setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, top, z).setColor(col)
                .setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
    }

    // Handle the textured quad
    private static void texturedQuad(
            ResourceLocation texture, float left, float top, float right, float bottom,
            float z, PoseStack poseStack, MultiBufferSource buffer
    ) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = buffer.getBuffer(RenderType.text(texture));
        vertices.addVertex(matrix, left, top, z).setColor(0xFFFFFFFF)
                .setUv(0.0F, 0.0F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, left, bottom, z).setColor(0xFFFFFFFF)
                .setUv(0.0F, 1.0F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, bottom, z).setColor(0xFFFFFFFF)
                .setUv(1.0F, 1.0F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, top, z).setColor(0xFFFFFFFF)
                .setUv(1.0F, 0.0F).setLight(LightTexture.FULL_BRIGHT);
    }

    // Build one display line
    private static void line(float x0, float y0, float x1, float y1, float thickness,
                             int col, PoseStack poseStack, MultiBufferSource buffer) {
        lineAtZ(x0, y0, x1, y1, thickness, LINE_Z, col, poseStack, buffer);
    }

    // Handle the line at z
    private static void lineAtZ(float x0, float y0, float x1, float y1, float thickness,
                                float z, int col,
                                PoseStack poseStack, MultiBufferSource buffer) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001F) {
            return;
        }
        float ox = -dy / length * thickness * 0.5F;
        float oy = dx / length * thickness * 0.5F;
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = buffer.getBuffer(RenderType.text(WHITE_TEXTURE));
        vertices.addVertex(matrix, x0 + ox, y0 + oy, z).setColor(col)
                .setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, x0 - ox, y0 - oy, z).setColor(col)
                .setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, x1 - ox, y1 - oy, z).setColor(col)
                .setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, x1 + ox, y1 + oy, z).setColor(col)
                .setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
    }

    // Get the widget color
    private static int widgetColor(CompoundTag elm) {
        return elm.contains("Color", Tag.TAG_INT) ? elm.getInt("Color") : 0xFFFFFFFF;
    }

    // Trim the ACC display
    private static String trim(String text, int maximum) {
        if (text == null) return "";
        return text.length() <= maximum ? text
                : text.substring(0, Math.max(0, maximum - 3)) + "...";
    }

    // Store the pixel point
    private record PixelPoint(int x, int y) {
    }

    // Store the content layout
    private record ContentLayout(float x, float y, int width, int height,
                                 float scale, float rotation) {
    }

    // Check if this should render off screen
    @Override
    public boolean shouldRenderOffScreen(AccDisplayBlockEntity blockEntity) {
        return false;
    }

    // Get the view distance
    @Override
    public int getViewDistance() {
        return 256;
    }

    // Get the render bounding box
    @Override
    public AABB getRenderBoundingBox(AccDisplayBlockEntity blockEntity) {
        net.minecraft.core.BlockPos start = blockEntity.getBlockPos();
        net.minecraft.core.BlockPos end = start
                .below(Math.max(0, blockEntity.networkHeight() - 1))
                .relative(blockEntity.screenRight(), Math.max(0, blockEntity.networkWidth() - 1));
        return new AABB(
                Math.min(start.getX(), end.getX()),
                Math.min(start.getY(), end.getY()),
                Math.min(start.getZ(), end.getZ()),
                Math.max(start.getX(), end.getX()) + 1.0D,
                Math.max(start.getY(), end.getY()) + 1.0D,
                Math.max(start.getZ(), end.getZ()) + 1.0D).inflate(hologramRenderMargin(blockEntity));
    }

    // Get the hologram render margin
    private static double hologramRenderMargin(AccDisplayBlockEntity blockEntity) {
        AdvancedGraphDocument graph = blockEntity.graph();
        double extent = 0.25D;
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (!"acc_hologram_widget".equals(node.type())
                    || !blockEntity.value(node.id(), "visible").asBoolean()
                    || !blockEntity.targetsThisDisplay(node)) {
                continue;
            }
            ListTag elements = node.data().getList("WidgetElements", Tag.TAG_COMPOUND);
            for (int idx = 0; idx < elements.size(); idx++) {
                CompoundTag elm = AdvancedHudElementBinding.resolvedCopy(
                        elements.getCompound(idx), port -> blockEntity.value(node.id(), port));
                if (elm.contains("Visible", Tag.TAG_BYTE) && !elm.getBoolean("Visible")) {
                    continue;
                }
                double scale = elm.contains("Scale", Tag.TAG_DOUBLE)
                        ? Math.max(0.01D, elm.getDouble("Scale")) : 1.0D;
                double diagonal = Math.hypot(elm.getInt("W"), elm.getInt("H")) * scale;
                double furthestPixel = Math.max(
                        Math.max(Math.abs(elm.getInt("X")), Math.abs(elm.getInt("Y"))),
                        Math.max(Math.abs(elm.getInt("X")) + diagonal,
                                Math.abs(elm.getInt("Y")) + diagonal));
                extent = Math.max(extent, Math.min(64.0D,
                        furthestPixel / AccDisplayBlockEntity.PIXELS_PER_BLOCK + 1.0D));
            }
        }
        return extent;
    }
}
