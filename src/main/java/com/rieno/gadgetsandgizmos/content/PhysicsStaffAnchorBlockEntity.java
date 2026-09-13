package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerTracker;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffWorldPowerRegistry;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffWorldPowerSource;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

// Hold a Physics Staff beside a Backtank and supply its retained locks
public class PhysicsStaffAnchorBlockEntity extends SmartBlockEntity implements PhysicsStaffWorldPowerSource {
    private static final String STAFF_KEY = "Staff";
    private static final String PLACED_TICK_KEY = "PlacedTick";
    private static final long STARTUP_GRACE_TICKS = 20L;

    private ItemStack staff = ItemStack.EMPTY;
    private long placedTick = Long.MIN_VALUE;

    // Initialize the placed staff
    public PhysicsStaffAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.PHYSICS_STAFF_ANCHOR.get(), pos, state);
    }

    // Tick the placed staff
    public static void tickServer(Level level, BlockPos pos, BlockState state, PhysicsStaffAnchorBlockEntity be) {
        be.registerSource();
    }

    // Add block entity behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Store the placed staff
    public void setStaff(ItemStack stack) {
        staff = stack.copyWithCount(1);
        if (level != null) placedTick = level.getGameTime();
        setChanged();
        sendData();
        registerSource();
    }

    // Get the stored staff for block drops
    public ItemStack getStaff() {
        return staff.copy();
    }

    // Remove the stored staff for a wrench pickup
    public ItemStack takeStaffForPickup() {
        ItemStack pickedUp = staff.copy();
        unregisterSource();
        staff = ItemStack.EMPTY;
        setChanged();
        sendData();
        return pickedUp;
    }

    // Get the staff supplied by this source
    @Override
    public UUID physicsStaffId() {
        return PhysicsStaffPowerHooks.getStaffId(staff);
    }

    // Get the current placed-staff source status
    @Override
    public Status physicsStaffPowerStatus() {
        if (staff.isEmpty() || level == null) return Status.UNAVAILABLE;
        if (placedTick != Long.MIN_VALUE && level.getGameTime() - placedTick < STARTUP_GRACE_TICKS) return Status.STARTING;
        BacktankBlockEntity backtank = connectedBacktank();
        return backtank != null && backtank.getAirLevel() > 0 ? Status.AVAILABLE : Status.UNAVAILABLE;
    }

    // Consume pressure from the adjacent Backtank
    @Override
    public boolean consumePhysicsStaffPower(int amount) {
        if (amount <= 0) return true;
        BacktankBlockEntity backtank = connectedBacktank();
        if (backtank == null || backtank.getAirLevel() <= 0 || level == null || level.isClientSide) return false;

        int available = backtank.getAirLevel();
        int drained = Math.min(available, amount);
        int previousSignal = backtank.getComparatorOutput();
        backtank.setAirLevel(available - drained);
        backtank.setChanged();
        if (previousSignal != backtank.getComparatorOutput()) {
            level.updateNeighbourForOutputSignal(backtank.getBlockPos(), backtank.getBlockState().getBlock());
        }
        return drained >= amount;
    }

    // Register the source when its chunk is loaded
    @Override
    public void onLoad() {
        super.onLoad();
        registerSource();
    }

    // Unregister the source when its chunk unloads
    @Override
    public void onChunkUnloaded() {
        unregisterSource();
        super.onChunkUnloaded();
    }

    // Release retained locks when this placed staff is destroyed
    public void releaseLocks() {
        if (level == null || level.isClientSide || level.getServer() == null) return;
        UUID staffId = physicsStaffId();
        if (staffId != null) {
            PhysicsStaffPowerTracker.get(level.getServer()).releaseWorldPoweredStaff(level.getServer(), staffId);
        }
        unregisterSource();
    }

    // Write the placed staff
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        if (!staff.isEmpty()) tag.put(STAFF_KEY, staff.saveOptional(provider));
        tag.putLong(PLACED_TICK_KEY, placedTick);
    }

    // Read the placed staff
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        staff = tag.contains(STAFF_KEY) ? ItemStack.parseOptional(provider, tag.getCompound(STAFF_KEY)) : ItemStack.EMPTY;
        placedTick = tag.contains(PLACED_TICK_KEY) ? tag.getLong(PLACED_TICK_KEY) : Long.MIN_VALUE;
    }

    // Find a Backtank on any adjacent side of the visual staff
    private BacktankBlockEntity connectedBacktank() {
        BacktankBlockEntity direct = connectedBacktank(worldPosition);
        return direct != null ? direct : connectedBacktank(worldPosition.below());
    }

    // Find a Backtank beside one staff section
    private BacktankBlockEntity connectedBacktank(BlockPos pos) {
        if (level == null) return null;
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (blockEntity instanceof BacktankBlockEntity backtank) return backtank;
        }
        return null;
    }

    // Register with the library tracker
    private void registerSource() {
        if (level == null || level.isClientSide || level.getServer() == null || physicsStaffId() == null) return;
        PhysicsStaffWorldPowerRegistry.register(level.getServer(), this);
    }

    // Remove this source from the library tracker
    private void unregisterSource() {
        if (level == null || level.isClientSide || level.getServer() == null || physicsStaffId() == null) return;
        PhysicsStaffWorldPowerRegistry.unregister(level.getServer(), this);
    }
}
