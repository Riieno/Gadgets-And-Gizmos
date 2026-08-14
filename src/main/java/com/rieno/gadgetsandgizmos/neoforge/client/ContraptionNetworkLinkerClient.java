package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSyncPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

// Store linker snapshots and highlights received from the server
public final class ContraptionNetworkLinkerClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CATEGORY = "key.categories.createthrusters";
    private static final KeyMapping CYCLE_SCOPE_MODE_MODIFIER = new KeyMapping(
            "key.createthrusters.contraption_network_linker.cycle_scope_mode_modifier",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker client
    private ContraptionNetworkLinkerClient() {
    }

    // Register the key mappings
    public static void registerKeyMappings(RegisterKeyMappingsEvent evt) {
        evt.register(CYCLE_SCOPE_MODE_MODIFIER);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the interaction key mapping triggered event
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered evt) {

        if (!evt.isUseItem()) {
            return;
        }

        if (!attemptCycleScopeMode()) {
            return;
        }

        evt.setSwingHand(false);
        evt.setCanceled(true);
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

        if (!isCycleModifierDown()) {
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

    // Check if the cycle modifier is held
    private static boolean isCycleModifierDown() {
                Minecraft minecraft = Minecraft.getInstance();
                long window = minecraft.getWindow().getWindow();
                return CYCLE_SCOPE_MODE_MODIFIER.isDown()
                    || Screen.hasAltDown()
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
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
