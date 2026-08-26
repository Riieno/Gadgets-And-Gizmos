package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableMapResolver;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Read and edit navigation targets through the same rules used by the table screen
@PeripheralTypeDoc("navigation_table")
public class NavigationTablePeripheral extends GadgetsPeripheral<BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    // Ext
    private final NavigationTableExtensionAccess ext;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the navigation table peripheral
    public NavigationTablePeripheral(BlockEntity blockEntity, NavigationTableExtensionAccess ext) {
        super(blockEntity, "navigation_table");
        this.ext = ext;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() {
        String name = blockEntity instanceof INamedBlockEntity named ? named.getCustomName() : null;
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name:string)",
            description = "Sets the name.")
    public final void setName(String name) {
        if (blockEntity instanceof INamedBlockEntity named) {
            named.setCustomName(name == null || name.isBlank() ? null : name.strip());
        }
    }

    // Get the slot count
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSlotCount", signature = "getSlotCount(): number",
            description = "Returns the slot count.")
    public final int getSlotCount() {
        return NavigationTableExtensionAccess.SLOT_COUNT;
    }

    // Get the selected slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSelectedSlot", signature = "getSelectedSlot(): number",
            description = "Returns the selected slot.")
    public final int getSelectedSlot() {
        return ext.ct$getSelectedSlot() + 1;
    }

    // Set the selected slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSelectedSlot", signature = "setSelectedSlot(slot:number)",
            description = "Sets the selected slot.")
    public final void setSelectedSlot(int slot) throws LuaException {
        ext.ct$setSelectedSlot(validateSlot(slot));
    }

    // Get the next slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "nextSlot", signature = "nextSlot(): number",
            description = "Returns the next slot.")
    public final int nextSlot() {
        int slot = (ext.ct$getSelectedSlot() + 1) % NavigationTableExtensionAccess.SLOT_COUNT;
        ext.ct$setSelectedSlot(slot);
        return slot + 1;
    }

    // Get the previous slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "previousSlot", signature = "previousSlot(): number",
            description = "Returns the previous slot.")
    public final int previousSlot() {
        int slot = ext.ct$getSelectedSlot() - 1;
        if (slot < 0) {
            slot = NavigationTableExtensionAccess.SLOT_COUNT - 1;
        }
        ext.ct$setSelectedSlot(slot);
        return slot + 1;
    }

    // Get the slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSlot", signature = "getSlot(slot:number): table",
            description = "Returns the slot.")
    public final Map<String, Object> getSlot(int slot) throws LuaException {
        return describeSlot(validateSlot(slot));
    }

    // Check if this has map
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasMap", signature = "hasMap(slot:number): boolean",
            description = "Returns whether this has map.")
    public final boolean hasMap(int slot) throws LuaException {
        return !ext.ct$getMapInSlot(validateSlot(slot)).isEmpty();
    }

    // Get the map name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMapName", signature = "getMapName(slot:number): string",
            description = "Returns the map name.")
    public final String getMapName(int slot) throws LuaException {
        var map = ext.ct$getMapInSlot(validateSlot(slot));
        return map.isEmpty() ? "" : map.getHoverName().getString();
    }

    // Get the slot target
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSlotTarget", signature = "getSlotTarget(slot:number): table",
            description = "Returns the slot target.")
    public final Map<String, Object> getSlotTarget(int slot) throws LuaException {
        int zeroBasedSlot = validateSlot(slot);
        return describeTarget(zeroBasedSlot, ext.ct$getResolvedTarget(zeroBasedSlot));
    }

    // Get the list slots
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listSlots", signature = "listSlots(): table[]",
            description = "Returns the list slots.")
    public final Object[] listSlots() {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            slots.add(describeSlot(i));
        }
        return slots.toArray();
    }

    // Get the filled slot count
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFilledSlotCount", signature = "getFilledSlotCount(): number",
            description = "Returns the filled slot count.")
    public final int getFilledSlotCount() {
        int filled = 0;
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            if (!ext.ct$getMapInSlot(i).isEmpty()) {
                filled++;
            }
        }
        return filled;
    }

    // Clear the slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearSlot", signature = "clearSlot(slot:number)",
            description = "Clears the slot.")
    public final void clearSlot(int slot) throws LuaException {
        ext.ct$setMapInSlot(validateSlot(slot), net.minecraft.world.item.ItemStack.EMPTY);
    }

    // Clear every navigation slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearAllSlots", signature = "clearAllSlots()",
            description = "Clears every navigation slot.")
    public final void clearAllSlots() {
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            ext.ct$setMapInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    // Get the state
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getState", signature = "getState(): string",
            description = "Returns the state.")
    public final String getState() {
        return ext.ct$getRunState().name().toLowerCase(java.util.Locale.ROOT);
    }

    // Set the state
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setState", signature = "setState(state:string)",
            description = "Sets the state.")
    public final void setState(String state) throws LuaException {
        if (state == null) {
            throw new LuaException("state must be 'idle', 'running', or 'paused'");
        }
        switch (state.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "idle", "stop", "stopped" -> ext.ct$stopNavigation();
            case "running", "run", "start" -> ext.ct$startNavigation();
            case "paused", "pause" -> ext.ct$pauseNavigation();
            default -> throw new LuaException("state must be 'idle', 'running', or 'paused'");
        }
    }

    // Check if this is running
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isRunning", signature = "isRunning(): boolean",
            description = "Returns whether this is running.")
    public final boolean isRunning() {
        return ext.ct$getRunState() == NavigationTableExtensionAccess.RunState.RUNNING;
    }

    // Check if this is paused
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isPaused", signature = "isPaused(): boolean",
            description = "Returns whether this is paused.")
    public final boolean isPaused() {
        return ext.ct$getRunState() == NavigationTableExtensionAccess.RunState.PAUSED;
    }

    // Check if this is idle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isIdle", signature = "isIdle(): boolean",
            description = "Returns whether this is idle.")
    public final boolean isIdle() {
        return ext.ct$getRunState() == NavigationTableExtensionAccess.RunState.IDLE;
    }

    // Start the navigation table peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "start", signature = "start()",
            description = "Start the navigation table peripheral.")
    public final void start() {
        ext.ct$startNavigation();
    }

    // Pause the navigation table peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pause", signature = "pause()",
            description = "Pause the navigation table peripheral.")
    public final void pause() {
        ext.ct$pauseNavigation();
    }

    // Stop the navigation table peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "stop", signature = "stop()",
            description = "Stop the navigation table peripheral.")
    public final void stop() {
        ext.ct$stopNavigation();
    }

    // Get the table position
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTablePosition", signature = "getTablePosition(): table",
            description = "Returns the table position.")
    public final Map<String, Object> getTablePosition() {
        return ComputerCraftPositionHelper.blockPosition(blockEntity);
    }

    // Get the block pos
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBlockPos", signature = "getBlockPos(): table",
            description = "Returns the block pos.")
    public final Map<String, Object> getBlockPos() {
        return getTablePosition();
    }

    // Get the block
    @LuaFunction(value = "getblock", mainThread = true)
    @PeripheralDoc(name = "getblock", signature = "getblock(): table",
            description = "Returns the block.")
    public final Map<String, Object> getBlock() {
        return getTablePosition();
    }

    // Get the current angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCurrentAngle", signature = "getCurrentAngle(): number",
            description = "Returns the current angle.")
    public final double getCurrentAngle() {
        return ext.ct$getRelativeAngleDeg();
    }

    // Get the vector
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getVector", signature = "getVector(): table",
            description = "Returns the vector.")
    public final Map<String, Object> getVector() {
        Map<String, Object> data = new LinkedHashMap<>();
        var snapshot = ext.ct$getDirectionalSnapshot();
        data.put("forward", snapshot.forward());
        data.put("backward", snapshot.backward());
        data.put("left", snapshot.left());
        data.put("right", snapshot.right());
        data.put("magnitude", snapshot.magnitude());
        data.put("angleDeg", ext.ct$getRelativeAngleDeg());
        return data;
    }

    // Check if this has target
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasTarget", signature = "hasTarget(): boolean",
            description = "Returns whether this has target.")
    public final boolean hasTarget() {
        return ext.ct$getResolvedTarget(ext.ct$getSelectedSlot()) != null;
    }

    // Check if this has target in slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasTargetInSlot", signature = "hasTargetInSlot(slot:number): boolean",
            description = "Returns whether this has target in slot.")
    public final boolean hasTargetInSlot(int slot) throws LuaException {
        return ext.ct$getResolvedTarget(validateSlot(slot)) != null;
    }

    // Get the target label
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetLabel", signature = "getTargetLabel(): string?",
            description = "Returns the target label.")
    public final String getTargetLabel() {
        String label = ext.ct$getTargetLabel();
        return label != null && !label.isBlank() ? label : null;
    }

    // Get the target label in slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetLabelInSlot", signature = "getTargetLabelInSlot(slot:number): string?",
            description = "Returns the target label in slot.")
    public final String getTargetLabelInSlot(int slot) throws LuaException {
        NavigationTableMapResolver.ResolvedTarget target = ext.ct$getResolvedTarget(validateSlot(slot));
        if (target == null || target.label() == null || target.label().isBlank()) {
            return null;
        }
        return target.label();
    }

    // Get the target
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTarget", signature = "getTarget(): table",
            description = "Returns the target.")
    public final Map<String, Object> getTarget() {
        return describeTarget(ext.ct$getSelectedSlot(), ext.ct$getResolvedTarget(ext.ct$getSelectedSlot()));
    }

    // Get the target position
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetPosition", signature = "getTargetPosition(): table?",
            description = "Returns the target position.")
    public final Map<String, Object> getTargetPosition() {
        return describePosition(ext.ct$getResolvedTarget(ext.ct$getSelectedSlot()));
    }

    // Get the target pos
    @LuaFunction(value = "getTargetPos", mainThread = true)
    @PeripheralDoc(name = "getTargetPos", signature = "getTargetPos(): table?",
            description = "Returns the target pos.")
    public final Map<String, Object> getTargetPos() {
        return getTargetPosition();
    }

    // Get the target position in slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetPositionInSlot", signature = "getTargetPositionInSlot(slot:number): table?",
            description = "Returns the target position in slot.")
    public final Map<String, Object> getTargetPositionInSlot(int slot) throws LuaException {
        return describePosition(ext.ct$getResolvedTarget(validateSlot(slot)));
    }

    // Get the target distance
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetDistance", signature = "getTargetDistance(): number",
            description = "Returns the target distance.")
    public final double getTargetDistance() {
        NavigationTableMapResolver.ResolvedTarget target = ext.ct$getResolvedTarget(ext.ct$getSelectedSlot());
        return target == null ? -1.0D : Math.sqrt(target.distanceSquared());
    }

    // Get the target distance in slot
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetDistanceInSlot", signature = "getTargetDistanceInSlot(slot:number): number",
            description = "Returns the target distance in slot.")
    public final double getTargetDistanceInSlot(int slot) throws LuaException {
        NavigationTableMapResolver.ResolvedTarget target = ext.ct$getResolvedTarget(validateSlot(slot));
        return target == null ? -1.0D : Math.sqrt(target.distanceSquared());
    }

    // Get the selected map info
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSelectedMapInfo", signature = "getSelectedMapInfo(): table",
            description = "Returns the selected map info.")
    public final Map<String, Object> getSelectedMapInfo() throws LuaException {
        Map<String, Object> data = new LinkedHashMap<>();
        int selectedSlot = ext.ct$getSelectedSlot();
        data.putAll(describeSlot(selectedSlot));
        data.put("target", getTarget());
        return data;
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", getName());
        data.put("state", getState());
        data.put("isRunning", isRunning());
        data.put("isPaused", isPaused());
        data.put("isIdle", isIdle());
        data.put("selectedSlot", getSelectedSlot());
        data.put("currentAngle", getCurrentAngle());
        data.put("target", getTarget());
        data.put("position", getTablePosition());
        data.put("totalSlots", getSlotCount());
        data.put("filledSlots", getFilledSlotCount());
        data.put("vector", getVector());
        return data;
    }

    // Validate the slot
    private int validateSlot(int slot) throws LuaException {
        if (slot < 1 || slot > NavigationTableExtensionAccess.SLOT_COUNT) {
            throw new LuaException("slot must be between 1 and " + NavigationTableExtensionAccess.SLOT_COUNT);
        }
        return slot - 1;
    }

    // Describe the slot
    private Map<String, Object> describeSlot(int zeroBasedSlot) {
        Map<String, Object> data = new LinkedHashMap<>();
        var map = ext.ct$getMapInSlot(zeroBasedSlot);
        data.put("slot", zeroBasedSlot + 1);
        data.put("filled", !map.isEmpty());
        data.put("selected", zeroBasedSlot == ext.ct$getSelectedSlot());
        if (!map.isEmpty()) {
            data.put("name", map.getHoverName().getString());
            data.put("count", map.getCount());
        }
        data.put("target", describeTarget(zeroBasedSlot, ext.ct$getResolvedTarget(zeroBasedSlot)));
        return data;
    }

    // Describe the target
    private Map<String, Object> describeTarget(int zeroBasedSlot, NavigationTableMapResolver.ResolvedTarget target) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("slot", zeroBasedSlot + 1);
        data.put("state", getState());
        data.put("selected", zeroBasedSlot == ext.ct$getSelectedSlot());
        data.put("angle", ext.ct$getRelativeAngleDeg());
        data.put("hasTarget", target != null);
        if (target == null) {
            data.put("label", "");
            data.put("banner", false);
            data.put("distance", -1.0D);
            data.put("position", null);
            return data;
        }
        data.put("label", target.label() == null ? "" : target.label());
        data.put("banner", target.bannerTarget());
        data.put("distance", Math.sqrt(target.distanceSquared()));
        data.put("position", describePosition(target));
        return data;
    }

    // Describe the position
    private Map<String, Object> describePosition(NavigationTableMapResolver.ResolvedTarget target) {
        if (target == null) {
            return null;
        }
        Map<String, Object> pos = ComputerCraftPositionHelper.worldPosition(
                blockEntity.getLevel(), target.targetPos());
        if (target.localTargetPos() != null) {
            pos.put("localX", target.localTargetPos().x);
            pos.put("localY", target.localTargetPos().y);
            pos.put("localZ", target.localTargetPos().z);
        }
        pos.put("subLevelId", target.subLevelId() == null ? "" : target.subLevelId().toString());
        return pos;
    }
}
