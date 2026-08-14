package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.particle.ClawMarkerPulseParticleOptions;
import com.rieno.gadgetsandgizmos.particle.ColoredCloudParticleOptions;
import com.rieno.gadgetsandgizmos.particle.RcsSteamParticleOptions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Register addon particles
public final class CTParticles {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<ParticleType<?>> REGISTRAR = DeferredRegister.create(Registries.PARTICLE_TYPE, CreateThrusters.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<ColoredCloudParticleOptions>> COLORED_CLOUD =
            REGISTRAR.register("colored_cloud", () -> new ParticleType<ColoredCloudParticleOptions>(false) {
                // Get the codec
                @Override
                public MapCodec<ColoredCloudParticleOptions> codec() {
                    return ColoredCloudParticleOptions.CODEC;
                }

                // Get the CT particles stream codec
                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, ColoredCloudParticleOptions> streamCodec() {
                    return ColoredCloudParticleOptions.STREAM_CODEC;
                }
            });

    public static final DeferredHolder<ParticleType<?>, ParticleType<ClawMarkerPulseParticleOptions>> CLAW_MARKER_PULSE =
            REGISTRAR.register("claw_marker_pulse", () -> new ParticleType<ClawMarkerPulseParticleOptions>(false) {
                // Get the codec
                @Override
                public MapCodec<ClawMarkerPulseParticleOptions> codec() {
                    return ClawMarkerPulseParticleOptions.CODEC;
                }

                // Get the CT particles stream codec
                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, ClawMarkerPulseParticleOptions> streamCodec() {
                    return ClawMarkerPulseParticleOptions.STREAM_CODEC;
                }
            });

    public static final DeferredHolder<ParticleType<?>, ParticleType<RcsSteamParticleOptions>> RCS_STEAM =
            REGISTRAR.register("rcs_steam", () -> new ParticleType<RcsSteamParticleOptions>(false) {
                // Get the codec
                @Override
                public MapCodec<RcsSteamParticleOptions> codec() {
                    return RcsSteamParticleOptions.CODEC;
                }

                // Get the CT particles stream codec
                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, RcsSteamParticleOptions> streamCodec() {
                    return RcsSteamParticleOptions.STREAM_CODEC;
                }
            });

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT particles
    private CTParticles() {
    }
}
