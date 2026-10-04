package com.rieno.gadgetsandgizmos.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PlumeRainbowTest {
    @Test
    void solidStaysUniformWhileUnicornMovesAlongPlume() {
        int solidStart = PlumeRainbow.color(PlumeRainbow.Mode.SOLID,
                PlumeRainbow.Palette.NORMAL, 80, 0.0F, 0);
        int solidEnd = PlumeRainbow.color(PlumeRainbow.Mode.SOLID,
                PlumeRainbow.Palette.NORMAL, 80, 1.0F, 10);
        int unicornStart = PlumeRainbow.color(PlumeRainbow.Mode.UNICORN,
                PlumeRainbow.Palette.NORMAL, 80, 0.0F, 0);
        int unicornEnd = PlumeRainbow.color(PlumeRainbow.Mode.UNICORN,
                PlumeRainbow.Palette.NORMAL, 80, 1.0F, 10);

        assertEquals(solidStart, solidEnd);
        assertNotEquals(unicornStart, unicornEnd);
        assertNotEquals(solidStart, PlumeRainbow.color(PlumeRainbow.Mode.SOLID,
                PlumeRainbow.Palette.NORMAL, 100, 0.0F, 0));
    }

    @Test
    void paletteAndStoredNamesStayDistinctAndSafe() {
        int dark = PlumeRainbow.color(PlumeRainbow.Mode.SOLID,
                PlumeRainbow.Palette.DARK, 40, 0.0F, 0);
        int normal = PlumeRainbow.color(PlumeRainbow.Mode.SOLID,
                PlumeRainbow.Palette.NORMAL, 40, 0.0F, 0);
        int pastel = PlumeRainbow.color(PlumeRainbow.Mode.SOLID,
                PlumeRainbow.Palette.PASTEL, 40, 0.0F, 0);

        assertNotEquals(dark, normal);
        assertNotEquals(normal, pastel);
        assertEquals(PlumeRainbow.Mode.OFF, PlumeRainbow.mode("INVALID"));
        assertEquals(PlumeRainbow.Palette.NORMAL, PlumeRainbow.palette("INVALID"));
    }
}
