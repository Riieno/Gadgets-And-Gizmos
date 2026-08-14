package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.InputConstants;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlock;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletActionPayload;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;


// Route tablet clicks between held, placed and remote tablet surfaces
public final class DiagnosticTabletClientInteraction {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet client interaction
    private DiagnosticTabletClientInteraction() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the interaction key mapping triggered event
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered evt) {
        if (evt.isCanceled() || !evt.isUseItem()
                || !CTFeatureToggles.isItemEnabled("diagnostic_tablet")) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) return;
        InteractionHand hand = evt.getHand();
        ItemStack stack = player.getItemInHand(hand);
        boolean altDown = isAltDown();
        if (hand == InteractionHand.MAIN_HAND && !altDown
                && stack.getItem() instanceof DiagnosticTabletItem
                && DiagnosticTabletData.read(stack).mode() == TabletInteractionMode.STANDARD
                && minecraft.hitResult instanceof BlockHitResult
                && isNonStandardTablet(player.getOffhandItem())) {
            return;
        }
        if (!(stack.getItem() instanceof DiagnosticTabletItem)) {
            if (hand != InteractionHand.MAIN_HAND
                    || mainHandOwnsInteraction(player.getMainHandItem())
                    || !(player.getOffhandItem().getItem() instanceof DiagnosticTabletItem)) return;
            hand = InteractionHand.OFF_HAND;
            stack = player.getOffhandItem();
        }
        if (altDown) {
            if (!tryCycleMode(hand, stack)) return;
        } else {
            if (player.isShiftKeyDown()
                    || DiagnosticTabletData.read(stack).mode() != TabletInteractionMode.STANDARD
                    || isPointingAtPlacedTabletScreen(minecraft)) return;
            DiagnosticTabletScreen.openItem(hand, stack);
        }
        evt.setSwingHand(false);
        evt.setCanceled(true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Try to cycle mode
    public static boolean tryCycleMode(InteractionHand hand, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || stack == null || stack.isEmpty() || !isAltDown()
                || !CTFeatureToggles.isItemEnabled("diagnostic_tablet")) {
            return false;
        }
        DiagnosticTabletData.State current = DiagnosticTabletData.read(stack);
        TabletInteractionMode next = current.mode() == TabletInteractionMode.READER
                ? TabletInteractionMode.STANDARD : TabletInteractionMode.READER;
        DiagnosticTabletData.State updated = current.withMode(next, "");
        DiagnosticTabletData.write(stack, updated);
        PacketDistributor.sendToServer(new DiagnosticTabletActionPayload(false, hand,
                BlockPos.ZERO, null, updated.app(), updated.tab(), "mode_cycle", next.id()));

        return true;
    }

    // Handle the key input event
    public static void onKeyInput(InputEvent.Key evt) {
        if (evt.getKey() != GLFW.GLFW_KEY_ESCAPE || evt.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof DiagnosticTabletItem)) continue;
            DiagnosticTabletData.State state = DiagnosticTabletData.read(stack);
            if (state.mode() != TabletInteractionMode.READER) continue;
            DiagnosticTabletData.State updated = state.withMode(TabletInteractionMode.STANDARD, "");
            DiagnosticTabletData.write(stack, updated);
            PacketDistributor.sendToServer(new DiagnosticTabletActionPayload(false, hand,
                    BlockPos.ZERO, null, updated.app(), updated.tab(), "mode_cycle", updated.mode().id()));
            return;
        }
    }

    // Check if the Alt key is held
    private static boolean isAltDown() {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        return Screen.hasAltDown()
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    // Check if the pointing is at the placed tablet screen
    private static boolean isPointingAtPlacedTabletScreen(Minecraft minecraft) {
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return false;
        }
        DiagnosticTabletBlock.PlacedInteraction interaction =
                DiagnosticTabletBlock.resolveInteraction(minecraft.level, hit);
        return interaction != null && DiagnosticTabletBlock.isScreenHit(
                interaction.tablet().getBlockState(), interaction.tablet().getBlockPos(),
                interaction.hit());
    }

    // Check if the main hand owns the interaction
    private static boolean mainHandOwnsInteraction(ItemStack stack) {
        return stack.getItem() instanceof DiagnosticTabletItem
                || stack.getItem() instanceof ContraptionNetworkLinkerItem
                || (CTItems.POWERED_ZIPLINE != null && stack.is(CTItems.POWERED_ZIPLINE.get()));
    }

    // Check if this is non-standard tablet
    private static boolean isNonStandardTablet(ItemStack stack) {
        return stack.getItem() instanceof DiagnosticTabletItem
                && DiagnosticTabletData.read(stack).mode() != TabletInteractionMode.STANDARD;
    }
}
