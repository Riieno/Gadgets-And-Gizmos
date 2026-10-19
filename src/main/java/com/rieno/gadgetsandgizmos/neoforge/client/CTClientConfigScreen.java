package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.client.ui.ItemIconButton;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.infrastructure.gui.OpenCreateMenuButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
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
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CTClientConfigScreen::addPauseMenuButton);
    }

    // Follow Create's configured pause-menu position after its own button has been added
    private static void addPauseMenuButton(ScreenEvent.Init.Post evt){
        if(!(evt.getScreen() instanceof PauseScreen)) return;
        for(var listener : evt.getListenersList()){
            if(!(listener instanceof OpenCreateMenuButton anchor)) continue;
            evt.addListener(new ItemIconButton(anchor.getX(), anchor.getY() + anchor.getHeight() + 4,
                    anchor.getWidth(), anchor.getHeight(), Component.translatable("createthrusters.menu.config"),
                    CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().asItem().getDefaultInstance(),
                    button -> Minecraft.getInstance().setScreen(new BaseConfigScreen(evt.getScreen(), CreateThrusters.MOD_ID))));
            return;
        }
    }
}
