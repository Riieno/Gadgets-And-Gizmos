package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipLogisticsRun;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.HashSet;
import java.util.UUID;

// Move scheduled cargo between ship storage and docking inventories without duplicating handlers
public final class ShipCargoAutomation {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship cargo automation
    private ShipCargoAutomation() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the refuel
    public static int refuel(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock) {
        return refuel(controller, dock, List.of(), true, true);
    }

    // Get the refuel
    public static int refuel(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            List<ItemStack> fluidFilters
    ) {
        return refuel(controller, dock, fluidFilters, true, true);
    }

    // Get the refuel
    public static int refuel(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            List<ItemStack> fluidFilters,
            boolean fluids,
            boolean energy
    ) {
        List<IFluidHandler> sources = dockFluidHandlers(controller, dock);
        List<ThrusterBlockEntity> thrusters = shipBlockEntities(controller).stream()
                .filter(ThrusterBlockEntity.class::isInstance)
                .map(ThrusterBlockEntity.class::cast)
                .toList();
        int moved = 0;
        if (fluids) {
            for (IFluidHandler src : sources) {
                for (int tank = 0; tank < src.getTanks(); tank++) {
                    FluidStack available = src.getFluidInTank(tank);
                    if (available.isEmpty()
                            || !matchesFluidFilters(controller.getLevel(), available, fluidFilters)) {
                        continue;
                    }
                    for (ThrusterBlockEntity thruster : thrusters) {
                        int accepted = thruster.getFuelTank().fill(
                                available, IFluidHandler.FluidAction.SIMULATE);
                        if (accepted <= 0) {
                            continue;
                        }
                        FluidStack drained = src.drain(available.copyWithAmount(accepted),
                                IFluidHandler.FluidAction.EXECUTE);
                        moved += thruster.getFuelTank().fill(
                                drained, IFluidHandler.FluidAction.EXECUTE);
                        available = src.getFluidInTank(tank);
                        if (available.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }
        return moved + (energy ? transferEnergy(
                dockEnergyHandlers(controller, dock), shipEnergyHandlers(controller)) : 0);
    }

    // Get the restock
    public static int restock(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock) {
        return restock(controller, dock, List.of());
    }

    // Get the restock
    public static int restock(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            List<ItemStack> itemFilters
    ) {
        return transferItems(dockItemHandlers(controller, dock), shipItemHandlers(controller),
                stack -> !PackageItem.isPackage(stack)
                        && matchesItemFilters(controller.getLevel(), stack, itemFilters));
    }

    // Get the wireless docking transfer
    public static int wirelessDockingTransfer(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            ShipControlModuleRuntime.MappedDockingConnector connector,
            boolean pickup,
            boolean items,
            boolean fluids,
            boolean energy,
            boolean fuelRun,
            List<ItemStack> itemFilters,
            List<ItemStack> fluidFilters
    ) {
        if (controller == null || dock == null || connector == null) {
            return 0;
        }
        ShipDockBlockEntity shipDock = dockBlockEntity(controller, dock);
        if (shipDock == null) {
            return 0;
        }
        ShipStockNetworkCache.NetworkAccess dockStock = dockStockAccess(controller, dock);
        Set<ShipLogisticsRun.ResourceType> resourceTypes = new HashSet<>();
        if (items) resourceTypes.add(fuelRun
                ? ShipLogisticsRun.ResourceType.FUEL : ShipLogisticsRun.ResourceType.ITEM);
        if (fluids) resourceTypes.add(fuelRun
                ? ShipLogisticsRun.ResourceType.FUEL : ShipLogisticsRun.ResourceType.FLUID);
        if (energy) resourceTypes.add(fuelRun
                ? ShipLogisticsRun.ResourceType.FUEL : ShipLogisticsRun.ResourceType.ENERGY);
        ShipStockNetworkCache.NetworkAccess network = controller.getShipStockNetworkAccess(
                connector.subLevelId(), connector.blockPosition(), resourceTypes);
        if (network.isEmpty()) {
            return 0;
        }
        List<IItemHandler> dockItems = pickup
                ? appendHandlers(List.of(shipDock.getItemBuffer()), dockStock.items())
                : appendHandlers(dockStock.items(), dockDropoffItemHandlers(controller, dock, shipDock));
        List<IFluidHandler> dockFluids = pickup
                ? appendHandlers(List.of(shipDock.getFluidBuffer()), dockStock.fluids())
                : appendHandlers(dockStock.fluids(), List.of(shipDock.getFluidBuffer()));
        List<IEnergyStorage> dockEnergy = pickup
                ? appendHandlers(List.of(shipDock.getEnergyBuffer()), dockStock.energy())
                : appendHandlers(dockStock.energy(), List.of(shipDock.getEnergyBuffer()));
        int moved = 0;
        if (items) {
            moved += transferItems(
                    pickup ? dockItems : network.items(),
                    pickup ? network.items() : dockItems,
                    stack -> !PackageItem.isPackage(stack)
                            && (!fuelRun || isBurnableItem(stack))
                            && (fuelRun
                            ? matchesFuelItemFilters(controller.getLevel(), stack, itemFilters)
                            : matchesItemFilters(controller.getLevel(), stack, itemFilters)));
        }
        if (fluids) {
            moved += transferFluids(
                    pickup ? dockFluids : network.fluids(),
                    pickup ? network.fluids() : dockFluids,
                    stack -> matchesFluidFilters(
                            controller.getLevel(), stack, fluidFilters));
        }
        if (energy) {
            moved += transferEnergy(
                    pickup ? dockEnergy : network.energy(),
                    pickup ? network.energy() : dockEnergy);
        }
        return moved;
    }

    // Get the refuel from ship runs
    public static int refuelFromShipRuns(
            AdvancedContraptionControllerBlockEntity controller,
            List<ItemStack> fluidFilters,
            boolean fluids,
            boolean energy
    ) {
        return refuelFromShipRuns(controller, List.of(), fluidFilters, true, fluids, energy);
    }

    // Get the refuel from ship runs
    public static int refuelFromShipRuns(
            AdvancedContraptionControllerBlockEntity controller,
            List<ItemStack> itemFilters,
            List<ItemStack> fluidFilters,
            boolean items,
            boolean fluids,
            boolean energy
    ) {
        if (controller == null) return 0;
        if (controller.getLevel() == null || Math.floorMod(
                controller.getLevel().getGameTime() + controller.getBlockPos().asLong(), 4L) != 0L) {
            return 0;
        }
        ShipStockNetworkCache.NetworkAccess fuel = controller.getShipLogisticsRunAccess(
                ShipLogisticsRun.ResourceType.FUEL);
        int moved = 0;
        if (items) {
            moved += transferItems(fuel.items(), shipFuelItemHandlers(controller),
                    stack -> isBurnableItem(stack)
                            && matchesFuelItemFilters(controller.getLevel(), stack, itemFilters));
        }
        if (fluids) {
            moved += balanceFuelFluids(appendHandlers(
                    fuel.fluids(), liveFuelRunFluidHandlers(controller)),
                    controller.getLevel(), fluidFilters);
        }
        if (energy) {
            moved += balanceFuelEnergy(appendHandlers(
                    fuel.energy(), liveFuelRunEnergyHandlers(controller)));
        }
        return moved;
    }

    // Get the refuel items from dock
    public static int refuelItemsFromDock(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            List<ItemStack> itemFilters
    ) {
        if (controller == null || dock == null) return 0;
        return transferItems(dockItemHandlers(controller, dock), shipFuelItemHandlers(controller),
                stack -> isBurnableItem(stack)
                        && matchesFuelItemFilters(controller.getLevel(), stack, itemFilters));
    }

    // Get the deliver packages
    public static int deliverPackages(AdvancedContraptionControllerBlockEntity controller,
                                      ShipDockRegistry.Dock dock) {
        return transferItems(shipItemHandlers(controller), dockItemHandlers(controller, dock),
                stack -> PackageItem.isPackage(stack) && PackageItem.matchAddress(stack, dock.name()));
    }

    // Collect the packages
    public static int collectPackages(AdvancedContraptionControllerBlockEntity controller,
                                      ShipDockRegistry.Dock dock, String addressFilter) {
        String filter = addressFilter == null ? "" : addressFilter.trim();
        return transferItems(dockItemHandlers(controller, dock), shipItemHandlers(controller),
                stack -> PackageItem.isPackage(stack)
                        && (filter.isEmpty() || PackageItem.matchAddress(stack, filter)));
    }

    // Check if this has packages
    public static boolean hasPackages(AdvancedContraptionControllerBlockEntity controller) {
        return containsItem(shipItemHandlers(controller), PackageItem::isPackage)
                || shipStockSummaries(controller).stream().anyMatch(summary ->
                summary.getTotalOfMatching(PackageItem::isPackage) > 0);
    }

    // Check if this has package for dock
    public static boolean hasPackageForDock(AdvancedContraptionControllerBlockEntity controller, String dockName) {
        return containsItem(shipItemHandlers(controller),
                stack -> PackageItem.isPackage(stack) && PackageItem.matchAddress(stack, dockName))
                || shipStockSummaries(controller).stream().anyMatch(summary ->
                summary.getTotalOfMatching(stack -> PackageItem.isPackage(stack)
                        && PackageItem.matchAddress(stack, dockName)) > 0);
    }

    // Check if the dock has package
    public static boolean dockHasPackage(AdvancedContraptionControllerBlockEntity controller,
                                         ShipDockRegistry.Dock dock, String addressFilter) {
        String filter = addressFilter == null ? "" : addressFilter.trim();
        return containsItem(dockItemHandlers(controller, dock),
                stack -> PackageItem.isPackage(stack)
                        && (filter.isEmpty() || PackageItem.matchAddress(stack, filter)))
                || dockStockSummaries(controller, dock).stream().anyMatch(summary ->
                summary.getTotalOfMatching(stack -> PackageItem.isPackage(stack)
                        && (filter.isEmpty() || PackageItem.matchAddress(stack, filter))) > 0);
    }

    // Check if the dock has restock cargo
    public static boolean dockHasRestockCargo(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock
    ) {
        return containsItem(dockItemHandlers(controller, dock),
                stack -> !PackageItem.isPackage(stack))
                || dockStockSummaries(controller, dock).stream().anyMatch(summary ->
                summary.getTotalOfMatching(stack -> !PackageItem.isPackage(stack)) > 0);
    }

    // Check if the dock has fuel supply
    public static boolean dockHasFuelSupply(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock
    ) {
        for (IFluidHandler handler : dockFluidHandlers(controller, dock)) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                if (!handler.getFluidInTank(tank).isEmpty()) {
                    return true;
                }
            }
        }
        if (containsItem(dockItemHandlers(controller, dock), ShipCargoAutomation::isBurnableItem)) {
            return true;
        }
        return dockEnergyHandlers(controller, dock).stream()
                .anyMatch(handler -> handler.getEnergyStored() > 0);
    }

    // Check if the dock can receive the package
    public static boolean dockCanReceivePackage(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock
    ) {
        List<IItemHandler> destinations = dockItemHandlers(controller, dock);
        for (IItemHandler src : shipItemHandlers(controller)) {
            for (int slot = 0; slot < src.getSlots(); slot++) {
                ItemStack stack = src.getStackInSlot(slot);
                if (PackageItem.isPackage(stack) && PackageItem.matchAddress(stack, dock.name())
                        && insert(destinations, stack, true).getCount() < stack.getCount()) {
                    return true;
                }
            }
        }
        return false;
    }

    // Count the dock items
    public static long countDockItems(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            ItemStack filter
    ) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long amount = 0L;
        for (IItemHandler handler : dockItemHandlers(controller, dock)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && predicate.test(level, stack)) {
                    amount += stack.getCount();
                }
            }
        }
        for (InventorySummary summary : dockStockSummaries(controller, dock)) {
            amount += summary.getTotalOfMatching(stack -> predicate.test(level, stack));
        }
        return amount;
    }

    // Count the dock fluids
    public static long countDockFluids(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            ItemStack filter
    ) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long amount = 0L;
        for (IFluidHandler handler : dockFluidHandlers(controller, dock)) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty() && predicate.test(level, stack)) {
                    amount += stack.getAmount();
                }
            }
        }
        return amount;
    }

    // Count the dock energy
    public static long countDockEnergy(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock
    ) {
        long amount = 0L;
        for (IEnergyStorage handler : dockEnergyHandlers(controller, dock)) {
            amount = saturatingAdd(amount, Math.max(0L, handler.getEnergyStored()));
        }
        return amount;
    }

    // Check if the ship connector advertises the package
    public static boolean shipConnectorAdvertisesPackage(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector,
            String address
    ) {
        String filter = address == null ? "" : address.trim();
        return shipConnectorStockSummaries(controller, connector).stream().anyMatch(summary ->
                summary.getTotalOfMatching(stack -> PackageItem.isPackage(stack)
                        && (filter.isEmpty() || PackageItem.matchAddress(stack, filter))) > 0);
    }

    // Get the ship connector advertised items
    public static long shipConnectorAdvertisedItems(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector,
            ItemStack filter
    ) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long amount = 0L;
        for (InventorySummary summary : shipConnectorStockSummaries(controller, connector)) {
            amount += summary.getTotalOfMatching(stack -> predicate.test(level, stack));
        }
        return amount;
    }

    // Get the ship connector advertised fluids
    public static long shipConnectorAdvertisedFluids(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector,
            ItemStack filter
    ) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long amount = 0L;
        for (ShipStockNetworkCache.Network network : shipConnectorNetworks(controller, connector)) {
            if (network.isFuelRun()) continue;
            for (FluidStack stack : network.fluids()) {
                if (!stack.isEmpty() && predicate.test(level, stack)) {
                    amount += stack.getAmount();
                }
            }
        }
        return amount;
    }

    // Check if the ship connector supports items
    public static boolean shipConnectorSupportsItems(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector
    ) {
        return shipConnectorNetworks(controller, connector).stream()
                .anyMatch(ShipStockNetworkCache.Network::supportsItems);
    }

    // Check if the ship connector supports fluids
    public static boolean shipConnectorSupportsFluids(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector
    ) {
        return shipConnectorNetworks(controller, connector).stream()
                .anyMatch(ShipStockNetworkCache.Network::supportsFluids);
    }

    // Check if the ship connector supports energy
    public static boolean shipConnectorSupportsEnergy(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector
    ) {
        return shipConnectorNetworks(controller, connector).stream()
                .anyMatch(ShipStockNetworkCache.Network::supportsEnergy);
    }

    // Check if the ship connector has stock link
    public static boolean shipConnectorHasStockLink(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector
    ) {
        return !shipConnectorNetworks(controller, connector).isEmpty()
                || hasStockLinkAt(controller.getLevel(), connector.subLevelId(),
                connector.blockPosition());
    }

    // Count the items
    public static long countItems(AdvancedContraptionControllerBlockEntity controller, ItemStack filter) {
        Level level = controller.getLevel();
        if (level == null) {
            return 0L;
        }
        FilterItemStack predicate = FilterItemStack.of(filter);
        List<ShipStockNetworkCache.Network> networks = shipNetworks(controller);
        boolean scopedToRun = hasConfiguredRun(controller, ShipLogisticsRun.ResourceType.ITEM);
        long networkAmount = 0L;
        boolean networkTracksItems = false;
        for (ShipStockNetworkCache.Network network : networks) {
            if (scopedToRun
                    ? network.resourceType() != ShipLogisticsRun.ResourceType.ITEM
                    : network.isFuelRun()) continue;
            networkTracksItems |= network.supportsItems();
            networkAmount += network.items().getTotalOfMatching(
                    stack -> predicate.test(level, stack));
        }
        if (scopedToRun || networkTracksItems) {
            return networkAmount;
        }
        long directAmount = 0L;
        for (IItemHandler handler : shipItemHandlers(controller)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && predicate.test(level, stack)) {
                    directAmount += stack.getCount();
                }
            }
        }
        return directAmount;
    }

    // Count the full item stacks
    public static long countFullItemStacks(
            AdvancedContraptionControllerBlockEntity controller, ItemStack filter
    ) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        List<ShipStockNetworkCache.Network> networks = shipNetworks(controller);
        boolean scopedToRun = hasConfiguredRun(controller, ShipLogisticsRun.ResourceType.ITEM);
        long networkStacks = 0L;
        boolean networkTracksItems = false;
        for (ShipStockNetworkCache.Network network : networks) {
            if (scopedToRun
                    ? network.resourceType() != ShipLogisticsRun.ResourceType.ITEM
                    : network.isFuelRun()) continue;
            networkTracksItems |= network.supportsItems();
            for (BigItemStack entry : network.items().getStacks()) {
                if (!entry.stack.isEmpty() && predicate.test(level, entry.stack)) {
                    networkStacks += entry.count / Math.max(1, entry.stack.getMaxStackSize());
                }
            }
        }
        if (scopedToRun || networkTracksItems) {
            return networkStacks;
        }
        long directStacks = 0L;
        for (IItemHandler handler : shipItemHandlers(controller)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getCount() == stack.getMaxStackSize()
                        && predicate.test(level, stack)) {
                    directStacks++;
                }
            }
        }
        return directStacks;
    }

    // Count the fluids
    public static long countFluids(AdvancedContraptionControllerBlockEntity controller, ItemStack filter) {
        Level level = controller.getLevel();
        if (level == null) {
            return 0L;
        }
        FilterItemStack predicate = FilterItemStack.of(filter);
        boolean scopedToRun = hasConfiguredRun(controller, ShipLogisticsRun.ResourceType.FLUID);
        long networkAmount = 0L;
        boolean networkTracksFluids = false;
        for (ShipStockNetworkCache.Network network : shipNetworks(controller)) {
            if (scopedToRun
                    ? network.resourceType() != ShipLogisticsRun.ResourceType.FLUID
                    : network.isFuelRun()) continue;
            networkTracksFluids |= network.supportsFluids();
            for (FluidStack stack : network.fluids()) {
                if (!stack.isEmpty() && predicate.test(level, stack)) {
                    networkAmount += stack.getAmount();
                }
            }
        }
        if (scopedToRun || networkTracksFluids) {
            return networkAmount;
        }
        long directAmount = 0L;
        for (IFluidHandler handler : shipFluidHandlers(controller)) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty() && predicate.test(level, stack)) {
                    directAmount += stack.getAmount();
                }
            }
        }
        return directAmount;
    }

    // Get the fuel status
    public static FuelStatus fuelStatus(AdvancedContraptionControllerBlockEntity controller) {
        return fuelStatus(controller, 1.0D);
    }

    // Get the fuel status
    public static FuelStatus fuelStatus(
            AdvancedContraptionControllerBlockEntity controller,
            double expectedThrottle
    ) {
        double amount = 0.0D;
        double capacity = 0.0D;
        double usePerTick = 0.0D;
        boolean infinite = false;
        for (BlockEntity blockEntity : shipBlockEntities(controller)) {
            if (!(blockEntity instanceof ThrusterBlockEntity thruster) || thruster.getFuelCapacity() <= 0) {
                continue;
            }
            if (thruster.getFuelAmount() == Integer.MAX_VALUE) {
                infinite = true;
                continue;
            }
            amount += Math.min(thruster.getFuelAmount(), thruster.getFuelCapacity());
            capacity += thruster.getFuelCapacity();
            usePerTick += Math.max(0.0D,
                    thruster.getEstimatedFuelConsumptionMbPerTick(
                            (float) Math.max(0.0D, Math.min(1.0D, expectedThrottle))));
        }
        return new FuelStatus(amount, capacity, usePerTick, infinite);
    }

    // Get the resource status
    static ResourceStatus resourceStatus(AdvancedContraptionControllerBlockEntity controller) {
        List<ShipStockNetworkCache.Network> networks = shipNetworks(controller);
        boolean scopedItems = hasConfiguredRun(controller, ShipLogisticsRun.ResourceType.ITEM);
        boolean scopedFluids = hasConfiguredRun(controller, ShipLogisticsRun.ResourceType.FLUID);
        boolean scopedEnergy = hasConfiguredRun(controller, ShipLogisticsRun.ResourceType.ENERGY);
        boolean networkTracksItems = false;
        boolean networkTracksFluids = false;
        boolean networkTracksEnergy = false;
        long items = 0L;
        long fluids = 0L;
        long energy = 0L;
        for (ShipStockNetworkCache.Network network : networks) {
            if (network.isFuelRun()) continue;
            boolean itemNetwork = scopedItems
                    ? network.resourceType() == ShipLogisticsRun.ResourceType.ITEM
                    : network.supportsItems();
            boolean fluidNetwork = scopedFluids
                    ? network.resourceType() == ShipLogisticsRun.ResourceType.FLUID
                    : network.supportsFluids();
            boolean energyNetwork = scopedEnergy
                    ? network.resourceType() == ShipLogisticsRun.ResourceType.ENERGY
                    : network.supportsEnergy();
            networkTracksItems |= itemNetwork && network.supportsItems();
            networkTracksFluids |= fluidNetwork && network.supportsFluids();
            networkTracksEnergy |= energyNetwork && network.supportsEnergy();
            if (itemNetwork && network.supportsItems()) {
                for (BigItemStack stack : network.items().getStacks()) {
                    items = saturatingAdd(items, Math.max(0L, stack.count));
                }
            }
            if (fluidNetwork && network.supportsFluids()) {
                for (FluidStack stack : network.fluids()) {
                    fluids = saturatingAdd(fluids, Math.max(0L, stack.getAmount()));
                }
            }
            if (energyNetwork && network.supportsEnergy()) {
                energy = saturatingAdd(energy, Math.max(0L, network.energy()));
            }
        }
        if (!scopedItems && !networkTracksItems) {
            items = 0L;
            for (IItemHandler handler : shipItemHandlers(controller)) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    items = saturatingAdd(items,
                            Math.max(0L, handler.getStackInSlot(slot).getCount()));
                }
            }
        }
        if (!scopedFluids && !networkTracksFluids) {
            fluids = 0L;
            for (IFluidHandler handler : shipFluidHandlers(controller)) {
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    fluids = saturatingAdd(fluids,
                            Math.max(0L, handler.getFluidInTank(tank).getAmount()));
                }
            }
        }
        if (!scopedEnergy && !networkTracksEnergy) {
            energy = 0L;
            for (IEnergyStorage handler : shipEnergyHandlers(controller)) {
                energy = saturatingAdd(energy, Math.max(0L, handler.getEnergyStored()));
            }
        }
        return new ResourceStatus(items, fluids, energy);
    }

    // Check if this has configured run
    private static boolean hasConfiguredRun(
            AdvancedContraptionControllerBlockEntity controller,
            ShipLogisticsRun.ResourceType resourceType
    ) {
        return controller != null && controller.shipLogisticsRuns().stream()
                .anyMatch(run -> run.resourceType() == resourceType);
    }

    // Get the saturating add
    private static long saturatingAdd(long current, long amount) {
        return amount <= 0L || current >= Long.MAX_VALUE - amount
                ? amount <= 0L ? current : Long.MAX_VALUE
                : current + amount;
    }

    // Check if this is a burnable item
    static boolean isBurnableItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && ThrusterBlockEntity.canUseAsSolidFuel(stack);
    }

    // Check if this matches fuel item filters
    private static boolean matchesFuelItemFilters(
            Level level, ItemStack stack, List<ItemStack> filters) {
        if (filters == null || filters.isEmpty()) return true;
        List<ItemStack> fuelFilters = filters.stream()
                .filter(ShipCargoAutomation::isBurnableItem)
                .toList();
        return fuelFilters.isEmpty() || matchesItemFilters(level, stack, fuelFilters);
    }

    // Get the ship block entities
    public static List<BlockEntity> shipBlockEntities(AdvancedContraptionControllerBlockEntity controller) {
        Set<BlockEntity> mapped = Collections.newSetFromMap(new IdentityHashMap<>());
        if (controller.getLevel() != null) {
            for (java.util.UUID subLevelId : controller.getMappedShipSubLevelIds()) {
                Object subLevel = SubLevelBlockEntityCollector.getSubLevel(
                        controller.getLevel(), subLevelId);
                if (subLevel != null) {
                    mapped.addAll(SubLevelBlockEntityCollector.getBlockEntities(subLevel));
                }
            }
        }
        if (!mapped.isEmpty()) {
            return List.copyOf(mapped);
        }
        Object subLevel = SimulatedHelper.getContainingSubLevel(controller);
        if (subLevel != null) {
            return SubLevelBlockEntityCollector.getBlockEntities(subLevel);
        }
        return List.of(controller);
    }

    // Get the ship item handlers
    private static List<IItemHandler> shipItemHandlers(AdvancedContraptionControllerBlockEntity controller) {
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockEntity blockEntity : shipBlockEntities(controller)) {
            if (DockingConnectorAutomation.isDockingConnector(blockEntity)) {
                continue;
            }
            addItemHandler(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity, handlers, seen);
        }
        return handlers;
    }

    // Get the ship fuel item handlers
    private static List<IItemHandler> shipFuelItemHandlers(
            AdvancedContraptionControllerBlockEntity controller) {
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockEntity blockEntity : shipBlockEntities(controller)) {
            if (blockEntity instanceof ThrusterBlockEntity thruster
                    && seen.add(thruster.getItemInventory())) {
                handlers.add(thruster.getItemInventory());
            }
        }
        for (IItemHandler handler : shipItemHandlers(controller)) {
            if (seen.add(handler)) handlers.add(handler);
        }
        return handlers;
    }

    // Get the ship fluid handlers
    private static List<IFluidHandler> shipFluidHandlers(AdvancedContraptionControllerBlockEntity controller) {
        List<IFluidHandler> handlers = new ArrayList<>();
        Set<IFluidHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockEntity blockEntity : shipBlockEntities(controller)) {
            if (DockingConnectorAutomation.isDockingConnector(blockEntity)) {
                continue;
            }
            addFluidHandler(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity, handlers, seen);
        }
        return handlers;
    }

    // Get the ship energy handlers
    private static List<IEnergyStorage> shipEnergyHandlers(AdvancedContraptionControllerBlockEntity controller) {
        List<IEnergyStorage> handlers = new ArrayList<>();
        Set<IEnergyStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockEntity blockEntity : shipBlockEntities(controller)) {
            if (DockingConnectorAutomation.isDockingConnector(blockEntity)) {
                continue;
            }
            addEnergyHandler(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity, handlers, seen);
        }
        return handlers;
    }

    // Get the live fuel run energy handlers
    private static List<IEnergyStorage> liveFuelRunEnergyHandlers(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        Level rootLevel = controller.getLevel();
        if (rootLevel == null) {
            return List.of();
        }
        List<IEnergyStorage> handlers = new ArrayList<>();
        Set<IEnergyStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ShipLogisticsRun run : controller.shipLogisticsRuns()) {
            if (!run.isFuelRun()) {
                continue;
            }
            for (ShipLogisticsRun.Endpoint endpoint : run.endpoints()) {
                UUID subLevelId = endpoint.subLevelId().equals(new UUID(0L, 0L))
                        ? null : endpoint.subLevelId();
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        rootLevel, subLevelId, endpoint.position());
                addFuelEndpointEnergyHandlers(blockEntity, handlers, seen);
            }
        }
        return List.copyOf(handlers);
    }

    // Get the live fuel run fluid handlers
    private static List<IFluidHandler> liveFuelRunFluidHandlers(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        Level rootLevel = controller.getLevel();
        if (rootLevel == null) {
            return List.of();
        }
        List<IFluidHandler> handlers = new ArrayList<>();
        Set<IFluidHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ShipLogisticsRun run : controller.shipLogisticsRuns()) {
            if (!run.isFuelRun()) {
                continue;
            }
            for (ShipLogisticsRun.Endpoint endpoint : run.endpoints()) {
                UUID subLevelId = endpoint.subLevelId().equals(new UUID(0L, 0L))
                        ? null : endpoint.subLevelId();
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        rootLevel, subLevelId, endpoint.position());
                addFuelEndpointFluidHandlers(blockEntity, handlers, seen);
            }
        }
        return List.copyOf(handlers);
    }

    // Add the fuel endpoint fluid handlers
    private static void addFuelEndpointFluidHandlers(
            @org.jetbrains.annotations.Nullable BlockEntity blockEntity,
            List<IFluidHandler> handlers,
            Set<IFluidHandler> seen
    ) {
        if (blockEntity == null || blockEntity.isRemoved()
                || DockingConnectorAutomation.isDockingConnector(blockEntity)) {
            return;
        }
        if (blockEntity instanceof ShippingManifestBlockEntity manifest) {
            Level level = manifest.getLevel();
            if (level == null) {
                return;
            }
            BlockPos target = ShippingManifestBlock.attachedTargetPos(
                    manifest.getBlockPos(), manifest.getBlockState());
            Direction side = ShippingManifestBlock.attachedTargetSide(manifest.getBlockState());
            BlockEntity targetBlockEntity = level.getBlockEntity(target);
            IFluidHandler handler = targetBlockEntity instanceof ThrusterBlockEntity thruster
                    && thruster.canAcceptFuel()
                    ? thruster.getFuelTank()
                    : ShippingManifestBlock.resolveFluidHandler(
                            level, target, side, manifest.isCombinedManifest());
            if (handler != null && seen.add(handler)) {
                handlers.add(handler);
            }
            return;
        }
        if (blockEntity instanceof ThrusterBlockEntity thruster && thruster.canAcceptFuel()) {
            if (seen.add(thruster.getFuelTank())) {
                handlers.add(thruster.getFuelTank());
            }
            return;
        }
        addFluidHandler(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity, handlers, seen);
    }

    // Add the fuel endpoint energy handlers
    private static void addFuelEndpointEnergyHandlers(
            @org.jetbrains.annotations.Nullable BlockEntity blockEntity,
            List<IEnergyStorage> handlers,
            Set<IEnergyStorage> seen
    ) {
        if (blockEntity == null || blockEntity.isRemoved()
                || DockingConnectorAutomation.isDockingConnector(blockEntity)) {
            return;
        }
        if (blockEntity instanceof ShippingManifestBlockEntity manifest) {
            Level level = manifest.getLevel();
            if (level == null) {
                return;
            }
            BlockPos target = ShippingManifestBlock.attachedTargetPos(
                    manifest.getBlockPos(), manifest.getBlockState());
            Direction side = ShippingManifestBlock.attachedTargetSide(manifest.getBlockState());
            BlockEntity targetBlockEntity = level.getBlockEntity(target);
            IEnergyStorage handler = targetBlockEntity instanceof ThrusterBlockEntity thruster
                    && thruster.isFocusedMode()
                    ? thruster.getEnergyStorage()
                    : ShippingManifestBlock.findEnergyHandler(level, target, side);
            if (handler != null && seen.add(handler)) {
                handlers.add(handler);
            }
            return;
        }
        if (blockEntity instanceof ThrusterBlockEntity thruster && thruster.isFocusedMode()) {
            if (seen.add(thruster.getEnergyStorage())) {
                handlers.add(thruster.getEnergyStorage());
            }
            return;
        }
        addEnergyHandler(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity, handlers, seen);
    }

    // Get the dock item handlers
    private static List<IItemHandler> dockItemHandlers(AdvancedContraptionControllerBlockEntity controller,
                                                       ShipDockRegistry.Dock dock) {
        ServerLevel level = dockLevel(controller, dock);
        if (level == null) {
            return List.of();
        }
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (dock.connectorPos() != null) {
            addItemHandlerAt(level, dock.connectorSubLevelId(),
                    dock.connectorPos(), handlers, seen);
            for (Direction dir : Direction.values()) {
                addItemHandlerAt(level, dock.connectorSubLevelId(),
                        dock.connectorPos().relative(dir), handlers, seen);
            }
        } else {
            for (Direction dir : Direction.values()) {
                addItemHandlerAt(level, dock.subLevelId(),
                        dock.pos().relative(dir), handlers, seen);
            }
        }
        for (IItemHandler handler : dockStockAccess(controller, dock).items()) {
            if (seen.add(handler)) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    // Get the dock dropoff item handlers
    private static List<IItemHandler> dockDropoffItemHandlers(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            ShipDockBlockEntity shipDock
    ) {
        ServerLevel level = dockLevel(controller, dock);
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (level != null) {
            UUID subLevelId = dock.connectorPos() == null
                    ? dock.subLevelId() : dock.connectorSubLevelId();
            BlockPos origin = dock.connectorPos() == null ? dock.pos() : dock.connectorPos();
            for (Direction dir : Direction.values()) {
                BlockPos pos = origin.relative(dir);
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        level, subLevelId, pos);
                if (DockingConnectorAutomation.isDockingConnector(blockEntity)) {
                    continue;
                }
                addItemHandler(level, pos, blockEntity,
                        dir.getOpposite(), handlers, seen);
            }
        }
        IItemHandler buffer = shipDock.getItemBuffer();
        if (seen.add(buffer)) {
            handlers.add(buffer);
        }
        return List.copyOf(handlers);
    }

    // Get the dock fluid handlers
    private static List<IFluidHandler> dockFluidHandlers(AdvancedContraptionControllerBlockEntity controller,
                                                         ShipDockRegistry.Dock dock) {
        ServerLevel level = dockLevel(controller, dock);
        if (level == null) {
            return List.of();
        }
        List<IFluidHandler> handlers = new ArrayList<>();
        Set<IFluidHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (dock.connectorPos() != null) {
            addFluidHandlerAt(level, dock.connectorSubLevelId(),
                    dock.connectorPos(), handlers, seen);
            for (Direction dir : Direction.values()) {
                addFluidHandlerAt(level, dock.connectorSubLevelId(),
                        dock.connectorPos().relative(dir), handlers, seen);
            }
        } else {
            for (Direction dir : Direction.values()) {
                addFluidHandlerAt(level, dock.subLevelId(),
                        dock.pos().relative(dir), handlers, seen);
            }
        }
        for (IFluidHandler handler : dockStockAccess(controller, dock).fluids()) {
            if (seen.add(handler)) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    // Get the dock energy handlers
    private static List<IEnergyStorage> dockEnergyHandlers(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock
    ) {
        ServerLevel level = dockLevel(controller, dock);
        if (level == null) {
            return List.of();
        }
        List<IEnergyStorage> handlers = new ArrayList<>();
        Set<IEnergyStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (dock.connectorPos() != null) {
            addEnergyHandlerAt(level, dock.connectorSubLevelId(),
                    dock.connectorPos(), handlers, seen);
            for (Direction dir : Direction.values()) {
                addEnergyHandlerAt(level, dock.connectorSubLevelId(),
                        dock.connectorPos().relative(dir), handlers, seen);
            }
        } else {
            for (Direction dir : Direction.values()) {
                addEnergyHandlerAt(level, dock.subLevelId(),
                        dock.pos().relative(dir), handlers, seen);
            }
        }
        for (IEnergyStorage handler : dockStockAccess(controller, dock).energy()) {
            if (seen.add(handler)) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    // Add the item handler
    private static void addItemHandlerAt(
            Level level,
            @org.jetbrains.annotations.Nullable java.util.UUID subLevelId,
            BlockPos pos,
            List<IItemHandler> handlers,
            Set<IItemHandler> seen
    ) {
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, subLevelId, pos);
        addItemHandler(level, pos, blockEntity, handlers, seen);
    }

    // Add the fluid handler
    private static void addFluidHandlerAt(
            Level level,
            @org.jetbrains.annotations.Nullable java.util.UUID subLevelId,
            BlockPos pos,
            List<IFluidHandler> handlers,
            Set<IFluidHandler> seen
    ) {
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, subLevelId, pos);
        addFluidHandler(level, pos, blockEntity, handlers, seen);
    }

    // Add the energy handler
    private static void addEnergyHandlerAt(
            Level level,
            @org.jetbrains.annotations.Nullable java.util.UUID subLevelId,
            BlockPos pos,
            List<IEnergyStorage> handlers,
            Set<IEnergyStorage> seen
    ) {
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, subLevelId, pos);
        addEnergyHandler(level, pos, blockEntity, handlers, seen);
    }

    // Get the dock level
    private static ServerLevel dockLevel(AdvancedContraptionControllerBlockEntity controller,
                                         ShipDockRegistry.Dock dock) {
        if (controller.getLevel() == null || controller.getLevel().getServer() == null) {
            return null;
        }
        return controller.getLevel().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dock.dimension()));
    }

    // Get the dock stock access
    private static ShipStockNetworkCache.NetworkAccess dockStockAccess(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock) {
        ServerLevel level = dockLevel(controller, dock);
        if (level == null || dock.connectorPos() == null) {
            return new ShipStockNetworkCache.NetworkAccess(List.of(), List.of(), List.of());
        }
        BlockEntity stationConnector = DockingConnectorAutomation.resolve(
                level, dock.connectorSubLevelId(), dock.connectorPos());
        BlockEntity shipDock = SimulatedHelper.findLoadedBlockEntityExact(
                level, dock.subLevelId(), dock.pos());
        return WirelessDockingTransfer.nearbyDockStock(stationConnector, shipDock);
    }

    // Add the handlers
    private static <T> List<T> appendHandlers(List<T> first, List<T> second) {
        List<T> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    // Get the dock block entity
    private static ShipDockBlockEntity dockBlockEntity(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock
    ) {
        ServerLevel level = dockLevel(controller, dock);
        if (level == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, dock.subLevelId(), dock.pos());
        return blockEntity instanceof ShipDockBlockEntity shipDock ? shipDock : null;
    }

    // Get the dock stock summaries
    private static List<InventorySummary> dockStockSummaries(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock
    ) {
        ServerLevel level = dockLevel(controller, dock);
        if (level == null || dock.connectorPos() == null) {
            return List.of();
        }
        return stockSummariesAt(level, dock.connectorSubLevelId(), dock.connectorPos());
    }

    // Get the ship networks
    private static List<ShipStockNetworkCache.Network> shipNetworks(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        return List.copyOf(controller.getShipStockNetworkSnapshot().networks().values());
    }

    // Get the ship stock summaries
    private static List<InventorySummary> shipStockSummaries(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        return shipNetworks(controller).stream()
                .filter(network -> !network.isFuelRun())
                .map(ShipStockNetworkCache.Network::items)
                .filter(summary -> summary != null && !summary.isEmpty())
                .toList();
    }

    // Get the ship connector networks
    private static List<ShipStockNetworkCache.Network> shipConnectorNetworks(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector
    ) {
        if (connector == null) {
            return List.of();
        }
        return controller.getShipStockNetworkSnapshot().networksFor(
                connector.subLevelId(), connector.blockPosition());
    }

    // Get the ship connector stock summaries
    private static List<InventorySummary> shipConnectorStockSummaries(
            AdvancedContraptionControllerBlockEntity controller,
            ShipControlModuleRuntime.MappedDockingConnector connector
    ) {
        List<ShipStockNetworkCache.Network> networks = shipConnectorNetworks(controller, connector);
        List<InventorySummary> summaries = networks.stream()
                .filter(network -> !network.isFuelRun())
                .map(ShipStockNetworkCache.Network::items)
                .filter(summary -> summary != null && !summary.isEmpty())
                .toList();
        if (!summaries.isEmpty() || !networks.isEmpty()) {
            return summaries;
        }
        return stockSummariesAt(controller.getLevel(), connector.subLevelId(),
                connector.blockPosition());
    }

    // Get the stock summaries
    private static List<InventorySummary> stockSummariesAt(
            Level level,
            java.util.UUID subLevelId,
            BlockPos connectorPos
    ) {
        if (level == null || connectorPos == null) return List.of();
        List<InventorySummary> summaries = new ArrayList<>();
        Set<UUID> frequencies = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, subLevelId, connectorPos.relative(dir));
            if (!(blockEntity instanceof PackagerLinkBlockEntity stockLink)
                    || stockLink.behaviour == null
                    || !frequencies.add(stockLink.behaviour.freqId)) {
                continue;
            }
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(
                    stockLink.behaviour.freqId, true);
            if (summary != null && !summary.isEmpty()) {
                summaries.add(summary);
            }
        }
        return List.copyOf(summaries);
    }

    // Check if this has stock link
    private static boolean hasStockLinkAt(
            Level level, java.util.UUID subLevelId, BlockPos connectorPos
    ) {
        if (level == null || connectorPos == null) return false;
        for (Direction dir : Direction.values()) {
            if (SimulatedHelper.findLoadedBlockEntityExact(
                    level, subLevelId, connectorPos.relative(dir))
                    instanceof PackagerLinkBlockEntity stockLink
                    && stockLink.behaviour != null) {
                return true;
            }
        }
        return false;
    }

    // Add the item handler
    private static void addItemHandler(Level level, BlockPos pos, BlockEntity blockEntity,
                                       List<IItemHandler> handlers, Set<IItemHandler> seen) {
        addItemHandler(level, pos, blockEntity, null, handlers, seen);
    }

    // Add the item handler
    private static void addItemHandler(
            Level level,
            BlockPos pos,
            BlockEntity blockEntity,
            @org.jetbrains.annotations.Nullable Direction side,
            List<IItemHandler> handlers,
            Set<IItemHandler> seen
    ) {
        if (level == null || blockEntity instanceof ShipDockBlockEntity) {
            return;
        }
        Level capabilityLevel = blockEntity == null ? level : blockEntity.getLevel();
        BlockPos capabilityPos = blockEntity == null ? pos : blockEntity.getBlockPos();
        if (capabilityLevel == null) {
            return;
        }
        IItemHandler handler = capabilityLevel.getCapability(Capabilities.ItemHandler.BLOCK, capabilityPos,
                blockEntity == null ? capabilityLevel.getBlockState(capabilityPos) : blockEntity.getBlockState(),
                blockEntity, side);
        if (handler != null && seen.add(handler)) {
            handlers.add(handler);
        }
    }

    // Add the fluid handler
    private static void addFluidHandler(Level level, BlockPos pos, BlockEntity blockEntity,
                                        List<IFluidHandler> handlers, Set<IFluidHandler> seen) {
        if (level == null || blockEntity instanceof ShipDockBlockEntity) {
            return;
        }
        Level capabilityLevel = blockEntity == null ? level : blockEntity.getLevel();
        BlockPos capabilityPos = blockEntity == null ? pos : blockEntity.getBlockPos();
        if (capabilityLevel == null) {
            return;
        }
        IFluidHandler handler = capabilityLevel.getCapability(Capabilities.FluidHandler.BLOCK, capabilityPos,
                blockEntity == null ? capabilityLevel.getBlockState(capabilityPos) : blockEntity.getBlockState(),
                blockEntity, null);
        if (handler != null && seen.add(handler)) {
            handlers.add(handler);
        }
    }

    // Add the energy handler
    private static void addEnergyHandler(Level level, BlockPos pos, BlockEntity blockEntity,
                                         List<IEnergyStorage> handlers, Set<IEnergyStorage> seen) {
        if (level == null || blockEntity instanceof ShipDockBlockEntity) {
            return;
        }
        Level capabilityLevel = blockEntity == null ? level : blockEntity.getLevel();
        BlockPos capabilityPos = blockEntity == null ? pos : blockEntity.getBlockPos();
        if (capabilityLevel == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = blockEntity == null
                ? capabilityLevel.getBlockState(capabilityPos) : blockEntity.getBlockState();
        IEnergyStorage unsided = capabilityLevel.getCapability(Capabilities.EnergyStorage.BLOCK,
                capabilityPos, state, blockEntity, null);
        if (unsided != null) {
            if (seen.add(unsided)) {
                handlers.add(unsided);
            }
            return;
        }
        for (Direction dir : Direction.values()) {
            addEnergyHandler(capabilityLevel, capabilityPos, state, blockEntity, dir, handlers, seen);
        }
    }

    // Add the energy handler
    private static void addEnergyHandler(
            Level level,
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            @org.jetbrains.annotations.Nullable BlockEntity blockEntity,
            @org.jetbrains.annotations.Nullable Direction side,
            List<IEnergyStorage> handlers,
            Set<IEnergyStorage> seen
    ) {
        IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                pos, state, blockEntity, side);
        if (handler != null && seen.add(handler)) {
            handlers.add(handler);
        }
    }

    // Transfer cargo items
    private static int transferItems(List<IItemHandler> sources, List<IItemHandler> destinations,
                                     Predicate<ItemStack> predicate) {
        int moved = 0;
        for (IItemHandler src : sources) {
            for (int slot = 0; slot < src.getSlots(); slot++) {
                ItemStack stack = src.getStackInSlot(slot);
                ItemStack available = stack.isEmpty()
                        ? ItemStack.EMPTY : src.extractItem(slot, stack.getCount(), true);
                if (available.isEmpty() || !predicate.test(available)) {
                    continue;
                }
                int accepted = available.getCount() - insert(destinations, available, true).getCount();
                if (accepted <= 0) {
                    continue;
                }
                ItemStack extracted = src.extractItem(slot, accepted, false);
                ItemStack remainder = insert(destinations, extracted, false);
                moved += extracted.getCount() - remainder.getCount();
                if (!remainder.isEmpty()) {
                    insert(List.of(src), remainder, false);
                }
            }
        }
        return moved;
    }

    // Transfer cargo fluids
    private static int transferFluids(
            List<IFluidHandler> sources,
            List<IFluidHandler> destinations,
            Predicate<FluidStack> predicate
    ) {
        int moved = 0;
        for (IFluidHandler src : sources) {
            for (int tank = 0; tank < src.getTanks(); tank++) {
                FluidStack available = src.getFluidInTank(tank);
                if (available.isEmpty() || !predicate.test(available)) {
                    continue;
                }
                FluidStack remaining = available.copy();
                for (IFluidHandler destination : destinations) {
                    if (remaining.isEmpty()) {
                        break;
                    }
                    int accepted = destination.fill(
                            remaining, IFluidHandler.FluidAction.SIMULATE);
                    if (accepted <= 0) {
                        continue;
                    }
                    FluidStack drained = src.drain(
                            remaining.copyWithAmount(accepted),
                            IFluidHandler.FluidAction.EXECUTE);
                    int inserted = destination.fill(
                            drained, IFluidHandler.FluidAction.EXECUTE);
                    moved += inserted;
                    if (inserted < drained.getAmount()) {
                        src.fill(drained.copyWithAmount(
                                drained.getAmount() - inserted),
                                IFluidHandler.FluidAction.EXECUTE);
                    }
                    remaining = src.getFluidInTank(tank);
                }
            }
        }
        return moved;
    }

    // Check if this matches item filters
    private static boolean matchesItemFilters(
            Level level, ItemStack stack, List<ItemStack> filters
    ) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (filters == null || filters.isEmpty() || level == null) {
            return true;
        }
        return filters.stream().anyMatch(filter ->
                filter == null || filter.isEmpty()
                        || FilterItemStack.of(filter).test(level, stack));
    }

    // Check if this matches fluid filters
    private static boolean matchesFluidFilters(
            Level level, FluidStack stack, List<ItemStack> filters
    ) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (filters == null || filters.isEmpty() || level == null) {
            return true;
        }
        return filters.stream().anyMatch(filter ->
                filter == null || filter.isEmpty()
                        || FilterItemStack.of(filter).test(level, stack));
    }

    // Check if this contains item
    private static boolean containsItem(List<IItemHandler> handlers, Predicate<ItemStack> predicate) {
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && predicate.test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Insert the ship cargo automation
    private static ItemStack insert(List<IItemHandler> destinations, ItemStack original, boolean simulate) {
        ItemStack remainder = original.copy();
        for (IItemHandler destination : destinations) {
            for (int slot = 0; slot < destination.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = destination.insertItem(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    // Transfer cargo energy
    private static int transferEnergy(
            List<IEnergyStorage> sources,
            List<IEnergyStorage> destinations
    ) {
        int moved = 0;
        for (IEnergyStorage src : sources) {
            if (!src.canExtract()) {
                continue;
            }
            for (IEnergyStorage destination : destinations) {
                if (src == destination || !destination.canReceive()) {
                    continue;
                }
                int available = src.extractEnergy(src.getEnergyStored(), true);
                int accepted = destination.receiveEnergy(available, true);
                if (accepted <= 0) {
                    continue;
                }
                int extracted = src.extractEnergy(accepted, false);
                int inserted = destination.receiveEnergy(extracted, false);
                moved += inserted;
                if (inserted < extracted && src.canReceive()) {
                    src.receiveEnergy(extracted - inserted, false);
                }
            }
        }
        return moved;
    }

    // Get the balance fuel fluids
    private static int balanceFuelFluids(
            List<IFluidHandler> handlers,
            Level level,
            List<ItemStack> filters
    ) {
        List<IFluidHandler> pool = distinctHandlers(handlers);
        if (pool.size() < 2) {
            return 0;
        }
        List<FluidStack> fuelTypes = new ArrayList<>();
        for (IFluidHandler handler : pool) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (stack.isEmpty() || !matchesFluidFilters(level, stack, filters)
                        || fuelTypes.stream().anyMatch(existing ->
                        FluidStack.isSameFluidSameComponents(existing, stack))) {
                    continue;
                }
                fuelTypes.add(stack.copyWithAmount(1));
            }
        }
        int moved = 0;
        for (FluidStack fuelType : fuelTypes) {
            moved += balanceFuelFluid(pool, fuelType);
        }
        return moved;
    }

    // Get the balance fuel fluid
    private static int balanceFuelFluid(List<IFluidHandler> pool, FluidStack fuelType) {
        // -----------------------------------------------------FLUID POOL-----------------------------------------------------
        List<FluidPoolState> states = new ArrayList<>();
        long totalAmount = 0L;
        long totalCapacity = 0L;
        for (IFluidHandler handler : pool) {
            FluidPoolState state = fluidPoolState(handler, fuelType);
            if (state.capacity() <= 0L) {
                continue;
            }
            FluidStack drainProbe = state.amount() <= 0L
                    ? FluidStack.EMPTY : handler.drain(
                    fuelType.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE);
            boolean canDrain = !drainProbe.isEmpty()
                    && FluidStack.isSameFluidSameComponents(drainProbe, fuelType);
            boolean canFill = handler.fill(
                    fuelType.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE) > 0;
            if (!canDrain && !canFill) {
                continue;
            }
            states.add(state);
            totalAmount += state.amount();
            totalCapacity += state.capacity();
        }
        if (states.size() < 2 || totalAmount <= 0L || totalCapacity <= 0L) {
            return 0;
        }

        // ------------------------------------BALANCE TARGETS------------------------------------
        double rawFillRatio = totalAmount / (double) totalCapacity;
        double poolFillRatio = Double.isFinite(rawFillRatio)
                ? Math.min(1.0D, Math.max(0.0D, rawFillRatio)) : 0.0D;
        List<FluidBalance> sources = new ArrayList<>();
        List<FluidBalance> destinations = new ArrayList<>();
        for (FluidPoolState state : states) {
            long target = Math.min(state.capacity(), Math.max(0L,
                    (long) Math.floor(poolFillRatio * state.capacity())));
            if (state.amount() > target && !state.handler().drain(
                    fuelType.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
                sources.add(new FluidBalance(state.handler(), state.amount() - target));
            }
            if (state.amount() < target && state.handler().fill(
                    fuelType.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE) > 0) {
                destinations.add(new FluidBalance(state.handler(), target - state.amount()));
            }
        }
        sources.sort(Comparator.comparingLong(FluidBalance::amount).reversed());
        destinations.sort(Comparator.comparingLong(FluidBalance::amount).reversed());

        // ------------------------------------FLUID TRANSFER------------------------------------
        int moved = 0;
        int sourceIndex = 0;
        int destinationIndex = 0;
        while (sourceIndex < sources.size() && destinationIndex < destinations.size()) {
            FluidBalance src = sources.get(sourceIndex);
            FluidBalance destination = destinations.get(destinationIndex);
            if (src.handler() == destination.handler()) {
                if (src.amount() <= destination.amount()) {
                    sourceIndex++;
                } else {
                    destinationIndex++;
                }
                continue;
            }
            int requested = (int) Math.min(Integer.MAX_VALUE,
                    Math.min(src.amount(), destination.amount()));
            FluidStack extractable = src.handler().drain(
                    fuelType.copyWithAmount(requested), IFluidHandler.FluidAction.SIMULATE);
            int accepted = extractable.isEmpty() ? 0 : destination.handler().fill(
                    extractable, IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) {
                if (extractable.isEmpty()) {
                    sourceIndex++;
                } else {
                    destinationIndex++;
                }
                continue;
            }
            FluidStack drained = src.handler().drain(
                    fuelType.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
            int inserted = drained.isEmpty() ? 0 : destination.handler().fill(
                    drained, IFluidHandler.FluidAction.EXECUTE);
            if (inserted < drained.getAmount()) {
                src.handler().fill(drained.copyWithAmount(drained.getAmount() - inserted),
                        IFluidHandler.FluidAction.EXECUTE);
            }
            moved += inserted;
            src = src.withAmount(src.amount() - inserted);
            destination = destination.withAmount(destination.amount() - inserted);
            sources.set(sourceIndex, src);
            destinations.set(destinationIndex, destination);
            if (src.amount() <= 0L || drained.getAmount() < requested) {
                sourceIndex++;
            }
            if (destination.amount() <= 0L || inserted < drained.getAmount()) {
                destinationIndex++;
            }
        }
        return moved;
    }

    // Get the fluid pool state
    private static FluidPoolState fluidPoolState(IFluidHandler handler, FluidStack fuelType) {
        long amount = 0L;
        long capacity = 0L;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stored = handler.getFluidInTank(tank);
            boolean matchingStored = !stored.isEmpty()
                    && FluidStack.isSameFluidSameComponents(stored, fuelType);
            if (matchingStored || stored.isEmpty() && handler.isFluidValid(tank, fuelType)) {
                capacity += Math.max(0, handler.getTankCapacity(tank));
                if (matchingStored) {
                    amount += Math.max(0, stored.getAmount());
                }
            }
        }
        return new FluidPoolState(handler, amount, capacity);
    }

    // Store fluid pool state
    private record FluidPoolState(IFluidHandler handler, long amount, long capacity) {
    }

    // Store the fluid balance
    private record FluidBalance(IFluidHandler handler, long amount) {
        // Copy the fluid balance with the amount
        private FluidBalance withAmount(long amount) {
            return new FluidBalance(handler, Math.max(0L, amount));
        }
    }

    // Get the balance fuel energy
    private static int balanceFuelEnergy(List<IEnergyStorage> handlers) {
        List<IEnergyStorage> pool = distinctHandlers(handlers);
        if (pool.size() < 2) {
            return 0;
        }
        long totalEnergy = 0L;
        long totalCapacity = 0L;
        for (IEnergyStorage handler : pool) {
            int capacity = Math.max(0, handler.getMaxEnergyStored());
            totalCapacity += capacity;
            totalEnergy += Math.min(capacity, Math.max(0, handler.getEnergyStored()));
        }
        if (totalCapacity <= 0L || totalEnergy <= 0L) {
            return 0;
        }
        List<EnergyBalance> sources = new ArrayList<>();
        List<EnergyBalance> destinations = new ArrayList<>();
        for (IEnergyStorage handler : pool) {
            int capacity = Math.max(0, handler.getMaxEnergyStored());
            if (capacity <= 0) {
                continue;
            }
            long target = totalEnergy * capacity / totalCapacity;
            long stored = Math.min(capacity, Math.max(0, handler.getEnergyStored()));
            if (handler.canExtract() && stored > target) {
                sources.add(new EnergyBalance(handler, stored - target));
            }
            if (handler.canReceive() && stored < target) {
                destinations.add(new EnergyBalance(handler, target - stored));
            }
        }
        sources.sort(Comparator.comparingLong(EnergyBalance::amount).reversed());
        destinations.sort(Comparator.comparingLong(EnergyBalance::amount).reversed());
        int moved = 0;
        int sourceIndex = 0;
        int destinationIndex = 0;
        while (sourceIndex < sources.size() && destinationIndex < destinations.size()) {
            EnergyBalance src = sources.get(sourceIndex);
            EnergyBalance destination = destinations.get(destinationIndex);
            if (src.handler() == destination.handler()) {
                if (src.amount() <= destination.amount()) {
                    sourceIndex++;
                } else {
                    destinationIndex++;
                }
                continue;
            }
            int requested = (int) Math.min(Integer.MAX_VALUE,
                    Math.min(src.amount(), destination.amount()));
            int extractable = src.handler().extractEnergy(requested, true);
            int accepted = destination.handler().receiveEnergy(extractable, true);
            if (accepted <= 0) {
                if (extractable <= 0) {
                    sourceIndex++;
                } else {
                    destinationIndex++;
                }
                continue;
            }
            int extracted = src.handler().extractEnergy(accepted, false);
            int inserted = destination.handler().receiveEnergy(extracted, false);
            if (inserted < extracted && src.handler().canReceive()) {
                src.handler().receiveEnergy(extracted - inserted, false);
            }
            moved += inserted;
            src = src.withAmount(src.amount() - inserted);
            destination = destination.withAmount(destination.amount() - inserted);
            sources.set(sourceIndex, src);
            destinations.set(destinationIndex, destination);
            if (src.amount() <= 0L || extracted < requested) {
                sourceIndex++;
            }
            if (destination.amount() <= 0L || inserted < extracted) {
                destinationIndex++;
            }
        }
        return moved;
    }

    // Store the energy balance
    private record EnergyBalance(IEnergyStorage handler, long amount) {
        // Copy the energy balance with the amount
        private EnergyBalance withAmount(long amount) {
            return new EnergyBalance(handler, Math.max(0L, amount));
        }
    }

    // Get the distinct handlers
    private static <T> List<T> distinctHandlers(List<T> handlers) {
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<T> res = new ArrayList<>();
        for (T handler : handlers == null ? List.<T>of() : handlers) {
            if (handler != null && seen.add(handler)) {
                res.add(handler);
            }
        }
        return res;
    }

    // Store the fuel status
    public record FuelStatus(double amount, double capacity, double usePerTick, boolean infinite) {
        // Get the ratio
        public double ratio() {
            if (infinite) {
                return 1.0D;
            }
            return capacity <= 0.0D ? 1.0D : amount / capacity;
        }

        // Check if this can reach
        public boolean canReach(double distance, double speed) {
            if (infinite || usePerTick <= 0.0D) {
                return ratio() >= 0.1D;
            }
            double travelTicks = distance / Math.max(0.05D, speed) * 20.0D;
            return amount - usePerTick * travelTicks >= capacity * 0.1D;
        }
    }

    // Store the resource status
    record ResourceStatus(long items, long fluids, long energy) {
        // Initialize the resource status
        ResourceStatus {
            items = Math.max(0L, items);
            fluids = Math.max(0L, fluids);
            energy = Math.max(0L, energy);
        }
    }
}
