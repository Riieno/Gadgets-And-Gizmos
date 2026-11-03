package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.lib.client.tablet.PaintedDeviceCardsRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal;
import net.minecraft.resources.ResourceLocation;

// Map painted device cards to the existing pairing and remote-controller sessions
public final class RemoteDesktop extends PaintedDeviceCardsRenderer{
    public RemoteDesktop(){ this(null); }
    public RemoteDesktop(ColorPickerModal.TextureAppearance colors){ super(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/apps/screens/redstone.png"), colors); }

    @Override protected void addDevice(TabletAppClientContext ctx){ ctx.actions().send("begin_reader", ""); }
    @Override protected void renameDevice(TabletAppClientContext ctx, String key, String name){ ctx.actions().send("rename_controller", key + "|" + name); }
    @Override protected void removeDevice(TabletAppClientContext ctx, String key){ ctx.actions().send("remove_controller", key); }
    @Override protected void colorDevice(TabletAppClientContext ctx, String key, int color){
        ctx.actions().send("controller_color", key + "|" + String.format(java.util.Locale.ROOT, "%06X", color & 0xFFFFFF));
    }
    @Override protected void openDevice(TabletAppClientContext ctx, String key, View view){
        ctx.actions().send(switch(view){ case CONTROL -> "remote_control"; case GRAPH -> "open_graph"; case PLOT -> "open_plotter"; }, key);
    }
}
