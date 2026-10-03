package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Draw the Contraption Network Linker item
public class ContraptionNetworkLinkerItemRenderer extends CustomRenderedItemModelRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int COUNT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int INPUT_MODE_TEXT_COLOR = 0xFF3373F2;
    private static final int OUTPUT_MODE_TEXT_COLOR = 0xFFF23D47;
    private static final int WORKER_MODE_TEXT_COLOR = 0xFF33E652;
    private static final int TABLE_COLUMN_COUNT = 3;
    private static final int TABLE_BLOCK_ROWS = 5;
    private static final int TABLE_LINE_COUNT = TABLE_BLOCK_ROWS + 3;
    private static final float MODE_TABLE_GAP_LINES = 1.0f;

    private static final ContraptionNetworkLinkerData.LinkMode[] TABLE_MODES = {
            ContraptionNetworkLinkerData.LinkMode.OUTPUT,
            ContraptionNetworkLinkerData.LinkMode.INPUT,
            ContraptionNetworkLinkerData.LinkMode.SCM
    };

    private static final float SCREEN_LEFT = 5.6f / 16.0f - 0.5f;
    private static final float SCREEN_RIGHT = 10.4f / 16.0f - 0.5f;
    private static final float SCREEN_TOP = 3.6f / 16.0f - 0.5f;
    private static final float SCREEN_BOTTOM = 7.4f / 16.0f - 0.5f;
    private static final float SCREEN_TEXT_Y = 7.08f / 16.0f - 0.5f;
    private static final float SCREEN_TEXT_WIDTH_PAD = 0.9f;
    private static final float SCREEN_TEXT_HEIGHT_PAD = 0.86f;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the contraption network linker item
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffer,
                          int light, int overlay) {
        List<ContraptionNetworkLinkerData.LinkedTarget> targets = ContraptionNetworkLinkerData.readClientTargets(stack);
        int linkedBlocks = linkedBlockCount(targets);

        if (ctx == ItemDisplayContext.GUI) {
            renderer.render(model.getOriginalModel(), light);
        } else {
            ContraptionNetworkLinkerItem.renderWithoutFoil(() -> renderer.render(model.getOriginalModel(), light));
        }

        poseStack.pushPose();
        renderScannerDisplay(targets, linkedBlocks, ContraptionNetworkLinkerData.getClientEditMode(stack),
                poseStack, buffer);
        poseStack.popPose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the linked block count
    public static int linkedBlockCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        return linkedBlockCount(ContraptionNetworkLinkerData.readClientTargets(stack));
    }

    // Draw the scanner target table
    private static void renderScannerDisplay(List<ContraptionNetworkLinkerData.LinkedTarget> targets, int linkedBlocks,
                                             ContraptionNetworkLinkerData.LinkMode activeMode, PoseStack poseStack,
                                             MultiBufferSource buffer) {
        Font font = Minecraft.getInstance().font;
        List<List<String>> targetColumns = targetColumns(targets);
        List<String> headers = tableHeaders();
        String activeModeText = Component.translatable("item.createthrusters.contraption_network_linker.mode",
                Component.translatable(activeMode.translationKey())).getString();
        float maxWidth = (SCREEN_RIGHT - SCREEN_LEFT) * SCREEN_TEXT_WIDTH_PAD;
        float maxHeight = (SCREEN_BOTTOM - SCREEN_TOP) * SCREEN_TEXT_HEIGHT_PAD;
        float columnWidth = maxWidth / TABLE_COLUMN_COUNT;
        float scale = Math.min(columnWidth / widestHeaderWidth(font, headers),
                Math.min(maxWidth / Math.max(1, font.width(activeModeText)),
                        maxHeight / (font.lineHeight * (TABLE_LINE_COUNT + MODE_TABLE_GAP_LINES))));
        int maximumColumnWidth = Math.max(1, (int) (columnWidth / scale));
        float topLine = -font.lineHeight * (TABLE_LINE_COUNT + MODE_TABLE_GAP_LINES) * 0.5f;
        float tableTopLine = topLine + font.lineHeight * (MODE_TABLE_GAP_LINES + 1.0f);

        poseStack.pushPose();
        poseStack.translate((SCREEN_LEFT + SCREEN_RIGHT) * 0.5f,
                SCREEN_TEXT_Y,
                (SCREEN_TOP + SCREEN_BOTTOM) * 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        poseStack.scale(scale, scale, scale);

        Matrix4f pose = poseStack.last().pose();
        drawCenteredScreenText(font, activeModeText, 0.0f, topLine, modeTextColor(activeMode), pose, buffer);
        for (int columnIndex = 0; columnIndex < TABLE_COLUMN_COUNT; columnIndex++) {
            float x = (-maxWidth * 0.5f + columnWidth * (columnIndex + 0.5f)) / scale;
            ContraptionNetworkLinkerData.LinkMode mode = TABLE_MODES[columnIndex];
            drawCenteredScreenText(font, headers.get(columnIndex), x, tableTopLine,
                    modeTextColor(mode), pose, buffer);
            List<String> labels = targetColumns.get(columnIndex);
            for (int rowIndex = 0; rowIndex < Math.min(TABLE_BLOCK_ROWS, labels.size()); rowIndex++) {
                String label = truncateScreenText(font, labels.get(rowIndex), maximumColumnWidth);
                drawCenteredScreenText(font, label, x, tableTopLine + font.lineHeight * (rowIndex + 1),
                        COUNT_TEXT_COLOR, pose, buffer);
            }
        }
        drawCenteredScreenText(font, linkedBlocks + " BLOCKS", 0.0f,
                tableTopLine + font.lineHeight * (TABLE_BLOCK_ROWS + 1), COUNT_TEXT_COLOR, pose, buffer);
        poseStack.popPose();
    }

    // Get the target labels for each table column
    private static List<List<String>> targetColumns(List<ContraptionNetworkLinkerData.LinkedTarget> targets) {
        List<List<String>> columns = new ArrayList<>();
        for (ContraptionNetworkLinkerData.LinkMode mode : TABLE_MODES) {
            Set<String> seenBlocks = new LinkedHashSet<>();
            List<String> labels = new ArrayList<>();
            for (ContraptionNetworkLinkerData.LinkedTarget target : targets) {
                if (target.mode() != mode || !seenBlocks.add(linkedBlockKey(target))) {
                    continue;
                }
                labels.add(targetLabel(target));
            }
            labels.sort(String.CASE_INSENSITIVE_ORDER);
            columns.add(labels);
        }
        return columns;
    }

    // Get the table headers
    private static List<String> tableHeaders() {
        List<String> headers = new ArrayList<>();
        for (ContraptionNetworkLinkerData.LinkMode mode : TABLE_MODES) {
            headers.add(Component.translatable(mode.translationKey()).getString());
        }
        return headers;
    }

    // Get the widest table header
    private static int widestHeaderWidth(Font font, List<String> headers) {
        int width = 1;
        for (String header : headers) {
            width = Math.max(width, font.width(header));
        }
        return width;
    }

    // Get the display label for a target
    private static String targetLabel(ContraptionNetworkLinkerData.LinkedTarget target) {
        if (target.label() != null && !target.label().isBlank()) {
            return target.label();
        }
        String blockId = target.blockId();
        int separator = blockId.indexOf(':');
        return separator >= 0 ? blockId.substring(separator + 1) : blockId;
    }

    // Get the block key for a target
    private static String linkedBlockKey(ContraptionNetworkLinkerData.LinkedTarget target) {
        String subLevel = target.subLevelId() == null ? "world" : target.subLevelId().toString();
        return subLevel + ":" + target.blockPos().asLong();
    }

    // Get the full linked block count
    private static int linkedBlockCount(List<ContraptionNetworkLinkerData.LinkedTarget> targets) {
        Set<String> linkedBlocks = new LinkedHashSet<>();
        for (ContraptionNetworkLinkerData.LinkedTarget target : targets) {
            linkedBlocks.add(linkedBlockKey(target));
        }
        return linkedBlocks.size();
    }

    // Get the screen color for the current link mode
    private static int modeTextColor(ContraptionNetworkLinkerData.LinkMode mode) {
        return switch (mode) {
            case INPUT -> INPUT_MODE_TEXT_COLOR;
            case OUTPUT -> OUTPUT_MODE_TEXT_COLOR;
            case SCM -> WORKER_MODE_TEXT_COLOR;
        };
    }

    // Draw centered text above the scanner screen without depth conflicts
    private static void drawCenteredScreenText(Font font, String text, float x, float y, int color, Matrix4f pose,
                                               MultiBufferSource buffer) {
        float left = x - font.width(text) / 2.0f;
        font.drawInBatch(text, left, y, color, false, pose, buffer,
                Font.DisplayMode.SEE_THROUGH, 0x80000000, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, left, y, color, false, pose, buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    // Truncate text to fit a table column
    private static String truncateScreenText(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        String prefix = font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis)));
        return prefix.isEmpty() ? ellipsis : prefix + ellipsis;
    }
}
