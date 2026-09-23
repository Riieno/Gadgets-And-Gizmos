package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffInteractionGuard;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipDockScheduler;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

// Request linker reconciliation after block and chunk changes instead of rescanning every tick
@EventBusSubscriber(modid = CreateThrusters.MOD_ID)
public final class ContraptionNetworkLinkerTrackerEvents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker tracker events
    private ContraptionNetworkLinkerTrackerEvents() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the post-server tick
    @SubscribeEvent
    public static void postServerTick(ServerTickEvent.Post event) {
        ShipCouplerService.pruneTopologyCaches(event.getServer());
        ContraptionNetworkLinkerTracker.get(event.getServer()).tick(event.getServer());
    }

    // Handle chunk loading
    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        requestReconciliation(event);
    }

    // Handle chunk unloading
    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        requestReconciliation(event);
    }

    // Handle level unloading
    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ShipCouplerService.clearLoadedCouplers(level);
        }
    }

    // Handle player login
    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        migratePlayerStorage(player, player.getInventory());
        migratePlayerStorage(player, player.getEnderChestInventory());
    }

    // Handle block removal
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer().level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos blockPos = event.getPos().immutable();
        SubLevel containing = Sable.HELPER.getContaining(level, blockPos);
        UUID subLevelId = containing == null ? null : containing.getUniqueId();
        String brokenBlockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();

        ControllerSqliteStore.removeLinkedBlockAssignments(level, blockPos, subLevelId, brokenBlockId);
        ContraptionNetworkLinkerTracker.get(level.getServer()).clearLinks(level,
                (target, worldCenter) -> ContraptionNetworkLinkerTracker.matchesBrokenTarget(
                        target, blockPos, subLevelId, brokenBlockId));
    }

    // Handle block placement
    @SubscribeEvent
    public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || CTBlocks.SHIP_CONTROL_MODULE == null
                || CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER == null) {
            return;
        }
        BlockPos modulePosition = event.getPos().below();
        boolean shipControlModule = event.getLevel().getBlockState(modulePosition)
                .is(CTBlocks.SHIP_CONTROL_MODULE.get());
        boolean slabModule = CTBlocks.ACC_DISPLAY_SLAB != null
                && event.getLevel().getBlockState(modulePosition)
                .is(CTBlocks.ACC_DISPLAY_SLAB.get());
        if (!shipControlModule && !slabModule) {
            return;
        }
        if (!event.getPlacedBlock().is(CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get())) {
            event.setCanceled(true);
        }
    }

    // Handle server shutdown
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStopping(ServerStoppingEvent event) {
        ShipCouplerService.clearLoadedCouplers(event.getServer());
        ControllerRuntimeObserver.clear(event.getServer());
        ShipDockScheduler.shutdown(event.getServer());
        ContraptionNetworkLinkerTracker.beginShutdownIfLoaded(event.getServer());
        AccDisplayControllerRegistry.beginShutdown(event.getServer());
        ShippingScheduleRuntime.closeAllForServer(event.getServer());
        ShipControlModuleRuntime.closeAllForServer(event.getServer());
        PhysicsStaffInteractionGuard.clearInitializationTargets();
        ContraptionNetworkLinkerSignalBus.clearAll();
        ContraptionNetworkLinkerSnapshotPayload.clearServerState();
        ShipDockRegistry.finishShutdown(event.getServer());
    }

    // Handle server shutdown
    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        ShipDockScheduler.finishShutdown(event.getServer());
        ShipDockRegistry.finishShutdown(event.getServer());
        ContraptionNetworkLinkerTracker.finishShutdown(event.getServer());
        ControllerSqliteStore.closeAll();
        AccDisplayControllerRegistry.finishShutdown(event.getServer());
    }

    // Migrate the player storage
    private static void migratePlayerStorage(ServerPlayer player, Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.getItem() instanceof ContraptionNetworkLinkerItem) {
                ContraptionNetworkLinkerData.migrateLegacyStorage(stack);
            } else {
                PortableContraptionControllerItem.migrateLegacyStorage(
                        stack, player.level(), player.blockPosition());
            }
        }
    }

    // Request the reconciliation
    private static void requestReconciliation(ChunkEvent evt) {
        if (evt.getLevel() instanceof ServerLevel level && level.getServer() != null) {
            ContraptionNetworkLinkerTracker.get(level.getServer()).requestReconciliation();
        }
    }
}
