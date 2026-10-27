package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Supply the analogue controller atlas and the shared modal artwork
final class CTAnalogueControllerAppearance{
    static final int LABEL_COLOR = 0xFFF3EEE4;
    static final int VALUE_COLOR = 0xFFE5BA64;
    static final int SUBTLE_TEXT_COLOR = 0xFFB8B8B8;
    static final int BANNER_TITLE_COLOR = 0xFFFFFFFF;
    static final int ATLAS_X = 25;
    static final int INVENTORY_X = ATLAS_X + 28;
    static final int INVENTORY_Y = 223;
    static final GuiTextureRegion FRAME = controller(0, 0, 246, 320);
    static final GuiTextureRegion INVENTORY = controller(28, 223, 174, 97);
    static final GuiTextureRegion KEY = controller(73, 322, 13, 14);
    static final GuiTextureRegion BOUND_KEY = controller(60, 322, 13, 14);
    static final GuiTextureRegion SECOND_FREQUENCY = controller(18, 322, 18, 18);
    static final GuiTextureRegion FIRST_FREQUENCY = controller(0, 322, 18, 18);
    static final PaintedGuiStyle STYLE = new PaintedGuiStyle(
            PaintedGuiStyle.slice(new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters",
                    "textures/gui/modals/generic_modal.png"), 280, 240, 0, 0, 280, 240), 8, 44, 8, 25),
            PaintedGuiStyle.slice(template(195, 115, 187, 42), 2, 2, 2, 2),
            PaintedGuiStyle.slice(template(104, 176, 18, 16), 3, 3, 3, 3),
            PaintedGuiStyle.slice(template(104, 160, 18, 16), 3, 3, 3, 3),
            template(50, 183, 44, 2), LABEL_COLOR, SUBTLE_TEXT_COLOR);

    private CTAnalogueControllerAppearance(){}

    // Keep the inventory aligned beneath each configuration view
    static void renderInventory(GuiGraphics graphics, int x, int y){
        INVENTORY.draw(graphics, x + INVENTORY_X, y + INVENTORY_Y);
    }

    // Draw key caps from the controller's own atlas
    static void renderKey(GuiGraphics graphics, int x, int y, int width, boolean bound, boolean hovered){
        (bound ? BOUND_KEY : KEY).drawHorizontallyTiled(graphics, x, y, width, 14, 5, 5);
        if(hovered) graphics.fill(x + 2, y + 2, x + width - 2, y + 11, 0x33FFFFFF);
    }

    private static GuiTextureRegion controller(int x, int y, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters",
                "textures/gui/analogue_controller/analogue_controller.png"), 246, 340, x, y, width, height);
    }

    private static GuiTextureRegion template(int x, int y, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters",
                "textures/gui/template.png"), 508, 268, x, y, width, height);
    }
}
