package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Draw and handle the CT Client Config screen
public final class CTClientConfigScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT client config
    private CTClientConfigScreen() {
    }

    // Register the CT client config
    public static void register(ModContainer modContainer) {
        BaseConfigScreen.setDefaultActionFor(CreateThrusters.MOD_ID, screen -> screen.withSpecs(
                CTConfigs.CLIENT_SPEC,
                CTConfigs.COMMON_SPEC,
                CTConfigs.SERVER_SPEC));
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new ConfigurationScreen(container, parent));
    }
}
