package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerAreaConfigPayload;

// Store linker snapshots and highlights received from the server
public final class ContraptionNetworkLinkerClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker client
    private ContraptionNetworkLinkerClient() {
    }

    // Open the screen
    public static void openScreen(InteractionHand hand, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ScreenOpener.open(new ContraptionNetworkLinkerScreen(hand, stack));
    }

    public static void openAreaConfiguration(ContraptionNetworkLinkerAreaConfigPayload area){
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.player == null || minecraft.screen != null) return;
        ScreenOpener.open(new ContraptionNetworkLinkerAreaScreen(area));
    }
}
