package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointSnapshot;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

// Read worker-managed ship and dock storage without creating wireless transfer paths
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
                                                           FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Check whether manifested or smart ship storage contains any package
    public static boolean hasPackages(AdvancedContraptionControllerBlockEntity controller) {
        return shipItems(controller).stream().anyMatch(PackageItem::isPackage);
    }

    // Check whether manifested or smart ship storage contains a package for this dock
    public static boolean hasPackageForDock(
            AdvancedContraptionControllerBlockEntity controller, String dockName
    ) {
        return shipItems(controller).stream().anyMatch(stack -> PackageItem.isPackage(stack)
                && PackageItem.matchAddress(stack, dockName));
    }

    // Check whether the dock worker buffer contains a matching package
    public static boolean dockHasPackage(AdvancedContraptionControllerBlockEntity controller,
                                         ShipDockRegistry.Dock dock, String addressFilter) {
        String filter = addressFilter == null ? "" : addressFilter.strip();
        return dockItems(controller, dock).stream().anyMatch(stack -> PackageItem.isPackage(stack)
                && (filter.isEmpty() || PackageItem.matchAddress(stack, filter)));
    }

    // Check whether the dock worker buffer has ordinary item cargo available
    public static boolean dockHasRestockCargo(
            AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock
    ) {
        return dockItems(controller, dock).stream().anyMatch(stack -> !PackageItem.isPackage(stack));
    }

    // Check whether the dock worker buffer has fuel or FE available
    public static boolean dockHasFuelSupply(
            AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock
    ) {
        WorkerStorageEndpoint endpoint = dockEndpoint(controller, dock);
        if (endpoint == null) return false;
        return !endpoint.stockedFluids(WorkerResourceType.FUEL).isEmpty()
                || endpoint.stockedItems().stream().anyMatch(ShipCargoAutomation::isBurnableItem)
                || endpoint.storedEnergy() > 0L;
    }

    // Check whether the dock worker buffer has room for a ship package
    public static boolean dockCanReceivePackage(
            AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock
    ) {
        WorkerStorageEndpoint endpoint = dockEndpoint(controller, dock);
        if (endpoint == null) return false;
        return shipItems(controller).stream().anyMatch(stack -> PackageItem.isPackage(stack)
                && PackageItem.matchAddress(stack, dock.name()) && endpoint.canInsert(stack));
    }

    // Count exact items in the dock worker buffer
    public static long countDockItems(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            ItemStack filter
    ) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long amount = 0L;
        for (ItemStack stack : dockItems(controller, dock)) {
            if (predicate.test(level, stack)) amount = saturatingAdd(amount, stack.getCount());
        }
        return amount;
    }

    // Count exact fluids in the dock worker buffer
    public static long countDockFluids(
            AdvancedContraptionControllerBlockEntity controller,
            ShipDockRegistry.Dock dock,
            ItemStack filter
    ) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long amount = 0L;
        for (FluidStack stack : dockFluids(controller, dock)) {
            if (predicate.test(level, stack)) amount = saturatingAdd(amount, stack.getAmount());
        }
        return amount;
    }

    // Count FE in the dock worker buffer
    public static long countDockEnergy(
            AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock
    ) {
        WorkerStorageEndpoint endpoint = dockEndpoint(controller, dock);
        return endpoint == null ? 0L : endpoint.storedEnergy();
    }

    // Count exact worker-managed ship items
    public static long countItems(AdvancedContraptionControllerBlockEntity controller, ItemStack filter) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long amount = 0L;
        for (ItemStack stack : shipItems(controller)) {
            if (predicate.test(level, stack)) amount = saturatingAdd(amount, stack.getCount());
        }
        return amount;
    }

    // Count exact full worker-managed ship item stacks
    public static long countFullItemStacks(
            AdvancedContraptionControllerBlockEntity controller, ItemStack filter
    ) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long stacks = 0L;
        for (ItemStack stack : shipItems(controller)) {
            if (predicate.test(level, stack)) {
                stacks = saturatingAdd(stacks, stack.getCount() / Math.max(1, stack.getMaxStackSize()));
            }
        }
        return stacks;
    }

    // Count exact worker-managed ship fluids
    public static long countFluids(AdvancedContraptionControllerBlockEntity controller, ItemStack filter) {
        Level level = controller.getLevel();
        if (level == null) return 0L;
        FilterItemStack predicate = FilterItemStack.of(filter);
        long amount = 0L;
        for (FluidStack stack : shipFluids(controller)) {
            if (predicate.test(level, stack)) amount = saturatingAdd(amount, stack.getAmount());
        }
        return amount;
    }

    // Get the actual propulsion fuel status rather than cargo staging amounts
    public static FuelStatus fuelStatus(AdvancedContraptionControllerBlockEntity controller) {
        return fuelStatus(controller, 1.0D);
    }

    // Get the actual propulsion fuel status at an expected throttle
    public static FuelStatus fuelStatus(
            AdvancedContraptionControllerBlockEntity controller, double expectedThrottle
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
            usePerTick += Math.max(0.0D, thruster.getEstimatedFuelConsumptionMbPerTick(
                    (float) Math.max(0.0D, Math.min(1.0D, expectedThrottle))));
        }
        return new FuelStatus(amount, capacity, usePerTick, infinite);
    }

    // Get all worker-managed ship resources selected by manifests and smart storage
    static ResourceStatus resourceStatus(AdvancedContraptionControllerBlockEntity controller) {
        long items = 0L;
        long fluids = 0L;
        long energy = 0L;
        for (WorkerStorageEndpoint endpoint : shipStorageEndpoints(controller)) {
            for (WorkerEndpointSnapshot.ResourceAmount resource : endpoint.snapshot().resources()) {
                switch (resource.resource().type()) {
                    case ITEM -> items = saturatingAdd(items, resource.amount());
                    case FLUID -> fluids = saturatingAdd(fluids, resource.amount());
                    case ENERGY -> energy = saturatingAdd(energy, resource.amount());
                    case FUEL -> {
                    }
                }
            }
        }
        return new ResourceStatus(items, fluids, energy);
    }

    // Check whether an item can fuel a standard fuel-burning thruster
    static boolean isBurnableItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ThrusterBlockEntity.canUseAsSolidFuel(stack);
    }

    // Get the loaded block entities belonging to the ship's mapped sublevels
    public static List<BlockEntity> shipBlockEntities(AdvancedContraptionControllerBlockEntity controller) {
        Set<BlockEntity> mapped = Collections.newSetFromMap(new IdentityHashMap<>());
        if (controller.getLevel() != null) {
            for (java.util.UUID subLevelId : controller.getMappedShipSubLevelIds()) {
                Object subLevel = SubLevelBlockEntityCollector.getSubLevel(controller.getLevel(), subLevelId);
                if (subLevel != null) mapped.addAll(SubLevelBlockEntityCollector.getBlockEntities(subLevel));
            }
        }
        if (!mapped.isEmpty()) return List.copyOf(mapped);
        Object subLevel = SimulatedHelper.getContainingSubLevel(controller);
        if (subLevel != null) return SubLevelBlockEntityCollector.getBlockEntities(subLevel);
        return List.of(controller);
    }

    // Get the deduplicated manifested and smart storage aboard this ship
    private static List<WorkerStorageEndpoint> shipStorageEndpoints(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        List<WorkerStorageEndpoint> endpoints = new ArrayList<>();
        Set<java.util.UUID> seen = new HashSet<>();
        for (BlockEntity blockEntity : shipBlockEntities(controller)) {
            if (DockingConnectorAutomation.isDockingConnector(blockEntity)) continue;
            WorkerStorageEndpoint endpoint = WorkerStorageEndpoint.managed(blockEntity);
            if (endpoint != null && seen.add(endpoint.id())) endpoints.add(endpoint);
        }
        return List.copyOf(endpoints);
    }

    // Get exact worker-managed item stacks aboard this ship
    private static List<ItemStack> shipItems(AdvancedContraptionControllerBlockEntity controller) {
        List<ItemStack> stacks = new ArrayList<>();
        for (WorkerStorageEndpoint endpoint : shipStorageEndpoints(controller)) {
            stacks.addAll(endpoint.stockedItems());
        }
        return List.copyOf(stacks);
    }

    // Get exact worker-managed fluids aboard this ship
    private static List<FluidStack> shipFluids(AdvancedContraptionControllerBlockEntity controller) {
        List<FluidStack> stacks = new ArrayList<>();
        for (WorkerStorageEndpoint endpoint : shipStorageEndpoints(controller)) {
            stacks.addAll(endpoint.stockedFluids(WorkerResourceType.FLUID));
        }
        return List.copyOf(stacks);
    }

    // Resolve the live Ship Dock worker buffer without reaching a remote stock network
    private static WorkerStorageEndpoint dockEndpoint(
            AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock
    ) {
        if (controller == null || dock == null) return null;
        ServerLevel level = dockLevel(controller, dock);
        if (level == null) return null;
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, dock.subLevelId(), dock.pos());
        return blockEntity instanceof ShipDockBlockEntity ? WorkerStorageEndpoint.managed(blockEntity) : null;
    }

    // Get the exact dock-buffer item stacks
    private static List<ItemStack> dockItems(
            AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock
    ) {
        WorkerStorageEndpoint endpoint = dockEndpoint(controller, dock);
        return endpoint == null ? List.of() : endpoint.stockedItems();
    }

    // Get the exact dock-buffer fluid stacks
    private static List<FluidStack> dockFluids(
            AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock
    ) {
        WorkerStorageEndpoint endpoint = dockEndpoint(controller, dock);
        return endpoint == null ? List.of() : endpoint.stockedFluids(WorkerResourceType.FLUID);
    }

    // Get the loaded server level which owns this dock
    private static ServerLevel dockLevel(
            AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock
    ) {
        if (controller.getLevel() == null || controller.getLevel().getServer() == null) return null;
        return controller.getLevel().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dock.dimension()));
    }

    // Add an amount without wrapping long schedule counters
    private static long saturatingAdd(long current, long amount) {
        return amount <= 0L || current >= Long.MAX_VALUE - amount
                ? amount <= 0L ? current : Long.MAX_VALUE : current + amount;
    }

    // Store physical propulsion fuel status
    public record FuelStatus(double amount, double capacity, double usePerTick, boolean infinite) {
        // Get the fill ratio
        public double ratio() {
            if (infinite) return 1.0D;
            return capacity <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, amount / capacity));
        }

        // Check whether this fuel state can reach one route target
        public boolean canReach(double distance, double speed) {
            if (infinite || usePerTick <= 0.0D) return ratio() >= 0.1D;
            double travelTicks = distance / Math.max(0.05D, speed) * 20.0D;
            return amount - travelTicks * usePerTick >= capacity * 0.1D;
        }
    }

    // Store worker-managed resource totals used by SCM displays and schedule conditions
    record ResourceStatus(long items, long fluids, long energy) {
        ResourceStatus {
            items = Math.max(0L, items);
            fluids = Math.max(0L, fluids);
            energy = Math.max(0L, energy);
        }
    }
}
