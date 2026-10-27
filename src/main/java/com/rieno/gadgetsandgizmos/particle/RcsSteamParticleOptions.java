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
import com.rieno.gadgetsandgizmos.content.PlumeRainbow;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

import java.util.Locale;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;

// Define RCS steam particle options
public record RcsSteamParticleOptions(
        float red,
        float green,
        float blue,
        float scale,
        PlumeRainbow.Mode rainbowMode,
        PlumeRainbow.Palette rainbowPalette,
        ColorEffectPreset colorEffect
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
                    Codec.FLOAT.fieldOf("scale").forGetter(RcsSteamParticleOptions::scale),
                    Codec.STRING.optionalFieldOf("rainbow_mode", "OFF").forGetter(opts -> opts.rainbowMode().name()),
                    Codec.STRING.optionalFieldOf("rainbow_palette", "NORMAL").forGetter(opts -> opts.rainbowPalette().name()),
                    ColorEffectPreset.CODEC.optionalFieldOf("color_effect", ColorEffectPreset.DEFAULT).forGetter(RcsSteamParticleOptions::colorEffect)
            ).apply(instance, (red, green, blue, scale, mode, palette, effect) ->
                    new RcsSteamParticleOptions(red, green, blue, scale,
                            PlumeRainbow.mode(mode), PlumeRainbow.palette(palette), effect)));

    public static final StreamCodec<RegistryFriendlyByteBuf, RcsSteamParticleOptions> STREAM_CODEC =
            StreamCodec.of(RcsSteamParticleOptions::encode, RcsSteamParticleOptions::decode);

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
        rainbowMode = rainbowMode == null ? PlumeRainbow.Mode.OFF : rainbowMode;
        rainbowPalette = rainbowPalette == null ? PlumeRainbow.Palette.NORMAL : rainbowPalette;
    }

    // Retain the ordinary steam constructor
    public RcsSteamParticleOptions(float red, float green, float blue, float scale, PlumeRainbow.Mode mode, PlumeRainbow.Palette palette){
        this(red, green, blue, scale, mode, palette, ColorEffectPreset.DEFAULT);
    }

    public RcsSteamParticleOptions(float red, float green, float blue, float scale) {
        this(red, green, blue, scale, PlumeRainbow.Mode.OFF, PlumeRainbow.Palette.NORMAL);
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

    // Encode the steam color and optional cycle
    private static void encode(RegistryFriendlyByteBuf buffer, RcsSteamParticleOptions opts) {
        buffer.writeFloat(opts.red());
        buffer.writeFloat(opts.green());
        buffer.writeFloat(opts.blue());
        buffer.writeFloat(opts.scale());
        buffer.writeEnum(opts.rainbowMode());
        buffer.writeEnum(opts.rainbowPalette());
        boolean hasEffect = opts.colorEffect().active() || opts.colorEffect().additive();
        buffer.writeBoolean(hasEffect);
        if(hasEffect) buffer.writeNbt(opts.colorEffect().write());
    }

    // Decode the steam color and optional cycle
    private static RcsSteamParticleOptions decode(RegistryFriendlyByteBuf buffer) {
        return new RcsSteamParticleOptions(buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readEnum(PlumeRainbow.Mode.class),
                buffer.readEnum(PlumeRainbow.Palette.class), buffer.readBoolean()
                        ? ColorEffectPreset.read(buffer.readNbt()) : ColorEffectPreset.DEFAULT);
    }

    // Write the string
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.3f %.3f %.3f %.3f",
                "createthrusters:rcs_steam", red, green, blue, scale);
    }
}
