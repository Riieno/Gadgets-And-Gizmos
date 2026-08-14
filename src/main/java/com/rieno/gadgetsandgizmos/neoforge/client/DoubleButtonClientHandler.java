package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlock;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.DoubleButtonHoldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

// Handle Double Button Client input and keep the affected state synchronized
public final class DoubleButtonClientHandler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active target
    private static MenuConfigTarget activeTarget;
    // Active button
    private static DoubleButtonBlockEntity.ButtonHalf activeButton;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the double button client handler
    private DoubleButtonClientHandler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Start the holding
    public static void startHolding(DoubleButtonBlockEntity blockEntity, DoubleButtonBlockEntity.ButtonHalf btn) {
        if (blockEntity.getResponseMode(btn) != DoubleButtonBlockEntity.ResponseMode.HOLD) {
            return;
        }
        activeTarget = MenuConfigTarget.of(blockEntity.getBlockPos(), SimulatedHelper.getContainingSubLevelId(blockEntity));
        activeButton = btn;
        sendHeld(true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the double button client handler
    public static void tick() {
        if (activeTarget == null || activeButton == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !minecraft.options.keyUse.isDown()) {
            stopHolding();
            return;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult hitResult)
                || hitResult.getType() == HitResult.Type.MISS
                || !hitResult.getBlockPos().equals(activeTarget.pos())) {
            stopHolding();
            return;
        }

        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                minecraft.level, activeTarget.subLevelId(), activeTarget.pos());
        if (!(blockEntity instanceof DoubleButtonBlockEntity doubleButton)
                || doubleButton.getResponseMode(activeButton) != DoubleButtonBlockEntity.ResponseMode.HOLD
                || !(DoubleButtonBlock.targetAt(doubleButton.getBlockState(), activeTarget.pos(), hitResult.getLocation())
                instanceof DoubleButtonBlock.Target.Button hitButton)
                || hitButton.button() != activeButton) {
            stopHolding();
            return;
        }

        sendHeld(true);
    }

    // Reset the double button client handler
    public static void reset() {
        activeTarget = null;
        activeButton = null;
    }

    // Stop the holding
    private static void stopHolding() {
        sendHeld(false);
        reset();
    }

    // Send the held
    private static void sendHeld(boolean held) {
        if (activeTarget != null && activeButton != null) {
            PacketDistributor.sendToServer(new DoubleButtonHoldPayload(activeTarget, activeButton, held));
        }
    }
}
