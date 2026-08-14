package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingManifestRefreshPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Handle Shipping Manifest Client input and keep the affected state synchronized
public final class ShippingManifestClientHandler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Last requested pos
    private static BlockPos lastRequestedPos;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping manifest client handler
    private ShippingManifestClientHandler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the shipping manifest client handler
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if ((!CTConfigs.CLIENT.renderShippingManifestContents.get()
                && !CTConfigs.CLIENT.showShippingManifestGoggleTooltip.get())
                || minecraft.screen != null
                || minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult hitResult)
                || CTBlocks.SHIPPING_MANIFEST == null
                || !minecraft.level.getBlockState(hitResult.getBlockPos()).is(CTBlocks.SHIPPING_MANIFEST.get())) {
            lastRequestedPos = null;
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        minecraft.level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
        if (!pos.equals(lastRequestedPos)) {
            PacketDistributor.sendToServer(new ShippingManifestRefreshPayload(pos));
            lastRequestedPos = pos.immutable();
        }
    }

    // Handle the mouse scrolling event
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent evt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!CTConfigs.CLIENT.renderShippingManifestContents.get()
                || minecraft.screen != null
                || minecraft.level == null
                || minecraft.player == null
                || !minecraft.player.isShiftKeyDown()
                || !(minecraft.hitResult instanceof BlockHitResult hitResult)) {
            return;
        }

        BlockEntity blockEntity = minecraft.level.getChunkAt(hitResult.getBlockPos())
                .getBlockEntity(hitResult.getBlockPos(), LevelChunk.EntityCreationType.IMMEDIATE);
        if (!(blockEntity instanceof ShippingManifestBlockEntity manifest)) {
            return;
        }

        double delta = evt.getScrollDeltaY();
        if (delta != 0.0D) {
            manifest.scrollClientDisplay(delta > 0.0D ? -1 : 1);
        }
        evt.setCanceled(true);
    }
}
