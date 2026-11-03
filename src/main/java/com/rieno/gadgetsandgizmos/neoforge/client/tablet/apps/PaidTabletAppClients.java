package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;

// Register paid app surfaces while retaining their definition's texture icons
public final class PaidTabletAppClients{
    // Prevent construction of the paid app client registrar
    private PaidTabletAppClients(){}

    // Register the independent paid app renderers
    public static void register(){
        TabletAppClientRegistry.register(PaidTabletApps.DIGISABLE.id(), new Digisable());
        TabletAppClientRegistry.register(PaidTabletApps.MANIFEST.id(), new Manifest());
        TabletAppClientRegistry.register(PaidTabletApps.BLOCKMATES.id(), new Blockmates());
        TabletAppClientRegistry.register(PaidTabletApps.CCTV.id(), new Cctv());
    }
}
