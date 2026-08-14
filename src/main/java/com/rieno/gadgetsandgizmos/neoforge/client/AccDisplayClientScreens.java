package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayTextInputOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayTextInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.neoforged.neoforge.network.PacketDistributor;

// Register the ACC display screens
public final class AccDisplayClientScreens {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display client screens
    private AccDisplayClientScreens() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Open the text input
    public static void openTextInput(AccDisplayTextInputOpenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        String prompt = payload.prompt().isBlank() ? "Enter text" : payload.prompt();
        minecraft.setScreen(new ControllerTextInputScreen(
                minecraft.screen,
                Component.literal("ACC Display Text Input"),
                Component.literal(prompt),
                payload.value(),
                val -> PacketDistributor.sendToServer(new AccDisplayTextInputPayload(
                        payload.target(), payload.nodeId(), payload.interactionId(), val))));
    }

    // Handle projected display interaction
    public static boolean interactProjection(AccDisplayBlockEntity display, BlockHitResult hit,
                                             int mouseButton) {
        return AccDisplayGuiProjection.interact(display, hit, mouseButton);
    }

    // Open the mode selection
    public static void openModeSelection(AccDisplayBlockEntity display) {
        if (display == null) {
            return;
        }
        AccDisplayBlockEntity root = display.networkRoot();
        if (root != null) {
            display = root;
        }
        Minecraft.getInstance().setScreen(new AccDisplayModeScreen(
                MenuConfigTarget.of(display.getBlockPos(),
                        SimulatedHelper.getContainingSubLevelId(display)),
                display.displayMode(), display.displayFrame()));
    }
}
