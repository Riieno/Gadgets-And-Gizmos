package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.create.CreateFuelConsumers;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageIdentity;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceBalancer;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting.Pool;
import com.rieno.gadgetsandgizmos.lib.shipping.FuelBurnTracker;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings.Endpoint;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings.Group;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

// Resolve only authored logistics storage and the SCM's mapped fuel consumers
public final class ScmLogisticsRuntime{
    private final AdvancedContraptionControllerBlockEntity controller;
    private final FuelBurnTracker<String> burn = new FuelBurnTracker<>(600L);
    private final EnumMap<Group, Pool> pools = new EnumMap<>(Group.class);
    private List<BlockEntity> consumers = List.of();
    private List<Pool> consumerInputs = List.of();
    private List<Pool> consumerBuffers = List.of();
    private int consumerCursor;
    private long poolTick = Long.MIN_VALUE;
    private long tick = Long.MIN_VALUE;
    private boolean topologyChanged = true;
    private final Map<String, ItemStack> fuelItems = new LinkedHashMap<>();
    private final Map<String, FluidStack> fuelFluids = new LinkedHashMap<>();

    public ScmLogisticsRuntime(AdvancedContraptionControllerBlockEntity controller){ this.controller = controller; }

    public void invalidate(){ poolTick = Long.MIN_VALUE; topologyChanged = true; burn.reset(); fuelItems.clear(); fuelFluids.clear(); }

    public void tick(){
        if(controller.getLevel() == null || controller.getLevel().isClientSide || !controller.hasShipControlModule()) return;
        ScmDockingAccess.track(controller);
        long now = controller.getLevel().getGameTime();
        if(now == tick || Math.floorMod(now + controller.getBlockPos().asLong(), 4L) != 0L) return;
        tick = now;
        resolve();
        Map<String, Double> before = fuelAmounts(false);
        if(topologyChanged){ burn.baseline(now, before); topologyChanged = false; }
        else burn.sample(now, before);
        Pool fuel = pool(Group.FUEL);
        long itemStock = fuel.items().stream().mapToLong(handler -> {
            long count = 0; for(int slot = 0; slot < handler.getSlots(); slot++) count += handler.getStackInSlot(slot).getCount(); return count;
        }).sum();
        long fluidStock = fuel.fluids().stream().mapToLong(handler -> {
            long count = 0; for(int tank = 0; tank < handler.getTanks(); tank++) count += handler.getFluidInTank(tank).getAmount(); return count;
        }).sum();
        long energyStock = fuel.energy().stream().mapToLong(IEnergyStorage::getEnergyStored).sum();
        int count = consumerInputs.size();
        int fluidShare = (int)Math.max(1, Math.min(4000, fluidStock / Math.max(1, count)));
        int energyShare = (int)Math.max(1, Math.min(1_000_000, energyStock / Math.max(1, count)));
        Set<Integer> changed = new LinkedHashSet<>();
        List<Pool> ordered = new ArrayList<>(count);
        for(int idx = 0; idx < count; idx++){
            int consumer = Math.floorMod(idx + consumerCursor, count);
            Pool input = consumerInputs.get(consumer);
            int items = itemStock > 0 ? ResourceRouting.moveItems(fuel, input, stack -> true, 1) : 0;
            int fluids = fluidStock > 0 ? ResourceRouting.moveFluids(fuel, input, stack -> true, fluidShare) : 0;
            int energy = energyStock > 0 ? ResourceRouting.moveEnergy(fuel, input, energyShare) : 0;
            itemStock -= items;
            fluidStock -= fluids;
            energyStock -= energy;
            if(items > 0 || fluids > 0 || energy > 0) changed.add(consumer);
            ordered.add(consumerBuffers.get(consumer));
        }
        var balanced = ResourceBalancer.balance(ordered, this::acceptsFuel, this::acceptsFuel,
                new ResourceBalancer.Limits((int)Math.min(Integer.MAX_VALUE, 64L * count),
                        (int)Math.min(Integer.MAX_VALUE, 4000L * count),
                        (int)Math.min(Integer.MAX_VALUE, 1_000_000L * count)));
        if(balanced.moved()){
            Set<Object> touched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            touched.addAll(balanced.changed().items());
            touched.addAll(balanced.changed().fluids());
            touched.addAll(balanced.changed().energy());
            for(int idx = 0; idx < count; idx++){
                Pool buffer = consumerBuffers.get(idx);
                if(buffer.items().stream().anyMatch(touched::contains)
                        || buffer.fluids().stream().anyMatch(touched::contains)
                        || buffer.energy().stream().anyMatch(touched::contains)) changed.add(idx);
            }
        }
        for(int idx : changed){
            BlockEntity consumer = consumers.get(idx);
            if(consumer instanceof ThrusterBlockEntity thruster) thruster.fuelStorageChanged();
            else consumer.setChanged();
        }
        consumerCursor = count == 0 ? 0 : (consumerCursor + 1) % count;
        Map<String, Double> after = fuelAmounts(false);
        Map<String, Double> immediateUse = new HashMap<>();
        before.forEach((key, amount) -> {
            double consumed = amount - after.getOrDefault(key, 0.0D);
            if(consumed > 0.0D) immediateUse.put(key, consumed);
        });
        burn.recordConsumption(immediateUse);
        burn.baseline(now, after);
    }

    // Capability callbacks and schedule transfers must not count as fuel burn
    public void transferred(){
        if(controller.getLevel() != null) burn.baseline(controller.getLevel().getGameTime(), fuelAmounts(false));
    }

    public Pool pool(Group group){ resolve(); return pools.getOrDefault(group, Pool.EMPTY); }

    public boolean acceptsEnergyFuel(){
        resolve();
        return consumers.isEmpty() || consumerInputs.stream().anyMatch(input ->
                input.energy().stream().anyMatch(IEnergyStorage::canReceive));
    }

    public boolean acceptsFuel(ItemStack stack){
        if(stack.isEmpty()) return false;
        resolve();
        for(int idx = 0; idx < consumers.size(); idx++){
            if(consumers.get(idx) instanceof BlazeBurnerBlockEntity burner){
                if(!burner.isCreative() && (CreateFuelConsumers.burnTicks(stack) > 0 || burner.isCreativeFuel(stack))) return true;
                continue;
            }
            for(var handler : consumerInputs.get(idx).items()) for(int slot = 0; slot < handler.getSlots(); slot++){
                if(handler.isItemValid(slot, stack)) return true;
            }
        }
        return consumers.isEmpty() && (ThrusterBlockEntity.canUseAsSolidFuel(stack) || CreateFuelConsumers.burnTicks(stack) > 0);
    }

    public boolean acceptsFuel(FluidStack stack){
        if(stack.isEmpty()) return false;
        resolve();
        for(Pool input : consumerInputs) for(var handler : input.fluids()) for(int tank = 0; tank < handler.getTanks(); tank++){
            if(handler.isFluidValid(tank, stack)) return true;
        }
        return consumers.isEmpty() && (stack.getFluid() == net.minecraft.world.level.material.Fluids.WATER
                || com.rieno.gadgetsandgizmos.util.ThrusterFuelData.getProfile(stack.getFluid()) != null);
    }

    public Pool cargo(){ return combine(List.of(pool(Group.ITEMS), pool(Group.FLUIDS), pool(Group.ENERGY))); }

    // Include fuel already held by consumers, including focused thrusters' internal FE buffers
    public Pool fuelStorage(){
        resolve();
        return combine(List.of(pool(Group.FUEL), combine(consumerBuffers)));
    }

    private void resolve(){
        if(controller.getLevel() == null) return;
        long now = controller.getLevel().getGameTime();
        if(poolTick == now) return;
        poolTick = now;
        LogisticsBindings bindings = controller.getScmConfigurationProfile().logistics();
        Map<BlockEntity, Pool> thrusterAccess = new java.util.IdentityHashMap<>();
        for(Group group : Group.values()){
            if(group == Group.FUEL_CONSUMER) continue;
            List<IItemHandler> items = new ArrayList<>();
            List<IFluidHandler> fluids = new ArrayList<>();
            List<IEnergyStorage> energy = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for(Endpoint endpoint : bindings.endpoints(group)){
                BlockEntity be = resolve(endpoint);
                if(be == null || DockingConnectorAutomation.isDockingConnector(be)) continue;
                var root = ContainerStorageIdentity.position(be.getLevel(), be.getBlockPos());
                String key = endpoint.subLevelId() + ":" + root + ":" + endpoint.face();
                if(!seen.add(key)) continue;
                Pool access = be instanceof ThrusterBlockEntity
                        ? thrusterAccess.computeIfAbsent(be, value -> access(value, endpoint.face(), true))
                        : access(be, endpoint.face(), false);
                if(group == Group.FUEL && be instanceof com.rieno.gadgetsandgizmos.lib.inventory.FuelResourceStorage selectedFuel){
                    access = selectedFuel.fuelResources();
                }
                if(group == Group.ITEMS || group == Group.FUEL) items.addAll(access.items());
                if(group == Group.FLUIDS || group == Group.FUEL) fluids.addAll(access.fluids());
                if(group == Group.ENERGY || group == Group.FUEL) energy.addAll(access.energy());
            }
            pools.put(group, new Pool(items, fluids, energy));
        }
        Map<String, BlockEntity> selected = new LinkedHashMap<>();
        List<Endpoint> targets = new ArrayList<>(bindings.endpoints(Group.FUEL_CONSUMER));
        ShipControlMap map = controller.localShipControlMap();
        if(map != null) map.units().forEach(unit -> targets.add(new Endpoint(unit.subLevelId(), unit.blockPosition(), null)));
        List<Pool> accesses = new ArrayList<>();
        for(Endpoint endpoint : targets){
            BlockEntity be = resolve(endpoint);
            if(be == null || DockingConnectorAutomation.isDockingConnector(be)) continue;
            for(BlockEntity consumer : CreateFuelConsumers.expand(be)){
                String key = SimulatedHelper.getContainingSubLevelId(consumer) + ":" + consumer.getBlockPos();
                Pool input = consumer instanceof ThrusterBlockEntity
                        ? thrusterAccess.computeIfAbsent(consumer, value -> access(value, endpoint.face(), true))
                        : access(consumer, endpoint.face(), true);
                if(input.isEmpty()) continue;
                if(selected.putIfAbsent(key, consumer) == null) accesses.add(input);
            }
        }
        List<BlockEntity> nextConsumers = List.copyOf(selected.values());
        if(!nextConsumers.equals(consumers)){
            topologyChanged = true;
            burn.reset();
        }
        consumers = nextConsumers;
        consumerInputs = List.copyOf(accesses);
        List<Pool> buffers = new ArrayList<>(accesses.size());
        for(int idx = 0; idx < consumers.size(); idx++){
            BlockEntity consumer = consumers.get(idx);
            // Internal routing may redistribute FE without exposing an external output capability.
            buffers.add(consumer instanceof ThrusterBlockEntity thruster && thruster.isFocusedMode()
                    ? new Pool(List.of(), List.of(), List.of(thruster.getEnergyStorage())) : accesses.get(idx));
        }
        consumerBuffers = List.copyOf(buffers);
        pools.put(Group.FUEL_CONSUMER, combine(accesses));
    }

    private BlockEntity resolve(Endpoint endpoint){
        return SimulatedHelper.findLoadedBlockEntityExact(controller.getLevel(), endpoint.subLevelId(), endpoint.position());
    }

    // Unsided handlers take precedence; sided-only machines keep their native face restrictions
    public static Pool access(BlockEntity be, Direction face, boolean consumer){
        if(be == null || be.isRemoved() || be.getLevel() == null || !be.getLevel().isLoaded(be.getBlockPos())) return Pool.EMPTY;
        if(consumer && be instanceof ThrusterBlockEntity thruster){
            if(thruster.isFocusedMode()) return new Pool(List.of(), List.of(), List.of(thruster.getFocusInput()));
            return new Pool(List.of(new RangedWrapper((net.neoforged.neoforge.items.IItemHandlerModifiable)thruster.getItemInventory(), ThrusterBlockEntity.SLOT_SOLID_FUEL,
                    ThrusterBlockEntity.SLOT_SOLID_FUEL + 1)), List.of(thruster.getFuelTank()),
                    List.of());
        }
        if(consumer && be instanceof BlazeBurnerBlockEntity burner){
            return new Pool(List.of(CreateFuelConsumers.burnerInput(burner)), List.of(), List.of());
        }
        return consumer ? ResourceRouting.consumerAccess(be, face) : ResourceRouting.access(be, face);
    }



    public static Pool combine(List<Pool> pools){ return ResourceRouting.combine(pools); }

    private Map<String, Double> fuelAmounts(boolean capacity){ return fuelAmounts(capacity, true); }

    private Map<String, Double> fuelAmounts(boolean capacity, boolean includeConsumers){
        resolve();
        Map<String, Double> amounts = new HashMap<>();
        Pool fuel = includeConsumers ? combine(List.of(pool(Group.FUEL), pool(Group.FUEL_CONSUMER))) : pool(Group.FUEL);
        for(IItemHandler handler : fuel.items()) for(int slot = 0; slot < handler.getSlots(); slot++){
            ItemStack stack = handler.getStackInSlot(slot);
            if(!stack.isEmpty() && acceptsFuel(stack)) fuelItems.put(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.copyWithCount(1));
        }
        for(IFluidHandler handler : fuel.fluids()) for(int tank = 0; tank < handler.getTanks(); tank++){
            FluidStack fluid = handler.getFluidInTank(tank);
            if(!fluid.isEmpty() && acceptsFuel(fluid)) fuelFluids.put(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(), fluid.copyWithAmount(1));
        }
        fuelItems.entrySet().removeIf(entry -> !acceptsFuel(entry.getValue()));
        fuelFluids.entrySet().removeIf(entry -> !acceptsFuel(entry.getValue()));
        for(IItemHandler handler : fuel.items()) for(int slot = 0; slot < handler.getSlots(); slot++){
            ItemStack stack = handler.getStackInSlot(slot);
            if(!stack.isEmpty() && !acceptsFuel(stack)) continue;
            if(stack.isEmpty() && capacity){
                final int targetSlot = slot;
                stack = fuelItems.values().stream().filter(ref -> handler.isItemValid(targetSlot, ref)).findFirst().orElse(ItemStack.EMPTY);
            }
            if(stack.isEmpty()) continue;
            double burnTicks = fuelUnits(stack);
            String key = fuelKey(stack);
            double count = capacity ? Math.min(stack.getMaxStackSize(), handler.getSlotLimit(slot)) : stack.getCount();
            amounts.merge(key, count * Math.max(1, burnTicks), Double::sum);
        }
        for(IFluidHandler handler : fuel.fluids()) for(int tank = 0; tank < handler.getTanks(); tank++){
            FluidStack fluid = handler.getFluidInTank(tank);
            if(!fluid.isEmpty() && !acceptsFuel(fluid)) continue;
            if(fluid.isEmpty() && capacity){
                final int targetTank = tank;
                fluid = fuelFluids.values().stream().filter(ref -> handler.isFluidValid(targetTank, ref)).findFirst().orElse(FluidStack.EMPTY);
            }
            if(fluid.isEmpty()) continue;
            amounts.merge("fluid:" + BuiltInRegistries.FLUID.getKey(fluid.getFluid()),
                    (double)(capacity ? handler.getTankCapacity(tank) : fluid.getAmount()), Double::sum);
        }
        if(acceptsEnergyFuel()) for(IEnergyStorage handler : fuel.energy()) amounts.merge("energy",
                (double)(capacity ? handler.getMaxEnergyStored() : handler.getEnergyStored()), Double::sum);
        if(includeConsumers) for(BlockEntity consumer : consumers){
            if(consumer instanceof ThrusterBlockEntity thruster && thruster.getSolidFuelTicks() > 0){
                amounts.merge("solid", thruster.getSolidFuelTicks() * solidFuelScale(thruster), Double::sum);
            }else if(consumer instanceof BlazeBurnerBlockEntity burner && !burner.isCreative()){
                amounts.merge("solid", (double)(capacity ? BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY : burner.getRemainingBurnTime()), Double::sum);
            }
        }
        return amounts;
    }

    private static double fuelUnits(ItemStack stack){
        int burnerTicks = CreateFuelConsumers.burnTicks(stack);
        if(burnerTicks > 0) return burnerTicks;
        var profile = com.rieno.gadgetsandgizmos.util.ThrusterFuelData.getSolidFuelProfile(stack);
        return profile != null && profile.burnTicks() > 0 ? profile.burnTicks() : 0.0D;
    }

    private static String fuelKey(ItemStack stack){
        return fuelUnits(stack) > 0.0D ? "solid" : "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    // Convert configured thruster durations to the same units as the stored item reserves
    private static double solidFuelScale(ThrusterBlockEntity thruster){
        var id = net.minecraft.resources.ResourceLocation.tryParse(thruster.getFuelTypeId());
        if(id == null) return 1.0D;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        var profile = com.rieno.gadgetsandgizmos.util.ThrusterFuelData.getSolidFuelProfile(stack);
        if(profile == null || profile.burnTicks() <= 0) return 1.0D;
        return Math.max(1.0D, fuelUnits(stack)) / profile.burnTicks();
    }

    // Finish filling the engines before treating a full supply group as refuelled
    public boolean fuelStorageFull(){
        Map<String, Double> amounts = fuelAmounts(false, false);
        Map<String, Double> capacities = fuelAmounts(true, false);
        return !capacities.isEmpty() && capacities.entrySet().stream().allMatch(entry ->
                entry.getValue() <= 0.0D || amounts.getOrDefault(entry.getKey(), 0.0D) >= entry.getValue() * 0.999D)
                && consumerInputs.stream().noneMatch(input -> ResourceRouting.canMove(pool(Group.FUEL), input,
                this::acceptsFuel, this::acceptsFuel));
    }

    public ShipCargoAutomation.FuelStatus fuelStatus(double expectedThrottle){
        resolve();
        Map<String, Double> amounts = fuelAmounts(false);
        Map<String, Double> capacities = fuelAmounts(true);
        Map<String, Double> fallback = new HashMap<>();
        Set<String> required = new LinkedHashSet<>();
        Map<String, Double> measured = burn.rates();
        boolean infinite = !consumers.isEmpty() && consumers.stream().allMatch(consumer ->
                consumer instanceof ThrusterBlockEntity thruster && thruster.hasInfiniteSolidFuel()
                || consumer instanceof BlazeBurnerBlockEntity burner && burner.isCreative());
        for(BlockEntity consumer : consumers){
            Pool input = access(consumer, null, true);
            if(input.energy().stream().anyMatch(IEnergyStorage::canReceive)) required.add("energy");
            if(consumer instanceof BlazeBurnerBlockEntity burner && !burner.isCreative()) required.add("solid");
            for(var handler : consumer instanceof ThrusterBlockEntity thruster && thruster.getSolidFuelTicks() > 0
                    ? List.<IFluidHandler>of() : input.fluids()) for(int idx = 0; idx < handler.getTanks(); idx++){
                FluidStack current = handler.getFluidInTank(idx);
                if(!current.isEmpty()) required.add("fluid:" + BuiltInRegistries.FLUID.getKey(current.getFluid()));
                else for(var entry : fuelFluids.entrySet()){
                    String key = "fluid:" + entry.getKey();
                    if(measured.containsKey(key) && handler.isFluidValid(idx, entry.getValue())) required.add(key);
                }
            }
            if(!(consumer instanceof ThrusterBlockEntity)) for(var handler : input.items()) for(int idx = 0; idx < handler.getSlots(); idx++){
                for(var entry : fuelItems.entrySet()){
                    String key = fuelKey(entry.getValue());
                    if(measured.containsKey(key) && handler.isItemValid(idx, entry.getValue())) required.add(key);
                }
            }
            if(!(consumer instanceof ThrusterBlockEntity thruster)) continue;

            FluidStack fluid = thruster.getFuelTank().getFluid();
            double rate = thruster.getEstimatedFuelConsumptionMbPerTick((float)expectedThrottle);
            if(thruster.getSolidFuelTicks() > 0) rate *= solidFuelScale(thruster);
            String key = thruster.getSolidFuelTicks() > 0 ? "solid"
                    : fluid.isEmpty() ? "" : "fluid:" + BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            if(!key.isEmpty()) required.add(key);
            if(!key.isEmpty() && rate > 0) fallback.merge(key, rate, Double::sum);
        }
        double fraction = 1.0D;
        for(var entry : capacities.entrySet()) if(entry.getValue() > 0.0D && (required.isEmpty() || required.contains(entry.getKey()))){
            fraction = Math.min(fraction, amounts.getOrDefault(entry.getKey(), 0.0D) / entry.getValue());
        }
        if(required.isEmpty()) required.addAll(amounts.keySet());
        double remaining = burn.remainingTicks(amounts, fallback, required);
        if(capacities.isEmpty() && !consumers.isEmpty()) return new ShipCargoAutomation.FuelStatus(0, 1, 0, infinite, 0);
        return new ShipCargoAutomation.FuelStatus(capacities.isEmpty() ? 0.0D : fraction,
                capacities.isEmpty() ? 0.0D : 1.0D, Double.isFinite(remaining) && remaining > 0 ? fraction / remaining : 0.0D, infinite, remaining);
    }

    public int exchange(Pool dock, Group group, boolean pickup, Predicate<ItemStack> items,
                        Predicate<FluidStack> fluids, int itemLimit, int fluidLimit, int energyLimit){
        Pool ship = pool(group);
        Pool source = pickup ? dock : ship;
        Pool target = pickup ? ship : dock;
        int moved = ResourceRouting.moveItems(source, target, items, Math.max(0, itemLimit));
        moved += ResourceRouting.moveFluids(source, target, fluids, Math.max(0, fluidLimit));
        moved += ResourceRouting.moveEnergy(source, target, Math.max(0, energyLimit));
        if(moved > 0) transferred();
        return moved;
    }
}
