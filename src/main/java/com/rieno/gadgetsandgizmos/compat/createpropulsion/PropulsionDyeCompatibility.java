package com.rieno.gadgetsandgizmos.compat.createpropulsion;

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectParticles;
import net.minecraft.resources.ResourceLocation;

// Keep Propulsion policy local while the library transports optional visual presets
public final class PropulsionDyeCompatibility{
    public static final ResourceLocation GROUP = ResourceLocation.parse("createthrusters:propulsion_plume");

    private PropulsionDyeCompatibility(){}

    public static void register(){ ColorEffectParticles.registerPolicy(GROUP, PropulsionDyeCompatibility::enabled); }

    public static boolean enabled(){ return CTConfigs.COMMON.dyeApplicatorPropulsionSimulated.get(); }
}
