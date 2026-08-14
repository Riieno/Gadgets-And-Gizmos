package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipLogisticsRun;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Keep loaded ship stock endpoints indexed so schedules never scan whole plots for every request
public final class ShipStockNetworkCache {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long TOPOLOGY_REFRESH_TICKS = 10L;
    private static final long SUMMARY_REFRESH_TICKS = 5L;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Ship stock network cache controller
    private final AdvancedContraptionControllerBlockEntity controller;
    // Topology tick
    private long topologyTick = Long.MIN_VALUE;
    // Summary tick
    private long summaryTick = Long.MIN_VALUE;
    // Links indexed by network
    private Map<UUID, List<LinkEndpoint>> linksByNetwork = Map.of();
    // Access indexed by network
    private Map<UUID, NetworkAccess> accessByNetwork = Map.of();
    // Tracked connectors
    private List<Connector> connectors = List.of();
    // Tracked direct runs
    private List<ShipLogisticsRun> directRuns = List.of();
    // Runs indexed by network
    private Map<UUID, ShipLogisticsRun> runsByNetwork = Map.of();
    // Current snapshot
    private Snapshot snapshot = Snapshot.EMPTY;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship stock network cache
    public ShipStockNetworkCache(AdvancedContraptionControllerBlockEntity controller) {
        this.controller = controller;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the ship stock network cache
    public void tick() {
        snapshot();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the snapshot
    public Snapshot snapshot() {
        Level level = controller.getLevel();
        if (level == null || level.isClientSide) {
            return Snapshot.EMPTY;
        }
        long gameTime = level.getGameTime();
        if (intervalElapsed(gameTime, topologyTick, TOPOLOGY_REFRESH_TICKS)) {
            refreshTopology(gameTime);
        }
        if (intervalElapsed(gameTime, summaryTick, SUMMARY_REFRESH_TICKS)) {
            refreshSummaries(gameTime);
        }
        return snapshot;
    }

    // Invalidate the ship stock network cache
    public void invalidate() {
        topologyTick = Long.MIN_VALUE;
        summaryTick = Long.MIN_VALUE;
    }

    // Clear the ship stock network cache
    public void clear() {
        WirelessDockingTransfer.clearController(controller);
        linksByNetwork = Map.of();
        accessByNetwork = Map.of();
        connectors = List.of();
        snapshot = Snapshot.EMPTY;
        invalidate();
    }

    // Refresh the topology
    private void refreshTopology(long gameTime) {
        Map<UUID, List<LinkEndpoint>> discoveredLinks = new LinkedHashMap<>();
        Map<EndpointKey, UUID> networkAt = new LinkedHashMap<>();
        List<ConnectorSeed> discoveredConnectors = new ArrayList<>();
        directRuns = controller.shipLogisticsRuns();
        Map<UUID, ShipLogisticsRun> runIndex = new LinkedHashMap<>();
        for (ShipLogisticsRun run : directRuns) runIndex.put(run.id(), run);
        runsByNetwork = Map.copyOf(runIndex);
        for (BlockEntity blockEntity : ShipCargoAutomation.shipBlockEntities(controller)) {
            UUID subLevelId = SimulatedHelper.getContainingSubLevelId(blockEntity);
            if (subLevelId == null || blockEntity.isRemoved()) {
                continue;
            }
            if (blockEntity instanceof PackagerLinkBlockEntity stockLink
                    && stockLink.behaviour != null
                    && stockLink.behaviour.redstonePower != 15) {
                keepAlive(stockLink);
                UUID networkId = stockLink.behaviour.freqId;
                LinkEndpoint endpoint = new LinkEndpoint(subLevelId, stockLink);
                discoveredLinks.computeIfAbsent(networkId, ignored -> new ArrayList<>())
                        .add(endpoint);
                networkAt.put(new EndpointKey(subLevelId, stockLink.getBlockPos()), networkId);
                continue;
            }
            if (DockingConnectorAutomation.isDockingConnector(blockEntity)) {
                Direction facing = blockEntity.getBlockState().getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                discoveredConnectors.add(new ConnectorSeed(
                        subLevelId, blockEntity.getBlockPos().immutable(), facing));
            }
        }

        Map<UUID, List<LinkEndpoint>> immutableLinks = new LinkedHashMap<>();
        discoveredLinks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> immutableLinks.put(entry.getKey(), List.copyOf(entry.getValue())));
        for (ShipLogisticsRun run : directRuns) immutableLinks.putIfAbsent(run.id(), List.of());
        linksByNetwork = Map.copyOf(immutableLinks);

        List<Connector> networkedConnectors = new ArrayList<>();
        for (ConnectorSeed connector : discoveredConnectors) {
            Set<UUID> networkIds = new LinkedHashSet<>();
            for (ShipLogisticsRun run : directRuns) {
                if (run.contains(connector.subLevelId(), connector.position())
                        || legacyAutomaticallyConnects(run)) {
                    networkIds.add(run.id());
                }
            }
            for (Direction dir : Direction.values()) {
                UUID networkId = networkAt.get(new EndpointKey(
                        connector.subLevelId(), connector.position().relative(dir)));
                if (networkId != null) {
                    networkIds.add(networkId);
                }
            }
            if (!networkIds.isEmpty()) {
                networkedConnectors.add(new Connector(
                        connector.subLevelId(), connector.position(), connector.facing(), networkIds));
            }
        }
        networkedConnectors.sort(Comparator
                .comparing((Connector connector) -> connector.subLevelId().toString())
                .thenComparingLong(connector -> connector.position().asLong()));
        connectors = List.copyOf(networkedConnectors);
        topologyTick = gameTime;
        summaryTick = Long.MIN_VALUE;
    }

    // Check if the legacy run connects automatically
    private static boolean legacyAutomaticallyConnects(ShipLogisticsRun run) {
        return run.name().startsWith("Imported ")
                || run.name().equals(run.resourceType().label() + " Run");
    }

    // Refresh the summaries
    private void refreshSummaries(long gameTime) {
        // -----------------------------------------------------NETWORK SETUP-----------------------------------------------------
        Map<UUID, Network> networks = new LinkedHashMap<>();
        Map<UUID, NetworkAccess> networkAccess = new LinkedHashMap<>();
        // ------------------------------------NETWORK SOURCES------------------------------------
        for (Map.Entry<UUID, List<LinkEndpoint>> entry : linksByNetwork.entrySet()) {
            UUID networkId = entry.getKey();
            List<LinkEndpoint> links = entry.getValue();
            ShipLogisticsRun directRun = runsByNetwork.get(networkId);
            InventorySummary linkedItems = new InventorySummary();
            Set<PackagerBlockEntity> seenPackagers =
                    Collections.newSetFromMap(new IdentityHashMap<>());
            Set<IItemHandler> nearbyItems = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<IFluidHandler> nearbyFluids = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<IEnergyStorage> nearbyEnergy = Collections.newSetFromMap(new IdentityHashMap<>());
            for (LinkEndpoint endpoint : links) {
                PackagerLinkBlockEntity stockLink = endpoint.stockLink();
                keepAlive(stockLink);
                try {
                    PackagerBlockEntity packager = stockLink.getPackager();
                    if (packager != null && seenPackagers.add(packager)) {
                        InventorySummary summary = stockLink.fetchSummaryFromPackager(null);
                        if (summary != null && summary != InventorySummary.EMPTY) {
                            linkedItems.add(directRun != null && directRun.isFuelRun()
                                    ? fuelItems(summary) : summary);
                        }
                        if (packager.targetInventory != null) {
                            IItemHandler targetInventory = packager.targetInventory.getInventory();
                            if (targetInventory != null) {
                                nearbyItems.add(targetInventory);
                            }
                        }
                    }
                } catch (RuntimeException ignored) {
                }
                collectNearbyCapabilities(
                        endpoint, nearbyItems, nearbyFluids, nearbyEnergy);
            }
            if (directRun != null) {
                collectDirectCapabilities(directRun, nearbyItems, nearbyFluids, nearbyEnergy);
            }

            // ------------------------------------RESOURCE SUMMARY------------------------------------
            InventorySummary adjacentItems = summarizeItems(
                    nearbyItems, directRun != null && directRun.isFuelRun());
            InventorySummary globalItems;
            try {
                InventorySummary global = directRun == null
                        ? LogisticsManager.getSummaryOfNetwork(networkId, true)
                        : InventorySummary.EMPTY;
                globalItems = global == null ? InventorySummary.EMPTY : global;
            } catch (RuntimeException err) {
                globalItems = InventorySummary.EMPTY;
            }
            boolean hasLocalItemSource = !seenPackagers.isEmpty() || !nearbyItems.isEmpty();
            InventorySummary localItems = largestSummary(linkedItems, adjacentItems);
            InventorySummary items = (hasLocalItemSource ? localItems : globalItems).copy();
            List<FluidStack> fluids = summarizeFluids(nearbyFluids);
            long energy = 0L;
            long energyCapacity = 0L;
            for (IEnergyStorage handler : nearbyEnergy) {
                energy += Math.max(0, handler.getEnergyStored());
                energyCapacity += Math.max(0, handler.getMaxEnergyStored());
            }
            networks.put(networkId, new Network(
                    networkId, directRun == null ? "Create Stock Network" : directRun.name(),
                    directRun == null ? null : directRun.resourceType(),
                    items, fluids, energy, energyCapacity,
                    hasLocalItemSource || !items.isEmpty(),
                    !nearbyFluids.isEmpty(), !nearbyEnergy.isEmpty()));
            networkAccess.put(networkId, new NetworkAccess(
                    List.copyOf(nearbyItems), List.copyOf(nearbyFluids),
                    List.copyOf(nearbyEnergy)));
        }
        // ------------------------------------SNAPSHOT COMMIT------------------------------------
        accessByNetwork = Map.copyOf(networkAccess);
        snapshot = new Snapshot(networks, connectors);
        List<WirelessDockingTransfer.ConnectorNetwork> publishedConnectors =
                new ArrayList<>();
        // ------------------------------------DOCKING NETWORKS------------------------------------
        for (Connector connector : connectors) {
            publishedConnectors.add(new WirelessDockingTransfer.ConnectorNetwork(
                    connector.subLevelId(), connector.position(),
                    combinedAccess(filteredNetworkIds(connector.networkIds(),
                            ShipLogisticsRun.ResourceType.ITEM), networkAccess),
                    combinedAccess(filteredNetworkIds(connector.networkIds(),
                            ShipLogisticsRun.ResourceType.FLUID), networkAccess),
                    combinedAccess(filteredNetworkIds(connector.networkIds(),
                            ShipLogisticsRun.ResourceType.ENERGY), networkAccess),
                    combinedAccess(filteredNetworkIds(connector.networkIds(),
                            ShipLogisticsRun.ResourceType.FUEL), networkAccess)));
        }
        WirelessDockingTransfer.publishNetworks(controller, publishedConnectors);
        summaryTick = gameTime;
    }

    // Collect the direct capabilities
    private void collectDirectCapabilities(
            ShipLogisticsRun run,
            Set<IItemHandler> items, Set<IFluidHandler> fluids,
            Set<IEnergyStorage> energy) {
        for (ShipLogisticsRun.Endpoint endpoint : run.endpoints()) {
            UUID subLevel = endpoint.subLevelId().equals(new UUID(0L, 0L))
                    ? null : endpoint.subLevelId();
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), subLevel, endpoint.position());
            if (blockEntity == null || blockEntity.isRemoved()
                    || DockingConnectorAutomation.isDockingConnector(blockEntity)) continue;
            if (blockEntity instanceof ShippingManifestBlockEntity manifest) {
                Set<IItemHandler> manifestItems = Collections.newSetFromMap(new IdentityHashMap<>());
                Set<IFluidHandler> manifestFluids = Collections.newSetFromMap(new IdentityHashMap<>());
                Set<IEnergyStorage> manifestEnergy = Collections.newSetFromMap(new IdentityHashMap<>());
                addManifestCapabilities(manifest, manifestItems, manifestFluids, manifestEnergy);
                if (run.resourceType() == ShipLogisticsRun.ResourceType.ITEM
                        || run.isFuelRun()) {
                    items.addAll(manifestItems);
                }
                if (run.resourceType() == ShipLogisticsRun.ResourceType.FLUID
                        || run.resourceType() == ShipLogisticsRun.ResourceType.FUEL) {
                    fluids.addAll(manifestFluids);
                }
                if (run.resourceType() == ShipLogisticsRun.ResourceType.ENERGY
                        || run.resourceType() == ShipLogisticsRun.ResourceType.FUEL) {
                    energy.addAll(manifestEnergy);
                }
            }
            Level level = blockEntity.getLevel();
            if (level == null) continue;
            boolean directThrusterEnergy = false;
            if ((run.resourceType() == ShipLogisticsRun.ResourceType.ENERGY
                    || run.resourceType() == ShipLogisticsRun.ResourceType.FUEL)
                    && blockEntity instanceof ThrusterBlockEntity thruster
                    && thruster.isFocusedMode()) {
                energy.add(thruster.getEnergyStorage());
                directThrusterEnergy = true;
            }
            if ((run.resourceType() == ShipLogisticsRun.ResourceType.ENERGY
                    || run.resourceType() == ShipLogisticsRun.ResourceType.FUEL)
                    && !directThrusterEnergy) {
                collectEnergyCapabilities(level, blockEntity, energy);
            }
            for (Direction side : Direction.values()) {
                if (run.resourceType() == ShipLogisticsRun.ResourceType.ITEM
                        || run.isFuelRun()) {
                    IItemHandler item = level.getCapability(Capabilities.ItemHandler.BLOCK,
                            blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
                    if (item != null) items.add(item);
                }
                if (run.resourceType() == ShipLogisticsRun.ResourceType.FLUID
                        || run.resourceType() == ShipLogisticsRun.ResourceType.FUEL) {
                    IFluidHandler fluid = level.getCapability(Capabilities.FluidHandler.BLOCK,
                            blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
                    if (fluid != null) fluids.add(fluid);
                }
            }
        }
    }

    // Get the access
    public NetworkAccess accessFor(UUID subLevelId, BlockPos connectorPosition) {
        Snapshot current = snapshot();
        return combinedAccess(
                current.networkIdsFor(subLevelId, connectorPosition), accessByNetwork);
    }

    // Get the access
    public NetworkAccess accessFor(
            UUID subLevelId,
            BlockPos connectorPosition,
            Set<ShipLogisticsRun.ResourceType> resourceTypes
    ) {
        Snapshot current = snapshot();
        Set<UUID> selected = current.networkIdsFor(subLevelId, connectorPosition).stream()
                .filter(id -> {
                    ShipLogisticsRun run = runsByNetwork.get(id);
                    return run == null || resourceTypes == null || resourceTypes.isEmpty()
                            || resourceTypes.contains(run.resourceType());
                })
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return combinedAccess(selected, accessByNetwork);
    }

    // Get the access
    public NetworkAccess accessFor(ShipLogisticsRun.ResourceType resourceType) {
        snapshot();
        Set<UUID> selected = runsByNetwork.values().stream()
                .filter(run -> run.resourceType() == resourceType)
                .map(ShipLogisticsRun::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return combinedAccess(selected, accessByNetwork);
    }

    // Get the combined access
    private static NetworkAccess combinedAccess(
            Set<UUID> networkIds,
            Map<UUID, NetworkAccess> accessByNetwork
    ) {
        Set<IItemHandler> items = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IFluidHandler> fluids = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IEnergyStorage> energy = Collections.newSetFromMap(new IdentityHashMap<>());
        for (UUID networkId : networkIds) {
            NetworkAccess access = accessByNetwork.get(networkId);
            if (access == null) {
                continue;
            }
            items.addAll(access.items());
            fluids.addAll(access.fluids());
            energy.addAll(access.energy());
        }
        return new NetworkAccess(List.copyOf(items), List.copyOf(fluids), List.copyOf(energy));
    }

    // Get the filtered network ids
    private Set<UUID> filteredNetworkIds(
            Set<UUID> networkIds, ShipLogisticsRun.ResourceType resourceType
    ) {
        return networkIds.stream()
                .filter(id -> {
                    ShipLogisticsRun run = runsByNetwork.get(id);
                    return run == null || run.resourceType() == resourceType;
                })
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    // Collect the nearby capabilities
    private void collectNearbyCapabilities(
            LinkEndpoint endpoint,
            Set<IItemHandler> items,
            Set<IFluidHandler> fluids,
            Set<IEnergyStorage> energy
    ) {
        for (Direction dir : Direction.values()) {
            BlockPos pos = endpoint.stockLink().getBlockPos().relative(dir);
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), endpoint.subLevelId(), pos);
            if (blockEntity == null || blockEntity instanceof PackagerBlockEntity
                    || DockingConnectorAutomation.isDockingConnector(blockEntity)) {
                continue;
            }
            if (blockEntity instanceof ShippingManifestBlockEntity manifest) {
                addManifestCapabilities(manifest, items, fluids, energy);
            }
            Level level = blockEntity.getLevel();
            if (level == null) {
                continue;
            }
            IItemHandler itemHandler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(),
                    blockEntity.getBlockState(), blockEntity, null);
            IFluidHandler fluidHandler = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, blockEntity.getBlockPos(),
                    blockEntity.getBlockState(), blockEntity, null);
            if (itemHandler != null) items.add(itemHandler);
            if (fluidHandler != null) fluids.add(fluidHandler);
            collectEnergyCapabilities(level, blockEntity, energy);
        }
    }

    // Add the manifest capabilities
    private static void addManifestCapabilities(
            ShippingManifestBlockEntity manifest,
            Set<IItemHandler> items,
            Set<IFluidHandler> fluids,
            Set<IEnergyStorage> energy
    ) {
        Level level = manifest.getLevel();
        if (level == null) {
            return;
        }
        BlockPos target = ShippingManifestBlock.attachedTargetPos(
                manifest.getBlockPos(), manifest.getBlockState());
        Direction side = ShippingManifestBlock.attachedTargetSide(manifest.getBlockState());
        IItemHandler itemHandler = ShippingManifestBlock.findItemHandler(level, target, side);
        IFluidHandler fluidHandler = ShippingManifestBlock.resolveFluidHandler(
                level, target, side, manifest.isCombinedManifest());
        BlockEntity targetBlockEntity = level.getBlockEntity(target);
        IEnergyStorage energyHandler = targetBlockEntity instanceof ThrusterBlockEntity thruster
                && thruster.isFocusedMode()
                ? thruster.getEnergyStorage()
                : ShippingManifestBlock.findEnergyHandler(level, target, side);
        if (itemHandler != null) items.add(itemHandler);
        if (fluidHandler != null) fluids.add(fluidHandler);
        if (energyHandler != null) energy.add(energyHandler);
    }

    // Collect the energy capabilities
    private static void collectEnergyCapabilities(
            Level level,
            BlockEntity blockEntity,
            Set<IEnergyStorage> energy
    ) {
        if (blockEntity instanceof ThrusterBlockEntity thruster
                && thruster.isFocusedMode()) {
            energy.add(thruster.getEnergyStorage());
            return;
        }
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

    // Get the summarize items
    private static InventorySummary summarizeItems(
            Set<IItemHandler> handlers, boolean fuelOnly) {
        InventorySummary summary = new InventorySummary();
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && (!fuelOnly || ShipCargoAutomation.isBurnableItem(stack))) {
                    summary.add(stack);
                }
            }
        }
        return summary;
    }

    // Get the fuel items
    private static InventorySummary fuelItems(InventorySummary src) {
        InventorySummary filtered = new InventorySummary();
        for (BigItemStack entry : src.getStacks()) {
            if (!entry.stack.isEmpty() && ShipCargoAutomation.isBurnableItem(entry.stack)) {
                filtered.add(new BigItemStack(entry.stack.copy(), entry.count));
            }
        }
        return filtered;
    }

    // Get the summarize fluids
    private static List<FluidStack> summarizeFluids(Set<IFluidHandler> handlers) {
        List<FluidStack> fluids = new ArrayList<>();
        for (IFluidHandler handler : handlers) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (stack.isEmpty()) {
                    continue;
                }
                FluidStack existing = fluids.stream()
                        .filter(candidate -> FluidStack.isSameFluidSameComponents(candidate, stack))
                        .findFirst().orElse(null);
                if (existing == null) {
                    fluids.add(stack.copy());
                } else {
                    existing.grow(stack.getAmount());
                }
            }
        }
        return fluids.stream().map(FluidStack::copy).toList();
    }

    // Get the largest summary
    private static InventorySummary largestSummary(InventorySummary... summaries) {
        InventorySummary selected = InventorySummary.EMPTY;
        for (InventorySummary summary : summaries) {
            if (summary != null && summary.getTotalCount() > selected.getTotalCount()) {
                selected = summary;
            }
        }
        return selected;
    }

    // Keep the alive
    private static void keepAlive(PackagerLinkBlockEntity stockLink) {
        try {
            if (stockLink.behaviour != null && stockLink.behaviour.redstonePower != 15) {
                LogisticallyLinkedBehaviour.keepAlive(stockLink.behaviour);
            }
        } catch (RuntimeException ignored) {
        }
    }

    // Check if the interval elapsed
    private static boolean intervalElapsed(long currentTick, long previousTick, long interval) {
        return previousTick == Long.MIN_VALUE || currentTick < previousTick
                || currentTick - previousTick >= interval;
    }

    // Store the endpoint key
    private record EndpointKey(UUID subLevelId, BlockPos position) {
        // Initialize the endpoint key
        private EndpointKey {
            position = position.immutable();
        }
    }

    // Store the link endpoint
    private record LinkEndpoint(UUID subLevelId, PackagerLinkBlockEntity stockLink) {
    }

    // Store the connector seed
    private record ConnectorSeed(UUID subLevelId, BlockPos position, Direction facing) {
    }

    // Store the connector
    public record Connector(
            UUID subLevelId,
            BlockPos position,
            Direction facing,
            Set<UUID> networkIds
    ) {
        // Initialize the connector
        public Connector {
            position = position.immutable();
            networkIds = Set.copyOf(networkIds);
        }
    }

    // Store the network
    public record Network(
            UUID id,
            String name,
            ShipLogisticsRun.ResourceType resourceType,
            InventorySummary items,
            List<FluidStack> fluids,
            long energy,
            long energyCapacity,
            boolean supportsItems,
            boolean supportsFluids,
            boolean supportsEnergy
    ) {
        // Initialize the network
        public Network {
            name = name == null || name.isBlank() ? "Stock Network" : name;
            items = items == null ? new InventorySummary() : items;
            fluids = fluids == null ? List.of() : fluids.stream().map(FluidStack::copy).toList();
        }

        // Check if this is a fuel run
        public boolean isFuelRun() {
            return resourceType == ShipLogisticsRun.ResourceType.FUEL;
        }
    }

    // Expose network
    public record NetworkAccess(
            List<IItemHandler> items,
            List<IFluidHandler> fluids,
            List<IEnergyStorage> energy
    ) {
        // Initialize the network
        public NetworkAccess {
            items = items == null ? List.of() : List.copyOf(items);
            fluids = fluids == null ? List.of() : List.copyOf(fluids);
            energy = energy == null ? List.of() : List.copyOf(energy);
        }

        // Check if this is empty
        public boolean isEmpty() {
            return items.isEmpty() && fluids.isEmpty() && energy.isEmpty();
        }
    }

    // Store the snapshot
    public record Snapshot(Map<UUID, Network> networks, List<Connector> connectors) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), List.of());

        // Initialize the snapshot
        public Snapshot {
            networks = Map.copyOf(networks);
            connectors = List.copyOf(connectors);
        }

        // Get the networks
        public List<Network> networksFor(UUID subLevelId, BlockPos connectorPosition) {
            return networkIdsFor(subLevelId, connectorPosition).stream()
                    .map(networks::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        // Get the network ids
        public Set<UUID> networkIdsFor(UUID subLevelId, BlockPos connectorPosition) {
            if (subLevelId == null || connectorPosition == null) {
                return Set.of();
            }
            return connectors.stream()
                    .filter(candidate -> candidate.subLevelId().equals(subLevelId)
                            && candidate.position().equals(connectorPosition))
                    .map(Connector::networkIds)
                    .findFirst().orElse(Set.of());
        }
    }
}
