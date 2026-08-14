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

// Define the supported interactive HUD styles and their defaults
public final class AdvancedHudInteractiveStyles {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String CREATE_PAINTED = "create_painted";
    public static final String CREATE_ICON = "create_icon";
    public static final String TNT_BRASS = "tnt_brass";
    public static final String CONTROLLER_GRAPH = "controller_graph";
    public static final String MINECRAFT = "minecraft";
    public static final String THRUSTER_TRIM = "thruster_trim";
    public static final String AILERON_RANGE = "aileron_range";
    public static final String POWERED_ZIPLINE = "powered_zipline";
    public static final String GYROSCOPE_RANGE = "gyroscope_range";

    public static final List<Option> BUTTON_OPTIONS = List.of(
            new Option(CREATE_PAINTED, "Create Painted"),
            new Option(CREATE_ICON, "Create Icon"),
            new Option(TNT_BRASS, "Thruster Brass"),
            new Option(CONTROLLER_GRAPH, "Controller Graph"),
            new Option(MINECRAFT, "Minecraft")
    );
    public static final List<Option> SLIDER_OPTIONS = List.of(
            new Option(CONTROLLER_GRAPH, "Controller Graph"),
            new Option(TNT_BRASS, "Thruster Brass"),
            new Option(THRUSTER_TRIM, "Thruster Trim"),
            new Option(AILERON_RANGE, "Aileron Range"),
            new Option(POWERED_ZIPLINE, "Powered Zipline"),
            new Option(GYROSCOPE_RANGE, "Gyroscope Range")
    );

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD interactive styles
    private AdvancedHudInteractiveStyles() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this supports the value
    public static boolean supports(String type) {
        return "button".equals(type) || "toggle".equals(type) || "slider".equals(type);
    }

    // Get the options
    public static List<Option> options(String type) {
        return "slider".equals(type) ? SLIDER_OPTIONS
                : "button".equals(type) || "toggle".equals(type) ? BUTTON_OPTIONS : List.of();
    }

    // Get the style
    public static String style(CompoundTag elm) {
        if (elm == null) {
            return CREATE_PAINTED;
        }
        return normalize(elm.getString("Type"), elm.getString("Style"));
    }

    // Apply the default
    public static void applyDefault(CompoundTag elm) {
        if (elm == null || !supports(elm.getString("Type"))) {
            return;
        }
        String type = elm.getString("Type");
        String requested = elm.contains("Style", Tag.TAG_STRING) ? elm.getString("Style") : "";
        elm.putString("Style", normalize(type, requested));
    }

    // Set the style
    public static void setStyle(CompoundTag elm, String requestedStyle) {
        if (elm == null || !supports(elm.getString("Type"))) {
            return;
        }
        elm.putString("Style", normalize(elm.getString("Type"), requestedStyle));
    }

    // Get the label
    public static String label(String type, String style) {
        String normalized = normalize(type, style);
        return options(type).stream()
                .filter(option -> option.id().equals(normalized))
                .map(Option::label)
                .findFirst()
                .orElse("Create Painted");
    }

    // Normalize the advanced HUD interactive styles
    private static String normalize(String type, String requestedStyle) {
        List<Option> opts = options(type);
        String fallback = "slider".equals(type) ? CONTROLLER_GRAPH : CREATE_PAINTED;
        String normalized = requestedStyle == null ? ""
                : requestedStyle.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return opts.stream().anyMatch(option -> option.id().equals(normalized)) ? normalized : fallback;
    }

    // Store the option
    public record Option(String id, String label) {
    }
}
