package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.tablet.PaintedControlCardsRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal;
import net.minecraft.resources.ResourceLocation;

// Supply the app atlas and the existing ACC frequency and color picker artwork
public final class RedstoneLink extends PaintedControlCardsRenderer{
    public RedstoneLink(ColorPickerModal.TextureAppearance colors){
        super(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/apps/screens/redstone.png"),
                new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/claw_gui.png"), 256, 256, 40, 91, 176, 155), colors);
    }
}
