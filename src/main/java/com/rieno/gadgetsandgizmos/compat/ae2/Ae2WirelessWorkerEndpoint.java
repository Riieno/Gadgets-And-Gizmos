package com.rieno.gadgetsandgizmos.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerInventoryEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourcePacket;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Loaded only when AE2 is installed. The terminal remains in Tools while the worker accesses its linked grid.
public final class Ae2WirelessWorkerEndpoint extends WorkerInventoryEndpoint {
    private static final double ACTION_POWER = 1.0;
    private final ServerLevel level;
    private final PlayerMannequinEntity worker;
    private final UUID endpointId;
    private long connectionTick = Long.MIN_VALUE;
    private Connection connectionCache;

    public Ae2WirelessWorkerEndpoint(ServerLevel level, PlayerMannequinEntity worker) {
        super(UUID.nameUUIDFromBytes((worker.getUUID() + ":ae2-wireless").getBytes(StandardCharsets.UTF_8)),
                worker::blockPosition, new ItemStackHandler(0), level.registryAccess(), stack -> false, true);
        this.level = level;
        this.worker = worker;
        this.endpointId = UUID.nameUUIDFromBytes((worker.getUUID() + ":ae2-wireless")
                .getBytes(StandardCharsets.UTF_8));
    }

    @Override public UUID id() { return endpointId; }
    @Override public String label() { return "Worker wireless terminal"; }
    @Override public boolean isAvailable() { return connection() != null; }
    @Override public boolean canExtract(WorkerResourceKey resource) {
        return resource != null && (resource.type() == WorkerResourceType.ITEM
                || resource.type() == WorkerResourceType.FLUID) && isAvailable();
    }
    @Override public boolean canInsert(WorkerResourceKey resource) { return canExtract(resource); }
    @Override public int insertionPriority(WorkerResourceKey resource) {
        return available(resource) > 0L ? 0 : 2;
    }

    @Override public Map<WorkerResourceKey, Long> contents() {
        Connection connection = connection();
        if(connection == null) return Map.of();
        Map<WorkerResourceKey, Long> stock = new LinkedHashMap<>();
        for(var entry : connection.grid().getStorageService().getCachedInventory()) {
            if(entry.getLongValue() <= 0L) continue;
            WorkerResourceKey resource;
            if(entry.getKey() instanceof AEItemKey key)
                resource = new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(key.getItem()));
            else if(entry.getKey() instanceof AEFluidKey key)
                resource = new WorkerResourceKey(WorkerResourceType.FLUID, key.getId());
            else continue;
            stock.merge(resource, entry.getLongValue(),
                    (first, second) -> first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second);
        }
        return Map.copyOf(stock);
    }

    @Override public long available(WorkerResourceKey resource) {
        Connection connection = connection();
        if(connection == null || resource == null) return 0L;
        long amount = 0L;
        for(var entry : connection.grid().getStorageService().getCachedInventory()){
            if(entry.getLongValue() <= 0L) continue;
            boolean matches = resource.type() == WorkerResourceType.ITEM
                    && entry.getKey() instanceof AEItemKey item
                    && BuiltInRegistries.ITEM.getKey(item.getItem()).equals(resource.id())
                    || resource.type() == WorkerResourceType.FLUID
                    && entry.getKey() instanceof AEFluidKey fluid && fluid.getId().equals(resource.id());
            if(matches) amount = amount > Long.MAX_VALUE - entry.getLongValue()
                    ? Long.MAX_VALUE : amount + entry.getLongValue();
        }
        return amount;
    }

    @Override public long space(WorkerResourceKey resource) {
        Connection connection = connection();
        if(connection == null || resource == null) return 0L;
        if(resource.type() == WorkerResourceType.FLUID){
            var fluid = BuiltInRegistries.FLUID.getOptional(resource.id()).orElse(null);
            AEFluidKey key = fluid == null ? null : AEFluidKey.of(fluid);
            return key == null ? 0L : connection.storage().insert(key, Integer.MAX_VALUE,
                    Actionable.SIMULATE, IActionSource.ofMachine(connection.accessPoint()));
        }
        if(resource.type() != WorkerResourceType.ITEM) return 0L;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(resource.id()));
        AEItemKey key = AEItemKey.of(stack);
        if(key == null) return 0L;
        return connection.storage().insert(key, 4096L, Actionable.SIMULATE,
                IActionSource.ofMachine(connection.accessPoint()));
    }

    @Override public WorkerResourcePacket extract(WorkerResourceKey resource, long maximumAmount, boolean simulate) {
        if(resource != null && resource.type() == WorkerResourceType.FLUID)
            return extractFluid(resource, maximumAmount, simulate);
        return extractMatchingItem(resource, maximumAmount, stack -> true, simulate);
    }

    private WorkerResourcePacket extractFluid(WorkerResourceKey resource, long maximumAmount, boolean simulate){
        Connection connection = connection();
        if(connection == null || maximumAmount <= 0L) return WorkerResourcePacket.empty(resource);
        AEFluidKey selected = null;
        long selectedAmount = 0L;
        for(var entry : connection.grid().getStorageService().getCachedInventory()){
            if(!(entry.getKey() instanceof AEFluidKey key) || entry.getLongValue() <= 0L
                    || !key.getId().equals(resource.id())) continue;
            if(entry.getLongValue() > selectedAmount){
                selected = key;
                selectedAmount = entry.getLongValue();
            }
            if(selectedAmount >= maximumAmount) break;
        }
        if(selected == null) return WorkerResourcePacket.empty(resource);
        long amount = connection.storage().extract(selected, Math.min(maximumAmount, selectedAmount),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                IActionSource.ofMachine(connection.accessPoint()));
        if(amount > 0L && !simulate) drainTerminal(connection);
        return new WorkerResourcePacket(resource, amount, new CompoundTag());
    }

    @Override public WorkerResourcePacket extractMatchingItem(WorkerResourceKey resource, long maximumAmount,
                                                               java.util.function.Predicate<ItemStack> matches,
                                                               boolean simulate){
        Connection connection = connection();
        if(connection == null || resource == null || resource.type() != WorkerResourceType.ITEM
                || maximumAmount <= 0L) return WorkerResourcePacket.empty(resource);
        for(var entry : connection.grid().getStorageService().getCachedInventory()) {
            if(!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0L
                    || !BuiltInRegistries.ITEM.getKey(key.getItem()).equals(resource.id())) continue;
            if(!matches.test(key.toStack(1))) continue;
            long amount = connection.storage().extract(key,
                    Math.min(Math.min(maximumAmount, entry.getLongValue()), Integer.MAX_VALUE),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                    IActionSource.ofMachine(connection.accessPoint()));
            if(amount <= 0L) continue;
            if(!simulate) drainTerminal(connection);
            CompoundTag payload = new CompoundTag();
            payload.put("Stack", key.toStack((int) amount).saveOptional(level.registryAccess()));
            return new WorkerResourcePacket(resource, amount, payload);
        }
        return WorkerResourcePacket.empty(resource);
    }

    @Override public long availableMatchingItem(WorkerResourceKey resource,
                                                 java.util.function.Predicate<ItemStack> matches){
        Connection connection = connection();
        if(connection == null || resource == null || resource.type() != WorkerResourceType.ITEM) return 0L;
        long total = 0L;
        for(var entry : connection.grid().getStorageService().getCachedInventory()){
            if(!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0L
                    || !BuiltInRegistries.ITEM.getKey(key.getItem()).equals(resource.id())
                    || !matches.test(key.toStack(1))) continue;
            total += entry.getLongValue();
        }
        return total;
    }

    @Override public long insert(WorkerResourcePacket packet, boolean simulate) {
        Connection connection = connection();
        if(connection == null || packet == null || packet.isEmpty()) return 0L;
        if(packet.resource().type() == WorkerResourceType.FLUID){
            var fluid = BuiltInRegistries.FLUID.getOptional(packet.resource().id()).orElse(null);
            AEFluidKey key = fluid == null ? null : AEFluidKey.of(fluid);
            if(key == null) return 0L;
            long inserted = connection.storage().insert(key, packet.amount(),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                    IActionSource.ofMachine(connection.accessPoint()));
            if(inserted > 0L && !simulate) drainTerminal(connection);
            return inserted;
        }
        if(packet.resource().type() != WorkerResourceType.ITEM) return 0L;
        ItemStack stack = packet.payload().contains("Stack")
                ? ItemStack.parseOptional(level.registryAccess(), packet.payload().getCompound("Stack"))
                : new ItemStack(BuiltInRegistries.ITEM.get(packet.resource().id()));
        AEItemKey key = AEItemKey.of(stack);
        if(key == null || !BuiltInRegistries.ITEM.getKey(key.getItem()).equals(packet.resource().id())) return 0L;
        long inserted = connection.storage().insert(key, packet.amount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                IActionSource.ofMachine(connection.accessPoint()));
        if(inserted > 0L && !simulate) drainTerminal(connection);
        return inserted;
    }

    private void drainTerminal(Connection connection) {
        connection.terminal().extractAEPower(connection.stack(), ACTION_POWER, Actionable.MODULATE);
        worker.workerTools().setStackInSlot(connection.slot(), connection.stack());
    }

    private Connection connection() {
        long tick = level.getGameTime();
        if(connectionTick == tick) return connectionCache != null
                && worker.workerTools().getStackInSlot(connectionCache.slot()) == connectionCache.stack()
                && connectionCache.terminal().getAECurrentPower(connectionCache.stack()) >= ACTION_POWER
                ? connectionCache : null;
        connectionTick = tick;
        connectionCache = findConnection();
        return connectionCache;
    }

    private Connection findConnection() {
        for(int slot = 0; slot < worker.workerTools().getSlots(); slot++) {
            ItemStack stack = worker.workerTools().getStackInSlot(slot);
            if(!(stack.getItem() instanceof WirelessTerminalItem terminal)
                    || terminal.getAECurrentPower(stack) < ACTION_POWER) continue;
            IGrid grid = terminal.getLinkedGrid(stack, level, null);
            if(grid == null || !grid.getEnergyService().isNetworkPowered()) continue;
            // AE2 registers access points under their concrete block-entity class.
            for(IWirelessAccessPoint accessPoint : grid.getMachines(
                    appeng.blockentity.networking.WirelessAccessPointBlockEntity.class)) {
                if(!accessPoint.isActive() || !accessPoint.getLocation().isInWorld(level)) continue;
                double range = accessPoint.getRange();
                if(worker.position().distanceToSqr(
                        net.minecraft.world.phys.Vec3.atCenterOf(accessPoint.getLocation().getPos()))
                        > range * range) continue;
                return new Connection(slot, stack, terminal, grid, accessPoint,
                        grid.getStorageService().getInventory());
            }
        }
        return null;
    }

    private record Connection(int slot, ItemStack stack, WirelessTerminalItem terminal, IGrid grid,
                              IWirelessAccessPoint accessPoint, MEStorage storage) {}
}
