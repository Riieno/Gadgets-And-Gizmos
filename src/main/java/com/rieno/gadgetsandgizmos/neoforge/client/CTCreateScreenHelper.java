package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Draw the reusable Create-style panels, slots, buttons and sliders used by addon screens
public final class CTCreateScreenHelper {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep shared screen sprites and active colors in one place
    public static final ResourceLocation SPRITE_MAP_TEX =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/sprite_map.png");
    private static final int SPRITE_MAP_W = 298, SPRITE_MAP_H = 328;

    public static final ResourceLocation ALTITUDE_TEX =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/altitude_sensor.png");

    public static final ResourceLocation THRUSTER_GUI_TEX =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/thruster_gui.png");
    public static final ResourceLocation AILERON_BEARING_GUI_TEX =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/aileron_bearing_gui.png");
    public static final ResourceLocation BI_DIRECTIONAL_GEARSHIFT_GUI_TEX =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/bi_directional_gearshift_gui.png");
    public static final ResourceLocation CLAW_GUI_TEX =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/claw_gui.png");
    public static final ResourceLocation TNT_GUI_SPRITES =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/sprites.png");
    private static final int TNT_GUI_TEX_W = 256, TNT_GUI_TEX_H = 256;
    private static final int TNT_SPRITES_W = 256, TNT_SPRITES_H = 256;
    private static final int THRUSTER_PANEL_SRC_X = 40, THRUSTER_PANEL_SRC_Y = 50,
            THRUSTER_PANEL_SRC_W = 200, THRUSTER_PANEL_SRC_H = 195;
    private static final int CLAW_PANEL_SRC_X = 40, CLAW_PANEL_SRC_Y = 91,
            CLAW_PANEL_SRC_W = 176, CLAW_PANEL_SRC_H = 155;
    private static final int TNT_BUTTON_SRC_X = 56, TNT_BUTTON_SRC_Y = 18,
            TNT_BUTTON_SRC_W = 39, TNT_BUTTON_SRC_H = 10;
    private static final int TNT_SLIDER_SRC_X = 112, TNT_SLIDER_SRC_Y = 91,
            TNT_SLIDER_SRC_W = 65, TNT_SLIDER_SRC_H = 11;
    private static final int TNT_SLIDER_THUMB_SRC_X = 114, TNT_SLIDER_THUMB_SRC_Y = 17,
            TNT_SLIDER_THUMB_SRC_W = 9, TNT_SLIDER_THUMB_SRC_H = 9;
    private static final int TNT_VERTICAL_HANDLE_SRC_W = 17, TNT_VERTICAL_HANDLE_SRC_H = 6;
    private static final int TNT_VERTICAL_FILL_SRC_X = 28, TNT_VERTICAL_FILL_SRC_Y = 58,
            TNT_VERTICAL_FILL_SRC_W = 9, TNT_VERTICAL_FILL_SRC_H = 56;
    private static final int TNT_RANGE_TRACK_SRC_X = 13, TNT_RANGE_TRACK_SRC_Y = 127,
            TNT_RANGE_TRACK_SRC_W = 52, TNT_RANGE_TRACK_SRC_H = 5;
    private static final int TNT_RANGE_MIN_THUMB_SRC_X = 66, TNT_RANGE_MAX_THUMB_SRC_X = 69,
            TNT_RANGE_THUMB_SRC_Y = 127, TNT_RANGE_THUMB_SRC_W = 2, TNT_RANGE_THUMB_SRC_H = 5;
    private static final int TNT_RANGE_FILL_SRC_X = 72, TNT_RANGE_FILL_SRC_Y = 128,
            TNT_RANGE_FILL_SRC_W = 1, TNT_RANGE_FILL_SRC_H = 3;
    private static final int TNT_RANGE_SCALE = 2;
    private static final int TNT_RANGE_TRACK_DISPLAY_H = TNT_RANGE_TRACK_SRC_H * TNT_RANGE_SCALE;
    private static final int TNT_RANGE_THUMB_DISPLAY_W = TNT_RANGE_THUMB_SRC_W * TNT_RANGE_SCALE;
    private static final int TNT_RANGE_THUMB_DISPLAY_H = TNT_RANGE_THUMB_SRC_H * TNT_RANGE_SCALE;
    private static final int TNT_RANGE_FILL_DISPLAY_H = TNT_RANGE_FILL_SRC_H * TNT_RANGE_SCALE;

    private static final int BG_BANNER_SRC_X = 69, BG_BANNER_SRC_Y = 5,
                             BG_BANNER_SRC_W = 224, BG_BANNER_SRC_H = 21;

    private static final int BG_BODY_SRC_X = 77, BG_BODY_SRC_Y = 33,
                             BG_BODY_SRC_W = 208, BG_BODY_SRC_H = 20;

    private static final int BG_FOOT_SRC_X = 77, BG_FOOT_SRC_Y = 61,
                             BG_FOOT_SRC_W = 208, BG_FOOT_SRC_H = 16;

    public static final int BANNER_HEIGHT = BG_BANNER_SRC_H;

    private static final int SEP_SRC_X = 37, SEP_SRC_Y = 141,
                             SEP_SRC_W = 256, SEP_SRC_H = 3;

    public static final int HANDLE_SRC_X = 9, HANDLE_SRC_Y = 125,
                            HANDLE_SRC_W = 20, HANDLE_SRC_H = 16;

    private static final int BTN_SRC_Y = 90, BTN_SRC_H = 14;
    private static final int BTN_L_SRC_X = 9,  BTN_L_SRC_W = 14;
    private static final int BTN_M_SRC_X = 38;
    private static final int BTN_R_SRC_X = 56, BTN_R_SRC_W = 15;

    private static final int SLOT_SRC_Y = 172, SLOT_SRC_H = 23;
    private static final int SLOT_L_SRC_X = 11, SLOT_L_SRC_W = 10;
    private static final int SLOT_M_SRC_X = 28, SLOT_M_SRC_W = 20;
    private static final int SLOT_R_SRC_X = 56, SLOT_R_SRC_W = 10;
        private static final int SLOT_UPGRADE_OVERLAY_SRC_X = 80;
        private static final int SLOT_LIST_OVERLAY_SRC_X = 101;
        private static final int SLOT_LENS_OVERLAY_SRC_X = 122;
        private static final int SLOT_OVERLAY_SRC_Y = 177;
        private static final int SLOT_OVERLAY_SRC_W = 14, SLOT_OVERLAY_SRC_H = 14;

    public static final int SLOT_HEIGHT = SLOT_SRC_H;

    private static final int SLIDER_BG_SRC_X = 236, SLIDER_BG_SRC_Y = 219,
                             SLIDER_BG_SRC_W = 57,  SLIDER_BG_SRC_H = 105;

    public static final int TITLE_COLOR        = 0xF3EEE4;

        public static final int BANNER_TITLE_COLOR = 0xFFFFFF;
    public static final int LABEL_COLOR        = 0xDDD3C3;
    public static final int VALUE_COLOR        = 0xC5E6FF;
    public static final int SUBTLE_TEXT_COLOR  = 0x8E8B83;
    public static final int INSET_BORDER       = 0xFF443526;
    public static final int INSET_FILL         = 0xCC1E1711;
    public static final int INSET_FILL_HOVER   = 0xCC2B2118;
    public static final int INSET_FILL_ACTIVE  = 0xCC314226;
        public static final int FREQ_A_BORDER      = 0xFF4A171C;
        public static final int FREQ_A_FILL        = 0xFF8A2A32;
        public static final int FREQ_B_BORDER      = 0xFF152244;
        public static final int FREQ_B_FILL        = 0xFF2D4A92;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT create screen
    private CTCreateScreenHelper() {}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the thruster gui background
    public static void renderThrusterGuiBackground(GuiGraphics graphics, int x, int y) {
        graphics.blit(THRUSTER_GUI_TEX, x + THRUSTER_PANEL_SRC_X, y + THRUSTER_PANEL_SRC_Y,
                THRUSTER_PANEL_SRC_W, THRUSTER_PANEL_SRC_H,
                THRUSTER_PANEL_SRC_X, THRUSTER_PANEL_SRC_Y,
                THRUSTER_PANEL_SRC_W, THRUSTER_PANEL_SRC_H,
                TNT_GUI_TEX_W, TNT_GUI_TEX_H);
    }

    // Draw the aileron bearing gui background
    public static void renderAileronBearingGuiBackground(GuiGraphics graphics, int x, int y) {
        graphics.blit(AILERON_BEARING_GUI_TEX, x, y, TNT_GUI_TEX_W, TNT_GUI_TEX_H,
                0, 0, TNT_GUI_TEX_W, TNT_GUI_TEX_H, TNT_GUI_TEX_W, TNT_GUI_TEX_H);
    }

    // Draw the bi directional gearshift gui background
    public static void renderBiDirectionalGearshiftGuiBackground(GuiGraphics graphics, int x, int y) {
        graphics.blit(BI_DIRECTIONAL_GEARSHIFT_GUI_TEX, x, y, TNT_GUI_TEX_W, TNT_GUI_TEX_H,
                0, 0, TNT_GUI_TEX_W, TNT_GUI_TEX_H, TNT_GUI_TEX_W, TNT_GUI_TEX_H);
    }

    // Draw the claw gui background
    public static void renderClawGuiBackground(GuiGraphics graphics, int x, int y) {
        graphics.blit(CLAW_GUI_TEX, x + CLAW_PANEL_SRC_X, y + CLAW_PANEL_SRC_Y,
                CLAW_PANEL_SRC_W, CLAW_PANEL_SRC_H,
                CLAW_PANEL_SRC_X, CLAW_PANEL_SRC_Y,
                CLAW_PANEL_SRC_W, CLAW_PANEL_SRC_H,
                TNT_GUI_TEX_W, TNT_GUI_TEX_H);
    }

    // Draw the TNT small button
    public static void renderTntSmallButton(GuiGraphics graphics, Font font, int x, int y,
                                            int width, int height, Component text,
                                            boolean hovered, boolean active) {
        blitTntSprite(graphics, x, y, width, height,
                TNT_BUTTON_SRC_X, TNT_BUTTON_SRC_Y, TNT_BUTTON_SRC_W, TNT_BUTTON_SRC_H);
        if (hovered) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x18FFFFFF);
        }
        int col = active ? 0xFFFFFFFF : 0xFF704018;
        drawFittedCenteredString(graphics, font, text, x + width / 2, y, width - 4, height, 0.82f, col);
    }

    // Draw the TNT horizontal slider
    public static void renderTntHorizontalSlider(GuiGraphics graphics, int x, int y,
                                                 int width, float val, boolean hovered) {
        blitTntSprite(graphics, x, y, width, TNT_SLIDER_SRC_H,
                TNT_SLIDER_SRC_X, TNT_SLIDER_SRC_Y, TNT_SLIDER_SRC_W, TNT_SLIDER_SRC_H);
        float clamped = Math.max(0.0f, Math.min(1.0f, val));
        int thumbX = x + Math.round(clamped * Math.max(0, width - TNT_SLIDER_THUMB_SRC_W));
        blitTntSprite(graphics, thumbX, y + 1, TNT_SLIDER_THUMB_SRC_W, TNT_SLIDER_THUMB_SRC_H,
                TNT_SLIDER_THUMB_SRC_X, TNT_SLIDER_THUMB_SRC_Y,
                TNT_SLIDER_THUMB_SRC_W, TNT_SLIDER_THUMB_SRC_H);
        if (hovered) {
            graphics.fill(thumbX, y + 1, thumbX + TNT_SLIDER_THUMB_SRC_W, y + 1 + TNT_SLIDER_THUMB_SRC_H, 0x22FFFFFF);
        }
    }

    // Draw the TNT vertical handle
    public static void renderTntVerticalHandle(GuiGraphics graphics, int x, int y, boolean right, boolean hovered) {
        int sourceX = right ? 26 : 8;
        int sourceY = hovered ? 9 : 2;
        blitTntSprite(graphics, x, y, TNT_VERTICAL_HANDLE_SRC_W, TNT_VERTICAL_HANDLE_SRC_H,
                sourceX, sourceY, TNT_VERTICAL_HANDLE_SRC_W, TNT_VERTICAL_HANDLE_SRC_H);
    }

    // Draw the TNT range min handle
    public static void renderTntRangeMinHandle(GuiGraphics graphics, int x, int y, boolean hovered) {
        renderTntColoredVerticalHandle(graphics, x, y, 30, hovered);
    }

    // Draw the TNT range max handle
    public static void renderTntRangeMaxHandle(GuiGraphics graphics, int x, int y, boolean hovered) {
        renderTntVerticalHandle(graphics, x, y, false, hovered);
    }

    // Draw the TNT horizontal range min handle
    public static void renderTntHorizontalRangeMinHandle(GuiGraphics graphics, int centerX, int centerY,
                                                         boolean hovered) {
        renderTntHorizontalRangeHandle(graphics, centerX, centerY, TNT_RANGE_MIN_THUMB_SRC_X, hovered);
    }

    // Draw the TNT horizontal range max handle
    public static void renderTntHorizontalRangeMaxHandle(GuiGraphics graphics, int centerX, int centerY,
                                                         boolean hovered) {
        renderTntHorizontalRangeHandle(graphics, centerX, centerY, TNT_RANGE_MAX_THUMB_SRC_X, hovered);
    }

    // Draw the TNT blend handle
    public static void renderTntBlendHandle(GuiGraphics graphics, int x, int y, boolean hovered) {
        renderTntVerticalHandle(graphics, x, y, false, hovered);
    }

    // Draw the TNT vertical track fill
    public static void renderTntVerticalTrackFill(GuiGraphics graphics, int centerX, int trackTop,
                                                  int trackHeight, int fillTop, int fillBottom) {
        int top = Math.max(trackTop, Math.min(fillTop, fillBottom));
        int bottom = Math.min(trackTop + trackHeight, Math.max(fillTop, fillBottom));
        if (bottom <= top) {
            return;
        }

        int sourceOffset = Math.max(0, Math.min(TNT_VERTICAL_FILL_SRC_H - 1, top - trackTop));
        int sourceHeight = Math.min(TNT_VERTICAL_FILL_SRC_H - sourceOffset, bottom - top);
        if (sourceHeight <= 0) {
            return;
        }

        graphics.blit(TNT_GUI_SPRITES, centerX - TNT_VERTICAL_FILL_SRC_W / 2, top,
                TNT_VERTICAL_FILL_SRC_W, bottom - top,
                TNT_VERTICAL_FILL_SRC_X, TNT_VERTICAL_FILL_SRC_Y + sourceOffset,
                TNT_VERTICAL_FILL_SRC_W, sourceHeight, TNT_SPRITES_W, TNT_SPRITES_H);
    }

    // Draw the TNT horizontal track fill
    public static void renderTntHorizontalTrackFill(GuiGraphics graphics, int trackLeft, int centerY,
                                                    int trackWidth, int fillLeft, int fillRight) {
        graphics.blit(TNT_GUI_SPRITES, trackLeft, centerY - TNT_RANGE_TRACK_DISPLAY_H / 2,
                trackWidth, TNT_RANGE_TRACK_DISPLAY_H,
                TNT_RANGE_TRACK_SRC_X, TNT_RANGE_TRACK_SRC_Y,
                TNT_RANGE_TRACK_SRC_W, TNT_RANGE_TRACK_SRC_H,
                TNT_SPRITES_W, TNT_SPRITES_H);

        int left = Math.max(trackLeft, Math.min(fillLeft, fillRight));
        int right = Math.min(trackLeft + trackWidth, Math.max(fillLeft, fillRight));
        if (right <= left) {
            return;
        }

        graphics.blit(TNT_GUI_SPRITES, left, centerY - TNT_RANGE_FILL_DISPLAY_H / 2,
                right - left, TNT_RANGE_FILL_DISPLAY_H,
                TNT_RANGE_FILL_SRC_X, TNT_RANGE_FILL_SRC_Y,
                TNT_RANGE_FILL_SRC_W, TNT_RANGE_FILL_SRC_H,
                TNT_SPRITES_W, TNT_SPRITES_H);
    }

    // Draw the TNT colored vertical handle
    private static void renderTntColoredVerticalHandle(GuiGraphics graphics, int x, int y,
                                                       int sourceY, boolean hovered) {
        blitTntSprite(graphics, x, y, TNT_VERTICAL_HANDLE_SRC_W, TNT_VERTICAL_HANDLE_SRC_H,
                8, sourceY, TNT_VERTICAL_HANDLE_SRC_W, TNT_VERTICAL_HANDLE_SRC_H);
        if (hovered) {
            graphics.fill(x, y, x + TNT_VERTICAL_HANDLE_SRC_W, y + TNT_VERTICAL_HANDLE_SRC_H, 0x18FFFFFF);
        }
    }

    // Draw the TNT horizontal handle
    private static void renderTntHorizontalHandle(GuiGraphics graphics, int centerX, int centerY, boolean hovered) {
        int sourceY = hovered ? 9 : 2;
        renderTntHorizontalHandle(graphics, centerX, centerY, 8, sourceY, hovered);
    }

    // Draw the TNT colored horizontal handle
    private static void renderTntColoredHorizontalHandle(GuiGraphics graphics, int centerX, int centerY,
                                                        int sourceY, boolean hovered) {
        renderTntHorizontalHandle(graphics, centerX, centerY, 8, sourceY, hovered);
    }

    // Draw the TNT horizontal range handle
    private static void renderTntHorizontalRangeHandle(GuiGraphics graphics, int centerX, int centerY,
                                                       int sourceX, boolean hovered) {
        int x = centerX - TNT_RANGE_THUMB_DISPLAY_W / 2;
        int y = centerY - TNT_RANGE_THUMB_DISPLAY_H / 2;
        blitTntSprite(graphics, x, y, TNT_RANGE_THUMB_DISPLAY_W, TNT_RANGE_THUMB_DISPLAY_H,
                sourceX, TNT_RANGE_THUMB_SRC_Y, TNT_RANGE_THUMB_SRC_W, TNT_RANGE_THUMB_SRC_H);
        if (hovered) {
            graphics.fill(x, y, x + TNT_RANGE_THUMB_DISPLAY_W, y + TNT_RANGE_THUMB_DISPLAY_H, 0x18FFFFFF);
        }
    }

    // Draw the TNT horizontal handle
    private static void renderTntHorizontalHandle(GuiGraphics graphics, int centerX, int centerY,
                                                  int sourceX, int sourceY, boolean hovered) {
        int x = centerX - TNT_VERTICAL_HANDLE_SRC_H / 2;
        int y = centerY - TNT_VERTICAL_HANDLE_SRC_W / 2;
        blitTntSpriteRotatedClockwise(graphics, x, y,
                TNT_VERTICAL_HANDLE_SRC_W, TNT_VERTICAL_HANDLE_SRC_H,
                sourceX, sourceY, TNT_VERTICAL_HANDLE_SRC_W, TNT_VERTICAL_HANDLE_SRC_H);
        if (hovered) {
            graphics.fill(x, y, x + TNT_VERTICAL_HANDLE_SRC_H, y + TNT_VERTICAL_HANDLE_SRC_W, 0x18FFFFFF);
        }
    }

    // Draw the TNT sprite
    private static void blitTntSprite(GuiGraphics graphics, int x, int y, int width, int height,
                                      int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        graphics.blit(TNT_GUI_SPRITES, x, y, width, height,
                sourceX, sourceY, sourceWidth, sourceHeight, TNT_SPRITES_W, TNT_SPRITES_H);
    }

    // Draw the TNT sprite rotated clockwise
    private static void blitTntSpriteRotatedClockwise(GuiGraphics graphics, int x, int y, int width, int height,
                                                      int sourceX, int sourceY,
                                                      int sourceWidth, int sourceHeight) {
        graphics.pose().pushPose();
        graphics.pose().translate(x + height, y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(90.0F));
        blitTntSprite(graphics, 0, 0, width, height, sourceX, sourceY, sourceWidth, sourceHeight);
        graphics.pose().popPose();
    }

    // Draw the fitted centered string
    private static void drawFittedCenteredString(GuiGraphics graphics, Font font, Component text,
                                                 int centerX, int y, int maxWidth, int height,
                                                 float maxScale, int col) {
        int textWidth = font.width(text);
        float scale = textWidth <= 0 ? maxScale : Math.max(0.55f, Math.min(maxScale, maxWidth / (float) textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y + (height - 8.0f * scale) * 0.5f, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawCenteredString(font, text, 0, 0, col);
        graphics.pose().popPose();
    }

    // Draw the panel
    public static void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;

        graphics.blit(SPRITE_MAP_TEX, x, y, width, BG_BANNER_SRC_H,
                BG_BANNER_SRC_X, BG_BANNER_SRC_Y, BG_BANNER_SRC_W, BG_BANNER_SRC_H,
                SPRITE_MAP_W, SPRITE_MAP_H);

        int bodyTop = y + BG_BANNER_SRC_H;
        int bodyH   = height - BG_BANNER_SRC_H - BG_FOOT_SRC_H;
        if (bodyH > 0) {
            graphics.blit(SPRITE_MAP_TEX, x, bodyTop, width, bodyH,
                    BG_BODY_SRC_X, BG_BODY_SRC_Y, BG_BODY_SRC_W, BG_BODY_SRC_H,
                    SPRITE_MAP_W, SPRITE_MAP_H);
        }

        graphics.blit(SPRITE_MAP_TEX, x, y + height - BG_FOOT_SRC_H, width, BG_FOOT_SRC_H,
                BG_FOOT_SRC_X, BG_FOOT_SRC_Y, BG_FOOT_SRC_W, BG_FOOT_SRC_H,
                SPRITE_MAP_W, SPRITE_MAP_H);
    }

    // Draw the separator
    public static void renderSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.blit(SPRITE_MAP_TEX, x, y, width, SEP_SRC_H,
                SEP_SRC_X, SEP_SRC_Y, SEP_SRC_W, SEP_SRC_H, SPRITE_MAP_W, SPRITE_MAP_H);
    }

    // Draw the slider background
    public static void renderSliderBackground(GuiGraphics graphics, int x, int y) {
        graphics.blit(SPRITE_MAP_TEX, x, y, SLIDER_BG_SRC_W, SLIDER_BG_SRC_H,
                SLIDER_BG_SRC_X, SLIDER_BG_SRC_Y, SLIDER_BG_SRC_W, SLIDER_BG_SRC_H,
                SPRITE_MAP_W, SPRITE_MAP_H);
    }

    // Draw the block slots
    public static void renderBlockSlots(GuiGraphics graphics, int x, int y, int numSlots) {

        graphics.blit(SPRITE_MAP_TEX, x, y, SLOT_L_SRC_W, SLOT_SRC_H,
                SLOT_L_SRC_X, SLOT_SRC_Y, SLOT_L_SRC_W, SLOT_SRC_H, SPRITE_MAP_W, SPRITE_MAP_H);

        int slotX = x + SLOT_L_SRC_W;
        for (int i = 0; i < numSlots; i++) {
            graphics.blit(SPRITE_MAP_TEX, slotX, y, SLOT_M_SRC_W, SLOT_SRC_H,
                    SLOT_M_SRC_X, SLOT_SRC_Y, SLOT_M_SRC_W, SLOT_SRC_H, SPRITE_MAP_W, SPRITE_MAP_H);
            slotX += SLOT_M_SRC_W;
        }

        graphics.blit(SPRITE_MAP_TEX, slotX, y, SLOT_R_SRC_W, SLOT_SRC_H,
                SLOT_R_SRC_X, SLOT_SRC_Y, SLOT_R_SRC_W, SLOT_SRC_H, SPRITE_MAP_W, SPRITE_MAP_H);
    }

    // Draw the item slot
    public static void renderItemSlot(GuiGraphics graphics, int x, int y) {
        renderInset(graphics, x, y, 18, 18, false, false);
    }

        // Draw the upgrade slot overlay
        public static void renderUpgradeSlotOverlay(GuiGraphics graphics, int x, int y, boolean hovered) {
                renderBlockSlotOverlay(graphics, x, y, SLOT_UPGRADE_OVERLAY_SRC_X, hovered);
        }

        // Draw the list slot overlay
        public static void renderListSlotOverlay(GuiGraphics graphics, int x, int y, boolean hovered) {
                renderBlockSlotOverlay(graphics, x, y, SLOT_LIST_OVERLAY_SRC_X, hovered);
        }

        // Draw the lens slot overlay
        public static void renderLensSlotOverlay(GuiGraphics graphics, int x, int y, boolean hovered) {
                renderBlockSlotOverlay(graphics, x, y, SLOT_LENS_OVERLAY_SRC_X, hovered);
        }

        // Draw the block slot overlay
        private static void renderBlockSlotOverlay(GuiGraphics graphics, int x, int y, int sourceX, boolean hovered) {
                graphics.blit(SPRITE_MAP_TEX, x, y, SLOT_OVERLAY_SRC_W, SLOT_OVERLAY_SRC_H,
                                sourceX, SLOT_OVERLAY_SRC_Y, SLOT_OVERLAY_SRC_W, SLOT_OVERLAY_SRC_H,
                                SPRITE_MAP_W, SPRITE_MAP_H);
                if (hovered) {
                        graphics.fill(x - 1, y - 1, x + SLOT_OVERLAY_SRC_W + 1, y + SLOT_OVERLAY_SRC_H + 1, 0x22FFFFFF);
                }
        }

    // Draw the inset
    public static void renderInset(GuiGraphics graphics, int x, int y, int width, int height,
                                   boolean hovered, boolean active) {
        int fill = active ? INSET_FILL_ACTIVE : hovered ? INSET_FILL_HOVER : INSET_FILL;
        graphics.fill(x, y, x + width, y + height, INSET_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    // Draw the text button
    public static void renderTextButton(GuiGraphics graphics, Font font, int x, int y,
                                        int width, int height, Component text,
                                        boolean hovered, boolean active, boolean green) {

                renderTextButton(graphics, font, x, y, width, height, text, hovered, active, green, 0xF3EEE4, 0x6A6A6A, true);
        }

        // Draw the text button
        public static void renderTextButton(GuiGraphics graphics, Font font, int x, int y,
                                                                                int width, int height, Component text,
                                                                                boolean hovered, boolean active, boolean green,
                                                                                int activeTextColor, int inactiveTextColor) {
                renderTextButton(graphics, font, x, y, width, height, text, hovered, active, green,
                                activeTextColor, inactiveTextColor, true);
        }

        // Draw the text button
        public static void renderTextButton(GuiGraphics graphics, Font font, int x, int y,
                                                                                int width, int height, Component text,
                                                                                boolean hovered, boolean active, boolean green,
                                                                                int activeTextColor, int inactiveTextColor,
                                                                                boolean darkenWhenInactive) {

                if (!active && darkenWhenInactive) {
            graphics.fill(x, y, x + width, y + height, 0xAA160A05);
        }

        graphics.blit(SPRITE_MAP_TEX, x, y, BTN_L_SRC_W, height,
                BTN_L_SRC_X, BTN_SRC_Y, BTN_L_SRC_W, BTN_SRC_H, SPRITE_MAP_W, SPRITE_MAP_H);

        int midLeft  = x + BTN_L_SRC_W;
        int midRight = x + width - BTN_R_SRC_W;
        if (midRight > midLeft) {
            graphics.blit(SPRITE_MAP_TEX, midLeft, y, midRight - midLeft, height,
                    BTN_M_SRC_X, BTN_SRC_Y, 1, BTN_SRC_H, SPRITE_MAP_W, SPRITE_MAP_H);
        }

        graphics.blit(SPRITE_MAP_TEX, x + width - BTN_R_SRC_W, y, BTN_R_SRC_W, height,
                BTN_R_SRC_X, BTN_SRC_Y, BTN_R_SRC_W, BTN_SRC_H, SPRITE_MAP_W, SPRITE_MAP_H);

        if (hovered) graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x22FFFFFF);
        int col = active ? activeTextColor : inactiveTextColor;
        graphics.drawCenteredString(font, text, x + width / 2, y + (height - 8) / 2, col);
    }

        // Draw the icon button
        public static void renderIconButton(GuiGraphics graphics, int x, int y, AllIcons icon,
                                                                                boolean hovered, boolean active, boolean green) {
                AllGuiTextures texture = !active ? AllGuiTextures.BUTTON_DISABLED
                                : hovered ? AllGuiTextures.BUTTON_HOVER
                                : green ? AllGuiTextures.BUTTON_GREEN
                                : AllGuiTextures.BUTTON;
                texture.render(graphics, x, y);
                icon.render(graphics, x + 1, y + 1);
        }

    // Draw the ghost slot
    public static void renderGhostSlot(GuiGraphics graphics, int x, int y, boolean hovered) {
        renderInset(graphics, x - 1, y - 1, 18, 18, hovered, false);
        graphics.fill(x, y, x + 16, y + 16, 0x66110D09);
    }

        // Draw the frequency ghost slot
        public static void renderFrequencyGhostSlot(GuiGraphics graphics, int x, int y,
                                                                                                boolean hovered, boolean secondFrequency) {
                renderGhostSlot(graphics, x, y, hovered);
                int border = secondFrequency ? FREQ_B_BORDER : FREQ_A_BORDER;
                int fill = secondFrequency ? FREQ_B_FILL : FREQ_A_FILL;
                graphics.fill(x - 1, y - 1, x + 17, y + 17, border);
                graphics.fill(x, y, x + 16, y + 16, fill);
        }
}
