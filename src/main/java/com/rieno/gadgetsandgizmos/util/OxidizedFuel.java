package com.rieno.gadgetsandgizmos.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.registry.CTDataComponents;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

// Resolve Oxidized fuel data
public final class OxidizedFuel {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final TagKey<Fluid> OXIDIZED_FUELS = TagKey.create(Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "oxidized_fuels"));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the oxidized fuel
    private OxidizedFuel() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is oxidized
    public static boolean isOxidized(FluidStack stack) {
        return !stack.isEmpty() && (stack.is(OXIDIZED_FUELS)
                || ThrusterFuelData.isConfiguredOxidized(stack.getFluid())
                || Boolean.TRUE.equals(stack.getComponents().get(CTDataComponents.OXIDIZED)));
    }

    // Check if this can oxidize
    public static boolean canOxidize(FluidStack stack) {
        return !stack.isEmpty() && !isOxidized(stack) && ThrusterFuelData.isFuel(stack.getFluid());
    }

    // Get the propulsion multiplier
    public static double propulsionMultiplier() {
        return Math.max(0.0D, CTConfigs.COMMON.oxidizedFuelThrustMultiplier.get());
    }

    // Get the oxidize
    public static FluidStack oxidize(FluidStack stack) {
        FluidStack oxidized = stack.copy();
        oxidized.set(CTDataComponents.OXIDIZED, true);
        return oxidized;
    }
}
