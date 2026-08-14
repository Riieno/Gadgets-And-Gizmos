package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherAnchorControlPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherInputPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherPowerModePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Handle Entity Launcher client input and keep its state synchronized
public final class EntityLauncherClientInputHandler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Last jump held state
    private static boolean lastJumpHeld;
    // Tracks whether this was holding launcher
    private static boolean wasHoldingLauncher;
    // Last mounted use held state
    private static boolean lastMountedUseHeld;
    // Last dismount held state
    private static boolean lastDismountHeld;
    // Aim sync tick count
    private static int aimSyncTicks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher client input handler
    private EntityLauncherClientInputHandler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the entity launcher client input handler
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            resetState();
            return;
        }

        BlockPos mountedAnchorPos = getMountedAnchorPos(minecraft, player);
        if (mountedAnchorPos != null) {
            tickMountedControls(minecraft, player, mountedAnchorPos);
        } else {
            lastMountedUseHeld = false;
            lastDismountHeld = false;
            aimSyncTicks = 0;
        }

        boolean holdingLauncher = isHoldingLauncher(player);
        boolean jumpHeld = holdingLauncher && minecraft.options.keyJump.isDown();
        boolean jumpPressed = holdingLauncher && jumpHeld && !lastJumpHeld;
        if (holdingLauncher && (jumpPressed || jumpHeld != lastJumpHeld || !wasHoldingLauncher)) {
            PacketDistributor.sendToServer(new ServerboundEntityLauncherInputPacket(jumpHeld, jumpPressed));
        } else if (!holdingLauncher && wasHoldingLauncher && lastJumpHeld) {
            PacketDistributor.sendToServer(new ServerboundEntityLauncherInputPacket(false, false));
        }

        lastJumpHeld = jumpHeld;
        wasHoldingLauncher = holdingLauncher;
    }

    // Handle the interaction key mapping triggered event
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered evt) {
        if (!evt.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (getMountedAnchorPos(minecraft, minecraft.player) != null) {
            evt.setSwingHand(false);
            evt.setCanceled(true);
            return;
        }
        if (!Screen.hasAltDown()) {
            return;
        }
        InteractionHand launcherHand = preferredLauncherHand(minecraft.player);
        if (launcherHand == null || evt.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PacketDistributor.sendToServer(new ServerboundEntityLauncherPowerModePacket(launcherHand));
        evt.setSwingHand(false);
        evt.setCanceled(true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the preferred launcher hand
    private static InteractionHand preferredLauncherHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof EntityLauncherItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof EntityLauncherItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    // Update the mounted controls
    private static void tickMountedControls(Minecraft minecraft, Player player, BlockPos pos) {
        boolean useHeld = minecraft.options.keyUse.isDown();
        boolean dismountHeld = minecraft.options.keySprint.isDown();

        if (useHeld && !lastMountedUseHeld) {
            sendMountedControl(pos, ServerboundEntityLauncherAnchorControlPacket.USE_PRESSED, player);
        } else if (!useHeld && lastMountedUseHeld) {
            sendMountedControl(pos, ServerboundEntityLauncherAnchorControlPacket.USE_RELEASED, player);
        }
        if (dismountHeld && !lastDismountHeld) {
            sendMountedControl(pos, ServerboundEntityLauncherAnchorControlPacket.DISMOUNT, player);
        }

        aimSyncTicks++;
        if (aimSyncTicks >= 2) {
            aimSyncTicks = 0;
            sendMountedControl(pos, ServerboundEntityLauncherAnchorControlPacket.AIM, player);
        }
        lastMountedUseHeld = useHeld;
        lastDismountHeld = dismountHeld;
    }

    // Send the mounted control
    private static void sendMountedControl(BlockPos pos, int action, Player player) {
        PacketDistributor.sendToServer(new ServerboundEntityLauncherAnchorControlPacket(
                pos, action, player.getXRot(), player.getYRot()));
    }

    // Get the mounted anchor pos
    private static BlockPos getMountedAnchorPos(Minecraft minecraft, Player player) {
        if (!player.isPassenger() || !(player.getVehicle() instanceof ArmorStand mount)
                || !mount.isInvisible() || minecraft.level == null) {
            return null;
        }
        return mount.blockPosition().immutable();
    }

    // Check if the player is holding the launcher
    private static boolean isHoldingLauncher(Player player) {
        return player.getMainHandItem().getItem() instanceof EntityLauncherItem
                || player.getOffhandItem().getItem() instanceof EntityLauncherItem;
    }

    // Reset the state
    private static void resetState() {
        lastJumpHeld = false;
        wasHoldingLauncher = false;
        lastMountedUseHeld = false;
        lastDismountHeld = false;
        aimSyncTicks = 0;
    }
}
