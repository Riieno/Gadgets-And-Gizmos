package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphNodeAlias;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Check graph structure, ports and data types before a graph is allowed to run
public final class AdvancedGraphValidator {
    // Store the diagnostic
    public record Diagnostic(String severity, String code, String message, String nodeId, String edgeId) {
        // Initialize the diagnostic
        public Diagnostic(String severity, String code, String msg, String nodeId) {
            this(severity, code, msg, nodeId, "");
        }

        // Initialize the diagnostic
        public Diagnostic {
            severity = severity == null ? "" : severity;
            code = code == null ? "" : code;
            message = message == null ? "" : message;
            nodeId = nodeId == null ? "" : nodeId;
            edgeId = edgeId == null ? "" : edgeId;
        }
    }

    // Store the operation result
    public record Result(boolean valid, List<Diagnostic> diagnostics) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph validator
    private AdvancedGraphValidator() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Validate the advanced graph validator
    public static Result validate(AdvancedGraphDocument graph) {
        return validate(graph, false, false);
    }

    // Validate the advanced graph validator
    public static Result validate(AdvancedGraphDocument graph, boolean gogglesTrackerAvailable) {
        return validate(graph, gogglesTrackerAvailable, false);
    }

    // Validate the advanced graph validator
    public static Result validate(AdvancedGraphDocument graph, boolean gogglesTrackerAvailable,
                                  boolean controllerTrackerAvailable) {
        // -----------------------------------------------------GRAPH LIMITS-----------------------------------------------------
        List<Diagnostic> diagnostics = new ArrayList<>();
        int maxNodes = AdvancedGraphDocument.maxNodes();
        if (graph.totalNodeCount() > maxNodes) {
            diagnostics.add(error("node_limit", "Graph exceeds the " + maxNodes + " node limit", ""));
        }
        if (graph.totalEdgeCount() > AdvancedGraphDocument.MAX_EDGES) {
            diagnostics.add(error("edge_limit", "Graph exceeds the 512 edge limit", ""));
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(graph.toTag(), new DataOutputStream(bytes));
            if (bytes.size() > AdvancedGraphDocument.MAX_SERIALIZED_BYTES) {
                diagnostics.add(error("size_limit", "Graph exceeds the 512 KiB serialized limit", ""));
            }
        } catch (Exception err) {
            diagnostics.add(error("serialization", "Graph cannot be serialized: " + err.getMessage(), ""));
        }

        // ------------------------------------NODE VALIDATION------------------------------------
        Map<String, AdvancedGraphDocument.Node> nodes = new HashMap<>();
        Set<String> nodeIds = graph.nodes().stream()
                .map(AdvancedGraphDocument.Node::id).collect(Collectors.toSet());
        Set<String> nodeAliases = new HashSet<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node.id().isBlank() || nodes.putIfAbsent(node.id(), node) != null) {
                diagnostics.add(error("duplicate_node", "Node IDs must be non-empty and unique", node.id()));
            }
            if (AdvancedGraphCatalog.get(node.type()) == null) {
                diagnostics.add(error("missing_type", "Unknown node type: " + node.type(), node.id()));
            }
            String alias = GraphNodeAlias.normalize(
                    node.data().getString(GraphNodeAlias.DATA_KEY));
            if (!GraphNodeAlias.isValid(alias)) {
                diagnostics.add(error("invalid_node_alias",
                        "Node aliases may contain at most " + GraphNodeAlias.MAX_LENGTH
                                + " non-control characters", node.id()));
            } else if (!alias.isBlank()
                    && (nodeIds.contains(alias) || !nodeAliases.add(alias))) {
                diagnostics.add(error("duplicate_node_alias",
                        "Node aliases must be unique and cannot match a node ID", node.id()));
            }
            if (AdvancedGraphFunctions.CALL_TYPE.equals(node.type())
                    && graph.function(node.data().getString(AdvancedGraphFunctions.FUNCTION_ID)) == null) {
                diagnostics.add(error("missing_function", "Function call references a missing function", node.id()));
            }
            if ("portable_tracker".equals(node.type()) && !gogglesTrackerAvailable) {
                diagnostics.add(error("goggles_tracker_unavailable",
                        "The Goggles Tracker requires linked goggles", node.id()));
            }
            if ("controller_tracker".equals(node.type()) && !controllerTrackerAvailable) {
                diagnostics.add(error("controller_tracker_unavailable",
                        "The Controller Tracker is only available to portable controllers", node.id()));
            }
            if (("controller_channel_input".equals(node.type()) || "gamepad_input".equals(node.type())
                    || "controller_channel_output".equals(node.type())
                    || "event_channel_change".equals(node.type())
                    || "event_redstone_change".equals(node.type()))
                    && node.data().getString("BindingId").isBlank()) {
                diagnostics.add(error("missing_binding", "Configured key and channel event nodes require a configured binding", node.id()));
            }
            if ((node.type().contains("target") || node.type().startsWith("linker_face")
                    || "get_block_data".equals(node.type()) || "set_block_data".equals(node.type()))
                    && !hasConfiguredTarget(node)) {
                diagnostics.add(error("missing_target", "Target nodes require a stored or linker target", node.id()));
            }
            if (("event_named_controller".equals(node.type())
                    || "send_named_controller_event".equals(node.type()))
                    && node.data().getString("Event").isBlank()) {
                diagnostics.add(error("missing_event_name",
                        "Named controller event nodes require an event name", node.id()));
            }
        }
        // ------------------------------------EDGE VALIDATION------------------------------------
        Set<String> edgeIds = new HashSet<>();
        Map<String, List<NodeDependency>> dependencies = new HashMap<>();
        for (AdvancedGraphDocument.Edge edge : graph.edges()) {
            if (edge.id().isBlank() || !edgeIds.add(edge.id())) {
                diagnostics.add(error("duplicate_edge", "Edge IDs must be non-empty and unique", "", edge.id()));
            }
            AdvancedGraphDocument.Node from = nodes.get(edge.fromNode());
            AdvancedGraphDocument.Node to = nodes.get(edge.toNode());
            if (from == null || to == null) {
                diagnostics.add(error("missing_endpoint", "Edge references a missing node",
                        from != null ? from.id() : to != null ? to.id() : "", edge.id()));
                continue;
            }
            AdvancedGraphCatalog.Definition toDef = AdvancedGraphCatalog.get(to.type());
            String fromType = AdvancedGraphCatalog.outputs(from).get(edge.fromPort());
            String toType = AdvancedGraphCatalog.inputs(to).get(edge.toPort());
            if (fromType == null || toType == null) {
                diagnostics.add(error("missing_port", "Edge references a missing port", to.id(), edge.id()));
                continue;
            }
            if (!AdvancedGraphCatalog.compatible(fromType, to, edge.toPort())) {
                diagnostics.add(error("type_mismatch", "Cannot connect " + fromType + " to " + toType,
                        to.id(), edge.id()));
            }
            if (!"exec".equals(fromType) && toDef != null && !toDef.stateful()) {
                dependencies.computeIfAbsent(from.id(), ignored -> new ArrayList<>())
                        .add(new NodeDependency(to.id(), edge.id()));
            }
        }
        // ------------------------------------DEPENDENCIES / VARIABLES------------------------------------
        detectCycles(nodes.keySet(), dependencies, diagnostics);
        for (String variable : graph.variables().keySet()) {
            if (!AdvancedGraphDocument.isValidVariableName(variable)) {
                List<AdvancedGraphDocument.Node> variableNodes = graph.nodes().stream()
                        .filter(node -> variable.equals(node.data().getString("Variable")))
                        .toList();
                if (variableNodes.isEmpty()) {
                    diagnostics.add(error("invalid_variable", "Invalid variable name: " + variable, ""));
                } else {
                    for (AdvancedGraphDocument.Node node : variableNodes) {
                        diagnostics.add(error("invalid_variable",
                                "Invalid variable name: " + variable, node.id()));
                    }
                }
            }
        }
        // -----------------------------------------------------FUNCTIONS-----------------------------------------------------
        Set<String> functionIds = new HashSet<>();
        for (AdvancedGraphDocument.FunctionGraph function : graph.functions()) {
            if (function.id().isBlank() || !functionIds.add(function.id())) {
                diagnostics.add(error("duplicate_function",
                        "Function IDs must be non-empty and unique", ""));
                continue;
            }
            AdvancedGraphDocument local = new AdvancedGraphDocument();
            local.nodes().addAll(function.nodes());
            local.edges().addAll(function.edges());
            Result localResult = validate(local, gogglesTrackerAvailable, controllerTrackerAvailable);
            for (Diagnostic diagnostic : localResult.diagnostics()) {
                if ("size_limit".equals(diagnostic.code()) || "missing_function".equals(diagnostic.code())) {
                    continue;
                }
                diagnostics.add(new Diagnostic(diagnostic.severity(), diagnostic.code(),
                        "Function " + function.name() + ": " + diagnostic.message(),
                        diagnostic.nodeId(), diagnostic.edgeId()));
            }
            for (AdvancedGraphDocument.Node node : function.nodes()) {
                if (AdvancedGraphFunctions.CALL_TYPE.equals(node.type())
                        && graph.function(node.data().getString(AdvancedGraphFunctions.FUNCTION_ID)) == null) {
                    diagnostics.add(error("missing_function",
                            "Function " + function.name() + ": function call references a missing function",
                            node.id()));
                }
            }
        }
        return new Result(diagnostics.stream().noneMatch(d -> "error".equals(d.severity())), List.copyOf(diagnostics));
    }

    // Check if this has configured target
    private static boolean hasConfiguredTarget(AdvancedGraphDocument.Node node) {
        if (!node.data().getString("Target").isBlank()) {
            return true;
        }
        ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        return target != null && target.isValid();
    }

    // Detect the cycles
    private static void detectCycles(Set<String> nodes, Map<String, List<NodeDependency>> dependencies,
                                     List<Diagnostic> diagnostics) {
        Map<String, Integer> states = new HashMap<>();
        List<String> pathNodes = new ArrayList<>();
        List<String> pathEdges = new ArrayList<>();
        for (String node : nodes) {
            if (visit(node, "", dependencies, states, pathNodes, pathEdges, diagnostics)) {
                return;
            }
        }
    }

    // Visit the advanced graph validator
    private static boolean visit(String node, String incomingEdge,
                                 Map<String, List<NodeDependency>> dependencies,
                                 Map<String, Integer> states, List<String> pathNodes, List<String> pathEdges,
                                 List<Diagnostic> diagnostics) {
        if (states.getOrDefault(node, 0) == 2) return false;
        states.put(node, 1);
        pathNodes.add(node);
        pathEdges.add(incomingEdge);
        for (NodeDependency dependency : dependencies.getOrDefault(node, List.of())) {
            int state = states.getOrDefault(dependency.nodeId(), 0);
            if (state == 1) {
                int cycleStart = pathNodes.indexOf(dependency.nodeId());
                for (int i = cycleStart; i < pathNodes.size(); i++) {
                    String edgeId = i + 1 < pathNodes.size()
                            ? pathEdges.get(i + 1) : dependency.edgeId();
                    diagnostics.add(error("data_cycle",
                            "Illegal data cycle detected; use an explicit state or delay node",
                            pathNodes.get(i), edgeId));
                }
                return true;
            }
            if (state == 0) {
                if (visit(dependency.nodeId(), dependency.edgeId(), dependencies,
                        states, pathNodes, pathEdges, diagnostics)) {
                    return true;
                }
            }
        }
        pathNodes.removeLast();
        pathEdges.removeLast();
        states.put(node, 2);
        return false;
    }

    // Get the error
    private static Diagnostic error(String code, String msg, String nodeId) {
        return new Diagnostic("error", code, msg, nodeId == null ? "" : nodeId);
    }

    // Get the error
    private static Diagnostic error(String code, String msg, String nodeId, String edgeId) {
        return new Diagnostic("error", code, msg,
                nodeId == null ? "" : nodeId, edgeId == null ? "" : edgeId);
    }

    // Store the node dependency
    private record NodeDependency(String nodeId, String edgeId) {
    }
}
