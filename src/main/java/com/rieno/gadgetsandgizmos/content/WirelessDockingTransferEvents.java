package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

// Clear cached dock stock when a loaded block or chunk can change an endpoint
@EventBusSubscriber(modid = CreateThrusters.MOD_ID)
final class WirelessDockingTransferEvents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the wireless docking transfer events
    private WirelessDockingTransferEvents() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle block placement
    @SubscribeEvent
    public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level) {
            WirelessDockingTransfer.invalidateDockStock(level, event.getPos());
        }
    }

    // Handle block removal
    @SubscribeEvent
    public static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level) {
            DockingConnectorAutomation.unregisterShipDockConnector(level, event.getPos());
            WirelessDockingTransfer.invalidateDockStock(level, event.getPos());
        }
    }

    // Handle the neighbours changed
    @SubscribeEvent
    public static void neighboursChanged(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof Level level) {
            WirelessDockingTransfer.invalidateDockStock(level, event.getPos());
        }
    }

    // Handle chunk loading
    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof Level level) {
            WirelessDockingTransfer.invalidateDockStock(level);
        }
    }

    // Handle chunk unloading
    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            WirelessDockingTransfer.invalidateDockStock(level);
        }
    }
}
