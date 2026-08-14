package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableMapResolver;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Read and edit navigation targets through the same rules used by the table screen
public class NavigationTablePeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final BlockEntity blockEntity;
    // Ext
    private final NavigationTableExtensionAccess ext;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the navigation table peripheral
    public NavigationTablePeripheral(BlockEntity blockEntity, NavigationTableExtensionAccess ext) {
        this.blockEntity = blockEntity;
        this.ext = ext;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return "navigation_table";
    }

    // Compare this navigation table peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof NavigationTablePeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Get the name
    @LuaFunction
    public final String getName() {
        String name = blockEntity instanceof INamedBlockEntity named ? named.getCustomName() : null;
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction
    public final void setName(String name) {
        if (blockEntity instanceof INamedBlockEntity named) {
            named.setCustomName(name == null || name.isBlank() ? null : name.strip());
        }
    }

    // Get the slot count
    @LuaFunction
    public final int getSlotCount() {
        return NavigationTableExtensionAccess.SLOT_COUNT;
    }

    // Get the selected slot
    @LuaFunction
    public final int getSelectedSlot() {
        return ext.ct$getSelectedSlot() + 1;
    }

    // Set the selected slot
    @LuaFunction
    public final void setSelectedSlot(int slot) throws LuaException {
        ext.ct$setSelectedSlot(validateSlot(slot));
    }

    // Get the next slot
    @LuaFunction
    public final int nextSlot() {
        int slot = (ext.ct$getSelectedSlot() + 1) % NavigationTableExtensionAccess.SLOT_COUNT;
        ext.ct$setSelectedSlot(slot);
        return slot + 1;
    }

    // Get the previous slot
    @LuaFunction
    public final int previousSlot() {
        int slot = ext.ct$getSelectedSlot() - 1;
        if (slot < 0) {
            slot = NavigationTableExtensionAccess.SLOT_COUNT - 1;
        }
        ext.ct$setSelectedSlot(slot);
        return slot + 1;
    }

    // Get the slot
    @LuaFunction
    public final Map<String, Object> getSlot(int slot) throws LuaException {
        return describeSlot(validateSlot(slot));
    }

    // Check if this has map
    @LuaFunction
    public final boolean hasMap(int slot) throws LuaException {
        return !ext.ct$getMapInSlot(validateSlot(slot)).isEmpty();
    }

    // Get the map name
    @LuaFunction
    public final String getMapName(int slot) throws LuaException {
        var map = ext.ct$getMapInSlot(validateSlot(slot));
        return map.isEmpty() ? "" : map.getHoverName().getString();
    }

    // Get the slot target
    @LuaFunction
    public final Map<String, Object> getSlotTarget(int slot) throws LuaException {
        int zeroBasedSlot = validateSlot(slot);
        return describeTarget(zeroBasedSlot, ext.ct$getResolvedTarget(zeroBasedSlot));
    }

    // Get the list slots
    @LuaFunction
    public final Object[] listSlots() {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            slots.add(describeSlot(i));
        }
        return slots.toArray();
    }

    // Get the filled slot count
    @LuaFunction
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
    @LuaFunction
    public final void clearSlot(int slot) throws LuaException {
        ext.ct$setMapInSlot(validateSlot(slot), net.minecraft.world.item.ItemStack.EMPTY);
    }

    // Clear every navigation slot
    @LuaFunction
    public final void clearAllSlots() {
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            ext.ct$setMapInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    // Get the state
    @LuaFunction
    public final String getState() {
        return ext.ct$getRunState().name().toLowerCase(java.util.Locale.ROOT);
    }

    // Set the state
    @LuaFunction
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
    @LuaFunction
    public final boolean isRunning() {
        return ext.ct$getRunState() == NavigationTableExtensionAccess.RunState.RUNNING;
    }

    // Check if this is paused
    @LuaFunction
    public final boolean isPaused() {
        return ext.ct$getRunState() == NavigationTableExtensionAccess.RunState.PAUSED;
    }

    // Check if this is idle
    @LuaFunction
    public final boolean isIdle() {
        return ext.ct$getRunState() == NavigationTableExtensionAccess.RunState.IDLE;
    }

    // Start the navigation table peripheral
    @LuaFunction
    public final void start() {
        ext.ct$startNavigation();
    }

    // Pause the navigation table peripheral
    @LuaFunction
    public final void pause() {
        ext.ct$pauseNavigation();
    }

    // Stop the navigation table peripheral
    @LuaFunction
    public final void stop() {
        ext.ct$stopNavigation();
    }

    // Get the table position
    @LuaFunction
    public final Map<String, Object> getTablePosition() {
        return ComputerCraftPositionHelper.blockPosition(blockEntity);
    }

    // Get the block pos
    @LuaFunction
    public final Map<String, Object> getBlockPos() {
        return getTablePosition();
    }

    // Get the block
    @LuaFunction(value = "getblock")
    public final Map<String, Object> getBlock() {
        return getTablePosition();
    }

    // Get the current angle
    @LuaFunction
    public final double getCurrentAngle() {
        return ext.ct$getRelativeAngleDeg();
    }

    // Get the vector
    @LuaFunction
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
    @LuaFunction
    public final boolean hasTarget() {
        return ext.ct$getResolvedTarget(ext.ct$getSelectedSlot()) != null;
    }

    // Check if this has target in slot
    @LuaFunction
    public final boolean hasTargetInSlot(int slot) throws LuaException {
        return ext.ct$getResolvedTarget(validateSlot(slot)) != null;
    }

    // Get the target label
    @LuaFunction
    public final String getTargetLabel() {
        String label = ext.ct$getTargetLabel();
        return label != null && !label.isBlank() ? label : null;
    }

    // Get the target label in slot
    @LuaFunction
    public final String getTargetLabelInSlot(int slot) throws LuaException {
        NavigationTableMapResolver.ResolvedTarget target = ext.ct$getResolvedTarget(validateSlot(slot));
        if (target == null || target.label() == null || target.label().isBlank()) {
            return null;
        }
        return target.label();
    }

    // Get the target
    @LuaFunction
    public final Map<String, Object> getTarget() {
        return describeTarget(ext.ct$getSelectedSlot(), ext.ct$getResolvedTarget(ext.ct$getSelectedSlot()));
    }

    // Get the target position
    @LuaFunction
    public final Map<String, Object> getTargetPosition() {
        return describePosition(ext.ct$getResolvedTarget(ext.ct$getSelectedSlot()));
    }

    // Get the target pos
    @LuaFunction(value = "getTargetPos")
    public final Map<String, Object> getTargetPos() {
        return getTargetPosition();
    }

    // Get the target position in slot
    @LuaFunction
    public final Map<String, Object> getTargetPositionInSlot(int slot) throws LuaException {
        return describePosition(ext.ct$getResolvedTarget(validateSlot(slot)));
    }

    // Get the target distance
    @LuaFunction
    public final double getTargetDistance() {
        NavigationTableMapResolver.ResolvedTarget target = ext.ct$getResolvedTarget(ext.ct$getSelectedSlot());
        return target == null ? -1.0D : Math.sqrt(target.distanceSquared());
    }

    // Get the target distance in slot
    @LuaFunction
    public final double getTargetDistanceInSlot(int slot) throws LuaException {
        NavigationTableMapResolver.ResolvedTarget target = ext.ct$getResolvedTarget(validateSlot(slot));
        return target == null ? -1.0D : Math.sqrt(target.distanceSquared());
    }

    // Get the selected map info
    @LuaFunction
    public final Map<String, Object> getSelectedMapInfo() throws LuaException {
        Map<String, Object> data = new LinkedHashMap<>();
        int selectedSlot = ext.ct$getSelectedSlot();
        data.putAll(describeSlot(selectedSlot));
        data.put("target", getTarget());
        return data;
    }

    // Get the status
    @LuaFunction
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

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "getName(): string",
                "setName(name:string)",
                "getSlotCount(): number",
                "getSelectedSlot(): number",
                "setSelectedSlot(slot:number)",
                "nextSlot(): number",
                "previousSlot(): number",
                "getSlot(slot:number): table",
                "hasMap(slot:number): boolean",
                "getMapName(slot:number): string",
                "getSlotTarget(slot:number): table",
                "listSlots(): table[]",
                "getFilledSlotCount(): number",
                "clearSlot(slot:number)",
                "clearAllSlots()",
                "getState(): string",
                "setState(state:string)",
                "isRunning(): boolean",
                "isPaused(): boolean",
                "isIdle(): boolean",
                "start()",
                "pause()",
                "stop()",
                "getTablePosition(): table",
                "getBlockPos(): table",
                "getblock(): table",
                "getCurrentAngle(): number",
                "getVector(): table",
                "hasTarget(): boolean",
                "hasTargetInSlot(slot:number): boolean",
                "getTargetLabel(): string?",
                "getTargetLabelInSlot(slot:number): string?",
                "getTarget(): table",
                "getTargetPosition(): table?",
                "getTargetPos(): table?",
                "getTargetPositionInSlot(slot:number): table?",
                "getTargetDistance(): number",
                "getTargetDistanceInSlot(slot:number): number",
                "getSelectedMapInfo(): table",
                "getStatus(): table",
                "help(method?: string): string|table"
        );
    }

    // Get the help
    @LuaFunction
    public final Object help(Optional<String> method) throws LuaException {
        Map<String, String> docs = Map.ofEntries(
                Map.entry("getName", "getName() -> current custom computer-facing name or empty string"),
                Map.entry("setName", "setName(name) -> set or clear the custom name used by this table"),
                Map.entry("getSlotCount", "getSlotCount() -> total stored navigation-item slots"),
                Map.entry("getSelectedSlot", "getSelectedSlot() -> current selected slot, 1-based"),
                Map.entry("setSelectedSlot", "setSelectedSlot(slot) -> select one stored navigation-item slot"),
                Map.entry("nextSlot", "nextSlot() -> advance selection, wrapping around, and return the new slot"),
                Map.entry("previousSlot", "previousSlot() -> move selection backward, wrapping around, and return the new slot"),
                Map.entry("getSlot", "getSlot(slot) -> item/selection/target data for one slot"),
                Map.entry("hasMap", "hasMap(slot) -> true when the slot contains a stored navigation item"),
                Map.entry("getMapName", "getMapName(slot) -> display name of the stored navigation item or empty string"),
                Map.entry("getSlotTarget", "getSlotTarget(slot) -> resolved target info for one slot, including coords when known"),
                Map.entry("listSlots", "listSlots() -> array of per-slot tables for all 15 slots"),
                Map.entry("getFilledSlotCount", "getFilledSlotCount() -> number of non-empty stored navigation-item slots"),
                Map.entry("clearSlot", "clearSlot(slot) -> remove the stored navigation item from one slot"),
                Map.entry("clearAllSlots", "clearAllSlots() -> remove every stored navigation item"),
                Map.entry("getState", "getState() -> current nav state: idle, running, or paused"),
                Map.entry("setState", "setState(state) -> switch the table to idle/running/paused"),
                Map.entry("isRunning", "isRunning() -> true when the selected navigation item is actively driving navigation output"),
                Map.entry("isPaused", "isPaused() -> true when navigation is paused"),
                Map.entry("isIdle", "isIdle() -> true when navigation is stopped"),
                Map.entry("start", "start() -> enter running state"),
                Map.entry("pause", "pause() -> enter paused state"),
                Map.entry("stop", "stop() -> enter idle state"),
                Map.entry("getTablePosition", "getTablePosition() -> projected world position with x/y/z, localX/localY/localZ, dimension, and subLevelId"),
                Map.entry("getBlockPos", "getBlockPos() -> alias of getTablePosition()"),
                Map.entry("getblock", "getblock() -> alias of getTablePosition()"),
                Map.entry("getCurrentAngle", "getCurrentAngle() -> current navigation angle in degrees"),
                Map.entry("getVector", "getVector() -> directional analogue snapshot and angle"),
                Map.entry("hasTarget", "hasTarget() -> true when the selected slot resolves to a target"),
                Map.entry("hasTargetInSlot", "hasTargetInSlot(slot) -> true when that slot resolves to a target"),
                Map.entry("getTargetLabel", "getTargetLabel() -> active target label for the selected slot when running"),
                Map.entry("getTargetLabelInSlot", "getTargetLabelInSlot(slot) -> resolved label for a stored slot when known"),
                Map.entry("getTarget", "getTarget() -> selected-slot target table including coords, distance, and state"),
                Map.entry("getTargetPosition", "getTargetPosition() -> selected-slot target x/y/z table or nil"),
                Map.entry("getTargetPos", "getTargetPos() -> alias of getTargetPosition()"),
                Map.entry("getTargetPositionInSlot", "getTargetPositionInSlot(slot) -> target x/y/z table for one slot or nil"),
                Map.entry("getTargetDistance", "getTargetDistance() -> horizontal distance from the table to the selected target, or -1 when absent"),
                Map.entry("getTargetDistanceInSlot", "getTargetDistanceInSlot(slot) -> distance for one slot, or -1 when absent"),
                Map.entry("getSelectedMapInfo", "getSelectedMapInfo() -> combined slot and target info for the selected navigation item"),
                Map.entry("getStatus", "getStatus() -> combined table status with target and vector data"),
                Map.entry("methods", "methods() -> list of all callable peripheral methods"),
                Map.entry("help", "help() -> all docs, help('name') -> one entry")
        );
        if (method.isEmpty()) {
            return docs;
        }
        String key = method.get();
        if (!docs.containsKey(key)) {
            throw new LuaException("unknown method '" + key + "'");
        }
        return docs.get(key);
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
