package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.lib.client.tablet.PaintedBlockInspectorRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import net.minecraft.resources.ResourceLocation;

// Supply Settings artwork while retaining NFC's reader and direct-control actions
public final class Nfc extends PaintedBlockInspectorRenderer{
    public Nfc(){ super(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/apps/screens/settings.png")); }

    @Override protected void inspect(TabletAppClientContext ctx){
        if(ctx.ui().placed()) ctx.refresh().run();
        else ctx.actions().send("begin_reader", "nfc_scan");
    }

    @Override protected void signal(TabletAppClientContext ctx, int val){ ctx.actions().send("nfc_signal", Integer.toString(val)); }
}
