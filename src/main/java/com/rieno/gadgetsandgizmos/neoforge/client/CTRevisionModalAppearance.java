package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedListModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedListModal.Region;
import net.minecraft.resources.ResourceLocation;

// Versions and Templates share the same painted panel and entry background.
final class CTRevisionModalAppearance{
    private CTRevisionModalAppearance(){}

    static final PaintedListModal.Appearance DEFAULT = new PaintedListModal.Appearance(
            sprite("revisions", 190, 170), sprite("revisions_entry", 143, 17),
            new PaintedListModal.Layout(new Region(4, 1, 168, 11), new Region(23, 25, 143, 126),
                    new Region(175, 2, 10, 10), new Region(4, 162, 182, 7), new Region(172, 25, 3, 126), 4),
            new PaintedListModal.Colors(0xA8000000, 0xFFFFFFFF, 0xFFAAAAAA, 0xFF303030, 0xFF404040, 0xFFE5BA64));

    private static GuiTextureRegion sprite(String name, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/modals/" + name + ".png"),
                width, height, 0, 0, width, height);
    }
}
