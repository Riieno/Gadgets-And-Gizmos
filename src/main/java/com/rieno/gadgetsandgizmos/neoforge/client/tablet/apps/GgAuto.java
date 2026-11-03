package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletMediaApp;
import com.rieno.gadgetsandgizmos.lib.client.tablet.PaintedVehicleDashboardRenderer;
import net.minecraft.resources.ResourceLocation;

// Bind the painted vehicle dashboard to this tablet's Media application
public final class GgAuto extends PaintedVehicleDashboardRenderer{
    public GgAuto(){
        super(ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/apps/screens/gng_auto.png"),
                DiagnosticTabletMediaApp.ID, "player", Manifest.SKIN.contents());
    }
}
