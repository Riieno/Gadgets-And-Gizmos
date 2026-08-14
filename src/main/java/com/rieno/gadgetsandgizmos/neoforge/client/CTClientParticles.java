package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.client.particle.ColoredCloudParticle;
import com.rieno.gadgetsandgizmos.neoforge.client.particle.ClawMarkerPulseParticle;
import com.rieno.gadgetsandgizmos.neoforge.client.particle.RcsSteamParticle;
import com.rieno.gadgetsandgizmos.registry.CTParticles;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

// Register the client particles
public final class CTClientParticles {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT client particles
    private CTClientParticles() {
    }

    // Register the particle providers
    public static void registerParticleProviders(RegisterParticleProvidersEvent evt) {
        evt.registerSpriteSet(CTParticles.COLORED_CLOUD.get(), ColoredCloudParticle.Provider::new);
        evt.registerSpriteSet(CTParticles.CLAW_MARKER_PULSE.get(), ClawMarkerPulseParticle.Provider::new);
        evt.registerSpriteSet(CTParticles.RCS_STEAM.get(), RcsSteamParticle.Provider::new);
    }
}
