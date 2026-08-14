package com.rieno.gadgetsandgizmos.content.advanced;

// Resolve Advanced HUD image sources
public final class AdvancedHudImageSource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD image source
    private AdvancedHudImageSource() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the advanced HUD image source from port
    public static String fromPort(AdvancedGraphLiveValue val) {
        if (val == null) {
            return "";
        }
        return switch (val.type()) {
            case "string", "direction" -> val.textValue();
            default -> "";
        };
    }

    // Create the advanced HUD image source from port
    public static String fromPort(AdvancedGraphDocument.Value val) {
        if (val == null) {
            return "";
        }
        return switch (val.type()) {
            case "string", "direction" -> val.asString();
            default -> "";
        };
    }

    // Copy the advanced HUD image source with the fallback
    public static String withFallback(String portSource, String fallback) {
        return portSource == null || portSource.isBlank()
                ? fallback == null ? "" : fallback
                : portSource;
    }
}
