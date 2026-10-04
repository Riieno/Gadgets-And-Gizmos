package com.rieno.gadgetsandgizmos.particle;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTParticles;
import com.rieno.gadgetsandgizmos.content.PlumeRainbow;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

import java.util.Locale;

// Define colored cloud particle options
public record ColoredCloudParticleOptions(float red, float green, float blue,
                                          PlumeRainbow.Mode rainbowMode,
                                          PlumeRainbow.Palette rainbowPalette,
                                          float plumeProgress) implements ParticleOptions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<ColoredCloudParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("red").forGetter(ColoredCloudParticleOptions::red),
                    Codec.FLOAT.fieldOf("green").forGetter(ColoredCloudParticleOptions::green),
                    Codec.FLOAT.fieldOf("blue").forGetter(ColoredCloudParticleOptions::blue),
                    Codec.STRING.optionalFieldOf("rainbow_mode", "OFF").forGetter(opts -> opts.rainbowMode().name()),
                    Codec.STRING.optionalFieldOf("rainbow_palette", "NORMAL").forGetter(opts -> opts.rainbowPalette().name()),
                    Codec.FLOAT.optionalFieldOf("plume_progress", 0.0F).forGetter(ColoredCloudParticleOptions::plumeProgress)
            ).apply(instance, (red, green, blue, mode, palette, progress) ->
                    new ColoredCloudParticleOptions(red, green, blue,
                            PlumeRainbow.mode(mode), PlumeRainbow.palette(palette), progress)));

    public static final StreamCodec<RegistryFriendlyByteBuf, ColoredCloudParticleOptions> STREAM_CODEC =
            StreamCodec.of(ColoredCloudParticleOptions::encode, ColoredCloudParticleOptions::decode);

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
        rainbowMode = rainbowMode == null ? PlumeRainbow.Mode.OFF : rainbowMode;
        rainbowPalette = rainbowPalette == null ? PlumeRainbow.Palette.NORMAL : rainbowPalette;
        plumeProgress = Mth.clamp(plumeProgress, 0.0F, 1.0F);
    }

    // Retain the ordinary cloud constructor
    public ColoredCloudParticleOptions(float red, float green, float blue) {
        this(red, green, blue, PlumeRainbow.Mode.OFF, PlumeRainbow.Palette.NORMAL, 0.0F);
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

    // Encode the exhaust color and optional cycle
    private static void encode(RegistryFriendlyByteBuf buffer, ColoredCloudParticleOptions opts) {
        buffer.writeFloat(opts.red());
        buffer.writeFloat(opts.green());
        buffer.writeFloat(opts.blue());
        buffer.writeEnum(opts.rainbowMode());
        buffer.writeEnum(opts.rainbowPalette());
        buffer.writeFloat(opts.plumeProgress());
    }

    // Decode the exhaust color and optional cycle
    private static ColoredCloudParticleOptions decode(RegistryFriendlyByteBuf buffer) {
        return new ColoredCloudParticleOptions(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readEnum(PlumeRainbow.Mode.class), buffer.readEnum(PlumeRainbow.Palette.class),
                buffer.readFloat());
    }

    // Write the string
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.3f %.3f %.3f", "createthrusters:colored_cloud", red, green, blue);
    }
}
