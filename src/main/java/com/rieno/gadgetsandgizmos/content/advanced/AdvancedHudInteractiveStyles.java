package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;

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

    public static final List<Option> BUTTON_OPTIONS = HudStyleCatalog.CONTROL_OPTIONS.stream()
            .map(option -> new Option(option.id(), option.label())).toList();
    public static final List<Option> SLIDER_OPTIONS = BUTTON_OPTIONS;
    public static final List<Option> PROGRESS_OPTIONS = HudStyleCatalog.PROGRESS_OPTIONS.stream()
            .map(option -> new Option(option.id(), option.label())).toList();

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
        return HudStyleCatalog.supports(type);
    }

    // Get the options
    public static List<Option> options(String type) {
        return "progress".equals(type) ? PROGRESS_OPTIONS
                : supports(type) ? BUTTON_OPTIONS : List.of();
    }

    // Get the style
    public static String style(CompoundTag elm) {
        if (elm == null) {
            return HudStyleCatalog.ControlStyle.MATERIAL_FILLED.id();
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
        if("toggle".equals(type)) elm.putString("ToggleShape",
                HudStyleCatalog.checkbox(elm.getString("ToggleShape")) ? "checkbox" : "switch");
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
                .orElse("Material Filled");
    }

    // Normalize the advanced HUD interactive styles
    private static String normalize(String type, String requestedStyle) {
        return HudStyleCatalog.style(type, requestedStyle);
    }

    // Store the option
    public record Option(String id, String label) {
    }


}
