package com.rieno.gadgetsandgizmos.content;

import net.minecraft.util.Mth;

// Define the color cycle used by V2 thruster exhaust
public final class PlumeRainbow {
    public enum Mode {
        OFF, SOLID, UNICORN
    }

    public enum Palette {
        DARK(0.9F, 0.48F), NORMAL(0.92F, 1.0F), PASTEL(0.42F, 1.0F);

        private final float saturation;
        private final float brightness;

        Palette(float saturation, float brightness) {
            this.saturation = saturation;
            this.brightness = brightness;
        }
    }

    private PlumeRainbow() {
    }

    // Resolve a stored mode without breaking older thruster data
    public static Mode mode(String name) {
        try {
            return Mode.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Mode.OFF;
        }
    }

    // Resolve a stored palette without breaking older thruster data
    public static Palette palette(String name) {
        try {
            return Palette.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Palette.NORMAL;
        }
    }

    // Cycle a single color or move a gradient along the exhaust
    public static int color(Mode mode, Palette palette, long gameTime, float progress, int age) {
        return color(mode, palette, (double) gameTime, progress, age);
    }

    // Interpolate the same cycle for rendered beams between game ticks
    public static int color(Mode mode, Palette palette, double renderTime, float progress, int age) {
        float phase = mode == Mode.UNICORN ? progress * 0.9F + age * 0.008F : 0.0F;
        float hue = (float) ((renderTime / 240.0D + phase) % 1.0D);
        return Mth.hsvToRgb(hue, palette.saturation, palette.brightness);
    }
}
