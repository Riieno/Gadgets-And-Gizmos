package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Move docked resources through named wireless runs while keeping every handler deduplicated
public final class WirelessDockingTransfer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Set<MinecraftServer> ENABLED_SERVERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MinecraftServer,
            Map<AdvancedContraptionControllerBlockEntity,
                    Map<ConnectorKey, RoutedNetworkAccess>>> NETWORKS =
            new WeakHashMap<>();
    private static final Map<BlockEntity, DockStockSnapshot> DOCK_STOCK_ACCESS =
            new WeakHashMap<>();
    private static final int DOCK_STOCK_RADIUS = 16;
    private static final long DOCK_STOCK_SAFETY_REFRESH_TICKS = 20L;
    private static final ThreadLocal<Boolean> ROUTE_OPERATION_ACTIVE =
            ThreadLocal.withInitial(() -> false);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the wireless docking transfer
    private WirelessDockingTransfer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is enabled
    public static synchronized boolean isEnabled(MinecraftServer server) {
        return server != null && ENABLED_SERVERS.contains(server);
    }

    // Check if the set is enabled
    public static synchronized boolean setEnabled(MinecraftServer server, boolean enabled) {
        if (server == null) {
            return false;
        }
        if (enabled) {
            ENABLED_SERVERS.add(server);
        } else {
            ENABLED_SERVERS.remove(server);
        }
        return enabled;
    }

    // Toggle wireless docking transfers
    public static synchronized boolean toggle(MinecraftServer server) {
        return setEnabled(server, !isEnabled(server));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the server started event
    public static void onServerStarted(ServerStartedEvent evt) {
        setEnabled(evt.getServer(), true);
    }

    // Handle the server stopped event
    public static synchronized void onServerStopped(ServerStoppedEvent evt) {
        clear(evt.getServer());
    }

    // Publish the networks
    public static synchronized void publishNetworks(
            AdvancedContraptionControllerBlockEntity controller,
            List<ConnectorNetwork> connectors
    ) {
        MinecraftServer server = server(controller == null ? null : controller.getLevel());
        if (server == null || controller == null) {
            return;
        }
        Map<ConnectorKey, RoutedNetworkAccess> published =
                new LinkedHashMap<>();
        ResourceLocation dimension = controller.getLevel().dimension().location();
        for (ConnectorNetwork connector : connectors == null ? List.<ConnectorNetwork>of() : connectors) {
            if (connector == null || connector.subLevelId() == null
                    || connector.position() == null || connector.isEmpty()) {
                continue;
            }
            published.put(new ConnectorKey(
                    dimension, connector.subLevelId(), connector.position()),
                    new RoutedNetworkAccess(connector.itemAccess(), connector.fluidAccess(),
                            connector.energyAccess(), connector.fuelAccess()));
        }
        NETWORKS.computeIfAbsent(server, ignored -> new WeakHashMap<>())
                .put(controller, Map.copyOf(published));
    }

    // Clear the controller
    public static synchronized void clearController(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        MinecraftServer server = server(controller == null ? null : controller.getLevel());
        Map<AdvancedContraptionControllerBlockEntity,
                Map<ConnectorKey, RoutedNetworkAccess>> byController =
                server == null ? null : NETWORKS.get(server);
        if (byController == null) {
            return;
        }
        byController.remove(controller);
        if (byController.isEmpty()) {
            NETWORKS.remove(server);
        }
    }

    // Check if the connectors route items
    public static boolean routesItems(BlockEntity stationConnector, BlockEntity shipConnector) {
        ShipStockNetworkCache.NetworkAccess access = routedAccess(
                stationConnector, shipConnector, RouteType.ITEM);
        return access != null && !access.items().isEmpty();
    }

    // Check if the connectors route fluids
    public static boolean routesFluids(BlockEntity stationConnector, BlockEntity shipConnector) {
        ShipStockNetworkCache.NetworkAccess access = routedAccess(
                stationConnector, shipConnector, RouteType.FLUID);
        return access != null && !access.fluids().isEmpty();
    }

    // Check if the connectors route energy
    public static boolean routesEnergy(BlockEntity stationConnector, BlockEntity shipConnector) {
        ShipStockNetworkCache.NetworkAccess access = routedAccess(
                stationConnector, shipConnector, RouteType.ENERGY);
        return access != null && !access.energy().isEmpty();
    }

    // Insert the item
    public static ItemStack insertItem(
            BlockEntity stationConnector,
            BlockEntity shipConnector,
            ItemStack offered,
            boolean simulate
    ) {
        if (!beginRouteOperation()) {
            return offered == null ? ItemStack.EMPTY : offered;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.ITEM);
            if (access == null || offered == null || offered.isEmpty()) {
                return offered == null ? ItemStack.EMPTY : offered;
            }
            ItemStack remainder = offered.copy();
            for (IItemHandler handler : access.items()) {
                for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
                    remainder = handler.insertItem(slot, remainder, simulate);
                }
                if (remainder.isEmpty()) {
                    break;
                }
            }
            return remainder;
        } finally {
            endRouteOperation();
        }
    }

    // Extract the item
    public static ItemStack extractItem(
            BlockEntity stationConnector,
            BlockEntity shipConnector,
            @Nullable ItemStack requested,
            int amount,
            boolean simulate
    ) {
        if (!beginRouteOperation()) {
            return ItemStack.EMPTY;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.ITEM);
            if (access == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = ItemStack.EMPTY;
            for (IItemHandler handler : access.items()) {
                for (int slot = 0; slot < handler.getSlots() && extracted.getCount() < amount; slot++) {
                    ItemStack available = handler.getStackInSlot(slot);
                    if (available.isEmpty()
                            || !matchesRequestedItem(requested, extracted, available)) {
                        continue;
                    }
                    ItemStack part = handler.extractItem(
                            slot, amount - extracted.getCount(), simulate);
                    if (part.isEmpty()) {
                        continue;
                    }
                    if (extracted.isEmpty()) {
                        extracted = part.copy();
                    } else {
                        extracted.grow(part.getCount());
                    }
                }
                if (extracted.getCount() >= amount) {
                    break;
                }
            }
            return extracted;
        } finally {
            endRouteOperation();
        }
    }

    // Get the peek item
    public static ItemStack peekItem(
            BlockEntity stationConnector,
            BlockEntity shipConnector
    ) {
        if (!beginRouteOperation()) {
            return ItemStack.EMPTY;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.ITEM);
            if (access == null) {
                return ItemStack.EMPTY;
            }
            for (IItemHandler handler : access.items()) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!stack.isEmpty()) {
                        return stack.copy();
                    }
                }
            }
            return ItemStack.EMPTY;
        } finally {
            endRouteOperation();
        }
    }

    // Insert the fluid
    public static int insertFluid(
            BlockEntity stationConnector,
            BlockEntity shipConnector,
            FluidStack offered,
            boolean simulate
    ) {
        if (!beginRouteOperation()) {
            return 0;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.FLUID);
            if (access == null || offered == null || offered.isEmpty()) {
                return 0;
            }
            int remaining = offered.getAmount();
            int accepted = 0;
            for (IFluidHandler handler : access.fluids()) {
                if (remaining <= 0) {
                    break;
                }
                int inserted = handler.fill(
                        offered.copyWithAmount(remaining),
                        simulate ? IFluidHandler.FluidAction.SIMULATE
                                : IFluidHandler.FluidAction.EXECUTE);
                inserted = Math.max(0, Math.min(remaining, inserted));
                accepted += inserted;
                remaining -= inserted;
            }
            return accepted;
        } finally {
            endRouteOperation();
        }
    }

    // Extract the fluid
    public static FluidStack extractFluid(
            BlockEntity stationConnector,
            BlockEntity shipConnector,
            @Nullable FluidStack requested,
            int amount,
            boolean simulate
    ) {
        if (!beginRouteOperation()) {
            return FluidStack.EMPTY;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.FLUID);
            if (access == null || amount <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack target = requested == null || requested.isEmpty()
                    ? firstFluid(access.fluids()) : requested.copy();
            if (target.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidStack extracted = FluidStack.EMPTY;
            IFluidHandler.FluidAction action = simulate
                    ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
            for (IFluidHandler handler : access.fluids()) {
                if (extracted.getAmount() >= amount) {
                    break;
                }
                int remaining = amount - extracted.getAmount();
                FluidStack part = handler.drain(target.copyWithAmount(remaining), action);
                if (part.isEmpty()
                        || !FluidStack.isSameFluidSameComponents(target, part)) {
                    continue;
                }
                if (extracted.isEmpty()) {
                    extracted = part.copy();
                } else {
                    extracted.grow(part.getAmount());
                }
            }
            return extracted;
        } finally {
            endRouteOperation();
        }
    }

    // Get the first fluid
    private static FluidStack firstFluid(List<IFluidHandler> handlers) {
        for (IFluidHandler handler : handlers) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty()) {
                    return stack.copyWithAmount(1);
                }
            }
        }
        return FluidStack.EMPTY;
    }

    // Get the peek fluid
    public static FluidStack peekFluid(
            BlockEntity stationConnector,
            BlockEntity shipConnector
    ) {
        if (!beginRouteOperation()) {
            return FluidStack.EMPTY;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.FLUID);
            if (access == null) {
                return FluidStack.EMPTY;
            }
            FluidStack visible = FluidStack.EMPTY;
            long amount = 0L;
            for (IFluidHandler handler : access.fluids()) {
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    FluidStack stack = handler.getFluidInTank(tank);
                    if (stack.isEmpty() || (!visible.isEmpty()
                            && !FluidStack.isSameFluidSameComponents(visible, stack))) {
                        continue;
                    }
                    if (visible.isEmpty()) {
                        visible = stack.copy();
                    }
                    amount += stack.getAmount();
                }
            }
            return visible.isEmpty() ? FluidStack.EMPTY
                    : visible.copyWithAmount((int) Math.min(Integer.MAX_VALUE, amount));
        } finally {
            endRouteOperation();
        }
    }

    // Insert the energy
    public static int insertEnergy(
            BlockEntity stationConnector,
            BlockEntity shipConnector,
            int offered,
            boolean simulate
    ) {
        if (!beginRouteOperation()) {
            return 0;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.ENERGY);
            if (access == null || offered <= 0) {
                return 0;
            }
            int remaining = offered;
            int accepted = 0;
            for (IEnergyStorage handler : access.energy()) {
                if (remaining <= 0) {
                    break;
                }
                int inserted = Math.max(0, Math.min(
                        remaining, handler.receiveEnergy(remaining, simulate)));
                accepted += inserted;
                remaining -= inserted;
            }
            return accepted;
        } finally {
            endRouteOperation();
        }
    }

    // Extract the energy
    public static int extractEnergy(
            BlockEntity stationConnector,
            BlockEntity shipConnector,
            int requested,
            boolean simulate
    ) {
        if (!beginRouteOperation()) {
            return 0;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.ENERGY);
            if (access == null || requested <= 0) {
                return 0;
            }
            int remaining = requested;
            int extracted = 0;
            for (IEnergyStorage handler : access.energy()) {
                if (remaining <= 0) {
                    break;
                }
                int part = Math.max(0, Math.min(
                        remaining, handler.extractEnergy(remaining, simulate)));
                extracted += part;
                remaining -= part;
            }
            return extracted;
        } finally {
            endRouteOperation();
        }
    }

    // Get the stored energy
    public static int storedEnergy(
            BlockEntity stationConnector,
            BlockEntity shipConnector
    ) {
        if (!beginRouteOperation()) {
            return 0;
        }
        try {
            ShipStockNetworkCache.NetworkAccess access = routedAccess(
                    stationConnector, shipConnector, RouteType.ENERGY);
            if (access == null) {
                return 0;
            }
            long stored = 0L;
            for (IEnergyStorage handler : access.energy()) {
                stored += Math.max(0, handler.getEnergyStored());
            }
            return (int) Math.min(Integer.MAX_VALUE, stored);
        } finally {
            endRouteOperation();
        }
    }

    // Clear the wireless docking transfer
    public static synchronized void clear(MinecraftServer server) {
        if (server != null) {
            ENABLED_SERVERS.remove(server);
            NETWORKS.remove(server);
            DOCK_STOCK_ACCESS.clear();
        }
    }

    // Get the nearby dock stock
    public static synchronized ShipStockNetworkCache.NetworkAccess nearbyDockStock(
            @Nullable BlockEntity stationConnector
    ) {
        if (stationConnector == null || stationConnector.getLevel() == null
                || stationConnector.isRemoved()) {
            return emptyAccess();
        }
        MinecraftServer server = server(stationConnector.getLevel());
        if (server == null || !isEnabled(server)) {
            return emptyAccess();
        }
        ShipDockRegistry.Dock dock = ShipDockRegistry.get(server).dockForConnector(
                stationConnector.getLevel().dimension().location(),
                SimulatedHelper.getContainingSubLevelId(stationConnector),
                stationConnector.getBlockPos());
        if (dock == null) {
            return emptyAccess();
        }
        BlockEntity shipDock = SimulatedHelper.findLoadedBlockEntityExact(
                stationConnector.getLevel(), dock.subLevelId(), dock.pos());
        return nearbyDockStock(stationConnector, shipDock);
    }

    // Get the nearby dock stock
    static synchronized ShipStockNetworkCache.NetworkAccess nearbyDockStock(
            @Nullable BlockEntity stationConnector,
            @Nullable BlockEntity shipDock
    ) {
        if (stationConnector == null || stationConnector.getLevel() == null
                || stationConnector.isRemoved() || !(shipDock instanceof ShipDockBlockEntity)
                || shipDock.getLevel() == null || shipDock.isRemoved()) {
            return emptyAccess();
        }
        MinecraftServer server = server(stationConnector.getLevel());
        if (server == null || server != server(shipDock.getLevel()) || !isEnabled(server)) {
            return emptyAccess();
        }
        long gameTime = shipDock.getLevel().getGameTime();
        DockStockSnapshot cached = DOCK_STOCK_ACCESS.get(shipDock);
        if (cached != null && gameTime >= cached.gameTime()
                && gameTime - cached.gameTime() < DOCK_STOCK_SAFETY_REFRESH_TICKS) {
            return cached.access();
        }
        ShipStockNetworkCache.NetworkAccess access = discoverDockStock(
                shipDock, DOCK_STOCK_RADIUS);
        DOCK_STOCK_ACCESS.put(shipDock, new DockStockSnapshot(gameTime, access));
        return access;
    }

    // Begin the route operation
    private static boolean beginRouteOperation() {
        if (ROUTE_OPERATION_ACTIVE.get()) {
            return false;
        }
        ROUTE_OPERATION_ACTIVE.set(true);
        return true;
    }

    // End the route operation
    private static void endRouteOperation() {
        ROUTE_OPERATION_ACTIVE.remove();
    }

    // Check if this matches requested item
    private static boolean matchesRequestedItem(
            @Nullable ItemStack requested,
            ItemStack extracted,
            ItemStack available
    ) {
        if (requested != null && !requested.isEmpty()
                && !ItemStack.isSameItemSameComponents(requested, available)) {
            return false;
        }
        return extracted.isEmpty()
                || ItemStack.isSameItemSameComponents(extracted, available);
    }

    // Get the routed access
    private static synchronized @Nullable ShipStockNetworkCache.NetworkAccess routedAccess(
            BlockEntity stationConnector,
            BlockEntity shipConnector,
            RouteType routeType
    ) {
        if (stationConnector == null || shipConnector == null
                || stationConnector.getLevel() == null || shipConnector.getLevel() == null) {
            return null;
        }
        MinecraftServer server = server(stationConnector.getLevel());
        if (!isEnabled(server) || server != server(shipConnector.getLevel())) {
            return null;
        }
        ShipDockRegistry registry = ShipDockRegistry.get(server);
        BlockEntity dockConnector = registeredDockConnector(registry, stationConnector, shipConnector);
        if (dockConnector == null) {
            return null;
        }
        BlockEntity shipSideConnector = dockConnector == stationConnector
                ? shipConnector : stationConnector;
        UUID dockSubLevelId = SimulatedHelper.getContainingSubLevelId(dockConnector);
        boolean refuelingConnector = registry.isRefuelingConnector(
                dockConnector.getLevel().dimension().location(), dockSubLevelId,
                dockConnector.getBlockPos());
        ConnectorKey key = connectorKey(shipSideConnector);
        Map<AdvancedContraptionControllerBlockEntity,
                Map<ConnectorKey, RoutedNetworkAccess>> byController =
                NETWORKS.get(server);
        if (key == null) {
            return null;
        }
        List<ShipStockNetworkCache.NetworkAccess> routedAccesses = new ArrayList<>();
        if (byController != null) {
            for (Map<ConnectorKey, RoutedNetworkAccess> networks
                    : new ArrayList<>(byController.values())) {
                RoutedNetworkAccess published = networks.get(key);
                ShipStockNetworkCache.NetworkAccess access = published == null ? null
                        : published.access(routeType, refuelingConnector);
                if (access != null && !access.isEmpty()) {
                    routedAccesses.add(access);
                }
            }
        }
        ShipStockNetworkCache.NetworkAccess dockStock = nearbyDockStock(dockConnector);
        ShipStockNetworkCache.NetworkAccess combined = mergeAccess(
                routeAccess(routedAccesses, routeType), dockStock, routeType);
        return combined.isEmpty() ? null : combined;
    }

    // Get the registered dock connector
    private static @Nullable BlockEntity registeredDockConnector(
            ShipDockRegistry registry,
            BlockEntity first,
            BlockEntity second
    ) {
        if (isRegisteredDockConnector(registry, first)) {
            return first;
        }
        return isRegisteredDockConnector(registry, second) ? second : null;
    }

    // Check if this is a registered dock connector
    private static boolean isRegisteredDockConnector(
            ShipDockRegistry registry,
            BlockEntity connector
    ) {
        return connector != null && connector.getLevel() != null
                && registry.isAssignedConnector(
                connector.getLevel().dimension().location(),
                SimulatedHelper.getContainingSubLevelId(connector), connector.getBlockPos());
    }

    // Discover the dock stock
    private static ShipStockNetworkCache.NetworkAccess discoverDockStock(
            BlockEntity shipDock, int radius) {
        Level level = shipDock.getLevel();
        if (level == null) return emptyAccess();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(shipDock);
        Set<UUID> frequencies = new java.util.LinkedHashSet<>();
        for (Direction dir : Direction.values()) {
            BlockEntity adjacent = SimulatedHelper.findLoadedBlockEntityExact(
                    level, subLevelId, shipDock.getBlockPos().relative(dir));
            if (adjacent instanceof PackagerLinkBlockEntity stockLink
                    && activeStockLink(stockLink)) {
                frequencies.add(stockLink.behaviour.freqId);
            }
        }
        if (frequencies.isEmpty()) return emptyAccess();

        Set<IItemHandler> items = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<IFluidHandler> fluids = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<IEnergyStorage> energy = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (BlockEntity candidate : nearbyBlockEntities(shipDock, radius)) {
            if (!(candidate instanceof PackagerLinkBlockEntity stockLink)
                    || !activeStockLink(stockLink)
                    || !frequencies.contains(stockLink.behaviour.freqId)) {
                continue;
            }
            keepAlive(stockLink);
            addPackagerTarget(stockLink, shipDock, radius, items);
            for (Direction dir : Direction.values()) {
                BlockEntity adjacent = SimulatedHelper.findLoadedBlockEntityExact(
                        level, subLevelId, stockLink.getBlockPos().relative(dir));
                addCapabilities(adjacent, items, fluids, energy);
            }
        }
        return new ShipStockNetworkCache.NetworkAccess(
                List.copyOf(items), List.copyOf(fluids), List.copyOf(energy));
    }

    // Get the nearby block entities
    private static List<BlockEntity> nearbyBlockEntities(BlockEntity origin, int radius) {
        Level level = origin.getLevel();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(origin);
        Object subLevel = SimulatedHelper.getContainingSubLevel(origin);
        List<BlockEntity> candidates = new ArrayList<>();
        if (subLevel != null) {
            candidates.addAll(SubLevelBlockEntityCollector.getBlockEntities(subLevel));
        } else {
            int chunkRadius = Math.max(1, (radius + 15) / 16);
            candidates.addAll(SubLevelBlockEntityCollector.getLoadedWorldBlockEntities(
                    level, origin.getBlockPos(), chunkRadius));
        }
        return candidates.stream()
                .filter(candidate -> !candidate.isRemoved())
                .filter(candidate -> java.util.Objects.equals(
                        SimulatedHelper.getContainingSubLevelId(candidate), subLevelId))
                .filter(candidate -> candidate.getBlockPos().distSqr(origin.getBlockPos())
                        <= (double) radius * radius)
                .toList();
    }

    // Invalidate the dock stock
    static synchronized void invalidateDockStock(@Nullable Level level) {
        if (level == null) {
            return;
        }
        DOCK_STOCK_ACCESS.keySet().removeIf(blockEntity ->
                blockEntity == null || blockEntity.isRemoved()
                        || blockEntity.getLevel() == level);
    }

    // Invalidate the dock stock
    static synchronized void invalidateDockStock(
            @Nullable Level level,
            @Nullable BlockPos changedPosition
    ) {
        if (level == null || changedPosition == null) {
            return;
        }
        double range = DOCK_STOCK_RADIUS + 2.0D;
        double rangeSquared = range * range;
        DOCK_STOCK_ACCESS.keySet().removeIf(blockEntity ->
                blockEntity == null || blockEntity.isRemoved()
                        || blockEntity.getLevel() == level
                        && blockEntity.getBlockPos().distSqr(changedPosition) <= rangeSquared);
    }

    // Add the packager target
    private static void addPackagerTarget(
            PackagerLinkBlockEntity stockLink,
            BlockEntity origin,
            int radius,
            Set<IItemHandler> items) {
        try {
            PackagerBlockEntity packager = stockLink.getPackager();
            if (packager == null || packager.targetInventory == null
                    || packager.getBlockPos().distSqr(origin.getBlockPos())
                    > (double) radius * radius) {
                return;
            }
            IItemHandler target = packager.targetInventory.getInventory();
            if (target != null) items.add(target);
        } catch (RuntimeException ignored) {
        }
    }

    // Add the capabilities
    private static void addCapabilities(
            @Nullable BlockEntity blockEntity,
            Set<IItemHandler> items,
            Set<IFluidHandler> fluids,
            Set<IEnergyStorage> energy) {
        if (blockEntity == null || blockEntity instanceof PackagerBlockEntity
                || DockingConnectorAutomation.isDockingConnector(blockEntity)
                || blockEntity instanceof ShipDockBlockEntity) {
            return;
        }
        Level level = blockEntity.getLevel();
        if (level == null) return;
        IItemHandler item = level.getCapability(Capabilities.ItemHandler.BLOCK,
                blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, null);
        IFluidHandler fluid = level.getCapability(Capabilities.FluidHandler.BLOCK,
                blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, null);
        if (item != null) items.add(item);
        if (fluid != null) fluids.add(fluid);
        addEnergyCapabilities(level, blockEntity, energy);
    }

    // Add the energy capabilities
    private static void addEnergyCapabilities(
            Level level,
            BlockEntity blockEntity,
            Set<IEnergyStorage> energy
    ) {
        IEnergyStorage unsided = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, null);
        if (unsided != null) {
            energy.add(unsided);
            return;
        }
        for (Direction side : Direction.values()) {
            IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
            if (handler != null) energy.add(handler);
        }
    }

    // Check if this is active stock link
    private static boolean activeStockLink(PackagerLinkBlockEntity stockLink) {
        return stockLink.behaviour != null && stockLink.behaviour.redstonePower != 15;
    }

    // Keep the alive
    private static void keepAlive(PackagerLinkBlockEntity stockLink) {
        try {
            LogisticallyLinkedBehaviour.keepAlive(stockLink.behaviour);
        } catch (RuntimeException ignored) {
        }
    }

    // Route the access
    private static ShipStockNetworkCache.NetworkAccess routeAccess(
            List<ShipStockNetworkCache.NetworkAccess> accesses, RouteType routeType) {
        Set<IItemHandler> items = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<IFluidHandler> fluids = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<IEnergyStorage> energy = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ShipStockNetworkCache.NetworkAccess access : accesses) {
            if (routeType == RouteType.ITEM) items.addAll(access.items());
            if (routeType == RouteType.FLUID) fluids.addAll(access.fluids());
            if (routeType == RouteType.ENERGY) energy.addAll(access.energy());
        }
        return new ShipStockNetworkCache.NetworkAccess(
                List.copyOf(items), List.copyOf(fluids), List.copyOf(energy));
    }

    // Merge the access
    private static ShipStockNetworkCache.NetworkAccess mergeAccess(
            ShipStockNetworkCache.NetworkAccess first,
            ShipStockNetworkCache.NetworkAccess second,
            RouteType routeType) {
        Set<IItemHandler> items = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<IFluidHandler> fluids = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<IEnergyStorage> energy = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (routeType == RouteType.ITEM) {
            items.addAll(first.items()); items.addAll(second.items());
        } else if (routeType == RouteType.FLUID) {
            fluids.addAll(first.fluids()); fluids.addAll(second.fluids());
        } else {
            energy.addAll(first.energy()); energy.addAll(second.energy());
        }
        return new ShipStockNetworkCache.NetworkAccess(
                List.copyOf(items), List.copyOf(fluids), List.copyOf(energy));
    }

    // Get the empty access
    private static ShipStockNetworkCache.NetworkAccess emptyAccess() {
        return new ShipStockNetworkCache.NetworkAccess(List.of(), List.of(), List.of());
    }

    // Get the connector key
    private static @Nullable ConnectorKey connectorKey(BlockEntity connector) {
        if (connector == null || connector.getLevel() == null) {
            return null;
        }
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(connector);
        if (subLevelId == null) {
            return null;
        }
        return new ConnectorKey(
                connector.getLevel().dimension().location(), subLevelId,
                connector.getBlockPos());
    }

    // Get the server
    private static @Nullable MinecraftServer server(@Nullable Level level) {
        return level == null ? null : level.getServer();
    }

    // Store the connector network
    public record ConnectorNetwork(
            UUID subLevelId,
            BlockPos position,
            ShipStockNetworkCache.NetworkAccess itemAccess,
            ShipStockNetworkCache.NetworkAccess fluidAccess,
            ShipStockNetworkCache.NetworkAccess energyAccess,
            ShipStockNetworkCache.NetworkAccess fuelAccess
    ) {
        // Initialize the connector network
        public ConnectorNetwork {
            position = position == null ? null : position.immutable();
            itemAccess = emptyIfNull(itemAccess);
            fluidAccess = emptyIfNull(fluidAccess);
            energyAccess = emptyIfNull(energyAccess);
            fuelAccess = emptyIfNull(fuelAccess);
        }

        // Initialize the connector network
        public ConnectorNetwork(
                UUID subLevelId,
                BlockPos pos,
                ShipStockNetworkCache.NetworkAccess access
        ) {
            this(subLevelId, pos, access, access, access, access);
        }

        // Check if this is empty
        public boolean isEmpty() {
            return itemAccess.isEmpty() && fluidAccess.isEmpty()
                    && energyAccess.isEmpty() && fuelAccess.isEmpty();
        }
    }

    // Get the empty if null
    private static ShipStockNetworkCache.NetworkAccess emptyIfNull(
            ShipStockNetworkCache.NetworkAccess access
    ) {
        return access == null ? new ShipStockNetworkCache.NetworkAccess(
                List.of(), List.of(), List.of()) : access;
    }

    // Expose routed network
    private record RoutedNetworkAccess(
            ShipStockNetworkCache.NetworkAccess items,
            ShipStockNetworkCache.NetworkAccess fluids,
            ShipStockNetworkCache.NetworkAccess energy,
            ShipStockNetworkCache.NetworkAccess fuel
    ) {
        // Get the access
        private ShipStockNetworkCache.NetworkAccess access(
                RouteType routeType, boolean refuelingConnector
        ) {
            return switch (routeType) {
                case ITEM -> items;
                case FLUID -> refuelingConnector ? fuel : fluids;
                case ENERGY -> refuelingConnector ? fuel : energy;
            };
        }
    }

    // Define the route type values
    private enum RouteType {
        ITEM,
        FLUID,
        ENERGY
    }

    // Store the connector key
    private record ConnectorKey(
            ResourceLocation dimension,
            UUID subLevelId,
            BlockPos position
    ) {
        // Initialize the connector key
        private ConnectorKey {
            position = position.immutable();
        }
    }

    // Store the dock stock snapshot
    private record DockStockSnapshot(long gameTime,
                                     ShipStockNetworkCache.NetworkAccess access) {
    }
}
