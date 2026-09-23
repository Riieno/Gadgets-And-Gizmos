package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.DockingConnectorBindingAccess;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

// Read docking connector services and run their item, fluid and energy transfers
public final class DockingConnectorAutomation {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the docking connector automation
    private DockingConnectorAutomation() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a docking connector item
    public static boolean isDockingConnectorItem(@Nullable ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BlockItem item)) return false;
        return ResourceLocation.fromNamespaceAndPath("simulated", "docking_connector")
                .equals(BuiltInRegistries.BLOCK.getKey(item.getBlock()));
    }

    // Bind a connector to a Ship Dock
    public static void bindToShipDock(
            @Nullable BlockEntity connector,
            @Nullable UUID dockId,
            String dockName,
            int connectorIndex
    ) {
        if (connector instanceof DockingConnectorBindingAccess access && dockId != null
                && connector.getLevel() != null && !connector.getLevel().isClientSide) {
            access.createthrusters$setShipDockBinding(dockId, dockName, connectorIndex);
        }
    }

    // Clear a Ship Dock binding from a connector
    public static void clearShipDockBinding(@Nullable BlockEntity connector, @Nullable UUID dockId) {
        if (connector instanceof DockingConnectorBindingAccess access && dockId != null
                && connector.getLevel() != null && !connector.getLevel().isClientSide) {
            access.createthrusters$clearShipDockBinding(dockId);
        }
    }

    // Remove a broken connector from every Ship Dock that registered it
    public static void unregisterShipDockConnector(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null || level.isClientSide || level.getServer() == null) {
            return;
        }
        BlockEntity connector = level.getBlockEntity(pos);
        if (!isDockingConnector(connector)) {
            return;
        }
        ShipDockRegistry.get(level.getServer()).removeLinkedDockingConnector(
                level.dimension().location(),
                SimulatedHelper.getContainingSubLevelId(connector),
                pos);
    }

    // Bind a connector to a Ship Control Module
    public static void bindToShipControlModule(
            @Nullable BlockEntity connector,
            String shipName,
            int connectorIndex
    ) {
        if (connector instanceof DockingConnectorBindingAccess access
                && connector.getLevel() != null && !connector.getLevel().isClientSide) {
            access.createthrusters$setShipControlModuleBinding(shipName, connectorIndex);
        }
    }

    // Clear a Ship Control Module binding from a connector
    public static void clearShipControlModuleBinding(@Nullable BlockEntity connector) {
        if (connector instanceof DockingConnectorBindingAccess access
                && connector.getLevel() != null && !connector.getLevel().isClientSide) {
            access.createthrusters$clearShipControlModuleBinding();
        }
    }
    // Resolve the docking connector automation
    public static @Nullable DockingConnectorBlockEntity resolve(
            @Nullable Level level,
            @Nullable UUID subLevelId,
            @Nullable BlockPos pos
    ) {
        if (level == null || pos == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, subLevelId, pos);
        return blockEntity instanceof DockingConnectorBlockEntity connector
                ? connector : null;
    }

    // Engage the docking connectors
    public static boolean engage(@Nullable BlockEntity ship, @Nullable BlockEntity dock) {
        boolean shipPowered = setPowered(ship, true);
        boolean dockPowered = setPowered(dock, true);
        return shipPowered && dockPowered;
    }

    // Disengage the docking connector
    public static void disengage(@Nullable BlockEntity ship, @Nullable BlockEntity dock) {
        Set<DockingConnectorBlockEntity> endpoints =
                Collections.newSetFromMap(new IdentityHashMap<>());
        collectEndpoint(endpoints, ship);
        collectEndpoint(endpoints, dock);
        for (DockingConnectorBlockEntity endpoint : new ArrayList<>(endpoints)) {
            if (!endpoint.isRemoved() && endpoint.getLevel() != null) {
                collectEndpoint(endpoints, endpoint.getOtherConnector());
            }
            collectStoredEndpoint(endpoints, endpoint);
        }
        for (DockingConnectorBlockEntity endpoint : endpoints) {
            setPowered(endpoint, false);
        }
        for (DockingConnectorBlockEntity endpoint : endpoints) {
            if (!canMutate(endpoint)) {
                continue;
            }
            endpoint.unDock();
            setPowered(endpoint, false);
        }
    }

    // Collect the endpoint
    private static void collectEndpoint(
            Set<DockingConnectorBlockEntity> endpoints,
            @Nullable BlockEntity candidate
    ) {
        if (candidate instanceof DockingConnectorBlockEntity connector) {
            endpoints.add(connector);
        }
    }

    // Collect the stored endpoint
    private static void collectStoredEndpoint(
            Set<DockingConnectorBlockEntity> endpoints,
            DockingConnectorBlockEntity connector
    ) {
        BlockPos pos = connector.otherConnectorPosition;
        if (pos == null || connector.getLevel() == null) {
            return;
        }
        collectEndpoint(endpoints, SimulatedHelper.findLoadedBlockEntityExact(
                connector.getLevel(), connector.otherConnectorSubLevelId, pos));
    }

    // Check if this is locked pair
    public static boolean isLockedPair(@Nullable BlockEntity ship, @Nullable BlockEntity dock) {
        return ship instanceof DockingConnectorBlockEntity shipConnector
                && dock instanceof DockingConnectorBlockEntity dockConnector
                && shipConnector.isLocked()
                && dockConnector.isLocked()
                && isMagneticCapturePair(shipConnector, dockConnector);
    }

    // Check if this is a reciprocal native magnetic capture pair
    public static boolean isMagneticCapturePair(@Nullable BlockEntity ship, @Nullable BlockEntity dock) {
        return ship instanceof DockingConnectorBlockEntity shipConnector
                && dock instanceof DockingConnectorBlockEntity dockConnector
                && !shipConnector.isRemoved()
                && !dockConnector.isRemoved()
                && shipConnector.getLevel() != null
                && dockConnector.getLevel() != null
                && shipConnector.hasOtherConnector()
                && dockConnector.hasOtherConnector()
                && shipConnector.getOtherConnector() == dockConnector
                && dockConnector.getOtherConnector() == shipConnector;
    }

    // Check if this is locked
    public static boolean isLocked(@Nullable BlockEntity connector) {
        return connector instanceof DockingConnectorBlockEntity dockingConnector
                && dockingConnector.isLocked();
    }

    // Check if this has connection state
    public static boolean hasConnectionState(@Nullable BlockEntity connector) {
        if (!(connector instanceof DockingConnectorBlockEntity dockingConnector)
                || dockingConnector.isRemoved() || dockingConnector.getLevel() == null) {
            return false;
        }
        return dockingConnector.isLocked() || dockingConnector.hasOtherConnector();
    }

    // Set the powered
    public static boolean setPowered(@Nullable BlockEntity connector, boolean powered) {
        if (!(connector instanceof DockingConnectorBlockEntity dockingConnector)
                || !canMutate(dockingConnector)) {
            return false;
        }
        BlockState state = dockingConnector.getBlockState();
        if (!state.hasProperty(BlockStateProperties.POWERED)) {
            return false;
        }
        if (state.getValue(BlockStateProperties.POWERED) != powered) {
            dockingConnector.getLevel().setBlock(
                    dockingConnector.getBlockPos(),
                    state.setValue(BlockStateProperties.POWERED, powered),
                    3);
            dockingConnector.setChanged();
        }
        return true;
    }

    // Check if this is a docking connector
    public static boolean isDockingConnector(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof DockingConnectorBlockEntity;
    }

    // Check if the docking connector can be changed on this side
    private static boolean canMutate(DockingConnectorBlockEntity connector) {
        return connector != null && !connector.isRemoved()
                && connector.getLevel() != null && !connector.getLevel().isClientSide;
    }
}
