package com.rieno.gadgetsandgizmos.particle;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

import java.util.Locale;

// Define claw marker pulse particle options
public record ClawMarkerPulseParticleOptions(float red, float green, float blue) implements ParticleOptions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<ClawMarkerPulseParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("red").forGetter(ClawMarkerPulseParticleOptions::red),
                    Codec.FLOAT.fieldOf("green").forGetter(ClawMarkerPulseParticleOptions::green),
                    Codec.FLOAT.fieldOf("blue").forGetter(ClawMarkerPulseParticleOptions::blue)
            ).apply(instance, ClawMarkerPulseParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClawMarkerPulseParticleOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ClawMarkerPulseParticleOptions::red,
            ByteBufCodecs.FLOAT, ClawMarkerPulseParticleOptions::green,
            ByteBufCodecs.FLOAT, ClawMarkerPulseParticleOptions::blue,
            ClawMarkerPulseParticleOptions::new);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw marker pulse particle options
    public ClawMarkerPulseParticleOptions {
        red = Mth.clamp(red, 0.0f, 1.0f);
        green = Mth.clamp(green, 0.0f, 1.0f);
        blue = Mth.clamp(blue, 0.0f, 1.0f);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public ParticleType<?> getType() {
        return CTParticles.CLAW_MARKER_PULSE.get();
    }

    // Write the string
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.3f %.3f %.3f", "createthrusters:claw_marker_pulse", red, green, blue);
    }
}
