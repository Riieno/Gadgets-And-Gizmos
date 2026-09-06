package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Keep derived reroute and compare port state identical for every graph editor
public final class AdvancedGraphPortNormalizer {
    private static final List<String> COMPARE_OPERATOR_OPTIONS =
            List.of("==", ">", "<", ">=", "<=", "!=");

    private AdvancedGraphPortNormalizer() {
    }

    public static ConnectResult connect(
            List<AdvancedGraphDocument.Node> nodes,
            List<AdvancedGraphDocument.Edge> edges,
            String edgeId, String fromNode, String fromPort,
            String toNode, String toPort) {
        AdvancedGraphDocument.Node from = find(nodes, fromNode);
        AdvancedGraphDocument.Node to = find(nodes, toNode);
        if (from == null || to == null) {
            return ConnectResult.failure("wire_node_missing",
                    "Both wire nodes must exist in the same graph scope");
        }
        if (AdvancedGraphFunctions.isInvalidInterfaceEdge(from, to)) {
            return ConnectResult.failure("invalid_function_interface_wire",
                    "Function inputs must feed the function body, and function outputs must be fed by it");
        }
        String fromType = AdvancedGraphCatalog.outputs(from).get(fromPort);
        if (fromType == null) {
            return ConnectResult.failure("incompatible_wire",
                    "The selected source port does not exist");
        }
        String inferredTo = inferredRerouteType(to, toPort, fromType);
        String toType = inferredTo == null
                ? AdvancedGraphCatalog.inputs(to).get(toPort) : inferredTo;
        if (toType == null) {
            return ConnectResult.failure("incompatible_wire",
                    "The selected target port does not exist");
        }
        String inferredFrom = inferredRerouteType(from, fromPort, toType);
        String effectiveFrom = inferredFrom == null ? fromType : inferredFrom;
        boolean compatible = inferredTo != null
                ? AdvancedGraphCatalog.compatible(effectiveFrom, toType)
                : AdvancedGraphCatalog.compatible(effectiveFrom, to, toPort);
        if (!compatible) {
            return ConnectResult.failure("incompatible_wire",
                    "The selected ports do not exist or are incompatible");
        }
        if (inferredTo != null) configureRerouteType(to, inferredTo);
        if (inferredFrom != null) configureRerouteType(from, inferredFrom);
        edges.removeIf(edge -> edge.toNode().equals(toNode)
                && edge.toPort().equals(toPort));
        if (edges.size() >= AdvancedGraphDocument.MAX_EDGES) {
            return ConnectResult.failure("edge_limit",
                    "This graph scope already contains the maximum edge count");
        }
        edges.add(new AdvancedGraphDocument.Edge(edgeId, fromNode, fromPort,
                toNode, toPort));
        normalize(nodes, edges);
        return ConnectResult.success();
    }

    public static void normalize(List<AdvancedGraphDocument.Node> nodes,
                                 List<AdvancedGraphDocument.Edge> edges) {
        synchronizeReroutes(nodes, edges);
        edges.removeIf(edge -> !validEdge(nodes, edge));
        synchronizeReroutes(nodes, edges);
        synchronizeComparePorts(nodes, edges);
    }

    private static boolean validEdge(List<AdvancedGraphDocument.Node> nodes,
                                     AdvancedGraphDocument.Edge edge) {
        AdvancedGraphDocument.Node from = find(nodes, edge.fromNode());
        AdvancedGraphDocument.Node to = find(nodes, edge.toNode());
        if (from == null || to == null) return false;
        if (AdvancedGraphFunctions.isInvalidInterfaceEdge(from, to)) return false;
        String fromType = AdvancedGraphCatalog.outputs(from).get(edge.fromPort());
        return fromType != null
                && AdvancedGraphCatalog.inputs(to).containsKey(edge.toPort())
                && AdvancedGraphCatalog.compatible(fromType, to, edge.toPort());
    }

    private static void synchronizeComparePorts(
            List<AdvancedGraphDocument.Node> nodes,
            List<AdvancedGraphDocument.Edge> edges) {
        for (AdvancedGraphDocument.Node node : nodes) {
            if (!"compare".equals(node.type())) continue;
            String matched = null;
            for (AdvancedGraphDocument.Edge edge : edges) {
                if (!edge.toNode().equals(node.id())
                        || (!"a".equals(edge.toPort())
                        && !"b".equals(edge.toPort()))) continue;
                AdvancedGraphDocument.Node source = find(nodes, edge.fromNode());
                String type = source == null ? null
                        : AdvancedGraphCatalog.outputs(source).get(edge.fromPort());
                if (type == null || "any".equals(type)) continue;
                matched = matched == null || matched.equals(type) ? type : "any";
            }
            CompoundTag inputs = node.data().getCompound("DynamicInputs");
            if (matched == null || "any".equals(matched)) {
                inputs.remove("a");
                inputs.remove("b");
            } else {
                inputs.putString("a", matched);
                inputs.putString("b", matched);
            }
            if (inputs.isEmpty()) node.data().remove("DynamicInputs");
            else node.data().put("DynamicInputs", inputs);
            configureCompareOperator(node);
        }
    }

    private static void configureCompareOperator(AdvancedGraphDocument.Node node) {
        CompoundTag defaults = node.data().getCompound("Defaults");
        CompoundTag entry = defaults.getCompound("operator");
        String raw = entry.getCompound("Payload").getString("Value");
        if (raw.isBlank()) raw = node.data().getString("Operator");
        CompoundTag payload = new CompoundTag();
        payload.putString("Value", normalizeCompareOperator(raw));
        entry.putString("Type", "string");
        entry.put("Payload", payload);
        defaults.put("operator", entry);
        node.data().put("Defaults", defaults);

        CompoundTag options = node.data().getCompound("InputOptions");
        ListTag values = new ListTag();
        COMPARE_OPERATOR_OPTIONS.forEach(value ->
                values.add(StringTag.valueOf(value)));
        options.put("operator", values);
        node.data().put("InputOptions", options);
    }

    private static String normalizeCompareOperator(String operator) {
        String normalized = operator == null ? ""
                : operator.strip().toLowerCase(Locale.ROOT);
        if (!List.of("==", "===", "!=", "!==", "~=", "<", "<=", ">", ">=")
                .contains(normalized)) {
            normalized = normalized.replace('-', ' ').replace('_', ' ')
                    .replaceAll("\\s+", " ");
        }
        return switch (normalized) {
            case "equal", "equals", "equal to", "eq", "is", "==", "===" -> "==";
            case "greater", "gt", "greater than", ">" -> ">";
            case "less", "lt", "less than", "<" -> "<";
            case "greater equal", "greater or equal", "greater than or equal",
                 "greater than or equal to", "greater than or equals", "gte", ">=" -> ">=";
            case "less equal", "less or equal", "less than or equal",
                 "less than or equal to", "less than or equals", "lte", "<=" -> "<=";
            case "not equal", "not equals", "not equal to", "ne", "is not",
                 "!=", "!==", "~=" -> "!=";
            default -> "==";
        };
    }

    private static void synchronizeReroutes(
            List<AdvancedGraphDocument.Node> nodes,
            List<AdvancedGraphDocument.Edge> edges) {
        Set<String> visited = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node root : nodes) {
            if (!isReroute(root) || !visited.add(root.id())) continue;
            List<AdvancedGraphDocument.Node> component = new ArrayList<>();
            ArrayDeque<AdvancedGraphDocument.Node> pending = new ArrayDeque<>();
            pending.add(root);
            while (!pending.isEmpty()) {
                AdvancedGraphDocument.Node reroute = pending.removeFirst();
                component.add(reroute);
                for (AdvancedGraphDocument.Edge edge : edges) {
                    String adjacentId = edge.fromNode().equals(reroute.id())
                            ? edge.toNode() : edge.toNode().equals(reroute.id())
                            ? edge.fromNode() : null;
                    AdvancedGraphDocument.Node adjacent = find(nodes, adjacentId);
                    if (isReroute(adjacent) && visited.add(adjacent.id())) {
                        pending.add(adjacent);
                    }
                }
            }
            Set<String> ids = new LinkedHashSet<>();
            component.forEach(node -> ids.add(node.id()));
            String type = connectedType(nodes, edges, ids, true);
            if (type == null) type = connectedType(nodes, edges, ids, false);
            for (AdvancedGraphDocument.Node reroute : component) {
                if (type == null) {
                    reroute.data().remove("DynamicInputs");
                    reroute.data().remove("DynamicOutputs");
                } else {
                    configureRerouteType(reroute, type);
                }
            }
        }
    }

    private static String connectedType(
            List<AdvancedGraphDocument.Node> nodes,
            List<AdvancedGraphDocument.Edge> edges,
            Set<String> component, boolean incoming) {
        for (AdvancedGraphDocument.Edge edge : edges) {
            boolean match = incoming
                    ? component.contains(edge.toNode())
                    && !component.contains(edge.fromNode())
                    : component.contains(edge.fromNode())
                    && !component.contains(edge.toNode());
            if (!match) continue;
            AdvancedGraphDocument.Node node = find(nodes,
                    incoming ? edge.fromNode() : edge.toNode());
            String type = node == null ? null : incoming
                    ? AdvancedGraphCatalog.outputs(node).get(edge.fromPort())
                    : AdvancedGraphCatalog.inputs(node).get(edge.toPort());
            if (type != null && !"any".equals(type)) return type;
        }
        return null;
    }

    private static String inferredRerouteType(
            AdvancedGraphDocument.Node node, String port, String connectedType) {
        if (!isReroute(node) || !"value".equals(port)
                || connectedType == null || "any".equals(connectedType)) return null;
        String current = AdvancedGraphCatalog.inputs(node)
                .getOrDefault("value", "any");
        return "any".equals(current) ? connectedType : null;
    }

    private static void configureRerouteType(
            AdvancedGraphDocument.Node node, String type) {
        if (!isReroute(node) || type == null || type.isBlank()) return;
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        inputs.putString("value", type);
        node.data().put("DynamicInputs", inputs);
        CompoundTag outputs = node.data().getCompound("DynamicOutputs");
        outputs.putString("value", type);
        node.data().put("DynamicOutputs", outputs);
    }

    private static boolean isReroute(AdvancedGraphDocument.Node node) {
        return node != null && "reroute".equals(node.type());
    }

    private static AdvancedGraphDocument.Node find(
            List<AdvancedGraphDocument.Node> nodes, String id) {
        if (id == null) return null;
        for (AdvancedGraphDocument.Node node : nodes) {
            if (node.id().equals(id)) return node;
        }
        return null;
    }

    public record ConnectResult(boolean connected, String code, String message) {
        private static ConnectResult success() {
            return new ConnectResult(true, "ok", "Wire connected");
        }

        private static ConnectResult failure(String code, String message) {
            return new ConnectResult(false, code, message);
        }
    }
}
