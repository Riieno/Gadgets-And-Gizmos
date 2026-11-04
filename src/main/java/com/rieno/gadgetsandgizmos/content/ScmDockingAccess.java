package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting.Pool;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings.Group;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

// Expose SCM logistics and networked Ship Dock storage through physically docked connectors
public final class ScmDockingAccess{
    private static final Map<AdvancedContraptionControllerBlockEntity, Boolean> CONTROLLERS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private ScmDockingAccess(){}

    public static void track(AdvancedContraptionControllerBlockEntity controller){
        if(controller.getScmConfigurationProfile().logistics().isEmpty()) CONTROLLERS.remove(controller);
        else CONTROLLERS.put(controller, true);
    }

    private static AdvancedContraptionControllerBlockEntity owner(BlockEntity requester, BlockEntity connector){
        if(ACTIVE.get() || requester == null || connector == null || connector.getLevel() == null
                || connector.getLevel().isClientSide || !DockingConnectorAutomation.isLockedPair(requester, connector)
                || !SimulatedDockingApi.hasPhysicalConnection(connector)) return null;
        var subLevel = SimulatedHelper.getContainingSubLevelId(connector);
        for(var controller : List.copyOf(CONTROLLERS.keySet())){
            if(controller.isRemoved() || !controller.hasShipControlModule() || controller.getLevel() == null
                    || controller.getLevel().getServer() != connector.getLevel().getServer()
                    || !Objects.equals(controller.getLevel().dimension(), connector.getLevel().dimension())) continue;
            if(controller.getShipDockingConnectors().stream().anyMatch(mapped ->
                    Objects.equals(mapped.subLevelId(), subLevel) && mapped.blockPosition().equals(connector.getBlockPos()))) return controller;
        }
        return null;
    }

    // Station connectors expose only a loaded Ship Dock's explicitly linked storage network
    private static Pool dockNetwork(BlockEntity requester, BlockEntity connector){
        if(ACTIVE.get() || requester == null || connector == null || connector.getLevel() == null
                || connector.getLevel().isClientSide || !DockingConnectorAutomation.isLockedPair(requester, connector)
                || !SimulatedDockingApi.hasPhysicalConnection(connector)) return Pool.EMPTY;
        var level = connector.getLevel();
        var dock = ShipDockRegistry.get(level.getServer()).dockForConnector(level.dimension().location(),
                SimulatedHelper.getContainingSubLevelId(connector), connector.getBlockPos());
        if(dock == null) return Pool.EMPTY;
        var target = SimulatedHelper.findLoadedBlockEntityExact(level, dock.subLevelId(), dock.pos());
        return target instanceof ShipDockBlockEntity
                ? com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkAccess.pool(target) : Pool.EMPTY;
    }

    private static Pool resources(BlockEntity requester, BlockEntity connector, Group kind){
        var owner = owner(requester, connector);
        return owner == null ? dockNetwork(requester, connector) : access(owner, connector, kind);
    }

    private static Pool access(AdvancedContraptionControllerBlockEntity owner, BlockEntity connector, Group kind){
        if(owner == null) return Pool.EMPTY;
        var category = owner.getScmDockingConnectorGroup(SimulatedHelper.getContainingSubLevelId(connector), connector.getBlockPos());
        return switch(category){
            case UNASSIGNED -> Pool.EMPTY;
            case FUEL -> owner.logistics().pool(Group.FUEL);
            case ITEMS -> kind == Group.ITEMS ? owner.logistics().pool(kind) : Pool.EMPTY;
            case FLUIDS -> kind == Group.FLUIDS ? owner.logistics().pool(kind) : Pool.EMPTY;
            case FE -> kind == Group.ENERGY ? owner.logistics().pool(kind) : Pool.EMPTY;
            case ANY -> ScmLogisticsRuntime.combine(List.of(owner.logistics().pool(kind), owner.logistics().pool(Group.FUEL)));
        };
    }

    private static Pool incoming(AdvancedContraptionControllerBlockEntity owner, BlockEntity connector, Group kind, boolean fuel){
        var category = owner.getScmDockingConnectorGroup(SimulatedHelper.getContainingSubLevelId(connector), connector.getBlockPos());
        if(category == ScmConfigurationProfile.DockingConnectorGroup.FUEL) return fuel ? owner.logistics().pool(Group.FUEL) : Pool.EMPTY;
        if(category == ScmConfigurationProfile.DockingConnectorGroup.ANY){
            return ScmLogisticsRuntime.combine(List.of(fuel ? owner.logistics().pool(Group.FUEL) : Pool.EMPTY,
                    owner.logistics().pool(kind)));
        }
        return access(owner, connector, kind);
    }

    public static boolean routesItems(BlockEntity requester, BlockEntity connector){ return !resources(requester, connector, Group.ITEMS).items().isEmpty(); }
    public static boolean routesFluids(BlockEntity requester, BlockEntity connector){ return !resources(requester, connector, Group.FLUIDS).fluids().isEmpty(); }
    public static boolean routesEnergy(BlockEntity requester, BlockEntity connector){ return !resources(requester, connector, Group.ENERGY).energy().isEmpty(); }

    public static ItemStack insertItem(BlockEntity requester, BlockEntity connector, ItemStack stack, boolean simulate){
        var owner = owner(requester, connector);
        Pool pool = owner == null ? dockNetwork(requester, connector) : incoming(owner, connector, Group.ITEMS, owner.logistics().acceptsFuel(stack));
        if(pool.isEmpty()) return stack;
        ACTIVE.set(true);
        try{
            ItemStack remainder = ResourceRouting.insertItem(pool, stack, simulate);
            if(owner != null && !simulate && remainder.getCount() < stack.getCount()) owner.logistics().transferred();
            return remainder;
        }finally{ ACTIVE.remove(); }
    }

    public static ItemStack extractItem(BlockEntity requester, BlockEntity connector, ItemStack filter, int limit, boolean simulate){
        var owner = owner(requester, connector);
        Pool pool = owner == null ? dockNetwork(requester, connector) : access(owner, connector, Group.ITEMS);
        if(pool.isEmpty()) return ItemStack.EMPTY;
        ACTIVE.set(true);
        try{
            ItemStack res = ResourceRouting.extractItem(pool,
                    stack -> filter == null || filter.isEmpty() || ItemStack.isSameItemSameComponents(stack, filter), limit, simulate);
            if(owner != null && !simulate && !res.isEmpty()) owner.logistics().transferred();
            return res;
        }finally{ ACTIVE.remove(); }
    }

    public static ItemStack peekItem(BlockEntity requester, BlockEntity connector){ return extractItem(requester, connector, null, 64, true); }

    public static int insertFluid(BlockEntity requester, BlockEntity connector, FluidStack stack, boolean simulate){
        var owner = owner(requester, connector);
        Pool pool = owner == null ? dockNetwork(requester, connector) : incoming(owner, connector, Group.FLUIDS, owner.logistics().acceptsFuel(stack));
        if(pool.isEmpty()) return 0;
        ACTIVE.set(true);
        try{
            int moved = ResourceRouting.insertFluid(pool, stack, simulate);
            if(owner != null && !simulate && moved > 0) owner.logistics().transferred();
            return moved;
        }finally{ ACTIVE.remove(); }
    }

    public static FluidStack extractFluid(BlockEntity requester, BlockEntity connector, FluidStack filter, int limit, boolean simulate){
        var owner = owner(requester, connector);
        Pool pool = owner == null ? dockNetwork(requester, connector) : access(owner, connector, Group.FLUIDS);
        if(pool.isEmpty()) return FluidStack.EMPTY;
        ACTIVE.set(true);
        try{
            FluidStack res = ResourceRouting.extractFluid(pool,
                    stack -> filter == null || filter.isEmpty() || FluidStack.isSameFluidSameComponents(stack, filter), limit, simulate);
            if(owner != null && !simulate && !res.isEmpty()) owner.logistics().transferred();
            return res;
        }finally{ ACTIVE.remove(); }
    }

    public static FluidStack peekFluid(BlockEntity requester, BlockEntity connector){ return extractFluid(requester, connector, null, Integer.MAX_VALUE, true); }

    public static int insertEnergy(BlockEntity requester, BlockEntity connector, int amount, boolean simulate){
        var owner = owner(requester, connector);
        Pool pool = owner == null ? dockNetwork(requester, connector) : incoming(owner, connector, Group.ENERGY, owner.logistics().acceptsEnergyFuel());
        if(pool.isEmpty()) return 0;
        ACTIVE.set(true);
        try{
            int moved = ResourceRouting.insertEnergy(pool, amount, simulate);
            if(owner != null && !simulate && moved > 0) owner.logistics().transferred();
            return moved;
        }finally{ ACTIVE.remove(); }
    }

    public static int extractEnergy(BlockEntity requester, BlockEntity connector, int amount, boolean simulate){
        var owner = owner(requester, connector);
        Pool pool = owner == null ? dockNetwork(requester, connector) : access(owner, connector, Group.ENERGY);
        if(pool.isEmpty()) return 0;
        ACTIVE.set(true);
        try{
            int moved = ResourceRouting.extractEnergy(pool, amount, simulate);
            if(owner != null && !simulate && moved > 0) owner.logistics().transferred();
            return moved;
        }finally{ ACTIVE.remove(); }
    }

    public static int storedEnergy(BlockEntity requester, BlockEntity connector){
        var owner = owner(requester, connector);
        Pool pool = owner == null ? dockNetwork(requester, connector) : access(owner, connector, Group.ENERGY);
        if(pool.isEmpty()) return 0;
        ACTIVE.set(true);
        try{
            long stored = 0L;
            for(var handler : pool.energy()) stored += handler.getEnergyStored();
            return (int)Math.min(Integer.MAX_VALUE, stored);
        }finally{ ACTIVE.remove(); }
    }

    // Schedule transfers use real dock buffers without recursing through the exposed wireless view
    public static <T> T nativeAccess(java.util.function.Supplier<T> operation){
        boolean prev = ACTIVE.get();
        ACTIVE.set(true);
        try{ return operation.get(); }
        finally{ if(prev) ACTIVE.set(true); else ACTIVE.remove(); }
    }
}
