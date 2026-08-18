package com.rieno.gadgetsandgizmos;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.create.CTCreateContraptionCompat;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShippingScheduleInstructions;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletApps;
import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntityDataAdapters;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTCreativeTabs;
import com.rieno.gadgetsandgizmos.registry.CTDataComponents;
import com.rieno.gadgetsandgizmos.registry.CTDisplaySources;
import com.rieno.gadgetsandgizmos.registry.CTDisplayTargets;
import com.rieno.gadgetsandgizmos.registry.CTUniversalDisplayAdapterConnections;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.registry.CTLootModifiers;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import com.rieno.gadgetsandgizmos.registry.CTParticles;
import com.rieno.gadgetsandgizmos.registry.CTRecipeSerializers;
import com.rieno.gadgetsandgizmos.registry.CTSoundEvents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.neoforged.bus.api.IEventBus;

// Register common addon content and optional integrations
public final class CreateThrusters {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String MOD_ID = "createthrusters";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the create thrusters
    private CreateThrusters() {
    }

    // Initialize the Create Thrusters
    public static void init(IEventBus modEventBus) {
        GadgetsNGizmosKineticGuard.registerGuardException(
                "createthrusters:thruster_bearing_extra_cog",
                CreateThrusters::isThrusterBearingExtraCog);
        ShippingScheduleInstructions.register();
        DiagnosticTabletApps.register();
        CTBlockEntityDataAdapters.register();
        CTUniversalDisplayAdapterConnections.register();
        CTDataComponents.register(modEventBus);
        CTBlocks.REGISTRAR.register(modEventBus);
        CTItems.REGISTRAR.register(modEventBus);
        CTEntityTypes.REGISTRAR.register(modEventBus);
        CTBlockEntities.REGISTRAR.register(modEventBus);
        CTDisplaySources.REGISTRAR.register(modEventBus);
        CTDisplayTargets.REGISTRAR.register(modEventBus);
        CTMenuTypes.REGISTRAR.register(modEventBus);
        CTParticles.REGISTRAR.register(modEventBus);
        CTRecipeSerializers.REGISTRAR.register(modEventBus);
        CTSoundEvents.REGISTRAR.register(modEventBus);
        CTLootModifiers.REGISTRAR.register(modEventBus);
        modEventBus.addListener(CTDisplaySources::onCommonSetup);
        modEventBus.addListener(CTDisplayTargets::onCommonSetup);
        modEventBus.addListener(CTCreateContraptionCompat::onCommonSetup);
        CTCreativeTabs.REGISTRAR.register(modEventBus);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a thruster bearing extra cog
    private static boolean isThrusterBearingExtraCog(KineticBlockEntity blockEntity) {
        return blockEntity instanceof ExtraKinetics.ExtraKineticsBlockEntity extraKinetics
                && extraKinetics.getParentBlockEntity() instanceof ThrusterBearingBlockEntity;
    }
}
