package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractiveStyles;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudControlRenderer;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

// Draw the Advanced HUD Interactive Style
public final class AdvancedHudInteractiveStyleRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation TEXT_INPUT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/widgets/text_input_widget.png");
    private static final GuiNineSlice TEXT_INPUT_WIDGET = new GuiNineSlice(
            textInputRegion(0, 0, 18, 5), textInputRegion(20, 0, 17, 5), textInputRegion(39, 0, 20, 5),
            textInputRegion(0, 6, 18, 8), textInputRegion(20, 6, 17, 8), textInputRegion(39, 6, 20, 8),
            textInputRegion(0, 15, 18, 4), textInputRegion(20, 15, 17, 4), textInputRegion(39, 15, 20, 4));

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
        if("text_input".equals(type)){
            drawTextInput(graphics, font, elm, x, y, width, height, layoutScale);
            return;
        }
        HudControlRenderer.Rect labelBounds = paint(HudControlRenderer.gui(graphics), elm,
                x, y, width, height, layoutScale, numberValue, toggleActive, hovered);
        String label = elm.getString("Text");
        if(label.isBlank()) label = "toggle".equals(type) ? "Toggle" : "Button";
        drawLabel(graphics, font, elm, labelBounds, label, layoutScale, !"toggle".equals(type));
    }

    // Draw the text field frame and keep its label clear of the textured caret
    private static void drawTextInput(GuiGraphics graphics, Font font, CompoundTag elm,
                                      int x, int y, int width, int height, double layoutScale){
        TEXT_INPUT_WIDGET.draw(graphics, x, y, width, height, layoutScale);
        int top = Math.max(1, (int)Math.round(5.0D * layoutScale));
        int bottom = Math.max(1, (int)Math.round(4.0D * layoutScale));
        int textHeight = Math.max(1, (int)Math.round(AdvancedHudElementStyle.fontSize(elm) * layoutScale));
        int innerHeight = height - top - bottom;
        if(textHeight > innerHeight) return;
        int left = Math.max(1, (int)Math.round(4.0D * layoutScale));
        int right = Math.max(1, (int)Math.round(12.0D * layoutScale));
        String label = elm.getString("Text");
        if(label.isBlank()) label = "Enter text";
        AdvancedHudElementRenderer.drawText(graphics, font, elm, label,
                x + left, y + top + (innerHeight - textHeight) / 2,
                width - left - right, innerHeight, layoutScale);
    }

    // Resolve a partial while excluding the transparent atlas gutters
    private static GuiTextureRegion textInputRegion(int u, int v, int width, int height){
        return new GuiTextureRegion(TEXT_INPUT_TEXTURE, 59, 19, u, v, width, height);
    }

    // Adapt element data to the library's GUI and world control renderer.
    public static HudControlRenderer.Palette palette(CompoundTag elm, double scale){
        return new HudControlRenderer.Palette(
                AdvancedHudElementStyle.color(elm, "BackgroundColor", AdvancedHudElementStyle.DEFAULT_WIDGET_BACKGROUND_COLOR),
                AdvancedHudElementStyle.color(elm, "AccentColor", AdvancedHudElementStyle.DEFAULT_WIDGET_ACCENT_COLOR),
                AdvancedHudElementStyle.color(elm, "GradientColor", AdvancedHudElementStyle.DEFAULT_WIDGET_GRADIENT_COLOR),
                AdvancedHudElementStyle.color(elm, "TrackColor", AdvancedHudElementStyle.DEFAULT_WIDGET_TRACK_COLOR),
                AdvancedHudElementStyle.color(elm, "BorderColor", 0xFF527185),
                AdvancedHudElementStyle.borderWidth(elm) * scale,
                AdvancedHudElementStyle.borderRadius(elm) * scale, scale);
    }

    public static HudControlRenderer.Rect paint(HudControlRenderer.Canvas canvas, CompoundTag elm,
                                                double x, double y, double width, double height, double scale,
                                                double value, boolean active, boolean hovered){
        HudControlRenderer.Rect bounds = new HudControlRenderer.Rect(x, y, width, height);
        HudControlRenderer.Palette palette = palette(elm, scale);
        String style = AdvancedHudInteractiveStyles.style(elm);
        return switch(elm.getString("Type")){
            case "toggle" -> HudControlRenderer.toggle(canvas, bounds, palette, HudStyleCatalog.controlStyle(style),
                    active, hovered, HudStyleCatalog.checkbox(elm.getString("ToggleShape")));
            case "slider" -> {
                HudControlRenderer.slider(canvas, bounds, palette, HudStyleCatalog.controlStyle(style),
                        HudStyleCatalog.amount(value, elm.getDouble("Min"), elm.getDouble("Max")), hovered);
                yield new HudControlRenderer.Rect(x, y, 0, 0);
            }
            case "progress" -> HudControlRenderer.progress(canvas, bounds, palette, HudStyleCatalog.progressStyle(style),
                    HudStyleCatalog.amount(value, elm.getDouble("Min"), elm.getDouble("Max")));
            default -> HudControlRenderer.button(canvas, bounds, palette, HudStyleCatalog.controlStyle(style), active, hovered);
        };
    }

    public static void drawProgress(GuiGraphics graphics, Font font, CompoundTag elm,
                                     int x, int y, int width, int height, double scale, double value){
        HudControlRenderer.Rect label = paint(HudControlRenderer.gui(graphics), elm,
                x, y, width, height, scale, value, false, false);
        String text = elm.getString("Text");
        if(text.isBlank()) text = Math.round(HudStyleCatalog.amount(value, elm.getDouble("Min"), elm.getDouble("Max")) * 100) + "%";
        drawLabel(graphics, font, elm, label, text, scale, true);
    }

    private static void drawLabel(GuiGraphics graphics, Font font, CompoundTag elm,
                                  HudControlRenderer.Rect bounds, String text, double scale, boolean centered){
        if(bounds.width() <= 0 || bounds.height() <= 0) return;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(0, 0, 1);
            if(centered) AdvancedHudElementRenderer.drawCenteredText(graphics, font, elm, text,
                    (int)Math.round(bounds.x()), (int)Math.round(bounds.y()),
                    (int)Math.floor(bounds.width()), (int)Math.floor(bounds.height()), scale);
            else AdvancedHudElementRenderer.drawText(graphics, font, elm, text,
                    (int)Math.round(bounds.x()), (int)Math.round(bounds.y() + Math.max(0,
                            (bounds.height() - AdvancedHudElementStyle.fontSize(elm) * scale) / 2)),
                    (int)Math.floor(bounds.width()), (int)Math.floor(bounds.height()), scale);
        }finally{
            graphics.pose().popPose();
        }
    }


}
