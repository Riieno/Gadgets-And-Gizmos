package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.lang.reflect.Method;

// Keep the ACC startup display available
public final class AccDisplayStartupGate {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OWNER = "Riieno";
    private static final boolean ENABLED = true; // Keep the startup display available to every player

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display startup gate
    private AccDisplayStartupGate() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is enabled
    public static boolean enabled() {
        return ENABLED;
    }

    // Get the startup player name
    static String startupPlayerName() {
        String configured = System.getProperty("createthrusters.accDisplays.player", "").strip();
        if (!configured.isBlank()) {
            return configured;
        }
        configured = System.getenv("CREATETHRUSTERS_ACC_DISPLAYS_PLAYER");
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstance = minecraftClass.getMethod("getInstance");
            Object minecraft = getInstance.invoke(null);
            if (minecraft == null) {
                return "";
            }
            Object user = minecraftClass.getMethod("getUser").invoke(minecraft);
            if (user == null) {
                return "";
            }
            Object name = user.getClass().getMethod("getName").invoke(user);
            return name instanceof String val ? val.strip() : "";
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "";
        }
    }
}
