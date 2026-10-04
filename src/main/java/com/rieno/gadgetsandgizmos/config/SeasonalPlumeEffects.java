package com.rieno.gadgetsandgizmos.config;

import com.rieno.gadgetsandgizmos.content.PlumeRainbow;

import java.time.LocalDate;
import java.time.Month;

// Resolve client seasonal colors without changing a thruster's saved selection
public final class SeasonalPlumeEffects {
    public enum Force {
        DEFAULT, PRIDE, HALLOWEEN, CHRISTMAS, OFF
    }

    public enum PrideEffect {
        SOLID, RAINBOW
    }

    public enum PridePalette {
        DARKER, DEFAULT, PASTEL
    }

    public record Selection(PlumeRainbow.Mode mode, PlumeRainbow.Palette palette) {
    }

    private SeasonalPlumeEffects() {
    }

    // Resolve the current client setting and calendar month
    public static Selection current(PlumeRainbow.Mode mode, PlumeRainbow.Palette palette) {
        return resolve(mode, palette,
                CTConfigs.CLIENT.forceSeasonalEffects.get(),
                CTConfigs.CLIENT.pridePlumeEffect.get(),
                CTConfigs.CLIENT.prideColorPalette.get(),
                LocalDate.now().getMonth());
    }

    // Apply Pride colors only when selected or when June is active by default
    public static Selection resolve(PlumeRainbow.Mode mode, PlumeRainbow.Palette palette,
            Force force, PrideEffect prideEffect, PridePalette pridePalette, Month month) {
        if (force != Force.PRIDE && (force != Force.DEFAULT || month != Month.JUNE)) {
            return new Selection(mode, palette);
        }
        PlumeRainbow.Mode seasonalMode = prideEffect == PrideEffect.RAINBOW
                ? PlumeRainbow.Mode.UNICORN : PlumeRainbow.Mode.SOLID;
        PlumeRainbow.Palette seasonalPalette = switch (pridePalette) {
            case DARKER -> PlumeRainbow.Palette.DARK;
            case DEFAULT -> PlumeRainbow.Palette.NORMAL;
            case PASTEL -> PlumeRainbow.Palette.PASTEL;
        };
        return new Selection(seasonalMode, seasonalPalette);
    }
}
