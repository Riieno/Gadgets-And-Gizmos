package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

// Parse and serialize the visual style shared by HUD elements
public final class AdvancedHudElementStyle {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int DEFAULT_TEXT_COLOR = 0xFFE8F7FF;
    public static final int DEFAULT_BOX_BACKGROUND_COLOR = 0x88263746;
    public static final int DEFAULT_BOX_BORDER_COLOR = 0xFFE8F7FF;
    public static final int DEFAULT_WIDGET_BACKGROUND_COLOR = 0xE61B2732;
    public static final int DEFAULT_WIDGET_ACCENT_COLOR = 0xFF4DA7D1;
    public static final int DEFAULT_WIDGET_TRACK_COLOR = 0xFF2B3D49;
    public static final int DEFAULT_FONT_SIZE = 9;
    public static final int MIN_FONT_SIZE = 4;
    public static final int MAX_FONT_SIZE = 64;
    public static final int MAX_BORDER_WIDTH = 16;
    public static final int MAX_BORDER_RADIUS = 64;
    public static final String DEFAULT_FONT_STYLE = "regular";
    public static final List<String> FONT_STYLE_OPTIONS = List.of(
            "regular", "bold", "italic", "bold_italic", "underline", "strikethrough", "obfuscated");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD element style
    private AdvancedHudElementStyle() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the defaults
    public static void applyDefaults(CompoundTag elm) {
        if (elm == null) {
            return;
        }
        String type = elm.getString("Type");
        putDoubleIfMissing(elm, "Rotation", 0.0D);
        putDoubleIfMissing(elm, "Scale", 1.0D);
        if ("box".equals(type)) {
            putIntIfMissing(elm, "Color", DEFAULT_BOX_BACKGROUND_COLOR);
            putIntIfMissing(elm, "BorderColor", DEFAULT_BOX_BORDER_COLOR);
            putIntIfMissing(elm, "BorderWidth", 0);
            putIntIfMissing(elm, "BorderRadius", 0);
        } else if ("text".equals(type) || "value".equals(type)
                || "button".equals(type) || "toggle".equals(type)
                || "slider".equals(type) || "text_input".equals(type)
                || "progress".equals(type)) {
            putIntIfMissing(elm, "Color", DEFAULT_TEXT_COLOR);
            putIntIfMissing(elm, "FontSize", DEFAULT_FONT_SIZE);
            if (!elm.contains("Bold", Tag.TAG_BYTE)) {
                elm.putBoolean("Bold", false);
            }
            if (!elm.contains("Italic", Tag.TAG_BYTE)) {
                elm.putBoolean("Italic", false);
            }
            if (!elm.contains("FontStyle", Tag.TAG_STRING)) {
                elm.putString("FontStyle", legacyFontStyle(elm));
            }
        }
        if ("button".equals(type) || "toggle".equals(type)
                || "slider".equals(type) || "text_input".equals(type)
                || "progress".equals(type)) {
            putIntIfMissing(elm, "BackgroundColor", DEFAULT_WIDGET_BACKGROUND_COLOR);
            putIntIfMissing(elm, "AccentColor", DEFAULT_WIDGET_ACCENT_COLOR);
            putIntIfMissing(elm, "TrackColor", DEFAULT_WIDGET_TRACK_COLOR);
            putIntIfMissing(elm, "BorderColor", 0xFF527185);
            putIntIfMissing(elm, "BorderWidth", 1);
            putIntIfMissing(elm, "BorderRadius", 4);
        }
        if (AdvancedHudInteractiveStyles.supports(type)) {
            putIntIfMissing(elm, "Color", DEFAULT_TEXT_COLOR);
            AdvancedHudInteractiveStyles.applyDefault(elm);
        }
    }

    // Get the color
    public static int color(CompoundTag elm, String key, int fallback) {
        return elm != null && elm.contains(key, Tag.TAG_INT) ? elm.getInt(key) : fallback;
    }

    // Get the font size
    public static int fontSize(CompoundTag elm) {
        return clampedInt(elm, "FontSize", DEFAULT_FONT_SIZE, MIN_FONT_SIZE, MAX_FONT_SIZE);
    }

    // Get the border width
    public static int borderWidth(CompoundTag elm) {
        return clampedInt(elm, "BorderWidth", 0, 0, MAX_BORDER_WIDTH);
    }

    // Get the border radius
    public static int borderRadius(CompoundTag elm) {
        return clampedInt(elm, "BorderRadius", 0, 0, MAX_BORDER_RADIUS);
    }

    // Get the font style
    public static String fontStyle(CompoundTag elm) {
        if (elm == null) {
            return DEFAULT_FONT_STYLE;
        }
        if (elm.contains("FontStyle", Tag.TAG_STRING)) {
            String style = normalizeFontStyle(elm.getString("FontStyle"));
            if (FONT_STYLE_OPTIONS.contains(style)) {
                return style;
            }
        }
        return legacyFontStyle(elm);
    }

    // Set the font style
    public static void setFontStyle(CompoundTag elm, String requestedStyle) {
        if (elm == null) {
            return;
        }
        String style = normalizeFontStyle(requestedStyle);
        if (!FONT_STYLE_OPTIONS.contains(style)) {
            style = DEFAULT_FONT_STYLE;
        }
        elm.putString("FontStyle", style);
        elm.putBoolean("Bold", "bold".equals(style) || "bold_italic".equals(style));
        elm.putBoolean("Italic", "italic".equals(style) || "bold_italic".equals(style));
    }

    // Get the font style label
    public static String fontStyleLabel(String style) {
        return switch (normalizeFontStyle(style)) {
            case "bold" -> "Bold";
            case "italic" -> "Italic";
            case "bold_italic" -> "Bold Italic";
            case "underline" -> "Underline";
            case "strikethrough" -> "Strikethrough";
            case "obfuscated" -> "Obfuscated";
            default -> "Regular";
        };
    }

    // Parse the color
    public static OptionalInt parseColor(String input) {
        if (input == null) {
            return OptionalInt.empty();
        }
        String hex = input.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return OptionalInt.empty();
        }
        try {
            long parsed = Long.parseUnsignedLong(hex, 16);
            if (hex.length() == 6) {
                parsed |= 0xFF000000L;
            }
            return OptionalInt.of((int) parsed);
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    // Format the color
    public static String formatColor(int col) {
        return String.format(Locale.ROOT, "#%08X", col);
    }

    // Put the int if missing
    private static void putIntIfMissing(CompoundTag elm, String key, int val) {
        if (!elm.contains(key, Tag.TAG_INT)) {
            elm.putInt(key, val);
        }
    }

    // Put the double if missing
    private static void putDoubleIfMissing(CompoundTag elm, String key, double val) {
        if (!elm.contains(key, Tag.TAG_DOUBLE)) {
            elm.putDouble(key, val);
        }
    }

    // Get the legacy font style
    private static String legacyFontStyle(CompoundTag elm) {
        boolean bold = elm != null && elm.getBoolean("Bold");
        boolean italic = elm != null && elm.getBoolean("Italic");
        if (bold && italic) {
            return "bold_italic";
        }
        if (bold) {
            return "bold";
        }
        return italic ? "italic" : DEFAULT_FONT_STYLE;
    }

    // Normalize the font style
    private static String normalizeFontStyle(String style) {
        return style == null ? DEFAULT_FONT_STYLE
                : style.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    // Get the clamped int
    private static int clampedInt(CompoundTag elm, String key, int fallback, int minimum, int maximum) {
        int val = elm != null && elm.contains(key, Tag.TAG_INT) ? elm.getInt(key) : fallback;
        return Math.max(minimum, Math.min(maximum, val));
    }
}
