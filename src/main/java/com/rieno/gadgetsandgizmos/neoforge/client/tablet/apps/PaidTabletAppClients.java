package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppIcons;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Keep paid app rendering and icon registration on the client
public final class PaidTabletAppClients{
    private PaidTabletAppClients(){}

    public static void register(){
        TabletAppClientRegistry.register(PaidTabletApps.DIGISABLE.id(), new Digisable());
        TabletAppClientRegistry.register(PaidTabletApps.MANIFEST.id(), new Manifest());
        TabletAppClientRegistry.register(PaidTabletApps.BLOCKMATES.id(), new Blockmates());
        icon(PaidTabletApps.DIGISABLE, new ItemStack(Items.ENDER_CHEST));
        icon(PaidTabletApps.MANIFEST, new ItemStack(Items.CHEST));
        icon(PaidTabletApps.BLOCKMATES, new ItemStack(Items.ARMOR_STAND));
    }

    private static void icon(TabletAppDefinition app, ItemStack item){
        TabletAppIcons.register(app.id(), (graphics, x, y, size) -> {
            graphics.fill(x, y, x + size, y + size, app.accentColor());
            graphics.pose().pushPose();
            graphics.pose().translate(x + size * 0.125F, y + size * 0.125F, 0);
            graphics.pose().scale(size * 0.75F / 16, size * 0.75F / 16, 1);
            graphics.renderItem(item, 0, 0);
            graphics.pose().popPose();
        });
    }
}
