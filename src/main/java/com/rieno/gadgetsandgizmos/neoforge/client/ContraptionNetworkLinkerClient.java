package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSyncPayload;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cycle the scope once when its configured action key is pressed
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        InteractionHand hand = minecraft.player == null ? null : findHeldLinkerHand(minecraft.player);
        if (CTActionKeys.LINKER_SCOPE.sample(hand != null).wasPressed(1)) attemptCycleScopeMode();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cycle the scope mode
    private static boolean attemptCycleScopeMode() {

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            return false;
        }

        InteractionHand hand = findHeldLinkerHand(player);
        if (hand == null) {
            return false;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!ContraptionNetworkLinkerData.hasClientData(stack)) {
            return false;
        }
        ContraptionNetworkLinkerData.TargetMode nextMode =
                ContraptionNetworkLinkerData.getClientTargetMode(stack).next();
        PacketDistributor.sendToServer(new ContraptionNetworkLinkerSyncPayload(hand,
                ContraptionNetworkLinkerData.writeClientEditRoot(stack,
                        ContraptionNetworkLinkerData.readClientTargets(stack),
                        ContraptionNetworkLinkerData.getClientEditMode(stack),
                        nextMode)));
                Component msg = Component.translatable(
                "item.createthrusters.contraption_network_linker.target_mode_set",
                    nextMode.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.AQUA);
                player.displayClientMessage(msg, true);
                minecraft.gui.setOverlayMessage(msg, false);
                return true;
    }

    // Find the held linker hand
    private static InteractionHand findHeldLinkerHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof ContraptionNetworkLinkerItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof ContraptionNetworkLinkerItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    // Open the screen
    public static void openScreen(InteractionHand hand, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ScreenOpener.open(new ContraptionNetworkLinkerScreen(hand, stack));
    }
}
