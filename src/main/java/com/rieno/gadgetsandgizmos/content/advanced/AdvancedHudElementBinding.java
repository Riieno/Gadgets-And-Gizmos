package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.function.Function;

// Bind one HUD element to its graph value and interaction state
public final class AdvancedHudElementBinding {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String BINDINGS_TAG = "PropertyBindings";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD element binding
    private AdvancedHudElementBinding() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the port
    public static String port(CompoundTag elm, String property) {
        if (elm == null || property == null) {
            return "";
        }
        return elm.getCompound(BINDINGS_TAG).getString(property);
    }

    // Bind the advanced HUD element binding
    public static void bind(CompoundTag elm, String property, String port) {
        if (elm == null || property == null || property.isBlank()) {
            return;
        }
        CompoundTag bindings = elm.getCompound(BINDINGS_TAG);
        if (port == null || port.isBlank()) {
            bindings.remove(property);
        } else {
            bindings.putString(property, port);
        }
        if (bindings.isEmpty()) {
            elm.remove(BINDINGS_TAG);
        } else {
            elm.put(BINDINGS_TAG, bindings);
        }
    }

    // Read the numeric value
    public static double number(CompoundTag elm, String property, double fallback,
                                Function<String, AdvancedGraphDocument.Value> values) {
        AdvancedGraphDocument.Value val = value(elm, property, values);
        if (val == null) {
            return fallback;
        }
        return switch (val.type()) {
            case "number" -> Double.isFinite(val.asNumber()) ? val.asNumber() : fallback;
            case "boolean" -> val.asBoolean() ? 1.0D : 0.0D;
            case "string", "direction" -> parseNumber(val.asString(), fallback);
            default -> fallback;
        };
    }

    // Resolve the HUD boolean value
    public static boolean bool(CompoundTag elm, String property, boolean fallback,
                               Function<String, AdvancedGraphDocument.Value> values) {
        AdvancedGraphDocument.Value val = value(elm, property, values);
        if (val == null) {
            return fallback;
        }
        return switch (val.type()) {
            case "boolean" -> val.asBoolean();
            case "number" -> val.asNumber() != 0.0D;
            case "string", "direction" -> !val.asString().isBlank()
                    && !"false".equalsIgnoreCase(val.asString())
                    && !"0".equals(val.asString().strip());
            default -> fallback;
        };
    }

    // Get the text
    public static String text(CompoundTag elm, String property, String fallback,
                              Function<String, AdvancedGraphDocument.Value> values) {
        AdvancedGraphDocument.Value val = value(elm, property, values);
        if (val == null) {
            return fallback == null ? "" : fallback;
        }
        return switch (val.type()) {
            case "string", "direction" -> val.asString();
            case "boolean" -> Boolean.toString(val.asBoolean());
            case "number" -> {
                double num = val.asNumber();
                yield num == Math.rint(num) ? Long.toString(Math.round(num))
                        : Double.toString(num);
            }
            default -> val.payload().toString();
        };
    }

    // Get the color
    public static int color(CompoundTag elm, String property, int fallback,
                            Function<String, AdvancedGraphDocument.Value> values) {
        AdvancedGraphDocument.Value val = value(elm, property, values);
        if (val == null) {
            return fallback;
        }
        if ("number".equals(val.type())) {
            return (int) Math.round(val.asNumber());
        }
        if ("string".equals(val.type()) || "direction".equals(val.type())) {
            return AdvancedHudElementStyle.parseColor(val.asString()).orElse(fallback);
        }
        return fallback;
    }

    // Get the resolved copy
    public static CompoundTag resolvedCopy(CompoundTag elm,
                                           Function<String, AdvancedGraphDocument.Value> values) {
        CompoundTag resolved = elm == null ? new CompoundTag() : elm.copy();
        resolved.putInt("X", (int) Math.round(number(elm, "X", resolved.getInt("X"), values)));
        resolved.putInt("Y", (int) Math.round(number(elm, "Y", resolved.getInt("Y"), values)));
        resolved.putInt("W", Math.max(1,
                (int) Math.round(number(elm, "W", Math.max(1, resolved.getInt("W")), values))));
        resolved.putInt("H", Math.max(1,
                (int) Math.round(number(elm, "H", Math.max(1, resolved.getInt("H")), values))));
        resolved.putDouble("Rotation", number(elm, "Rotation", resolved.getDouble("Rotation"), values));
        resolved.putDouble("Scale", Math.max(0.01D,
                number(elm, "Scale", resolved.contains("Scale") ? resolved.getDouble("Scale") : 1.0D, values)));
        resolved.putBoolean("Visible", bool(elm, "Visible",
                !resolved.contains("Visible") || resolved.getBoolean("Visible"), values));
        resolved.putInt("Color", color(elm, "Color",
                AdvancedHudElementStyle.color(resolved, "Color",
                        AdvancedHudElementStyle.DEFAULT_TEXT_COLOR), values));
        for (String property : java.util.List.of(
                "BackgroundColor", "AccentColor", "TrackColor", "BorderColor")) {
            if (resolved.contains(property)) {
                resolved.putInt(property, color(elm, property,
                        resolved.getInt(property), values));
            }
        }
        if (resolved.contains("BorderWidth")) {
            resolved.putInt("BorderWidth", Math.max(0, (int) Math.round(
                    number(elm, "BorderWidth", resolved.getInt("BorderWidth"), values))));
        }
        if (resolved.contains("BorderRadius")) {
            resolved.putInt("BorderRadius", Math.max(0, (int) Math.round(
                    number(elm, "BorderRadius", resolved.getInt("BorderRadius"), values))));
        }
        if (resolved.contains("FontSize")) {
            resolved.putInt("FontSize", Math.max(1, (int) Math.round(
                    number(elm, "FontSize", resolved.getInt("FontSize"), values))));
        }
        for (String property : java.util.List.of("Min", "Max", "Step", "Value")) {
            if (resolved.contains(property)) {
                resolved.putDouble(property,
                        number(elm, property, resolved.getDouble(property), values));
            }
        }
        if (resolved.contains("Text")) {
            resolved.putString("Text", text(elm, "Text", resolved.getString("Text"), values));
        }
        if (resolved.contains("Texture")) {
            resolved.putString("Texture", text(elm, "Texture", resolved.getString("Texture"), values));
        }
        return resolved;
    }

    // Get the value
    private static AdvancedGraphDocument.Value value(
            CompoundTag elm, String property,
            Function<String, AdvancedGraphDocument.Value> values) {
        String port = port(elm, property);
        return port.isBlank() || values == null ? null : values.apply(port);
    }

    // Parse the number
    private static double parseNumber(String text, double fallback) {
        try {
            return Double.parseDouble(text == null ? "" : text.strip().toLowerCase(Locale.ROOT));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
