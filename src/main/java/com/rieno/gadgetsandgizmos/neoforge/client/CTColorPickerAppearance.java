package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal.Region;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// The artwork and pixel layout are shared by all addon color pickers
final class CTColorPickerAppearance{
    private CTColorPickerAppearance(){}

    static final ColorPickerModal.TextureAppearance DEFAULT = new ColorPickerModal.TextureAppearance(
            sprite("color_picker", 190, 177), sprite("color_picker_cancel", 47, 12), sprite("color_picker_accept", 14, 12),
            new ColorPickerModal.TextureLayout(new Region(2, 1, 186, 11), new Region(14, 30, 100, 100),
                    new Region(143, 30, 14, 100), new Region(14, 134, 20, 13), new Region(36, 134, 76, 13),
                    new Region(143, 133, 14, 15), new Region(108, 163, 47, 12), new Region(169, 163, 14, 12),
                    new Region(14, 149, 75, 11), new Region(92, 149, 65, 11)),
            Component.translatable("createthrusters.color_picker.title"), Component.translatable("createthrusters.color_picker.hex"),
            Component.translatable("createthrusters.color_picker.cancel"), 0xA8000000, 0xFFFFFFFF, 0xFFB8B8B8,
            Component.translatable("createthrusters.color_picker.alpha"));

    private static GuiTextureRegion sprite(String name, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/modals/" + name + ".png"),
                width, height, 0, 0, width, height);
    }
}
