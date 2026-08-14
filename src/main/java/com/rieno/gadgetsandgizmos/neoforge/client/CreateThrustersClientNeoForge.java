package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// Load the addon client without loading client classes on a dedicated server
@Mod(value = CreateThrusters.MOD_ID, dist = Dist.CLIENT)
public final class CreateThrustersClientNeoForge {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the create thrusters client neo forge
    public CreateThrustersClientNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        CTClientBootstrap.register(modEventBus, modContainer);
    }
}
