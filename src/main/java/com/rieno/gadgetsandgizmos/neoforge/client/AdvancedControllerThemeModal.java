package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Artwork and coordinates for the client-local ACC theme editor.
final class AdvancedControllerThemeModal{
    static final int WIDTH = 340;
    static final int HEIGHT = 220;
    static final int ROW_HEIGHT = 18;
    static final int ROW_WIDTH = 100;
    static final int ROW_PAINT_HEIGHT = 17;
    private static final GuiTextureRegion BACKGROUND = sprite("theme_editor", WIDTH, HEIGHT);
    private static final GuiTextureRegion COLOR = sprite("theme_editor_color", ROW_WIDTH, ROW_PAINT_HEIGHT);
    private static final GuiTextureRegion PRESET = sprite("theme_editor_preset", ROW_WIDTH, ROW_PAINT_HEIGHT);
    private static final GuiTextureRegion RENAME = sprite("theme_editor_rename", 9, 9);
    private static final GuiTextureRegion RENAME_HOVER = sprite("theme_editor_rename_hover", 9, 9);
    private static final GuiTextureRegion MARKER = sprite("theme_editor_marker", 3, 9);

    private AdvancedControllerThemeModal(){}

    static UiRect bounds(){ return new UiRect(0, 0, WIDTH, HEIGHT); }
    static UiRect presets(){ return new UiRect(9, 31, 100, 126); }
    static UiRect colors(){ return new UiRect(120, 59, 202, 114); }
    static UiRect name(){ return new UiRect(151, 34, 132, 11); }
    static UiRect rename(){ return new UiRect(293, 34, 9, 9); }
    static UiRect create(){ return new UiRect(13, 165, 88, 16); }
    static UiRect confirm(){ return new UiRect(307, 196, 18, 18); }
    static UiRect preset(int offset){ return new UiRect(9, 31 + offset, ROW_WIDTH, ROW_PAINT_HEIGHT); }
    static UiRect color(int index, int offset){ return new UiRect(120 + index % 2 * 102, 59 + offset, ROW_WIDTH, ROW_PAINT_HEIGHT); }
    static UiRect swatch(UiRect row){ return new UiRect(row.right() - 15, row.y() + 6, 9, 5); }
    static UiRect presetTrack(){ return new UiRect(109, 31, 3, 126); }
    static UiRect colorTrack(){ return new UiRect(322, 59, 3, 114); }

    static void draw(GuiGraphics graphics, Font font, int mouseX, int mouseY, boolean editable, boolean editing){
        BACKGROUND.draw(graphics, 0, 0);
        text(graphics, font, label("title"), new UiRect(4, 2, 324, 11), 1.2F, 0xFFFFFFFF);
        if(create().contains(mouseX, mouseY)) highlight(graphics, create());
        text(graphics, font, label("new"), create(), 1, 0xFFFFFFFF);
        if(confirm().contains(mouseX, mouseY)) highlight(graphics, confirm());
        if(editable){
            (editing || rename().contains(mouseX, mouseY) ? RENAME_HOVER : RENAME).draw(graphics, rename().x(), rename().y());
        }else{
            graphics.fill(rename().x(), rename().y(), rename().right(), rename().bottom(), 0x66000000);
        }
    }

    static void drawPreset(GuiGraphics graphics, Font font, UiRect row, String name, boolean selected, boolean hovered){
        PRESET.draw(graphics, row.x(), row.y());
        if(hovered) highlight(graphics, row);
        text(graphics, font, name, new UiRect(row.x() + 4, row.y() + 3, 83, 11), 1, 0xFFFFFFFF, false);
        if(selected) MARKER.draw(graphics, row.x() + 91, row.y() + 4);
    }

    static void drawColor(GuiGraphics graphics, Font font, UiRect row, String name, int color, boolean editable, boolean hovered){
        COLOR.draw(graphics, row.x(), row.y());
        if(editable && hovered) highlight(graphics, row);
        text(graphics, font, name, new UiRect(row.x() + 3, row.y() + 3, 76, 11), 0.8F, editable ? 0xFFFFFFFF : 0xFFC7C4B7);
        UiRect swatch = swatch(row);
        graphics.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), color);
    }

    static void drawScroll(GuiGraphics graphics, UiRect track, double fraction, boolean scrollable){
        if(!scrollable) return;
        graphics.fill(track.x() + 1, track.y(), track.x() + 2, track.bottom(), 0xFF404040);
        MARKER.draw(graphics, track.x(), track.y() + (int)Math.round(fraction * (track.height() - 9)));
    }

    static String label(String key){ return Component.translatable("createthrusters.advanced_controller.theme." + key).getString(); }

    static void text(GuiGraphics graphics, Font font, String value, UiRect bounds, float preferredScale, int color){
        text(graphics, font, value, bounds, preferredScale, color, true);
    }

    private static void text(GuiGraphics graphics, Font font, String value, UiRect bounds, float preferredScale, int color, boolean centered){
        float scale = Math.min(preferredScale, bounds.width() / (float)Math.max(1, font.width(value)));
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x() + (centered ? (bounds.width() - font.width(value) * scale) / 2 : 0),
                bounds.y() + (bounds.height() - font.lineHeight * scale) / 2, 1);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, value, 0, 0, color, true);
        graphics.pose().popPose();
    }

    private static void highlight(GuiGraphics graphics, UiRect bounds){
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, 0x18FFFFFF);
    }

    private static GuiTextureRegion sprite(String name, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/modals/" + name + ".png"),
                width, height, 0, 0, width, height);
    }
}
