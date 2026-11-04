package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting.Pool;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings.Group;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

// Read authored SCM logistics storage and the selected Ship Dock's buffers
public final class ShipCargoAutomation{
    private ShipCargoAutomation(){}

    public static boolean hasPackages(AdvancedContraptionControllerBlockEntity controller){
        return shipItems(controller).stream().anyMatch(PackageItem::isPackage);
    }

    public static boolean hasPackageForDock(AdvancedContraptionControllerBlockEntity controller, String dockName){
        return shipItems(controller).stream().anyMatch(stack -> PackageItem.isPackage(stack) && PackageItem.matchAddress(stack, dockName));
    }

    public static boolean dockHasPackage(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock, String filter){
        return dockItems(controller, dock).stream().anyMatch(stack -> PackageItem.isPackage(stack)
                && (filter == null || filter.isBlank() || PackageItem.matchAddress(stack, filter)));
    }

    public static boolean dockHasRestockCargo(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock){
        return dockItems(controller, dock).stream().anyMatch(stack -> !PackageItem.isPackage(stack));
    }

    public static boolean dockHasFuelSupply(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock){
        return ScmDockingAccess.nativeAccess(() -> {
            Pool source = dockStorage(controller, dock);
            Pool fuel = controller.logistics().pool(Group.FUEL);
            return items(source).stream().anyMatch(stack -> controller.logistics().acceptsFuel(stack)
                    && ResourceRouting.insertItem(fuel, stack.copyWithCount(1), true).isEmpty())
                    || fluids(source).stream().anyMatch(stack -> controller.logistics().acceptsFuel(stack)
                    && ResourceRouting.insertFluid(fuel, stack, true) > 0)
                    || energy(source) > 0 && ResourceRouting.insertEnergy(fuel, 1, true) > 0;
        });
    }

    public static boolean dockCanReceivePackage(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock){
        return ScmDockingAccess.nativeAccess(() -> shipItems(controller).stream().anyMatch(stack -> PackageItem.isPackage(stack)
                && PackageItem.matchAddress(stack, dock.name())
                && ResourceRouting.insertItem(dockStorage(controller, dock), stack.copyWithCount(1), true).isEmpty()));
    }

    public static long countDockItems(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock, ItemStack filter){
        var predicate = FilterItemStack.of(filter);
        return dockItems(controller, dock).stream().filter(stack -> predicate.test(controller.getLevel(), stack)).mapToLong(ItemStack::getCount).sum();
    }

    public static long countDockFluids(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock, ItemStack filter){
        var predicate = FilterItemStack.of(filter);
        return dockFluids(controller, dock).stream().filter(stack -> predicate.test(controller.getLevel(), stack)).mapToLong(FluidStack::getAmount).sum();
    }

    public static long countDockEnergy(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock){
        return ScmDockingAccess.nativeAccess(() -> energy(dockStorage(controller, dock)));
    }

    public static long countItems(AdvancedContraptionControllerBlockEntity controller, ItemStack filter){
        var predicate = FilterItemStack.of(filter);
        return shipItems(controller).stream().filter(stack -> predicate.test(controller.getLevel(), stack)).mapToLong(ItemStack::getCount).sum();
    }

    public static long countFullItemStacks(AdvancedContraptionControllerBlockEntity controller, ItemStack filter){
        var predicate = FilterItemStack.of(filter);
        return shipItems(controller).stream().filter(stack -> predicate.test(controller.getLevel(), stack))
                .mapToLong(stack -> stack.getCount() / Math.max(1, stack.getMaxStackSize())).sum();
    }

    public static long countFluids(AdvancedContraptionControllerBlockEntity controller, ItemStack filter){
        var predicate = FilterItemStack.of(filter);
        return fluids(controller.logistics().pool(Group.FLUIDS)).stream().filter(stack -> predicate.test(controller.getLevel(), stack))
                .mapToLong(FluidStack::getAmount).sum();
    }

    public static FuelStatus fuelStatus(AdvancedContraptionControllerBlockEntity controller){ return fuelStatus(controller, 1.0D); }

    public static FuelStatus fuelStatus(AdvancedContraptionControllerBlockEntity controller, double throttle){
        return controller.logistics().fuelStatus(throttle);
    }

    static ResourceStatus resourceStatus(AdvancedContraptionControllerBlockEntity controller){
        Pool storage = controller.logistics().cargo();
        return new ResourceStatus(items(storage).stream().mapToLong(ItemStack::getCount).sum(),
                fluids(storage).stream().mapToLong(FluidStack::getAmount).sum(), energy(storage));
    }

    static boolean isBurnableItem(ItemStack stack){ return stack != null && !stack.isEmpty() && ThrusterBlockEntity.canUseAsSolidFuel(stack); }

    // Retain the loaded-body helper used by non-routing ship diagnostics
    public static List<BlockEntity> shipBlockEntities(AdvancedContraptionControllerBlockEntity controller){
        Set<BlockEntity> mapped = Collections.newSetFromMap(new IdentityHashMap<>());
        if(controller.getLevel() != null) for(var id : controller.getMappedShipSubLevelIds()){
            Object body = SubLevelBlockEntityCollector.getSubLevel(controller.getLevel(), id);
            if(body != null) mapped.addAll(SubLevelBlockEntityCollector.getBlockEntities(body));
        }
        if(!mapped.isEmpty()) return List.copyOf(mapped);
        Object body = SimulatedHelper.getContainingSubLevel(controller);
        return body == null ? List.of(controller) : SubLevelBlockEntityCollector.getBlockEntities(body);
    }

    static Pool dockStorage(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock){
        if(controller == null || dock == null || controller.getLevel() == null || controller.getLevel().getServer() == null) return Pool.EMPTY;
        ServerLevel level = controller.getLevel().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dock.dimension()));
        if(level == null) return Pool.EMPTY;
        List<Pool> pools = new ArrayList<>();
        BlockEntity selected = SimulatedHelper.findLoadedBlockEntityExact(level, dock.subLevelId(), dock.pos());
        if(selected instanceof ShipDockBlockEntity shipDock){
            pools.add(shipDock.networkStorage());
            pools.add(com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkAccess.pool(shipDock));
        }
        if(dock.hasDockingConnector()){
            var connector = SimulatedHelper.findLoadedBlockEntityExact(level, dock.connectorSubLevelId(), dock.connectorPos());
            pools.add(com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedConnectorStorage.localResources(connector));
        }
        return ScmLogisticsRuntime.combine(pools);
    }

    private static List<ItemStack> shipItems(AdvancedContraptionControllerBlockEntity controller){ return items(controller.logistics().pool(Group.ITEMS)); }

    private static List<ItemStack> dockItems(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock){
        return ScmDockingAccess.nativeAccess(() -> items(dockStorage(controller, dock)));
    }

    private static List<FluidStack> dockFluids(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock){
        return ScmDockingAccess.nativeAccess(() -> fluids(dockStorage(controller, dock)));
    }

    private static List<ItemStack> items(Pool pool){
        List<ItemStack> res = new ArrayList<>();
        for(var handler : pool.items()) for(int slot = 0; slot < handler.getSlots(); slot++){
            ItemStack stack = handler.getStackInSlot(slot);
            if(!stack.isEmpty()) res.add(stack.copy());
        }
        return List.copyOf(res);
    }

    private static List<FluidStack> fluids(Pool pool){
        List<FluidStack> res = new ArrayList<>();
        for(var handler : pool.fluids()) for(int tank = 0; tank < handler.getTanks(); tank++){
            FluidStack fluid = handler.getFluidInTank(tank);
            if(!fluid.isEmpty()) res.add(fluid.copy());
        }
        return List.copyOf(res);
    }

    private static long energy(Pool pool){ return pool.energy().stream().mapToLong(storage -> storage.getEnergyStored()).sum(); }

    // Reserve fraction and measured endurance keep incompatible fuel units separate
    public record FuelStatus(double amount, double capacity, double usePerTick, boolean infinite, double remainingTicks){
        public FuelStatus(double amount, double capacity, double usePerTick, boolean infinite){
            this(amount, capacity, usePerTick, infinite, usePerTick > 0.0D ? amount / usePerTick : Double.POSITIVE_INFINITY);
        }
        public double ratio(){ return infinite || capacity <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, amount / capacity)); }
        public boolean canReach(double distance, double speed){
            return infinite || ratio() >= 0.1D && remainingTicks * 0.9D >= distance / Math.max(0.05D, speed) * 20.0D;
        }
    }

    record ResourceStatus(long items, long fluids, long energy){}
}
