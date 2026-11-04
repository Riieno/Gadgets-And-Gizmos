package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiCanvasTransform;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.LayeredItemRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedListModal.Region;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSelectionGrid;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// Supply target-picker artwork and control positions while selection stays in the ACC
final class AdvancedControllerTargetModal{
    static final int WIDTH = 280;
    static final int HEIGHT = 240;
    static final GuiTextureRegion PANEL = sprite("target_modal", WIDTH, HEIGHT);
    static final GuiTextureRegion BLOCK_PANEL = sprite("target_modal_3d", WIDTH, HEIGHT);
    private static final GuiTextureRegion TAB = atlas(104, 176, 18, 16);
    private static final GuiTextureRegion SELECTED_TAB = atlas(104, 160, 18, 16);
    private static final GuiTextureRegion TICK = atlas(228, 163, 9, 9);
    private static final GuiTextureRegion FILTER_BUTTON = atlas(0, 210, 48, 12);
    static final PaintedSelectionGrid.Appearance GRID = new PaintedSelectionGrid.Appearance(
            new PaintedSelectionGrid.CardAppearance(sprite("select_button_square", 72, 66), TICK,
                    new Region(6, 12, 8, 8), new Region(32, 6, 16, 16), new Region(9, 44, 54, 14)),
            new Region(8, 47, 261, 172), new Region(133, 24, 139, 14), new Region(271, 47, 3, 172));

    private AdvancedControllerTargetModal(){}

    static UiRect bounds(int width, int height){
        var transform = GuiCanvasTransform.fit(WIDTH, HEIGHT, width, height, 8, 2);
        return new UiRect(transform.x(), transform.y(), Math.max(1, Math.round(WIDTH * transform.scale())),
                Math.max(1, Math.round(HEIGHT * transform.scale())));
    }

    static GuiCanvasTransform transform(UiRect bounds){ return new GuiCanvasTransform(bounds.x(), bounds.y(), scale(bounds)); }
    static UiRect linkerTab(UiRect bounds){ return at(bounds, 7, 24, 18, 16); }
    static UiRect blockTab(UiRect bounds){ return at(bounds, 31, 24, 18, 16); }
    static UiRect list(UiRect bounds){ return at(bounds, 8, 47, 261, 172); }
    static UiRect track(UiRect bounds){ return at(bounds, 271, 47, 3, 172); }
    static UiRect search(UiRect bounds){ return at(bounds, 133, 24, 139, 14); }
    static UiRect content(UiRect bounds){ return at(bounds, 12, 67, 165, 142); }
    static UiRect selections(UiRect bounds){ return at(bounds, 186, 67, 82, 127); }
    static UiRect alias(UiRect bounds){ return at(bounds, 188, 199, 77, 9); }
    static UiRect toolbar(UiRect bounds){ return at(bounds, 11, 45, 258, 12); }
    static UiRect filter(UiRect bounds){ return at(bounds, 12, 46, 44, 10); }
    static UiRect wireframe(UiRect bounds){ return at(bounds, 61, 46, 42, 10); }
    static UiRect solidFilters(UiRect bounds){ return at(bounds, 108, 46, 68, 10); }
    static UiRect accept(UiRect bounds){ return at(bounds, 265, 228, 9, 9); }
    static UiRect cancel(UiRect bounds){ return close(bounds); }
    static UiRect selectionLabel(UiRect bounds){ return at(bounds, 5, 228, 251, 9); }
    static UiRect close(UiRect bounds){ return at(bounds, 264, 1, 10, 12); }
    static UiRect merge(UiRect bounds, Font font){ return at(bounds, 54, 25, 75, 12); }
    static UiRect clear(UiRect bounds){ return at(bounds, 5, 228, 92, 9); }

    static UiRect row(UiRect list, int slot){
        Region card = PaintedSelectionGrid.entryBounds(GRID.grid(), GRID.card().background(), slot);
        float scale = list.width() / 261F;
        return new UiRect(list.x() + Math.round((card.x() - GRID.grid().x()) * scale),
                list.y() + Math.round((card.y() - GRID.grid().y()) * scale),
                Math.max(1, Math.round(card.width() * scale)), Math.max(1, Math.round(card.height() * scale)));
    }

    static void draw(GuiGraphics graphics, Font font, UiRect bounds, boolean source, boolean blockTab, int mouseX, int mouseY){
        (blockTab ? BLOCK_PANEL : PANEL).drawFitted(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        text(graphics, font, source ? "Select Source" : "Select Target", at(bounds, 5, 2, 250, 10), scale(bounds), 0xFFFFFFFF, false);
        text(graphics, font, "x", close(bounds), scale(bounds), 0xFFFFFFFF, true);
        TICK.drawFitted(graphics, accept(bounds).x(), accept(bounds).y(), accept(bounds).width(), accept(bounds).height());
    }

    static void drawTab(GuiGraphics graphics, UiRect bounds, UiRect tab, ItemStack icon, boolean selected, int mouseX, int mouseY){
        (selected ? SELECTED_TAB : TAB).drawFitted(graphics, tab.x(), tab.y(), tab.width(), tab.height());
        int size = Math.max(1, Math.round(12 * scale(bounds)));
        LayeredItemRenderer.renderVisible(graphics, icon, tab.x() + (tab.width() - size) / 2, tab.y() + (tab.height() - size) / 2, size);
        if(tab.contains(mouseX, mouseY)) graphics.fill(tab.x(), tab.y(), tab.right(), tab.bottom(), 0x22FFFFFF);
    }

    static void drawMerge(GuiGraphics graphics, Font font, UiRect bounds, boolean enabled){
        UiRect button = merge(bounds, font);
        text(graphics, font, (enabled ? "[x] " : "[ ] ") + "Merge Like Ports", button, scale(bounds) * 0.65F, 0xFFB8B8B8, false);
    }

    static void drawSolidFiltersBackground(GuiGraphics graphics, UiRect bounds, int mouseX, int mouseY){
        UiRect button = solidFilters(bounds);
        int inset = Math.max(1, Math.round(scale(bounds)));
        FILTER_BUTTON.drawHorizontallyTiled(graphics, button.x() - inset, button.y() - inset,
                button.width() + inset * 2, button.height() + inset * 2, 2, 2);
        if(button.contains(mouseX, mouseY)) graphics.fill(button.x(), button.y(), button.right(), button.bottom(), 0x22FFFFFF);
    }

    static void drawSelectionLabel(GuiGraphics graphics, Font font, UiRect bounds, String label){
        text(graphics, font, label, selectionLabel(bounds), scale(bounds) * 0.8F, 0xFF303030, false);
    }

    static void text(GuiGraphics graphics, Font font, String text, UiRect bounds, float scale, int color, boolean centered){
        GuiControlPreviews.drawFittedText(graphics, font, Component.literal(text), bounds.x(), bounds.y(), bounds.width(), bounds.height(), scale, color, centered);
    }

    private static UiRect at(UiRect bounds, int x, int y, int width, int height){
        float scale = scale(bounds);
        return new UiRect(bounds.x() + Math.round(x * scale), bounds.y() + Math.round(y * scale),
                Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
    }

    static float scale(UiRect bounds){ return Math.min(bounds.width() / (float)WIDTH, bounds.height() / (float)HEIGHT); }
    private static GuiTextureRegion atlas(int x, int y, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/template.png"), 508, 268, x, y, width, height);
    }
    private static GuiTextureRegion sprite(String name, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/modals/" + name + ".png"), width, height, 0, 0, width, height);
    }
}
