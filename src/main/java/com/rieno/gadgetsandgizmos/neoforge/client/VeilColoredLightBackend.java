package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.VeilPointLightBackend;

// Keep the addon bootstrap entry point while the library owns renderer compatibility
public final class VeilColoredLightBackend{
    private VeilColoredLightBackend(){}

    public static void install(){
        VeilPointLightBackend.install();
    }
}
