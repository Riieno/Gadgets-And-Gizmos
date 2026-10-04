package com.rieno.gadgetsandgizmos.config;

import com.rieno.gadgetsandgizmos.content.PlumeRainbow;
import org.junit.jupiter.api.Test;

import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SeasonalPlumeEffectsTest {
    private static final PlumeRainbow.Mode SAVED_MODE = PlumeRainbow.Mode.UNICORN;
    private static final PlumeRainbow.Palette SAVED_PALETTE = PlumeRainbow.Palette.DARK;

    @Test
    void defaultUsesPrideInJuneAndRestoresTheSavedSelectionAfterwards() {
        SeasonalPlumeEffects.Selection june = SeasonalPlumeEffects.resolve(SAVED_MODE, SAVED_PALETTE,
                SeasonalPlumeEffects.Force.DEFAULT, SeasonalPlumeEffects.PrideEffect.SOLID,
                SeasonalPlumeEffects.PridePalette.DEFAULT, Month.JUNE);
        SeasonalPlumeEffects.Selection july = SeasonalPlumeEffects.resolve(SAVED_MODE, SAVED_PALETTE,
                SeasonalPlumeEffects.Force.DEFAULT, SeasonalPlumeEffects.PrideEffect.SOLID,
                SeasonalPlumeEffects.PridePalette.DEFAULT, Month.JULY);

        assertEquals(new SeasonalPlumeEffects.Selection(
                PlumeRainbow.Mode.SOLID, PlumeRainbow.Palette.NORMAL), june);
        assertEquals(new SeasonalPlumeEffects.Selection(SAVED_MODE, SAVED_PALETTE), july);
    }

    @Test
    void forcedPrideWorksOutsideJuneWhileOffAndUnusedSeasonsPreserveTheCommand() {
        SeasonalPlumeEffects.Selection pride = SeasonalPlumeEffects.resolve(SAVED_MODE, SAVED_PALETTE,
                SeasonalPlumeEffects.Force.PRIDE, SeasonalPlumeEffects.PrideEffect.RAINBOW,
                SeasonalPlumeEffects.PridePalette.PASTEL, Month.DECEMBER);
        assertEquals(new SeasonalPlumeEffects.Selection(
                PlumeRainbow.Mode.UNICORN, PlumeRainbow.Palette.PASTEL), pride);

        for (SeasonalPlumeEffects.Force force : new SeasonalPlumeEffects.Force[]{
                SeasonalPlumeEffects.Force.OFF, SeasonalPlumeEffects.Force.HALLOWEEN,
                SeasonalPlumeEffects.Force.CHRISTMAS}) {
            assertEquals(new SeasonalPlumeEffects.Selection(SAVED_MODE, SAVED_PALETTE),
                    SeasonalPlumeEffects.resolve(SAVED_MODE, SAVED_PALETTE, force,
                            SeasonalPlumeEffects.PrideEffect.SOLID,
                            SeasonalPlumeEffects.PridePalette.DARKER, Month.JUNE));
        }
    }

    @Test
    void focusedBeamAndParticlesShareTheSameCycle() {
        int plume = PlumeRainbow.color(PlumeRainbow.Mode.UNICORN,
                PlumeRainbow.Palette.NORMAL, 120L, 0.25F, 0);
        int beam = PlumeRainbow.color(PlumeRainbow.Mode.UNICORN,
                PlumeRainbow.Palette.NORMAL, 120.0D, 0.25F, 0);
        int beamEnd = PlumeRainbow.color(PlumeRainbow.Mode.UNICORN,
                PlumeRainbow.Palette.NORMAL, 120.0D, 0.75F, 0);

        assertEquals(plume, beam);
        assertNotEquals(beam, beamEnd);
    }
}
