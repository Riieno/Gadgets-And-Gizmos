package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractiveStyles;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// Draw the Advanced HUD Interactive Style
public final class AdvancedHudInteractiveStyleRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final WidgetSprites MINECRAFT_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/button"),
            ResourceLocation.withDefaultNamespace("widget/button_disabled"),
            ResourceLocation.withDefaultNamespace("widget/button_highlighted"));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD interactive style
    private AdvancedHudInteractiveStyleRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the advanced HUD interactive style
    public static void draw(GuiGraphics graphics, Font font, CompoundTag elm,
                            int x, int y, int width, int height, double layoutScale,
                            double numberValue, boolean toggleActive, boolean hovered) {
        if (width <= 0 || height <= 0) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        String type = elm.getString("Type");
        if ("slider".equals(type)) {
            drawSlider(graphics, elm, x, y, width, height, numberValue, hovered);
            return;
        }

        drawButtonBg(graphics, elm, x, y, width, height, toggleActive, hovered);
        String label = elm.getString("Text");
        if (label.isBlank()) {
            label = "toggle".equals(type) ? "Toggle" : "Button";
        }
        int scaledFontHeight = Math.max(1,
                (int) Math.round(AdvancedHudElementStyle.fontSize(elm) * layoutScale));
        int textY = y + Math.max(2, (height - scaledFontHeight) / 2);
        AdvancedHudElementRenderer.drawText(
                graphics, font, elm, label, x + 4, textY,
                Math.max(1, width - 8), Math.max(1, height - 4), layoutScale);
    }

    // Draw the button bg
    private static void drawButtonBg(GuiGraphics graphics, CompoundTag elm,
                                             int x, int y, int width, int height,
                                             boolean toggleActive, boolean hovered) {
        String style = AdvancedHudInteractiveStyles.style(elm);
        switch (style) {
            case AdvancedHudInteractiveStyles.CREATE_ICON ->
                    drawCreateIconButton(graphics, x, y, width, height, toggleActive, hovered);
            case AdvancedHudInteractiveStyles.TNT_BRASS -> {
                CTCreateScreenHelper.renderTntSmallButton(
                        graphics, net.minecraft.client.Minecraft.getInstance().font,
                        x, y, width, height, net.minecraft.network.chat.Component.empty(), hovered, true);
                if (toggleActive) {
                    graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x443E8B57);
                }
            }
            case AdvancedHudInteractiveStyles.CONTROLLER_GRAPH ->
                    AdvancedContraptionControllerScreen.renderNodeOption(
                            graphics, x, y, width, height,
                            AdvancedHudElementStyle.color(elm, "Color",
                                    AdvancedHudElementStyle.DEFAULT_TEXT_COLOR),
                            toggleActive || hovered);
            case AdvancedHudInteractiveStyles.MINECRAFT ->
                    graphics.blitSprite(MINECRAFT_BUTTON_SPRITES.get(true, toggleActive || hovered),
                            x, y, width, height);
            default -> {
                CTCreateScreenHelper.renderTextButton(
                        graphics, net.minecraft.client.Minecraft.getInstance().font,
                        x, y, width, height, net.minecraft.network.chat.Component.empty(),
                        hovered, true, toggleActive);
                if (toggleActive) {
                    graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x443E8B57);
                }
            }
        }
    }

    // Draw the create icon button
    private static void drawCreateIconButton(GuiGraphics graphics, int x, int y, int width, int height,
                                             boolean toggleActive, boolean hovered) {
        AllGuiTextures texture = hovered ? AllGuiTextures.BUTTON_HOVER
                : toggleActive ? AllGuiTextures.BUTTON_GREEN : AllGuiTextures.BUTTON;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(width / 18.0F, height / 18.0F, 1.0F);
        texture.render(graphics, 0, 0);
        graphics.pose().popPose();
    }

    // Draw the slider
    private static void drawSlider(GuiGraphics graphics, CompoundTag elm,
                                   int x, int y, int width, int height,
                                   double val, boolean hovered) {
        double minimum = elm.getDouble("Min");
        double maximum = elm.getDouble("Max");
        if (!(maximum > minimum)) {
            maximum = minimum + 1.0D;
        }
        double amount = Mth.clamp((val - minimum) / (maximum - minimum), 0.0D, 1.0D);
        int col = AdvancedHudElementStyle.color(
                elm, "Color", AdvancedHudElementStyle.DEFAULT_TEXT_COLOR);
        String style = AdvancedHudInteractiveStyles.style(elm);
        switch (style) {
            case AdvancedHudInteractiveStyles.TNT_BRASS ->
                    CTCreateScreenHelper.renderTntHorizontalSlider(
                            graphics, x, y + Math.max(0, (height - 11) / 2), width, (float) amount, hovered);
            case AdvancedHudInteractiveStyles.THRUSTER_TRIM ->
                    drawThrusterTrimSlider(graphics, x, y, width, height, amount, col, hovered);
            case AdvancedHudInteractiveStyles.AILERON_RANGE ->
                    drawAileronRangeSlider(graphics, x, y, width, height, amount, hovered);
            case AdvancedHudInteractiveStyles.POWERED_ZIPLINE ->
                    drawPoweredZiplineSlider(graphics, x, y, width, height, amount, col, hovered);
            case AdvancedHudInteractiveStyles.GYROSCOPE_RANGE ->
                    drawGyroscopeRangeSlider(graphics, x, y, width, height, amount, col, hovered);
            default -> {
                AdvancedContraptionControllerScreen.renderNodeOption(
                        graphics, x, y, width, height, col, hovered);
                int trackLeft = x + Math.min(7, Math.max(2, width / 8));
                int trackWidth = Math.max(3, width - (trackLeft - x) * 2);
                AdvancedContraptionControllerScreen.renderAdvancedSlider(
                        graphics, trackLeft, y + height / 2, trackWidth, amount, col, hovered);
            }
        }
    }

    // Draw the aileron range slider
    private static void drawAileronRangeSlider(GuiGraphics graphics, int x, int y, int width, int height,
                                                double amount, boolean hovered) {
        int inset = Math.min(5, Math.max(2, width / 10));
        int left = x + inset;
        int trackWidth = Math.max(4, width - inset * 2);
        int centerY = y + height / 2;
        int thumbX = left + (int) Math.round(amount * trackWidth);
        CTCreateScreenHelper.renderTntHorizontalTrackFill(
                graphics, left, centerY, trackWidth, left, thumbX);
        CTCreateScreenHelper.renderTntHorizontalRangeMaxHandle(graphics, thumbX, centerY, hovered);
    }

    // Draw the thruster trim slider
    private static void drawThrusterTrimSlider(GuiGraphics graphics, int x, int y, int width, int height,
                                                double amount, int col, boolean hovered) {
        CTCreateScreenHelper.renderInset(graphics, x, y, width, height, hovered, false);
        int centerY = y + height / 2;
        int trackLeft = x + 8;
        int trackRight = x + Math.max(8, width - 8);
        graphics.fill(trackLeft, centerY - 2, trackRight, centerY + 2, 0xFF3D2F20);
        int fillRight = trackLeft + (int) Math.round(amount * Math.max(0, trackRight - trackLeft));
        graphics.fill(trackLeft, centerY - 2, fillRight, centerY + 2, opaque(col));
        int handleWidth = Math.min(17, Math.max(4, width));
        int handleX = x + (int) Math.round(amount * Math.max(0, width - handleWidth));
        CTCreateScreenHelper.renderTntRangeMinHandle(
                graphics, handleX, centerY - 3, hovered);
    }

    // Draw the powered zipline slider
    private static void drawPoweredZiplineSlider(GuiGraphics graphics, int x, int y, int width, int height,
                                                  double amount, int col, boolean hovered) {
        CTCreateScreenHelper.renderInset(graphics, x, y, width, height, hovered, false);
        int left = x + 4;
        int right = x + Math.max(4, width - 4);
        int trackTop = y + Math.max(2, height / 2 - 2);
        graphics.fill(left, trackTop, right, trackTop + 4, 0xFF3D2F20);
        int fillRight = left + (int) Math.round(amount * Math.max(0, right - left));
        graphics.fill(left, trackTop, fillRight, trackTop + 4, opaque(col));
        int handleWidth = Math.min(8, Math.max(4, width / 8));
        int handleX = x + (int) Math.round(amount * Math.max(0, width - handleWidth));
        CTCreateScreenHelper.renderTextButton(
                graphics, net.minecraft.client.Minecraft.getInstance().font,
                handleX, y - 1, handleWidth, height + 2,
                net.minecraft.network.chat.Component.empty(), hovered, true, false);
    }

    // Draw the gyroscope range slider
    private static void drawGyroscopeRangeSlider(GuiGraphics graphics, int x, int y, int width, int height,
                                                  double amount, int col, boolean hovered) {
        CTCreateScreenHelper.renderInset(graphics, x, y, width, height, hovered, false);
        int trackY = y + height / 2 - 1;
        graphics.fill(x + 2, trackY, x + width - 2, trackY + 2, 0xFF5B4633);
        int handleX = x + 2 + (int) Math.round(amount * Math.max(0, width - 4));
        graphics.fill(x + 2, trackY - 1, handleX, trackY + 3, opaque(col));
        graphics.fill(handleX - 2, y + 1, handleX + 2, y + height - 1, 0xFF2D4A92);
        if (hovered) {
            graphics.fill(handleX - 3, y, handleX + 3, y + height, 0x44FFFFFF);
        }
    }

    // Get the opaque
    private static int opaque(int col) {
        return (col & 0x00FFFFFF) | 0xFF000000;
    }
}
