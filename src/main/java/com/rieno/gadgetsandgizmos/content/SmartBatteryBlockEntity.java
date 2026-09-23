package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
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

// Store FE across a vault-shaped multiblock without exposing an item inventory
public class SmartBatteryBlockEntity extends ItemVaultBlockEntity implements LongEnergyStorage {
    private static final String ENERGY_TAG = "Energy";
    private static final long SINGLE_CAPACITY = 10_000_000L;
    private static final long FULL_CAPACITY = 5_000_000_000L;
    private static final int FULL_BLOCKS = 81;

    private long energy;
    private final IEnergyStorage energyHandler = new CombinedEnergyStorage();

    // Initialize the smart battery block entity
    public SmartBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SMART_BATTERY.get(), pos, state);
    }

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
    }

    // Write the battery data
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putLong(ENERGY_TAG, energy);
    }

    // Keep Create's vault visual state in sync for this custom multiblock
    @Override
    public void notifyMultiUpdated() {
        updateLargeState(getWidth() > 2, 6);
        super.notifyMultiUpdated();
    }

    // Reset the custom large-model state when the multiblock is split
    @Override
    public void removeController(boolean keepContents) {
        super.removeController(keepContents);
        updateLargeState(false, 22);
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
            BlockEntity blockEntity = level.getBlockEntity(pos);
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
        long stored = 0L;
        for (SmartBatteryBlockEntity member : members()) stored += member.energy;
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
        List<SmartBatteryBlockEntity> members = members();
        long totalCapacity = capacity(members.size());
        long accepted = Math.min(maximum, Math.max(0L, totalCapacity - storedEnergy()));
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
        return accepted - remaining;
    }

    // Extract FE without NeoForge's integer capability limit
    private long extractExactEnergy(long maximum, boolean simulate) {
        if (maximum <= 0L) return 0L;
        List<SmartBatteryBlockEntity> members = members();
        long extracted = Math.min(maximum, storedEnergy());
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
        return extracted - remaining;
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
}
