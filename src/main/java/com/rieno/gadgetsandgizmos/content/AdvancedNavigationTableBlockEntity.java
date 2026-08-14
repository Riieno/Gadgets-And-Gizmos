package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableMapResolver;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogMath;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSource;
import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Store navigation targets and expose resolved ship-space data to controllers and graphs
public class AdvancedNavigationTableBlockEntity extends NavTableBlockEntity
        implements NavigationTableExtensionAccess, DirectionalAnalogSource, AdvancedGraphDataProvider, INamedBlockEntity, MenuProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked maps
    private final NonNullList<ItemStack> maps = NonNullList.withSize(NavigationTableExtensionAccess.SLOT_COUNT, ItemStack.EMPTY);
    // Tracked resolved targets
    private final List<NavigationTableMapResolver.ResolvedTarget> resolvedTargets = new ArrayList<>(NavigationTableExtensionAccess.SLOT_COUNT);
    // Item handler
    private final NavigationInventoryHandler itemHandler = new NavigationInventoryHandler();
    // Selected slot
    private int selectedSlot = 0;
    // Current bottom redstone signal
    private int bottomRedstoneSignal = 0;
    // Current run state
    private NavigationTableExtensionAccess.RunState runState = NavigationTableExtensionAccess.RunState.IDLE;
    // Current directional snapshot
    private DirectionalAnalogSnapshot directionalSnapshot = DirectionalAnalogSnapshot.ZERO;
    // Target label
    private @Nullable String targetLabel;
    // Current custom name
    private @Nullable String customName;
    // Display distance tick count
    private int displayDistanceTicks = 0;
    // Current display distance to target
    private double displayDistanceToTarget = -1.0D;
    // Last display distance to target
    private double lastDisplayDistanceToTarget = -1.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced navigation table
    public AdvancedNavigationTableBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.ADVANCED_NAVIGATION_TABLE.get(), pos, state);
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            resolvedTargets.add(null);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the server
    public static void tickServer(Level level, BlockPos pos, BlockState state, AdvancedNavigationTableBlockEntity be) {
        be.tick();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the advanced navigation table
    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            updateRedstoneSlotSelection();
            syncHeldItemForRunState();
        }
        super.tick();
        updateExtendedState();
    }

    // Get the item handler
    public IItemHandlerModifiable getItemHandler() {
        return itemHandler;
    }

    // Write the advanced navigation table
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("CT_SelectedSlot", selectedSlot);
        tag.putString("CT_RunState", runState.name());
        if (customName != null && !customName.isBlank()) {
            tag.putString("CT_CustomName", customName);
        }

        writeMapItems(tag, registries);

        CompoundTag targetsTag = new CompoundTag();
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            NavigationTableMapResolver.ResolvedTarget target = resolvedTargets.get(i);
            if (target == null) {
                continue;
            }
            CompoundTag targetTag = new CompoundTag();
            targetTag.putDouble("X", target.targetPos().x);
            targetTag.putDouble("Y", target.targetPos().y);
            targetTag.putDouble("Z", target.targetPos().z);
            targetTag.putString("Label", target.label());
            targetTag.putBoolean("Banner", target.bannerTarget());
            targetTag.putDouble("DistanceSq", target.distanceSquared());
            targetsTag.put("T" + i, targetTag);
        }
        tag.put("CT_Targets", targetsTag);
    }

    // Write the advanced navigation table safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeMapItems(tag, registries);
    }

    // Write the map items
    private void writeMapItems(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag mapsTag = new CompoundTag();
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            ItemStack stack = maps.get(i);
            if (!stack.isEmpty()) {
                mapsTag.put("S" + i, stack.saveOptional(registries));
            }
        }
        tag.put("CT_Maps", mapsTag);
    }

    // Read the advanced navigation table
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        selectedSlot = Mth.clamp(tag.getInt("CT_SelectedSlot"), 0, NavigationTableExtensionAccess.SLOT_COUNT - 1);
        runState = switch (tag.getString("CT_RunState")) {
            case "RUNNING" -> NavigationTableExtensionAccess.RunState.RUNNING;
            case "PAUSED" -> NavigationTableExtensionAccess.RunState.PAUSED;
            default -> NavigationTableExtensionAccess.RunState.IDLE;
        };
        customName = tag.contains("CT_CustomName") ? tag.getString("CT_CustomName") : null;

        CompoundTag mapsTag = tag.contains("CT_Maps") ? tag.getCompound("CT_Maps") : new CompoundTag();
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            String key = "S" + i;
            ItemStack stack = mapsTag.contains(key)
                    ? ItemStack.parseOptional(registries, mapsTag.getCompound(key))
                    : ItemStack.EMPTY;
            maps.set(i, normalizeNavStack(stack));
        }

        CompoundTag targetsTag = tag.contains("CT_Targets") ? tag.getCompound("CT_Targets") : new CompoundTag();
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            String key = "T" + i;
            if (!targetsTag.contains(key)) {
                resolvedTargets.set(i, null);
                continue;
            }
            CompoundTag targetTag = targetsTag.getCompound(key);
            resolvedTargets.set(i, new NavigationTableMapResolver.ResolvedTarget(
                    i,
                    new Vec3(targetTag.getDouble("X"), targetTag.getDouble("Y"), targetTag.getDouble("Z")),
                    targetTag.getString("Label"),
                    targetTag.getBoolean("Banner"),
                    targetTag.getDouble("DistanceSq")));
        }
    }

    // Get the selected slot
    @Override
    public int ct$getSelectedSlot() {
        return selectedSlot;
    }

    // Set the selected slot
    @Override
    public void ct$setSelectedSlot(int slot) {
        int nextSlot = Mth.clamp(slot, 0, NavigationTableExtensionAccess.SLOT_COUNT - 1);
        if (selectedSlot != nextSlot) {
            selectedSlot = nextSlot;
            resetDisplayDistanceSample();
        }
        markNavigationChanged(true);
    }

    // Get the run state
    @Override
    public NavigationTableExtensionAccess.RunState ct$getRunState() {
        return runState;
    }

    // Start the navigation
    @Override
    public void ct$startNavigation() {
        runState = NavigationTableExtensionAccess.RunState.RUNNING;
        markNavigationChanged(true);
    }

    // Pause the navigation
    @Override
    public void ct$pauseNavigation() {
        runState = NavigationTableExtensionAccess.RunState.PAUSED;
        markNavigationChanged(true);
    }

    // Stop the navigation
    @Override
    public void ct$stopNavigation() {
        runState = NavigationTableExtensionAccess.RunState.IDLE;
        markNavigationChanged(true);
    }

    // Get the map in slot
    @Override
    public ItemStack ct$getMapInSlot(int slot) {
        return maps.get(clampSlot(slot));
    }

    // Set the map in slot
    @Override
    public void ct$setMapInSlot(int slot, ItemStack stack) {
        ct$setMapInSlot(slot, stack, null);
    }

    // Set the map in slot
    @Override
    public void ct$setMapInSlot(int slot, ItemStack stack, @Nullable Player player) {
        int idx = clampSlot(slot);
        if (!NavigationTableMapResolver.isNavigationMap(stack)) {
            removeMapInSlot(idx, player, true);
            return;
        }

        ItemStack prepared = prepareStoredMap(stack, player);
        ItemStack old = maps.get(idx);
        if (ItemStack.isSameItemSameComponents(old, prepared) && old.getCount() == prepared.getCount()) {
            return;
        }

        if (!old.isEmpty()) {
            prepareExtractedMap(old.copy(), player);
        }
        maps.set(idx, prepared);
        if (idx == selectedSlot) {
            resetDisplayDistanceSample();
        }
        markNavigationChanged(true);
    }

    // Remove the map in slot
    @Override
    public ItemStack ct$removeMapInSlot(int slot, @Nullable Player player) {
        return removeMapInSlot(clampSlot(slot), player, true);
    }

    // Get the resolved target
    @Override
    public @Nullable NavigationTableMapResolver.ResolvedTarget ct$getResolvedTarget(int slot) {
        return resolvedTargets.get(clampSlot(slot));
    }

    // Get the target label
    @Override
    public @Nullable String ct$getTargetLabel() {
        return targetLabel;
    }

    // Get the display distance to target
    public double ct$getDisplayDistanceToTarget() {
        return displayDistanceToTarget;
    }

    // Get the last display distance to target
    public double ct$getLastDisplayDistanceToTarget() {
        return lastDisplayDistanceToTarget;
    }

    // Get the relative angle deg
    @Override
    public float ct$getRelativeAngleDeg() {
        return getRelativeAngle();
    }

    // Get the directional snapshot
    @Override
    public DirectionalAnalogSnapshot ct$getDirectionalSnapshot() {
        return directionalSnapshot;
    }

    // Get the directional analog snapshot
    @Override
    public DirectionalAnalogSnapshot getDirectionalAnalogSnapshot() {
        return directionalSnapshot;
    }

    // Check if the directional analog is active
    @Override
    public boolean isDirectionalAnalogActive() {
        return runState == NavigationTableExtensionAccess.RunState.RUNNING && directionalSnapshot.magnitude() > 0.0D;
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("pointing_direction", "direction");
        data.put("redstone_forward", "number");
        data.put("redstone_backward", "number");
        data.put("redstone_left", "number");
        data.put("redstone_right", "number");
        data.put("redstone_north", "number");
        data.put("redstone_south", "number");
        data.put("redstone_east", "number");
        data.put("redstone_west", "number");
        data.put("redstone_up", "number");
        data.put("redstone_down", "number");
        data.put("redstone_input", "number");
        return data;
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        return Map.of();
    }

    // Read the graph data
    @Override
    public AdvancedGraphDocument.Value readGraphData(String field) {
        return switch (field) {
            case "pointing_direction" -> AdvancedGraphDocument.Value.direction(
                    getBlockState().getValue(BlockStateProperties.FACING).getSerializedName());
            case "redstone_forward" -> AdvancedGraphDocument.Value.number(directionalSnapshot.forwardRedstone());
            case "redstone_backward" -> AdvancedGraphDocument.Value.number(directionalSnapshot.backwardRedstone());
            case "redstone_left" -> AdvancedGraphDocument.Value.number(directionalSnapshot.leftRedstone());
            case "redstone_right" -> AdvancedGraphDocument.Value.number(directionalSnapshot.rightRedstone());
            case "redstone_input" -> AdvancedGraphDocument.Value.number(bottomRedstoneSignal);
            default -> AdvancedGraphDocument.Value.number(redstoneSignalForField(field));
        };
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, AdvancedGraphDocument.Value val) {
        return false;
    }

    // Get the redstone signal for field
    private int redstoneSignalForField(String field) {
        if (!field.startsWith("redstone_")) return 0;
        Direction dir = Direction.byName(field.substring("redstone_".length()));
        if (dir == null) return 0;
        return redstoneSignalForFace(dir);
    }

    // Get the redstone signal for face
    private int redstoneSignalForFace(Direction face) {
        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
        Direction horizontalFacing = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        if (face == horizontalFacing) return directionalSnapshot.forwardRedstone();
        if (face == horizontalFacing.getOpposite()) return directionalSnapshot.backwardRedstone();
        if (face == horizontalFacing.getCounterClockWise()) return directionalSnapshot.leftRedstone();
        if (face == horizontalFacing.getClockWise()) return directionalSnapshot.rightRedstone();
        return 0;
    }

    // Get the custom name
    @Override
    public @Nullable String getCustomName() {
        return customName;
    }

    // Set the custom name
    @Override
    public void setCustomName(@Nullable String name) {
        customName = name == null || name.isBlank() ? null : name.strip();
        markNavigationChanged(false);
    }

    // Clear the content
    @Override
    public void clearContent() {
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            removeMapInSlot(i, null, false);
        }
        super.clearContent();
        markNavigationChanged(true);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.navigation_table.screen.title");
    }

    // Create the menu
    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new NavigationTableMenu(containerId, playerInventory, worldPosition,
                SimulatedHelper.getContainingSubLevelId(this));
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        MenuOpenHeader.encode(buffer, worldPosition, SimulatedHelper.getContainingSubLevelId(this));
    }

    // Sync the held item for run state
    private void syncHeldItemForRunState() {
        ItemStack desired = ItemStack.EMPTY;
        if (runState == NavigationTableExtensionAccess.RunState.RUNNING) {
            desired = normalizeNavStack(ct$getMapInSlot(selectedSlot));
            maps.set(selectedSlot, desired);
        }

        ItemStack held = getHeldItem();
        if (!ItemStack.isSameItemSameComponents(held, desired) || held.getCount() != desired.getCount()) {
            super.setHeldItem(desired.copy());
            notifyUpdate();
        }
    }

    // Update the extended state
    private void updateExtendedState() {
        if (level == null) {
            clearResolvedTargets();
            directionalSnapshot = DirectionalAnalogSnapshot.ZERO;
            targetLabel = null;
            resetDisplayDistanceSample();
            return;
        }

        if (!level.isClientSide) {
            refreshResolvedTargets();
            updateSelectedDistanceSample();
        }

        if (runState != NavigationTableExtensionAccess.RunState.RUNNING) {
            directionalSnapshot = DirectionalAnalogSnapshot.ZERO;
            targetLabel = null;
            return;
        }

        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
        Direction horizontalFacing = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        double angle = Math.toRadians(getRelativeAngle());
        Vec3 dir = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        Vec3 forward = Vec3.atLowerCornerOf(horizontalFacing.getNormal());
        Vec3 right = Vec3.atLowerCornerOf(horizontalFacing.getClockWise().getNormal());
        double localX = Mth.clamp(dir.dot(right), -1.0D, 1.0D);
        double localZ = Mth.clamp(dir.dot(forward), -1.0D, 1.0D);
        directionalSnapshot = DirectionalAnalogMath.fromLocal(localX, localZ, 0.0D);

        NavigationTableMapResolver.ResolvedTarget target = ct$getResolvedTarget(selectedSlot);
        targetLabel = target == null ? null : target.label();
    }

    // Remove the map in slot
    private ItemStack removeMapInSlot(int slot, @Nullable Player player, boolean notify) {
        ItemStack old = maps.get(slot);
        if (old.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = prepareExtractedMap(old.copy(), player);
        maps.set(slot, ItemStack.EMPTY);
        resolvedTargets.set(slot, null);
        if (slot == selectedSlot) {
            resetDisplayDistanceSample();
        }
        if (notify) {
            markNavigationChanged(true);
        }
        return extracted;
    }

    // Prepare the stored map
    private ItemStack prepareStoredMap(ItemStack stack, @Nullable Player player) {
        ItemStack copy = normalizeNavStack(stack);
        if (copy.isEmpty()) {
            return ItemStack.EMPTY;
        }
        NavigationTarget target = NavigationTarget.ofStack(copy);
        if (target != null) {
            target.onInsert(copy, this, player);
        }
        return copy;
    }

    // Prepare the extracted map
    private ItemStack prepareExtractedMap(ItemStack stack, @Nullable Player player) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        NavigationTarget target = NavigationTarget.ofStack(stack);
        if (target != null) {
            target.onExtract(stack, this, player);
        }
        return stack;
    }

    // Normalize the nav stack
    private static ItemStack normalizeNavStack(ItemStack stack) {
        if (!NavigationTableMapResolver.isNavigationMap(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    // Get the projected self pos
    @Override
    public Vec3 getProjectedSelfPos() {
        return SimulatedHelper.toGlobalWorldPosition(this, Vec3.atCenterOf(getBlockPos()));
    }

    // Get the target position
    @Override
    public @Nullable Vec3 getTargetPosition(boolean project) {
        Vec3 target = super.getTargetPosition(false);
        return project && target != null
                ? SimulatedHelper.projectOutOfSubLevels(getLevel(), target)
                : target;
    }

    // Refresh the resolved targets
    private void refreshResolvedTargets() {
        Vec3 origin = getProjectedSelfPos();
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            ItemStack map = ct$getMapInSlot(i);
            NavigationTableMapResolver.ResolvedTarget target = NavigationTableMapResolver.isNavigationMap(map)
                    ? NavigationTableMapResolver.resolveNearestTarget(getLevel(), origin, map, i, this)
                    : null;
            resolvedTargets.set(i, target);
        }
    }

    // Clear the resolved targets
    private void clearResolvedTargets() {
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            resolvedTargets.set(i, null);
        }
        resetDisplayDistanceSample();
    }

    // Update the redstone slot selection
    public void updateRedstoneSlotSelection() {
        if (level == null || level.isClientSide) {
            return;
        }
        int signal = getBottomRedstoneSignal();
        if (signal == bottomRedstoneSignal) {
            return;
        }

        bottomRedstoneSignal = signal;
        if (signal <= 0) {
            return;
        }

        int redstoneSlot = Mth.clamp(signal - 1, 0, NavigationTableExtensionAccess.SLOT_COUNT - 1);
        if (redstoneSlot != selectedSlot) {
            selectedSlot = redstoneSlot;
            resetDisplayDistanceSample();
            markNavigationChanged(true);
        }
    }

    // Get the redstone input face
    public static Direction getRedstoneInputFace(BlockState state) {
        return state.getValue(BlockStateProperties.FACING).getOpposite();
    }

    // Get the bottom redstone signal
    private int getBottomRedstoneSignal() {
        Direction inputFace = getRedstoneInputFace(getBlockState());
        BlockPos inputPos = worldPosition.relative(inputFace);
        int weakSignal = level.getSignal(inputPos, inputFace);
        int directSignal = level.getDirectSignal(inputPos, inputFace);
        return Mth.clamp(Math.max(weakSignal, directSignal), 0, 15);
    }

    // Update the selected distance sample
    private void updateSelectedDistanceSample() {
        NavigationTableMapResolver.ResolvedTarget target = ct$getResolvedTarget(selectedSlot);
        if (target == null) {
            resetDisplayDistanceSample();
            return;
        }

        double distance = getProjectedSelfPos().distanceTo(target.targetPos());
        if (!Double.isFinite(distance)) {
            resetDisplayDistanceSample();
            return;
        }

        if (displayDistanceToTarget < 0.0D) {
            displayDistanceToTarget = distance;
            lastDisplayDistanceToTarget = distance;
            displayDistanceTicks = 0;
            return;
        }

        if (++displayDistanceTicks >= 10) {
            lastDisplayDistanceToTarget = displayDistanceToTarget;
            displayDistanceToTarget = distance;
            displayDistanceTicks = 0;
        }
    }

    // Reset the display distance sample
    private void resetDisplayDistanceSample() {
        displayDistanceTicks = 0;
        displayDistanceToTarget = -1.0D;
        lastDisplayDistanceToTarget = -1.0D;
    }

    // Mark the navigation changed
    private void markNavigationChanged(boolean refreshTargets) {
        if (level != null && !level.isClientSide) {
            if (refreshTargets) {
                refreshResolvedTargets();
            }
            syncHeldItemForRunState();
        }
        setChanged();
        notifyUpdate();
        sendData();
    }

    // Clamp the slot
    private static int clampSlot(int slot) {
        return Mth.clamp(slot, 0, NavigationTableExtensionAccess.SLOT_COUNT - 1);
    }

    // Handle the navigation inventory handler
    private class NavigationInventoryHandler implements IItemHandlerModifiable {
        // Get the slots
        @Override
        public int getSlots() {
            return NavigationTableExtensionAccess.SLOT_COUNT;
        }

        // Get the stack in slot
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ct$getMapInSlot(slot).copy();
        }

        // Insert the item
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= getSlots() || stack == null || stack.isEmpty()
                    || !NavigationTableMapResolver.isNavigationMap(stack)) {
                return stack;
            }
            if (!ct$getMapInSlot(slot).isEmpty()) {
                return stack;
            }

            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            if (!simulate) {
                ct$setMapInSlot(slot, stack, null);
            }
            return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
        }

        // Extract the item
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= getSlots() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = ct$getMapInSlot(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = stack.copy();
            extracted.setCount(1);
            if (!simulate) {
                extracted = ct$removeMapInSlot(slot, null);
            }
            return extracted;
        }

        // Get the slot limit
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        // Check if the item is valid
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < getSlots() && NavigationTableMapResolver.isNavigationMap(stack);
        }

        // Set the stack in slot
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot < 0 || slot >= getSlots()) {
                return;
            }
            ct$setMapInSlot(slot, stack, null);
        }
    }
}
