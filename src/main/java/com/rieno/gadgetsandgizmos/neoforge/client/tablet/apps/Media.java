package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.lib.client.tablet.PaintedMediaPlayerRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppIcons;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletMediaApp;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Supply the addon's painted Media atlas to the shared player
public final class Media extends PaintedMediaPlayerRenderer{
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/apps/screens/media.png");

    public Media(){ super(ATLAS, ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/template.png")); }

    public static void registerIcon(){
        TabletAppIcons.register(DiagnosticTabletMediaApp.ID, (graphics, x, y, size) -> {
            new GuiTextureRegion(ATLAS, 320, 400, 0, 334, 72, 66).draw(graphics, x, y, size, size);
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(x + size / 8F, y + size / 8F, 0);
                graphics.pose().scale(size * .75F / 16, size * .75F / 16, 1);
                graphics.renderItem(new ItemStack(Items.MUSIC_DISC_CREATOR), 0, 0);
            }finally{ graphics.pose().popPose(); }
        });
    }
}
