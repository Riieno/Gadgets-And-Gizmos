package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.graph.CompiledGraph;
import com.rieno.gadgetsandgizmos.lib.graph.GraphCompiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

// Compile an ACC graph into the compact execution data used by the portable runtime
final class AdvancedGraphProgram {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Graph
    private final AdvancedGraphDocument graph;
    // Index
    private final CompiledGraph<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> index;
    // Tracked tick nodes
    private final List<AdvancedGraphDocument.Node> tickNodes;
    // Tracked periodic nodes
    private final List<AdvancedGraphDocument.Node> periodicNodes;
    // Input nodes
    private final List<AdvancedGraphDocument.Node> inputNodes;
    // Tracked pulse on change nodes
    private final List<AdvancedGraphDocument.Node> pulseOnChangeNodes;
    // Tracked physical interaction nodes
    private final List<AdvancedGraphDocument.Node> physicalInteractionNodes;
    // Tracked HUD nodes
    private final List<AdvancedGraphDocument.Node> hudNodes;
    // Tracked trigger nodes
    private final Map<String, List<AdvancedGraphDocument.Node>> triggerNodes;
    // Tracked key nodes
    private final Map<String, List<AdvancedGraphDocument.Node>> keyNodes;
    // Tracked mouse nodes
    private final Map<String, List<AdvancedGraphDocument.Node>> mouseNodes;
    // Input nodes by binding
    private final Map<String, List<AdvancedGraphDocument.Node>> inputNodesByBinding;
    // Tracked channel change nodes
    private final Map<String, List<AdvancedGraphDocument.Node>> channelChangeNodes;
    // Tracked redstone change nodes
    private final Map<String, List<AdvancedGraphDocument.Node>> redstoneChangeNodes;
    // Tracked variable change nodes
    private final Map<String, List<AdvancedGraphDocument.Node>> variableChangeNodes;
    // Cached inactive branch
    private final Map<BranchKey, Set<String>> inactiveBranchCache = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph program
    private AdvancedGraphProgram(AdvancedGraphDocument graph,
                                 CompiledGraph<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> idx,
                                 List<AdvancedGraphDocument.Node> tickNodes,
                                 List<AdvancedGraphDocument.Node> periodicNodes,
                                 List<AdvancedGraphDocument.Node> inputNodes,
                                 List<AdvancedGraphDocument.Node> pulseOnChangeNodes,
                                 List<AdvancedGraphDocument.Node> physicalInteractionNodes,
                                 List<AdvancedGraphDocument.Node> hudNodes,
                                 Map<String, List<AdvancedGraphDocument.Node>> triggerNodes,
                                 Map<String, List<AdvancedGraphDocument.Node>> keyNodes,
                                 Map<String, List<AdvancedGraphDocument.Node>> mouseNodes,
                                 Map<String, List<AdvancedGraphDocument.Node>> inputNodesByBinding,
                                 Map<String, List<AdvancedGraphDocument.Node>> channelChangeNodes,
                                 Map<String, List<AdvancedGraphDocument.Node>> redstoneChangeNodes,
                                 Map<String, List<AdvancedGraphDocument.Node>> variableChangeNodes) {
        this.graph = graph;
        this.index = idx;
        this.tickNodes = List.copyOf(tickNodes);
        this.periodicNodes = List.copyOf(periodicNodes);
        this.inputNodes = List.copyOf(inputNodes);
        this.pulseOnChangeNodes = List.copyOf(pulseOnChangeNodes);
        this.physicalInteractionNodes = List.copyOf(physicalInteractionNodes);
        this.hudNodes = List.copyOf(hudNodes);
        this.triggerNodes = freezeListMap(triggerNodes);
        this.keyNodes = freezeListMap(keyNodes);
        this.mouseNodes = freezeListMap(mouseNodes);
        this.inputNodesByBinding = freezeListMap(inputNodesByBinding);
        this.channelChangeNodes = freezeListMap(channelChangeNodes);
        this.redstoneChangeNodes = freezeListMap(redstoneChangeNodes);
        this.variableChangeNodes = freezeListMap(variableChangeNodes);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Compile the advanced graph program
    static AdvancedGraphProgram compile(AdvancedGraphDocument graph) {
        if (graph == null) {
            graph = new AdvancedGraphDocument();
        }
        CompiledGraph<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> idx =
                GraphCompiler.compile(graph);
        List<AdvancedGraphDocument.Node> tickNodes = new ArrayList<>();
        List<AdvancedGraphDocument.Node> periodicNodes = new ArrayList<>();
        List<AdvancedGraphDocument.Node> inputNodes = new ArrayList<>();
        List<AdvancedGraphDocument.Node> pulseOnChangeNodes = new ArrayList<>();
        List<AdvancedGraphDocument.Node> physicalInteractionNodes = new ArrayList<>();
        List<AdvancedGraphDocument.Node> hudNodes = new ArrayList<>();
        Map<String, List<AdvancedGraphDocument.Node>> triggerNodes = new HashMap<>();
        Map<String, List<AdvancedGraphDocument.Node>> keyNodes = new HashMap<>();
        Map<String, List<AdvancedGraphDocument.Node>> mouseNodes = new HashMap<>();
        Map<String, List<AdvancedGraphDocument.Node>> inputNodesByBinding = new HashMap<>();
        Map<String, List<AdvancedGraphDocument.Node>> channelChangeNodes = new HashMap<>();
        Map<String, List<AdvancedGraphDocument.Node>> redstoneChangeNodes = new HashMap<>();
        Map<String, List<AdvancedGraphDocument.Node>> variableChangeNodes = new HashMap<>();

        for (AdvancedGraphDocument.Node node : idx.nodes().values()) {
            if (node == null) {
                continue;
            }
            switch (node.type()) {
                case "event_tick" -> tickNodes.add(node);
                case "event_periodic" -> periodicNodes.add(node);
                case "event_trigger" -> add(triggerNodes, node.data().getString("Event"), node);
                case "event_key" -> add(keyNodes, bindingId(node), node);
                case "mouse_input" -> add(mouseNodes, mouseInput(node), node);
                case "controller_channel_input", "gamepad_input", "local_redstone_input", "wireless_frequency_input",
                     "discovered_target_input", "linker_face_input" -> {
                    inputNodes.add(node);
                    add(inputNodesByBinding, bindingId(node), node);
                }
                case "event_channel_change" -> add(channelChangeNodes, bindingId(node), node);
                case "event_redstone_change" -> add(redstoneChangeNodes, bindingId(node), node);
                case "event_variable_change" -> add(variableChangeNodes, node.data().getString("Variable"), node);
                case "event_physical_interaction" -> physicalInteractionNodes.add(node);
                case "pulse_on_change", "event_value_change" -> pulseOnChangeNodes.add(node);
                case "hud_element", "advanced_hud_element", "acc_display_widget", "acc_hologram_widget",
                        "acc_display_plotter", "acc_display_external", "acc_display_crn" ->
                        hudNodes.add(node);
                default -> {
                }
            }
        }

        AdvancedGraphProgram program = new AdvancedGraphProgram(graph, idx,
                tickNodes, periodicNodes, inputNodes, pulseOnChangeNodes, physicalInteractionNodes,
                hudNodes, triggerNodes, keyNodes, mouseNodes, inputNodesByBinding,
                channelChangeNodes, redstoneChangeNodes, variableChangeNodes);
        return program;
    }

    // Get the graph
    AdvancedGraphDocument graph() {
        return graph;
    }

    // Get the node
    AdvancedGraphDocument.Node node(String id) {
        return index.node(id);
    }

    // Get the inputs
    List<AdvancedGraphDocument.Edge> inputs(String nodeId) {
        return index.incoming(nodeId);
    }

    // Get the outgoing
    List<AdvancedGraphDocument.Edge> outgoing(String nodeId, String port) {
        return index.outgoing(nodeId, port);
    }

    // Update the nodes
    List<AdvancedGraphDocument.Node> tickNodes() {
        return tickNodes;
    }

    // Get the periodic nodes
    List<AdvancedGraphDocument.Node> periodicNodes() {
        return periodicNodes;
    }

    // Get the input nodes
    List<AdvancedGraphDocument.Node> inputNodes() {
        return inputNodes;
    }

    // Get the input nodes
    List<AdvancedGraphDocument.Node> inputNodes(String binding) {
        return inputNodesByBinding.getOrDefault(binding, List.of());
    }

    // Get the input nodes for event
    List<AdvancedGraphDocument.Node> inputNodesForEvent(String eventId, String prefix) {
        return nodesMatchingEvent(inputNodesByBinding, eventId, prefix);
    }

    // Get the pulse on change nodes
    List<AdvancedGraphDocument.Node> pulseOnChangeNodes() {
        return pulseOnChangeNodes;
    }

    // Get the physical interaction nodes
    List<AdvancedGraphDocument.Node> physicalInteractionNodes() {
        return physicalInteractionNodes;
    }

    // Get the HUD nodes
    List<AdvancedGraphDocument.Node> hudNodes() {
        return hudNodes;
    }

    // Trigger the nodes
    List<AdvancedGraphDocument.Node> triggerNodes(String eventId) {
        return triggerNodes.getOrDefault(eventId, List.of());
    }

    // Handle key nodes
    List<AdvancedGraphDocument.Node> keyNodes(String binding) {
        return keyNodes.getOrDefault(binding, List.of());
    }

    // Handle key nodes for event
    List<AdvancedGraphDocument.Node> keyNodesForEvent(String eventId, String prefix) {
        return nodesMatchingEvent(keyNodes, eventId, prefix);
    }

    // Handle mouse nodes for event
    List<AdvancedGraphDocument.Node> mouseNodesForEvent(String eventId, String prefix) {
        return nodesMatchingEvent(mouseNodes, eventId, prefix);
    }

    // Get the channel change nodes
    List<AdvancedGraphDocument.Node> channelChangeNodes(String binding) {
        return channelChangeNodes.getOrDefault(binding, List.of());
    }

    // Get the channel change nodes for event
    List<AdvancedGraphDocument.Node> channelChangeNodesForEvent(String eventId, String prefix) {
        return nodesMatchingEvent(channelChangeNodes, eventId, prefix);
    }

    // Get the redstone change nodes
    List<AdvancedGraphDocument.Node> redstoneChangeNodes(String binding) {
        return redstoneChangeNodes.getOrDefault(binding, List.of());
    }

    // Get the redstone change nodes for event
    List<AdvancedGraphDocument.Node> redstoneChangeNodesForEvent(String eventId, String prefix) {
        return nodesMatchingEvent(redstoneChangeNodes, eventId, prefix);
    }

    // Get the variable change nodes
    List<AdvancedGraphDocument.Node> variableChangeNodes(String variable) {
        return variableChangeNodes.getOrDefault(variable, List.of());
    }

    // Check if this has input
    boolean hasInput(AdvancedGraphDocument.Node node, String port) {
        if (node == null || port == null) {
            return false;
        }
        for (AdvancedGraphDocument.Edge edge : inputs(node.id())) {
            if (port.equals(edge.toPort())) {
                return true;
            }
        }
        return false;
    }

    // Get the inactive branch output bindings
    Set<String> inactiveBranchOutputBindings(String branchId, String selectedPort) {
        if (branchId == null || branchId.isBlank()) {
            return Set.of();
        }
        BranchKey key = new BranchKey(branchId, selectedPort);
        return inactiveBranchCache.computeIfAbsent(key, ignored -> computeInactiveBranchOutputBindings(branchId, selectedPort));
    }

    // Calculate the inactive branch output bindings
    private Set<String> computeInactiveBranchOutputBindings(String branchId, String selectedPort) {
        String inactivePort = "true".equals(selectedPort) ? "false" : "true";
        Set<String> inactive = reachableOutputBindings(branchId, inactivePort);
        inactive.removeAll(reachableOutputBindings(branchId, selectedPort));
        return Set.copyOf(inactive);
    }

    // Get the reachable output bindings
    private Set<String> reachableOutputBindings(String fromNode, String fromPort) {
        Set<String> bindings = new LinkedHashSet<>();
        Queue<CompiledGraph.Port> queue = new ArrayDeque<>();
        Set<CompiledGraph.Port> visited = new HashSet<>();
        queue.add(new CompiledGraph.Port(fromNode, fromPort));
        while (!queue.isEmpty()) {
            CompiledGraph.Port current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            for (AdvancedGraphDocument.Edge edge : outgoing(current.nodeId(), current.port())) {
                AdvancedGraphDocument.Node target = node(edge.toNode());
                if (target == null) {
                    continue;
                }
                if (isOutputNode(target)) {
                    String binding = bindingId(target);
                    if (!binding.isBlank()) {
                        bindings.add(binding);
                    }
                }
                AdvancedGraphCatalog.outputs(target).forEach((port, type) -> {
                    if ("exec".equals(type)) {
                        queue.add(new CompiledGraph.Port(target.id(), port));
                    }
                });
            }
        }
        return bindings;
    }

    // Get the binding id
    static String bindingId(AdvancedGraphDocument.Node node) {
        String binding = node.data().getString("BindingId");
        if (binding.isBlank()) {
            binding = node.data().getString("RouteBindingId");
        }
        return binding.isBlank() ? node.data().getString("Channel") : binding;
    }

    // Handle mouse input
    private static String mouseInput(AdvancedGraphDocument.Node node) {
        String input = AdvancedContraptionControllerBlockEntity.normalizeMouseInput(
                node.data().getString("MouseInput"));
        return input.isBlank() ? "left_click" : input;
    }

    // Check if this is an output node
    private static boolean isOutputNode(AdvancedGraphDocument.Node node) {
        return switch (node.type()) {
            case "controller_channel_output", "local_redstone_output", "wireless_frequency_output",
                 "direct_target_output", "linker_face_output" -> true;
            default -> false;
        };
    }

    // Add the advanced graph program
    private static void add(Map<String, List<AdvancedGraphDocument.Node>> target,
                            String key,
                            AdvancedGraphDocument.Node node) {
        if (target == null || node == null || key == null || key.isBlank()) {
            return;
        }
        target.computeIfAbsent(key, ignored -> new ArrayList<>()).add(node);
    }

    // Get the nodes matching event
    private static List<AdvancedGraphDocument.Node> nodesMatchingEvent(
            Map<String, List<AdvancedGraphDocument.Node>> src,
            String eventId,
            String prefix) {
        if (src == null || src.isEmpty() || eventId == null || prefix == null || !eventId.startsWith(prefix)) {
            return List.of();
        }
        List<AdvancedGraphDocument.Node> matches = new ArrayList<>();
        src.forEach((binding, nodes) -> {
            if (eventMatchesBinding(eventId, prefix, binding)) {
                matches.addAll(nodes);
            }
        });
        return matches.isEmpty() ? List.of() : List.copyOf(matches);
    }

    // Check if the event matches the binding
    private static boolean eventMatchesBinding(String eventId, String prefix, String binding) {
        return binding != null && !binding.isBlank() && eventId.startsWith(prefix + binding)
                && (eventId.length() == prefix.length() + binding.length()
                || eventId.charAt(prefix.length() + binding.length()) == ':');
    }

    // Get the freeze list map
    private static <K, V> Map<K, List<V>> freezeListMap(Map<K, List<V>> src) {
        Map<K, List<V>> frozen = new HashMap<>();
        src.forEach((key, val) -> frozen.put(key, List.copyOf(val)));
        return Map.copyOf(frozen);
    }

    // Store the branch key
    private record BranchKey(String nodeId, String selectedPort) {
    }
}
