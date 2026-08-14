package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Expose navigation table selection and run controls through graph data ports
public final class NavigationTableGraphCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String BLOCK_ID = "createthrusters:advanced_navigation_table";
    public static final String SELECTED_SLOT_PORT = "selected_slot";
    public static final String RUN_STATE_PORT = "run_state";
    public static final String START_PORT = "start";
    public static final String PAUSE_PORT = "pause";
    public static final String STOP_PORT = "stop";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the navigation table graph compat
    private NavigationTableGraphCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a target
    public static boolean isTarget(Object target) {
        return target instanceof NavigationTableExtensionAccess;
    }

    // Check if this is a target
    public static boolean isTarget(AdvancedGraphDocument.Node node) {
        return node != null && BLOCK_ID.equalsIgnoreCase(
                node.data().getCompound("TargetData").getString("BlockId"));
    }

    // Get the readable data
    public static Map<String, String> readableData(Object target) {
        if (!isTarget(target)) {
            return Map.of();
        }
        return Map.of(
                SELECTED_SLOT_PORT, "number",
                RUN_STATE_PORT, "string");
    }

    // Get the writable data
    public static Map<String, String> writableData(Object target) {
        if (!isTarget(target)) {
            return Map.of();
        }
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put(SELECTED_SLOT_PORT, "number");
        ports.put(RUN_STATE_PORT, "string");
        ports.put(START_PORT, "boolean");
        ports.put(PAUSE_PORT, "boolean");
        ports.put(STOP_PORT, "boolean");
        return ports;
    }

    // Get the writable options
    public static CompoundTag writableOptions(Object target) {
        CompoundTag opts = new CompoundTag();
        if (!isTarget(target)) {
            return opts;
        }
        ListTag states = new ListTag();
        states.add(StringTag.valueOf("idle"));
        states.add(StringTag.valueOf("running"));
        states.add(StringTag.valueOf("paused"));
        opts.put(RUN_STATE_PORT, states);
        return opts;
    }

    // Check if this compatibility handler owns the target port
    public static boolean handles(Object target, String port) {
        return isTarget(target) && writableData(target).containsKey(port);
    }

    // Get the ports requiring write
    public static Set<String> portsRequiringWrite(AdvancedGraphDocument.Node node,
                                                   Set<String> activePorts) {
        if (!isTarget(node) || activePorts == null || activePorts.isEmpty()) {
            return Set.of();
        }
        return activePorts.stream()
                .filter(port -> START_PORT.equals(port) || PAUSE_PORT.equals(port) || STOP_PORT.equals(port))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // Read the navigation table graph compat
    public static @org.jetbrains.annotations.Nullable AdvancedGraphDocument.Value read(
            Object target, String port) {
        if (!(target instanceof NavigationTableExtensionAccess navigationTable)) {
            return null;
        }
        return switch (port) {
            case SELECTED_SLOT_PORT -> AdvancedGraphDocument.Value.number(navigationTable.ct$getSelectedSlot());
            case RUN_STATE_PORT -> AdvancedGraphDocument.Value.string(
                    navigationTable.ct$getRunState().name().toLowerCase(Locale.ROOT));
            default -> null;
        };
    }

    // Write the navigation table graph compat
    public static boolean write(Object target, Set<String> activePorts,
                                Function<String, AdvancedGraphDocument.Value> values) {
        if (!(target instanceof NavigationTableExtensionAccess navigationTable)
                || activePorts == null || values == null) {
            return false;
        }
        boolean changed = false;
        if (activePorts.contains(SELECTED_SLOT_PORT)) {
            navigationTable.ct$setSelectedSlot((int) Math.round(values.apply(SELECTED_SLOT_PORT).asNumber()));
            changed = true;
        }
        if (activePorts.contains(RUN_STATE_PORT)) {
            applyRunState(navigationTable, values.apply(RUN_STATE_PORT).asString());
            changed = true;
        }
        if (activePorts.contains(START_PORT) && values.apply(START_PORT).asBoolean()) {
            navigationTable.ct$startNavigation();
            changed = true;
        }
        if (activePorts.contains(PAUSE_PORT) && values.apply(PAUSE_PORT).asBoolean()) {
            navigationTable.ct$pauseNavigation();
            changed = true;
        }
        if (activePorts.contains(STOP_PORT) && values.apply(STOP_PORT).asBoolean()) {
            navigationTable.ct$stopNavigation();
            changed = true;
        }
        return changed;
    }

    // Apply the run state
    private static void applyRunState(NavigationTableExtensionAccess navigationTable, String state) {
        switch (state == null ? "" : state.trim().toLowerCase(Locale.ROOT)) {
            case "running", "run", "start" -> navigationTable.ct$startNavigation();
            case "paused", "pause" -> navigationTable.ct$pauseNavigation();
            case "idle", "stopped", "stop" -> navigationTable.ct$stopNavigation();
            default -> {
            }
        }
    }
}
