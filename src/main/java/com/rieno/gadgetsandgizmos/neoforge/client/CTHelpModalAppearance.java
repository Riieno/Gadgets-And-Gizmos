package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.DocumentationModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.HelpModalAppearance;
import com.rieno.gadgetsandgizmos.lib.client.ui.HelpModalStyle;
import com.rieno.gadgetsandgizmos.lib.client.ui.ShortcutsModal;
import net.minecraft.resources.ResourceLocation;

// Keep complete help textures and their original layout coordinates together
final class CTHelpModalAppearance{
    static final HelpModalStyle COLORS = new HelpModalStyle(0xFF202020, 0xFF303030, 0xFF404040,
            0xFFFFFFFF, 0xFFB8B8B8, 0xFFE5BA64);

    static final DocumentationModal.TextureAppearance DOCUMENTATION = new DocumentationModal.TextureAppearance(
            new HelpModalAppearance(sprite("docs_modal", 446, 220), 14, 14, 0xFFFFFFFF, 0xFF303030, 2),
            sprite("docs_modal_sidebar_button", 150, 25), sprite("docs_modal_sidebar_button_selected", 150, 25),
            sprite("color_picker_cancel", 47, 12),
            new DocumentationModal.Layout(region(8, 29, 101, 171), region(120, 29, 318, 171),
                    region(8, 18, 100, 8), region(120, 18, 318, 8), region(342, 207, 47, 12),
                    region(392, 207, 47, 12), region(6, 208, 280, 10)));

    static final ShortcutsModal.TextureAppearance SHORTCUTS = new ShortcutsModal.TextureAppearance(
            new HelpModalAppearance(sprite("shortcut_modal", 190, 170), 14, 9, 0xFFFFFFFF, 0xFF303030, 2),
            sprite("shortcut_modal_item", 150, 34), 98, region(8, 26, 174, 127));

    private CTHelpModalAppearance(){}

    private static HelpModalAppearance.Region region(int x, int y, int width, int height){
        return new HelpModalAppearance.Region(x, y, width, height);
    }

    private static GuiTextureRegion sprite(String name, int width, int height){
        return new GuiTextureRegion(texture(name), width, height, 0, 0, width, height);
    }

    private static ResourceLocation texture(String name){
        return ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/modals/" + name + ".png");
    }
}
