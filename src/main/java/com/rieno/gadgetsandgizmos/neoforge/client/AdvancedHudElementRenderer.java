package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

// Draw the Advanced HUD Element
public final class AdvancedHudElementRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD element
    private AdvancedHudElementRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the box
    public static void drawBox(GuiGraphics graphics, CompoundTag elm,
                               int x, int y, int width, int height, double layoutScale) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int background = AdvancedHudElementStyle.color(elm, "Color",
                AdvancedHudElementStyle.DEFAULT_BOX_BACKGROUND_COLOR);
        int borderColor = AdvancedHudElementStyle.color(elm, "BorderColor",
                AdvancedHudElementStyle.DEFAULT_BOX_BORDER_COLOR);
        int radius = Math.max(0, (int) Math.round(AdvancedHudElementStyle.borderRadius(elm) * layoutScale));
        int border = Math.max(0, (int) Math.round(AdvancedHudElementStyle.borderWidth(elm) * layoutScale));
        radius = Math.min(radius, Math.min(width, height) / 2);
        border = Math.min(border, Math.min(width, height) / 2);

        if (border <= 0) {
            fillRoundedRect(graphics, x, y, x + width, y + height, radius, background);
            return;
        }

        fillRoundedRect(graphics, x, y, x + width, y + height, radius, borderColor);
        int innerWidth = width - border * 2;
        int innerHeight = height - border * 2;
        if (innerWidth > 0 && innerHeight > 0) {
            fillRoundedRect(graphics, x + border, y + border,
                    x + border + innerWidth, y + border + innerHeight,
                    Math.max(0, radius - border), background);
        }
    }

    // Draw the text
    public static void drawText(GuiGraphics graphics, Font font, CompoundTag elm, String text,
                                int x, int y, int width, int height, double layoutScale) {
        if (text == null || text.isBlank() || width <= 0 || height <= 0) {
            return;
        }
        float textScale = (float) Math.max(0.05D,
                AdvancedHudElementStyle.fontSize(elm)
                        / (double) AdvancedHudElementStyle.DEFAULT_FONT_SIZE * layoutScale);
        int availableWidth = Math.max(1, (int) Math.floor(width / textScale + 0.01D));
        Component fitted = fitText(font, elm, text, availableWidth);
        if (fitted.getString().isEmpty()) {
            return;
        }
        int col = AdvancedHudElementStyle.color(elm, "Color",
                AdvancedHudElementStyle.DEFAULT_TEXT_COLOR);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(textScale, textScale, 1.0F);
        graphics.drawString(font, fitted, 0, 0, col, false);
        graphics.pose().popPose();
    }

    // Get the fit text
    private static Component fitText(Font font, CompoundTag elm, String text, int availableWidth) {
        Component full = styledText(elm, text);
        if (font.width(full) <= availableWidth) {
            return full;
        }
        int codePoints = text.codePointCount(0, text.length());
        int low = 0;
        int high = codePoints;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            int end = text.offsetByCodePoints(0, middle);
            if (font.width(styledText(elm, text.substring(0, end))) <= availableWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return styledText(elm, text.substring(0, text.offsetByCodePoints(0, low)));
    }

    // Get the styled text
    private static Component styledText(CompoundTag elm, String text) {
        return Component.literal(text).withStyle(style -> switch (AdvancedHudElementStyle.fontStyle(elm)) {
            case "bold" -> style.withBold(true);
            case "italic" -> style.withItalic(true);
            case "bold_italic" -> style.withBold(true).withItalic(true);
            case "underline" -> style.withUnderlined(true);
            case "strikethrough" -> style.withStrikethrough(true);
            case "obfuscated" -> style.withObfuscated(true);
            default -> style;
        });
    }

    // Fill the rounded rect
    private static void fillRoundedRect(GuiGraphics graphics, int left, int top, int right, int bottom,
                                        int requestedRadius, int col) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        int radius = Math.max(0, Math.min(requestedRadius, Math.min(width, height) / 2));
        if (radius == 0) {
            graphics.fill(left, top, right, bottom, col);
            return;
        }
        double radiusSquared = radius * (double) radius;
        for (int row = 0; row < height; row++) {
            int distanceFromEdge = Math.min(row, height - row - 1);
            int inset = 0;
            if (distanceFromEdge < radius) {
                double dy = radius - distanceFromEdge - 0.5D;
                inset = Math.max(0, radius - (int) Math.floor(Math.sqrt(
                        Math.max(0.0D, radiusSquared - dy * dy))));
            }
            graphics.fill(left + inset, top + row, right - inset, top + row + 1, col);
        }
    }
}
