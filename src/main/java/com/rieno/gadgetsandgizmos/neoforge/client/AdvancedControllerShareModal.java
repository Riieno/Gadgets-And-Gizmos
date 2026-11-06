package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.graph.SourcedGraphCatalog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Predicate;

// The ACC owns the painted layout; texture drawing and inline editing use the library APIs.
final class AdvancedControllerShareModal{
    static final int WIDTH = 340;
    static final int HEIGHT = 220;
    static final int ROW_HEIGHT = 38;
    static final int CARD_HEIGHT = 34;
    private static final GuiTextureRegion BACKGROUND = texture("share_modal", WIDTH, HEIGHT);
    private static final GuiTextureRegion SHARE_BUTTON = new GuiTextureRegion(BACKGROUND.texture(), WIDTH, HEIGHT, 9, 75, 100, 18);
    private static final GuiTextureRegion CARD = texture("share_modal_graph", 150, CARD_HEIGHT);
    private static final GuiTextureRegion RENAME = texture("share_modal_rename", 9, 9);
    private static final GuiTextureRegion RENAME_HOVER = texture("share_modal_rename_hover", 9, 9);
    private static final GuiTextureRegion ACCEPT = texture("share_modal_accept", 9, 9);
    private static final GuiTextureRegion TRACK = texture("share_modal_scroll_track", 2, 44);
    private static final GuiTextureRegion THUMB = texture("share_modal_scroll_thumb", 3, 9);

    private AdvancedControllerShareModal(){}

    enum Control{
        SAVE("save", 9, 52, 43, 18), UPLOAD("upload", 66, 52, 43, 18),
        DIRECTORY("directory", 9, 75, 100, 18), SHARE("share", 9, 98, 100, 18),
        REFRESH("refresh", 136, 161, 43, 18),
        LOAD("load", 199, 161, 43, 18), DELETE("delete", 266, 161, 43, 18),
        CLOSE("close", 307, 196, 18, 18);

        final String key;
        final UiRect rect;

        Control(String key, int x, int y, int width, int height){
            this.key = key;
            rect = new UiRect(x, y, width, height);
        }

        UiRect bounds(UiRect modal){ return at(modal, rect.x(), rect.y(), rect.width(), rect.height()); }
    }

    static UiRect name(UiRect modal){ return at(modal, 13, 35, 88, 10); }
    static UiRect url(UiRect modal){ return at(modal, 12, 166, 94, 11); }
    static UiRect list(UiRect modal){ return at(modal, 147, 49, 150, 102); }
    static UiRect track(UiRect modal){ return at(modal, 306, 47, 3, 104); }
    static UiRect row(UiRect modal, int offset){ return at(modal, 147, 49 + offset, 150, CARD_HEIGHT); }
    static UiRect rename(UiRect row){ return at(row, 136, 21, 9, 9); }
    static UiRect rowName(UiRect row){ return at(row, 42, 18, 91, 11); }

    static void draw(GuiGraphics graphics, Font font, UiRect modal, int mouseX, int mouseY,
                     Predicate<Control> enabled, boolean uploading, String url){
        BACKGROUND.draw(graphics, modal.x(), modal.y());
        UiRect share = Control.SHARE.bounds(modal);
        SHARE_BUTTON.draw(graphics, share.x(), share.y());
        text(graphics, font, label("title"), at(modal, 4, 2, 324, 11), 1.2F, 0xFFFFFFFF);
        for(Control control : Control.values()){
            UiRect button = control.bounds(modal);
            if(!enabled.test(control)) graphics.fill(button.x(), button.y(), button.right(), button.bottom(), 0x77000000);
            else if(button.contains(mouseX, mouseY)) graphics.fill(button.x() + 1, button.y() + 1, button.right() - 1, button.bottom() - 1, 0x22FFFFFF);
            if(control == Control.CLOSE) continue;
            String label = label(control == Control.SHARE && uploading ? "uploading" : control.key);
            text(graphics, font, label, at(button, 2, 2, button.width() - 4, button.height() - 4), 1.1F, 0xFFFFFFFF);
        }
        text(graphics, font, label(url.isBlank() ? "link" : "link_copied"), at(modal, 11, 143, 96, 12), 0.85F, 0xFFFFFFFF);
        String value = url.isBlank() ? "" : font.plainSubstrByWidth(url, 86);
        graphics.drawString(font, value, modal.x() + 13, modal.y() + 168, 0xFFFFFFFF, true);
    }

    static void drawRow(GuiGraphics graphics, Font font, UiRect row, SourcedGraphCatalog.Entry entry,
                        boolean selected, boolean editing, int mouseX, int mouseY){
        CARD.draw(graphics, row.x(), row.y());
        if(selected) graphics.renderOutline(row.x(), row.y(), row.width(), row.height(), 0xFFE5BA63);
        boolean client = entry.source() == SourcedGraphCatalog.Source.CLIENT;
        text(graphics, font, client ? "C" : "S", at(row, 5, 5, 23, 24), 1.8F, 0xFFFFFFFF);
        graphics.drawString(font, label(client ? "client" : "server"), row.x() + 42, row.y() + 5, 0xFFFFFFFF, true);
        if(!editing) graphics.drawString(font, font.plainSubstrByWidth(entry.name(), 91), row.x() + 42, row.y() + 18, 0xFFFFFFFF, true);
        UiRect rename = rename(row);
        (editing ? ACCEPT : rename.contains(mouseX, mouseY) ? RENAME_HOVER : RENAME).draw(graphics, rename.x(), rename.y());
    }

    static void drawScrollbar(GuiGraphics graphics, UiRect modal, double fraction, boolean enabled){
        UiRect track = track(modal);
        TRACK.draw(graphics, track.x(), track.y(), 2, track.height());
        if(enabled) THUMB.draw(graphics, track.x(), track.y() + (int)Math.round(fraction * (track.height() - 9)));
    }

    static String label(String key){ return Component.translatable("createthrusters.advanced_controller.share." + key).getString(); }

    static void text(GuiGraphics graphics, Font font, String value, UiRect bounds, float preferredScale, int color){
        float scale = Math.min(preferredScale, bounds.width() / (float)Math.max(1, font.width(value)));
        scale = Math.min(scale, bounds.height() / (float)font.lineHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x() + bounds.width() / 2.0F, bounds.y() + bounds.height() / 2.0F, 1);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, value, -font.width(value) / 2, -font.lineHeight / 2, color, true);
        graphics.pose().popPose();
    }

    private static UiRect at(UiRect bounds, int x, int y, int width, int height){
        return new UiRect(bounds.x() + x, bounds.y() + y, width, height);
    }

    private static GuiTextureRegion texture(String name, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/modals/" + name + ".png"),
                width, height, 0, 0, width, height);
    }
}
