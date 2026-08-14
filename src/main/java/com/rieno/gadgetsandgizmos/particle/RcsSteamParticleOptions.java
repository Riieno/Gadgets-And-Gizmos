package com.rieno.gadgetsandgizmos.particle;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rieno.gadgetsandgizmos.registry.CTParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

import java.util.Locale;

// Define RCS steam particle options
public record RcsSteamParticleOptions(
        float red,
        float green,
        float blue,
        float scale
) implements ParticleOptions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<RcsSteamParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("red").forGetter(RcsSteamParticleOptions::red),
                    Codec.FLOAT.fieldOf("green").forGetter(RcsSteamParticleOptions::green),
                    Codec.FLOAT.fieldOf("blue").forGetter(RcsSteamParticleOptions::blue),
                    Codec.FLOAT.fieldOf("scale").forGetter(RcsSteamParticleOptions::scale)
            ).apply(instance, RcsSteamParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RcsSteamParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, RcsSteamParticleOptions::red,
                    ByteBufCodecs.FLOAT, RcsSteamParticleOptions::green,
                    ByteBufCodecs.FLOAT, RcsSteamParticleOptions::blue,
                    ByteBufCodecs.FLOAT, RcsSteamParticleOptions::scale,
                    RcsSteamParticleOptions::new);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the RCS steam particle options
    public RcsSteamParticleOptions {
        red = Mth.clamp(red, 0.0F, 1.0F);
        green = Mth.clamp(green, 0.0F, 1.0F);
        blue = Mth.clamp(blue, 0.0F, 1.0F);
        scale = Mth.clamp(scale, 0.1F, 2.0F);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public ParticleType<?> getType() {
        return CTParticles.RCS_STEAM.get();
    }

    // Write the string
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.3f %.3f %.3f %.3f",
                "createthrusters:rcs_steam", red, green, blue, scale);
    }
}
