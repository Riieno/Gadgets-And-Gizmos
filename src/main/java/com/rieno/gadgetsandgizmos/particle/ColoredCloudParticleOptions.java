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

// Define colored cloud particle options
public record ColoredCloudParticleOptions(float red, float green, float blue) implements ParticleOptions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<ColoredCloudParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("red").forGetter(ColoredCloudParticleOptions::red),
                    Codec.FLOAT.fieldOf("green").forGetter(ColoredCloudParticleOptions::green),
                    Codec.FLOAT.fieldOf("blue").forGetter(ColoredCloudParticleOptions::blue)
            ).apply(instance, ColoredCloudParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ColoredCloudParticleOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ColoredCloudParticleOptions::red,
            ByteBufCodecs.FLOAT, ColoredCloudParticleOptions::green,
            ByteBufCodecs.FLOAT, ColoredCloudParticleOptions::blue,
            ColoredCloudParticleOptions::new);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the colored cloud particle options
    public ColoredCloudParticleOptions {
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
        return CTParticles.COLORED_CLOUD.get();
    }

    // Write the string
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.3f %.3f %.3f", "createthrusters:colored_cloud", red, green, blue);
    }
}
