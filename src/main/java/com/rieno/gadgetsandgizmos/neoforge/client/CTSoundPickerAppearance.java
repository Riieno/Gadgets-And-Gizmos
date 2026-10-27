package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedListModal.Region;
import com.rieno.gadgetsandgizmos.lib.client.ui.SoundPickerModal;
import net.minecraft.resources.ResourceLocation;

// Place the library sound picker over the addon's painted modal and entry textures
final class CTSoundPickerAppearance{
    private CTSoundPickerAppearance(){}

    static final SoundPickerModal.Appearance DEFAULT = new SoundPickerModal.Appearance(
            sprite("generic_select_with_search_modal", 280, 240), sprite("select_button_square", 72, 66),
            new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/template.png"),
                    508, 268, 228, 163, 9, 9),
            new SoundPickerModal.Layout(new Region(5, 1, 257, 12), new Region(133, 24, 139, 14),
                    new Region(8, 47, 261, 172), new Region(271, 47, 3, 172), new Region(5, 228, 251, 10),
                    new Region(264, 228, 9, 9), new Region(264, 1, 10, 12),
                    new SoundPickerModal.EntryLayout(new Region(6, 12, 8, 8), new Region(32, 6, 16, 16),
                            new Region(9, 44, 54, 14))));

    private static GuiTextureRegion sprite(String name, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/modals/" + name + ".png"),
                width, height, 0, 0, width, height);
    }
}
