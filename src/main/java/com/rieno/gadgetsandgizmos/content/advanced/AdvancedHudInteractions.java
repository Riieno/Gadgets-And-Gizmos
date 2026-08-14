package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Resolve HUD hit targets and route clicks back into the graph runtime
public final class AdvancedHudInteractions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String ELEMENTS = "WidgetElements";
    private static final String MANAGED_PORTS = "HudInteractionPorts";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD interactions
    private AdvancedHudInteractions() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is an interactive type
    public static boolean isInteractiveType(String type) {
        return "button".equals(type) || "toggle".equals(type) || "slider".equals(type)
                || "text_input".equals(type);
    }

    // Sync the advanced HUD interactions
    public static void synchronize(
            AdvancedGraphDocument.Node node, List<AdvancedGraphDocument.Edge> edges) {
        if (node == null || !("advanced_hud_element".equals(node.type())
                || "acc_display_widget".equals(node.type())
                || "acc_hologram_widget".equals(node.type()))) {
            return;
        }
        ListTag elements = node.data().getList(ELEMENTS, Tag.TAG_COMPOUND);
        Set<String> previousPorts = new LinkedHashSet<>();
        ListTag managedPorts = node.data().getList(MANAGED_PORTS, Tag.TAG_STRING);
        for (int idx = 0; idx < managedPorts.size(); idx++) {
            addIfPresent(previousPorts, managedPorts.getString(idx));
        }
        for (int idx = 0; idx < elements.size(); idx++) {
            CompoundTag elm = elements.getCompound(idx);
            addIfPresent(previousPorts, elm.getString("ExecPort"));
            addIfPresent(previousPorts, elm.getString("ValuePort"));
        }

        CompoundTag outputs = node.data().getCompound("DynamicOutputs");
        previousPorts.forEach(outputs::remove);
        Set<String> used = new LinkedHashSet<>(outputs.getAllKeys());
        Set<String> currentPorts = new LinkedHashSet<>();
        for (int idx = 0; idx < elements.size(); idx++) {
            CompoundTag elm = elements.getCompound(idx);
            String type = elm.getString("Type");
            if (!isInteractiveType(type)) {
                elm.remove("ExecPort");
                elm.remove("ValuePort");
                continue;
            }
            if (elm.getString("InteractionId").isBlank()) {
                elm.putString("InteractionId", UUID.randomUUID().toString());
            }
            String base = "hud_" + (idx + 1);
            String execPort = AdvancedGraphFunctions.uniquePort(
                    elm.getString("ExecPort").isBlank()
                            ? base + "_interacted" : elm.getString("ExecPort"), used);
            String valuePort = AdvancedGraphFunctions.uniquePort(
                    elm.getString("ValuePort").isBlank()
                            ? base + "_value" : elm.getString("ValuePort"), used);
            elm.putString("ExecPort", execPort);
            elm.putString("ValuePort", valuePort);
            currentPorts.add(execPort);
            currentPorts.add(valuePort);
            outputs.putString(execPort, "exec");
            outputs.putString(valuePort, "slider".equals(type) ? "number"
                    : "text_input".equals(type) ? "string" : "boolean");
        }
        if (outputs.isEmpty()) {
            node.data().remove("DynamicOutputs");
        } else {
            node.data().put("DynamicOutputs", outputs);
        }
        ListTag nextManagedPorts = new ListTag();
        currentPorts.forEach(port ->
                nextManagedPorts.add(net.minecraft.nbt.StringTag.valueOf(port)));
        if (nextManagedPorts.isEmpty()) {
            node.data().remove(MANAGED_PORTS);
        } else {
            node.data().put(MANAGED_PORTS, nextManagedPorts);
        }
        if (edges != null) {
            Set<String> valid = new LinkedHashSet<>(outputs.getAllKeys());
            edges.removeIf(edge -> node.id().equals(edge.fromNode())
                    && previousPorts.contains(edge.fromPort()) && !valid.contains(edge.fromPort()));
        }
        node.data().put(ELEMENTS, elements);
    }

    // Add the advanced HUD interactions if present
    private static void addIfPresent(Set<String> ports, String port) {
        if (port != null && !port.isBlank()) {
            ports.add(port);
        }
    }
}
