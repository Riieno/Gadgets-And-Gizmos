package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkAccess;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerDataSource;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks;
import com.rieno.gadgetsandgizmos.lib.inventory.LocalResourceStorage;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.create.ConnectedVaultBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferMeter;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferTrackedStorage;
import com.rieno.gadgetsandgizmos.lib.power.ConnectorOutputLimits;
import com.rieno.gadgetsandgizmos.lib.power.ElectricalStorage;
import com.rieno.gadgetsandgizmos.lib.power.powergrid.PowerGridDeviceConnectors;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.rieno.gadgetsandgizmos.lib.tablet.MountedTabletState;
import com.rieno.gadgetsandgizmos.lib.power.EnergyItemInventory;
import net.minecraft.world.Containers;
import net.minecraft.world.phys.AABB;

// Store FE across a vault-shaped multiblock without exposing an item inventory
public class SmartBatteryBlockEntity extends ConnectedVaultBlockEntity implements LongEnergyStorage, MountedTabletState.Host, LocalResourceStorage, TransferTrackedStorage, ElectricalStorage, ContainerDataSource {
    @Override public List<BlockEntity> dataContainers(){
        return level != null && level.isClientSide ? List.of(this) : ContainerStorageNetworks.connected(this);
    }

    private final ConnectorOutputLimits connectorOutputLimits = new ConnectorOutputLimits();

    @Override public LongEnergyStorage electricalStorage(){ return controller(); }
    @Override public List<SmartBatteryBlockEntity> electricalMembers(){ return controller().members(); }
    @Override public ConnectorOutputLimits connectorOutputLimits(){ return connectorOutputLimits; }
    private final TransferMeter transferMeter = new TransferMeter(() -> level == null ? 0 : level.getGameTime());

    @Override public TransferMeter.Rates transferRates(){ return controller().transferMeter.rates(); }
    private final ContainerNetworkAccess networkResources = new ContainerNetworkAccess(this);

    // Expose only the resource capability registered for this block's automation port
    public ContainerNetworkAccess networkResources(){ return networkResources; }

    private final com.rieno.gadgetsandgizmos.lib.inventory.LocalResourcePool localPool =
            new com.rieno.gadgetsandgizmos.lib.inventory.LocalResourcePool();
    @Override public ResourceRouting.Pool localResources(){
        return localPool.get(null, null, getEnergyHandler());
    }

    private static final String ENERGY_TAG = "Energy";
    private static final long SINGLE_CAPACITY = 10_000_000L;
    private static final long FULL_CAPACITY = 5_000_000_000L;
    private static final int FULL_BLOCKS = 81;

    private final EnergyItemInventory chargingItems =
            new EnergyItemInventory(this::setChanged,
                    com.rieno.gadgetsandgizmos.lib.power.powergrid.PowerGridPortableBattery::storage);

    public EnergyItemInventory chargingItems(){ return controller().chargingItems; }

    public void dropChargingItems(){
        if(level == null || level.isClientSide) return;
        for(int slot = 0; slot < chargingItems.getSlots(); slot++){
            var stack = chargingItems.extractItem(slot, Integer.MAX_VALUE, false);
            if(!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5,
                    worldPosition.getY() + .5, worldPosition.getZ() + .5, stack);
        }
    }

    @Override public void tick(){
        super.tick();
        if(level == null || level.isClientSide) return;
        // Saved controller links can be resolved after the members have a world
        synchronizePowerGrid();
        SmartBatteryBlockEntity root = controller();
        if(root != this){
            // Preserve row direction and retain overflow locally until a controller slot becomes available
            for(int slot = 0; slot < chargingItems.getSlots(); slot++){
                var stack = chargingItems.getStackInSlot(slot);
                if(stack.isEmpty()) continue;
                for(int dest = slot < 9 ? 0 : 9; dest < (slot < 9 ? 9 : 18); dest++){
                    var rest = root.chargingItems.insertItem(dest, stack, false);
                    if(rest.getCount() == stack.getCount()) continue;
                    chargingItems.setStackInSlot(slot, rest);
                    stack = rest;
                    if(stack.isEmpty()) break;
                }
            }
        }
        chargingItems.tick(getEnergyHandler());
        com.rieno.gadgetsandgizmos.lib.power.EnergyTransfer.supplyAdjacent(level, worldPosition, getEnergyHandler());
    }

    private long energy;
    private final IEnergyStorage energyHandler = new CombinedEnergyStorage();

    private final MountedTabletState tabletState = new MountedTabletState();

    @Override public MountedTabletState mountedTabletState(){ return tabletState; }

    // Initialize the smart battery block entity
    public SmartBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SMART_BATTERY.get(), pos, state);
    }

    public void addPowerGridSnapshot(CompoundTag data){
        if(powerGridInstalled()) PowerGridDeviceConnectors.snapshot(this, data);
    }

    public boolean setPowerGridOutput(BlockPos member, Direction face, double watts){
        return powerGridInstalled() && PowerGridDeviceConnectors.setOutput(this, member, face, watts);
    }

    public boolean setPowerGridVoltage(BlockPos member, Direction face, double volts){
        return powerGridInstalled() && PowerGridDeviceConnectors.setVoltage(this, member, face, volts);
    }

    public boolean setPowerGridPolarity(BlockPos member, Direction face, int sign){
        return powerGridInstalled() && PowerGridDeviceConnectors.setPolarity(this, member, face, sign);
    }

    private static boolean powerGridInstalled(){
        var mods = net.neoforged.fml.ModList.get();
        return mods != null && mods.isLoaded("powergrid");
    }

    private void synchronizePowerGrid(){ if(powerGridInstalled()) PowerGridDeviceConnectors.synchronize(this); }
    private void refreshPowerGrid(){ if(powerGridInstalled()) PowerGridDeviceConnectors.refresh(this); }

    // Get the complete multiblock energy handler
    public IEnergyStorage getEnergyHandler() {
        SmartBatteryBlockEntity controller = getControllerBE() instanceof SmartBatteryBlockEntity smart
                ? smart : this;
        return controller.energyHandler;
    }

    // Get the exact stored FE beyond the integer capability limit
    public long getLongEnergyStored() {
        SmartBatteryBlockEntity controller = getControllerBE() instanceof SmartBatteryBlockEntity smart
                ? smart : this;
        return controller.storedEnergy();
    }

    // Get the exact multiblock capacity beyond the integer capability limit
    public long getLongCapacity() {
        SmartBatteryBlockEntity controller = getControllerBE() instanceof SmartBatteryBlockEntity smart
                ? smart : this;
        return capacity(controller.members().size());
    }

    // Receive exact FE through the controller
    @Override
    public long receiveEnergy(long maximum, boolean simulate) {
        return controller().receiveExactEnergy(maximum, simulate);
    }

    // Extract exact FE through the controller
    @Override
    public long extractEnergy(long maximum, boolean simulate) {
        return controller().extractExactEnergy(maximum, simulate);
    }

    // Get exact stored FE through the controller
    @Override
    public long getEnergyStored() {
        return getLongEnergyStored();
    }

    // Get exact FE capacity through the controller
    @Override
    public long getMaxEnergyStored() {
        return getLongCapacity();
    }

    // Check whether exact FE may be extracted
    @Override
    public boolean canExtract() {
        return true;
    }

    // Check whether exact FE may be received
    @Override
    public boolean canReceive() {
        return true;
    }

    // Read the battery data
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        energy = Math.max(0L, tag.getLong(ENERGY_TAG));
        connectorOutputLimits.read(tag.getCompound("PowerGridOutputs"));
        tabletState.read(tag);
        if(tag.contains("ChargingItems")) chargingItems.deserializeNBT(provider, tag.getCompound("ChargingItems"));
    }

    // Write the battery data
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putLong(ENERGY_TAG, energy);
        tag.put("PowerGridOutputs", connectorOutputLimits.write());
        tabletState.write(tag);
        tag.put("ChargingItems", chargingItems.serializeNBT(provider));
    }

    // Keep Create's vault visual state in sync for this custom multiblock
    @Override
    public void notifyMultiUpdated() {
        updateLargeState(getWidth() > 2, 6);
        super.notifyMultiUpdated();
        SmartContainerTablets.inheritPresentation(this);
        if(powerGridInstalled()) PowerGridDeviceConnectors.invalidate(this);
    }

    // Reset the custom large-model state when the multiblock is split
    @Override
    public void removeController(boolean keepContents) {
        super.removeController(keepContents);
        updateLargeState(false, 22);
        if(powerGridInstalled()) PowerGridDeviceConnectors.invalidate(this);
    }

    // The battery uses vault connectivity but never exposes an item inventory
    @Override
    public boolean hasInventory() {
        return false;
    }

    // Get all loaded members belonging to this controller
    private List<SmartBatteryBlockEntity> members() {
        List<SmartBatteryBlockEntity> members = new ArrayList<>();
        if (level == null) {
            members.add(this);
            return members;
        }
        BlockPos controller = getController();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        pending.add(controller);
        while (!pending.isEmpty() && members.size() < FULL_BLOCKS) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos) || !level.isLoaded(pos)) continue;
            BlockEntity blockEntity = getLoadedMember(pos);
            if (!(blockEntity instanceof SmartBatteryBlockEntity battery)
                    || !controller.equals(battery.getController())) continue;
            members.add(battery);
            for (Direction direction : Direction.values()) pending.add(pos.relative(direction));
        }
        if (members.isEmpty()) members.add(this);
        return members;
    }

    // Calculate capacity from one block at 10 MFE to the full 3x3x9 multiblock at 5 GFE
    private static long capacity(int blocks) {
        int count = Math.max(1, Math.min(FULL_BLOCKS, blocks));
        long added = (long) (count - 1) * (FULL_CAPACITY - SINGLE_CAPACITY) / (FULL_BLOCKS - 1);
        return SINGLE_CAPACITY + added;
    }

    // Get exact stored energy across the loaded multiblock members
    private long storedEnergy() {
        return storedEnergy(members());
    }

    // Sum the same loaded membership already captured for this transfer
    private long storedEnergy(List<SmartBatteryBlockEntity> members){
        long stored = 0L;
        for(SmartBatteryBlockEntity member : members) stored += member.energy;
        return stored;
    }

    // Set the custom battery large-model property
    private void updateLargeState(boolean large, int flags) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(ItemVaultBlock.LARGE) || state.getValue(ItemVaultBlock.LARGE) == large) return;
        level.setBlock(worldPosition, state.setValue(ItemVaultBlock.LARGE, large), flags);
    }

    // Mark one member after an energy mutation
    private static void changed(SmartBatteryBlockEntity battery) {
        battery.setChanged();
        if (battery.level != null) {
            battery.level.sendBlockUpdated(battery.worldPosition, battery.getBlockState(),
                    battery.getBlockState(), 2);
        }
    }

    // Get the shared controller
    private SmartBatteryBlockEntity controller() {
        return getControllerBE() instanceof SmartBatteryBlockEntity smart ? smart : this;
    }

    // Receive FE without NeoForge's integer capability limit
    private long receiveExactEnergy(long maximum, boolean simulate) {
        if (maximum <= 0L) return 0L;
        if(!simulate) synchronizePowerGrid();
        List<SmartBatteryBlockEntity> members = members();
        long totalCapacity = capacity(members.size());
        long accepted = Math.min(maximum, Math.max(0L, totalCapacity - storedEnergy(members)));
        if (simulate || accepted <= 0L) return accepted;
        long remaining = accepted;
        long memberCapacity = Math.max(SINGLE_CAPACITY,
                (totalCapacity + members.size() - 1L) / members.size());
        for (SmartBatteryBlockEntity member : members) {
            long inserted = Math.min(remaining, Math.max(0L, memberCapacity - member.energy));
            if (inserted <= 0L) continue;
            member.energy += inserted;
            remaining -= inserted;
            changed(member);
            if (remaining == 0L) break;
        }
        long inserted = accepted - remaining;
        transferMeter.record(inserted, 0);
        refreshPowerGrid();
        return inserted;
    }

    // Extract FE without NeoForge's integer capability limit
    private long extractExactEnergy(long maximum, boolean simulate) {
        if (maximum <= 0L) return 0L;
        if(!simulate) synchronizePowerGrid();
        List<SmartBatteryBlockEntity> members = members();
        long extracted = Math.min(maximum, storedEnergy(members));
        if (simulate || extracted <= 0L) return extracted;
        long remaining = extracted;
        for (SmartBatteryBlockEntity member : members) {
            long removed = Math.min(remaining, member.energy);
            if (removed <= 0L) continue;
            member.energy -= removed;
            remaining -= removed;
            changed(member);
            if (remaining == 0L) break;
        }
        long removed = extracted - remaining;
        transferMeter.record(0, removed);
        refreshPowerGrid();
        return removed;
    }

    // Expose the controller's distributed FE storage
    private final class CombinedEnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maximum, boolean simulate) {
            return (int) receiveExactEnergy(maximum, simulate);
        }

        @Override
        public int extractEnergy(int maximum, boolean simulate) {
            return (int) extractExactEnergy(maximum, simulate);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, getLongEnergyStored());
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, getLongCapacity());
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
    // Include decorative panels spanning the edge of their owning member
    @Override
    public AABB getRenderBoundingBox(){
        return super.getRenderBoundingBox().inflate(1.0D);
    }
}
