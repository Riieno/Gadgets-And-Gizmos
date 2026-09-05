package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.graph.GraphApi;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataValue;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDiagnostic;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDocumentSnapshot;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphEditResult;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphMutation;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphNodeAlias;
import com.rieno.gadgetsandgizmos.lib.graph.edit.VersionedGraphEditor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

// Edit copied ACC drafts through one revision-checked server authority
public final class AdvancedGraphEditingService implements VersionedGraphEditor {
    private static final int MAX_MUTATIONS = 512;
    private static final Set<String> UNSAVEABLE_CODES = Set.of(
            "node_limit", "edge_limit", "size_limit", "serialization");
    private static final Set<String> DERIVED_DATA_KEYS = Set.of(
            "DynamicInputs", "DynamicOutputs", "OutputCount", "InputCount",
            "SwitchType", "DynamicConstructor", "FunctionId",
            "RuntimeFunctionId", "RuntimeSourceNodeId",
            "PreventIntegralWindup");

    private final AdvancedContraptionControllerBlockEntity controller;

    public AdvancedGraphEditingService(
            AdvancedContraptionControllerBlockEntity controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    public GraphDocumentSnapshot snapshot(View view) {
        Objects.requireNonNull(view, "view");
        return snapshotOf(view == View.ACTIVE
                ? controller.getActiveGraph() : controller.getDraftGraph());
    }

    @Override
    public List<GraphNodeDefinition> nodeTypes() {
        return List.copyOf(GraphApi.nodes().definitions());
    }

    @Override
    public GraphEditResult validateDraft() {
        AdvancedGraphDocument draft = controller.getDraftGraph();
        AdvancedGraphValidator.Result validation = controller.validateDraft();
        return new GraphEditResult(false, false, validation.valid(), draft.revision(),
                validation.valid() ? "ok" : "validation_failed",
                validation.valid() ? "Draft is valid" : "Draft validation failed",
                Map.of(), convert(validation.diagnostics()));
    }

    @Override
    public GraphEditResult applyDraft(int expectedRevision) {
        AdvancedGraphDocument draft = controller.getDraftGraph();
        if (draft.revision() != expectedRevision) {
            return failure(draft.revision(), "revision_conflict",
                    "The draft changed; validate the new revision before applying");
        }
        boolean applied = controller.applyDraft();
        AdvancedGraphValidator.Result validation = controller.validateDraft();
        return new GraphEditResult(false, applied, validation.valid(), draft.revision(),
                applied ? "ok" : "validation_failed",
                applied ? "Graph applied" : "Graph validation failed",
                Map.of(), convert(validation.diagnostics()));
    }
    private static GraphDocumentSnapshot snapshotOf(AdvancedGraphDocument graph) {
        List<GraphDocumentSnapshot.Node> nodes = graph.nodes().stream()
                .map(AdvancedGraphEditingService::snapshotNode).toList();
        List<GraphDocumentSnapshot.Edge> edges = graph.edges().stream()
                .map(AdvancedGraphEditingService::snapshotEdge).toList();
        List<GraphDocumentSnapshot.FunctionGraph> functions = graph.functions().stream()
                .map(AdvancedGraphEditingService::snapshotFunction).toList();
        Map<String, GraphValue> variables = new LinkedHashMap<>();
        graph.variables().forEach((name, value) ->
                variables.put(name, GraphRuntime.toLibraryValue(value)));
        return new GraphDocumentSnapshot(AdvancedGraphDocument.CURRENT_VERSION,
                graph.revision(), graph.templateId(), graph.viewportX(),
                graph.viewportY(), graph.viewportZoom(), nodes, edges,
                functions, graph.scmActionFunctions(), variables);
    }

    private static GraphDocumentSnapshot.Node snapshotNode(
            AdvancedGraphDocument.Node node) {
        return new GraphDocumentSnapshot.Node(node.id(), node.type(), node.label(),
                node.data().getString(GraphNodeAlias.DATA_KEY),
                node.x(), node.y(), AdvancedGraphCatalog.inputs(node),
                AdvancedGraphCatalog.outputs(node),
                AdvancedGraphDataCodec.fromCompound(node.data()));
    }

    private static GraphDocumentSnapshot.Edge snapshotEdge(
            AdvancedGraphDocument.Edge edge) {
        return new GraphDocumentSnapshot.Edge(edge.id(), edge.fromNode(),
                edge.fromPort(), edge.toNode(), edge.toPort());
    }

    private static GraphDocumentSnapshot.FunctionGraph snapshotFunction(
            AdvancedGraphDocument.FunctionGraph function) {
        return new GraphDocumentSnapshot.FunctionGraph(function.id(), function.name(),
                function.viewportX(), function.viewportY(), function.viewportZoom(),
                function.nodes().stream()
                        .map(AdvancedGraphEditingService::snapshotNode).toList(),
                function.edges().stream()
                        .map(AdvancedGraphEditingService::snapshotEdge).toList());
    }

    // Create a ready-to-edit SCM action function. Its initial body transparently
    // forwards every public port to the built-in action, avoiding a dead action
    // while still leaving an ordinary function graph for player customization.
    private static AdvancedGraphDocument.FunctionGraph createScmActionFunction(
            String id, String name, String action
    ) {
        AdvancedGraphCatalog.Definition definition = AdvancedGraphCatalog.get(action);
        AdvancedGraphDocument.FunctionGraph function =
                new AdvancedGraphDocument.FunctionGraph(id, name);
        CompoundTag inputData = new CompoundTag();
        CompoundTag inputPorts = new CompoundTag();
        definition.inputs().forEach(inputPorts::putString);
        inputData.put("DynamicOutputs", inputPorts);
        CompoundTag outputData = new CompoundTag();
        CompoundTag outputPorts = new CompoundTag();
        definition.outputs().forEach(outputPorts::putString);
        outputData.put("DynamicInputs", outputPorts);
        AdvancedGraphDocument.Node input = new AdvancedGraphDocument.Node(
                UUID.randomUUID().toString(), AdvancedGraphFunctions.INPUT_TYPE,
                "Inputs", 40.0D, 110.0D, inputData);
        AdvancedGraphDocument.Node builtin = new AdvancedGraphDocument.Node(
                UUID.randomUUID().toString(), action, "Built-in " + humanActionName(action),
                270.0D, 110.0D, new CompoundTag());
        AdvancedGraphDocument.Node output = new AdvancedGraphDocument.Node(
                UUID.randomUUID().toString(), AdvancedGraphFunctions.OUTPUT_TYPE,
                "Outputs", 510.0D, 110.0D, outputData);
        function.nodes().add(input);
        function.nodes().add(builtin);
        function.nodes().add(output);
        definition.inputs().forEach((port, ignored) -> function.edges().add(
                AdvancedGraphFunctions.edge(input.id(), port, builtin.id(), port)));
        definition.outputs().forEach((port, ignored) -> function.edges().add(
                AdvancedGraphFunctions.edge(builtin.id(), port, output.id(), port)));
        return function;
    }

    // Turn a catalog action id into a compact editor label.
    private static String humanActionName(String action) {
        if (action == null || action.isBlank()) {
            return "Action";
        }
        String raw = action.startsWith("ship_") ? action.substring("ship_".length()) : action;
        StringBuilder result = new StringBuilder();
        for (String word : raw.split("_")) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
    @Override
    public GraphEditResult mutateDraft(
            int expectedRevision, List<GraphMutation> mutations) {
        AdvancedGraphDocument working = controller.getDraftGraph();
        if (working.revision() != expectedRevision) {
            return failure(working.revision(), "revision_conflict",
                    "The draft changed; reload it before committing");
        }
        if (mutations == null || mutations.size() > MAX_MUTATIONS) {
            return failure(working.revision(), "mutation_limit",
                    "A transaction may contain at most " + MAX_MUTATIONS + " operations");
        }

        Map<String, String> resolved = new LinkedHashMap<>();
        List<GraphDiagnostic> editDiagnostics = new ArrayList<>();
        for (GraphMutation mutation : mutations) {
            if (mutation == null
                    || !applyMutation(working, mutation, resolved, editDiagnostics)) {
                if (editDiagnostics.isEmpty()) {
                    editDiagnostics.add(error("null_mutation",
                            "A mutation entry cannot be nil", "", ""));
                }
                return new GraphEditResult(false, false, false, working.revision(),
                        "edit_rejected", "Transaction rejected",
                        Map.of(), editDiagnostics);
            }
        }

        AdvancedGraphFunctions.synchronizeCalls(working);
        AdvancedGraphPortNormalizer.normalize(working.nodes(), working.edges());
        working.functions().forEach(function ->
                AdvancedGraphPortNormalizer.normalize(
                        function.nodes(), function.edges()));

        AdvancedGraphValidator.Result validation = AdvancedGraphValidator.validate(
                working, controller.isGogglesTrackerAvailable(),
                controller.isControllerTrackerAvailable());
        List<GraphDiagnostic> converted = convert(validation.diagnostics());
        boolean unsaveable = converted.stream()
                .anyMatch(entry -> UNSAVEABLE_CODES.contains(entry.code()));
        if (unsaveable) {
            return new GraphEditResult(false, false, false, working.revision(),
                    "storage_limit", "Transaction exceeds a hard graph limit",
                    Map.of(), converted);
        }
        if (!controller.saveDraft(working, expectedRevision)) {
            return failure(controller.getDraftGraph().revision(), "revision_conflict",
                    "The draft changed while the transaction was committing");
        }
        return new GraphEditResult(true, false, validation.valid(),
                expectedRevision + 1, "ok", "Draft saved", resolved,
                converted);
    }
    private boolean applyMutation(AdvancedGraphDocument graph,
                                  GraphMutation mutation,
                                  Map<String, String> resolved,
                                  List<GraphDiagnostic> diagnostics) {
        if (mutation instanceof GraphMutation.AddFunction value) {
            String id = allocate(value.temporaryId(), "function", resolved, diagnostics);
            if (id == null) return false;
            graph.functions().add(new AdvancedGraphDocument.FunctionGraph(id, value.name()));
            return true;
        }
        if (mutation instanceof GraphMutation.RenameFunction value) {
            AdvancedGraphDocument.FunctionGraph function = graph.function(
                    resolve(value.functionId(), resolved));
            if (function == null) return missing(diagnostics, "function_not_found",
                    "Function does not exist", value.functionId(), "");
            function.setName(value.name());
            return true;
        }
        if (mutation instanceof GraphMutation.RemoveFunction value) {
            String id = resolve(value.functionId(), resolved);
            boolean removed = AdvancedGraphFunctions.removeFunction(graph, id);
            return removed || missing(diagnostics, "function_not_found",
                    "Function does not exist", id, "");
        }
        if (mutation instanceof GraphMutation.SetScmActionFunction value) {
            String action = value.actionType() == null ? "" : value.actionType().trim();
            String functionId = resolve(value.functionId(), resolved);
            if (!AdvancedGraphCatalog.isScmActionDispatchType(action)) {
                return missing(diagnostics, "invalid_scm_action",
                        "SCM action must name an executable public ship node", action, "");
            }
            if (functionId.isBlank()) {
                graph.setScmActionFunction(action, "");
                return true;
            }
            if (!AdvancedGraphCatalog.isPublicScmActionDispatchType(action)) {
                return missing(diagnostics, "retired_scm_action",
                        "This SCM action has been retired; use Forward or Backward", action, "");
            }
            AdvancedGraphDocument.FunctionGraph function = graph.function(functionId);
            if (function == null) {
                return missing(diagnostics, "function_not_found",
                        "SCM action function does not exist", functionId, "");
            }
            if (!AdvancedGraphFunctions.hasActionSignature(action, function)) {
                return missing(diagnostics, "scm_action_signature",
                        "SCM action functions must mirror the public node inputs and outputs",
                        functionId, "");
            }
            graph.setScmActionFunction(action, function.id());
            return true;
        }
        if (mutation instanceof GraphMutation.CreateScmActionFunction value) {
            String action = value.actionType() == null ? "" : value.actionType().trim();
            if (!AdvancedGraphCatalog.isPublicScmActionDispatchType(action)) {
                return missing(diagnostics, "invalid_scm_action",
                        "SCM action must name an available public ship node", action, "");
            }
            String id = allocate(value.temporaryId(), "function", resolved, diagnostics);
            if (id == null) {
                return false;
            }
            String name = value.name() == null || value.name().isBlank()
                    ? "SCM " + humanActionName(action) : value.name();
            AdvancedGraphDocument.FunctionGraph function =
                    createScmActionFunction(id, name, action);
            graph.functions().add(function);
            graph.setScmActionFunction(action, function.id());
            return true;
        }
        if (mutation instanceof GraphMutation.AddNode value) {
            return addNode(graph, value, resolved, diagnostics);
        }
        if (mutation instanceof GraphMutation.RemoveNode value) {
            Scope scope = scope(graph, value.functionId(), resolved, diagnostics);
            if (scope == null) return false;
            AdvancedGraphDocument.Node target = resolveNode(
                    scope, value.nodeId(), resolved, diagnostics);
            if (target == null) return false;
            String nodeId = target.id();
            scope.nodes().remove(target);
            scope.edges().removeIf(edge -> edge.fromNode().equals(nodeId)
                    || edge.toNode().equals(nodeId));
            return true;
        }
        if (mutation instanceof GraphMutation.MoveNode value) {
            if (!Double.isFinite(value.x()) || !Double.isFinite(value.y())) {
                return missing(diagnostics, "invalid_position",
                        "Node coordinates must be finite", value.nodeId(), "");
            }
            return replaceNode(graph, value.functionId(), value.nodeId(), resolved,
                    diagnostics, node -> new AdvancedGraphDocument.Node(node.id(),
                            node.type(), node.label(), value.x(), value.y(), node.data()));
        }
        if (mutation instanceof GraphMutation.RenameNode value) {
            return replaceNode(graph, value.functionId(), value.nodeId(), resolved,
                    diagnostics, node -> new AdvancedGraphDocument.Node(node.id(),
                            node.type(), value.label(), node.x(), node.y(), node.data()));
        }
        if (mutation instanceof GraphMutation.SetNodeAlias value) {
            return setNodeAlias(graph, value.functionId(), value.nodeId(),
                    value.alias(), resolved, diagnostics);
        }
        if (mutation instanceof GraphMutation.SetNodeData value) {
            return editNodeData(graph, value.functionId(), value.nodeId(), resolved,
                    diagnostics, value.key(), value.value(), false);
        }
        if (mutation instanceof GraphMutation.RemoveNodeData value) {
            return editNodeData(graph, value.functionId(), value.nodeId(), resolved,
                    diagnostics, value.key(), null, true);
        }
        if (mutation instanceof GraphMutation.AddEdge value) {
            return addEdge(graph, value, resolved, diagnostics);
        }
        if (mutation instanceof GraphMutation.RemoveEdge value) {
            Scope scope = scope(graph, value.functionId(), resolved, diagnostics);
            if (scope == null) return false;
            String edgeId = resolve(value.edgeId(), resolved);
            boolean removed = scope.edges().removeIf(edge -> edge.id().equals(edgeId));
            return removed || missing(diagnostics, "edge_not_found",
                    "Edge does not exist", "", edgeId);
        }
        if (mutation instanceof GraphMutation.SetVariable value) {
            if (!AdvancedGraphDocument.isValidVariableName(value.name())
                    || value.value() == null) {
                return missing(diagnostics, "invalid_variable",
                        "Variable name and value are required", "", "");
            }
            graph.variables().put(value.name().strip(),
                    GraphRuntime.fromLibraryValue(value.value()));
            return true;
        }
        if (mutation instanceof GraphMutation.RemoveVariable value) {
            if (!AdvancedGraphDocument.isValidVariableName(value.name())) {
                return missing(diagnostics, "invalid_variable",
                        "A valid variable name is required", "", "");
            }
            graph.variables().remove(value.name().strip());
            return true;
        }
        return missing(diagnostics, "unknown_mutation",
                "Unsupported graph mutation", "", "");
    }
    private boolean addNode(AdvancedGraphDocument graph,
                            GraphMutation.AddNode value,
                            Map<String, String> resolved,
                            List<GraphDiagnostic> diagnostics) {
        if (!Double.isFinite(value.x()) || !Double.isFinite(value.y())) {
            return missing(diagnostics, "invalid_position",
                    "Node coordinates must be finite", value.temporaryId(), "");
        }
        String requestedType = value.type() == null ? "" : value.type().strip();
        if (requestedType.isBlank()) {
            return missing(diagnostics, "unknown_node_type",
                    "A non-blank node type is required", value.temporaryId(), "");
        }
        boolean functionCall = requestedType.startsWith("function:");
        String functionId = functionCall
                ? resolve(requestedType.substring("function:".length()), resolved) : "";
        AdvancedGraphDocument.FunctionGraph calledFunction = functionCall
                ? graph.function(functionId) : null;
        if (functionCall && calledFunction == null) {
            return missing(diagnostics, "function_not_found",
                    "Called function graph does not exist", functionId, "");
        }
        String nodeType = calledFunction == null
                ? requestedType : AdvancedGraphFunctions.CALL_TYPE;
        if (calledFunction == null && !GraphApi.nodes().contains(nodeType)) {
            return missing(diagnostics, "unknown_node_type",
                    "Unknown node type '" + requestedType + "'", value.temporaryId(), "");
        }
        if (graph.totalNodeCount() >= AdvancedGraphDocument.maxNodes()) {
            return missing(diagnostics, "node_limit",
                    "Graph already contains the maximum node count", "", "");
        }
        Scope scope = scope(graph, value.functionId(), resolved, diagnostics);
        if (scope == null) return false;
        String id = allocate(value.temporaryId(), "node", resolved, diagnostics);
        if (id == null) return false;

        CompoundTag data = AdvancedGraphNodeFactory.createDefaultData(
                nodeType, factoryContext(graph));
        CompoundTag supplied = AdvancedGraphDataCodec.toCompound(value.data());
        if (supplied.contains(GraphNodeAlias.DATA_KEY)) {
            return missing(diagnostics, "reserved_data_key",
                    "Use the node alias field to set a node alias",
                    value.temporaryId(), "");
        }
        if (supplied.getAllKeys().stream().anyMatch(DERIVED_DATA_KEYS::contains)) {
            return missing(diagnostics, "derived_data_key",
                    "Derived node port data is not editable through raw NBT",
                    value.temporaryId(), "");
        }
        for (String key : supplied.getAllKeys()) {
            Tag tag = supplied.get(key);
            if (tag != null) data.put(key, tag.copy());
        }
        AdvancedGraphDocument.Node node = new AdvancedGraphDocument.Node(id, nodeType,
                calledFunction == null ? value.label() : calledFunction.name(),
                value.x(), value.y(), data);
        String alias = GraphNodeAlias.normalize(value.alias());
        if (!GraphNodeAlias.isValid(alias)) {
            return missing(diagnostics, "invalid_node_alias",
                    "Node aliases may contain at most " + GraphNodeAlias.MAX_LENGTH
                            + " non-control characters", value.temporaryId(), "");
        }
        if (!alias.isBlank()) {
            if (alias.equals(id) || scope.nodes().stream().anyMatch(candidate -> alias.equals(candidate.id())
                    || alias.equals(GraphNodeAlias.normalize(candidate.data().getString(
                    GraphNodeAlias.DATA_KEY))))) {
                return missing(diagnostics, "duplicate_node_alias",
                        "Node aliases must be unique within their graph", value.temporaryId(), "");
            }
            node.data().putString(GraphNodeAlias.DATA_KEY, alias);
        }
        if (calledFunction != null) {
            AdvancedGraphFunctions.configureCall(node, calledFunction);
        }
        scope.nodes().add(node);
        return true;
    }

    private boolean addEdge(AdvancedGraphDocument graph,
                            GraphMutation.AddEdge value,
                            Map<String, String> resolved,
                            List<GraphDiagnostic> diagnostics) {
        Scope scope = scope(graph, value.functionId(), resolved, diagnostics);
        if (scope == null) return false;
        AdvancedGraphDocument.Node from = resolveNode(
                scope, value.fromNode(), resolved, diagnostics);
        if (from == null) return false;
        AdvancedGraphDocument.Node to = resolveNode(
                scope, value.toNode(), resolved, diagnostics);
        if (to == null) return false;
        String fromId = from.id();
        String toId = to.id();
        String id = allocate(value.temporaryId(), "edge", resolved, diagnostics);
        if (id == null) return false;
        AdvancedGraphPortNormalizer.ConnectResult result =
                AdvancedGraphPortNormalizer.connect(scope.nodes(), scope.edges(), id,
                        fromId, value.fromPort(), toId, value.toPort());
        return result.connected() || missing(diagnostics, result.code(),
                result.message(), toId, id);
    }

    private boolean editNodeData(AdvancedGraphDocument graph, String functionId,
                                 String nodeId, Map<String, String> resolved,
                                 List<GraphDiagnostic> diagnostics, String key,
                                 GraphDataValue value, boolean remove) {
        if (key == null || key.isBlank() || key.length() > 128) {
            return missing(diagnostics, "invalid_data_key",
                    "Node data key must contain 1 to 128 characters", nodeId, "");
        }
        if (DERIVED_DATA_KEYS.contains(key)) {
            return missing(diagnostics, "derived_data_key",
                    "Derived node port data is not editable through raw NBT", nodeId, "");
        }
        if (GraphNodeAlias.DATA_KEY.equals(key)) {
            return missing(diagnostics, "reserved_data_key",
                    "Use the node alias operation to change a node alias", nodeId, "");
        }
        if (!remove && value == null) {
            return missing(diagnostics, "missing_data_value",
                    "A non-null node data value is required", nodeId, "");
        }
        return replaceNode(graph, functionId, nodeId, resolved, diagnostics, node -> {
            CompoundTag data = node.data().copy();
            if (remove) data.remove(key);
            else data.put(key, AdvancedGraphDataCodec.toTag(value));
            return new AdvancedGraphDocument.Node(node.id(), node.type(), node.label(),
                    node.x(), node.y(), data);
        });
    }

    private boolean replaceNode(AdvancedGraphDocument graph, String functionId,
                                String nodeId, Map<String, String> resolved,
                                List<GraphDiagnostic> diagnostics,
                                UnaryOperator<AdvancedGraphDocument.Node> operation) {
        Scope scope = scope(graph, functionId, resolved, diagnostics);
        if (scope == null) return false;
        AdvancedGraphDocument.Node target = resolveNode(
                scope, nodeId, resolved, diagnostics);
        if (target == null) return false;
        for (int index = 0; index < scope.nodes().size(); index++) {
            if (scope.nodes().get(index).id().equals(target.id())) {
                scope.nodes().set(index, operation.apply(target));
                return true;
            }
        }
        return missing(diagnostics, "node_not_found", "Node does not exist", target.id(), "");
    }

    private boolean setNodeAlias(AdvancedGraphDocument graph, String functionId,
                                 String nodeId, String requestedAlias,
                                 Map<String, String> resolved,
                                 List<GraphDiagnostic> diagnostics) {
        Scope scope = scope(graph, functionId, resolved, diagnostics);
        if (scope == null) return false;
        AdvancedGraphDocument.Node target = resolveNode(
                scope, nodeId, resolved, diagnostics);
        if (target == null) return false;
        String alias = GraphNodeAlias.normalize(requestedAlias);
        if (!GraphNodeAlias.isValid(alias)) {
            return missing(diagnostics, "invalid_node_alias",
                    "Node aliases may contain at most " + GraphNodeAlias.MAX_LENGTH
                            + " non-control characters", target.id(), "");
        }
        if (!alias.isBlank() && (scope.nodes().stream()
                .anyMatch(candidate -> alias.equals(candidate.id()))
                || scope.nodes().stream()
                .filter(candidate -> !candidate.id().equals(target.id()))
                .anyMatch(candidate -> alias.equals(GraphNodeAlias.normalize(
                        candidate.data().getString(GraphNodeAlias.DATA_KEY)))))) {
            return missing(diagnostics, "duplicate_node_alias",
                    "Node aliases must be unique within their graph", target.id(), "");
        }
        return replaceNode(graph, functionId, target.id(), resolved, diagnostics, node -> {
            CompoundTag data = node.data().copy();
            if (alias.isBlank()) data.remove(GraphNodeAlias.DATA_KEY);
            else data.putString(GraphNodeAlias.DATA_KEY, alias);
            return new AdvancedGraphDocument.Node(node.id(), node.type(), node.label(),
                    node.x(), node.y(), data);
        });
    }

    private static AdvancedGraphDocument.Node resolveNode(
            Scope scope, String reference, Map<String, String> resolved,
            List<GraphDiagnostic> diagnostics) {
        String checked = resolve(reference, resolved);
        AdvancedGraphDocument.Node exact = findNode(scope.nodes(), checked);
        if (exact != null) return exact;
        AdvancedGraphDocument.Node match = null;
        for (AdvancedGraphDocument.Node node : scope.nodes()) {
            String alias = GraphNodeAlias.normalize(
                    node.data().getString(GraphNodeAlias.DATA_KEY));
            if (!alias.isBlank() && alias.equals(checked)) {
                if (match != null) {
                    diagnostics.add(error("ambiguous_node_alias",
                            "More than one node uses alias '" + checked + "'", "", ""));
                    return null;
                }
                match = node;
            }
        }
        if (match != null) return match;
        diagnostics.add(error("node_not_found",
                "Node ID or alias does not exist", checked, ""));
        return null;
    }

    private static AdvancedGraphDocument.Node findNode(
            List<AdvancedGraphDocument.Node> nodes, String id) {
        for (AdvancedGraphDocument.Node node : nodes) {
            if (node.id().equals(id)) return node;
        }
        return null;
    }

    private static Scope scope(AdvancedGraphDocument graph, String functionId,
                               Map<String, String> resolved,
                               List<GraphDiagnostic> diagnostics) {
        String id = resolve(functionId, resolved);
        if (id.isBlank()) return new Scope(graph.nodes(), graph.edges());
        AdvancedGraphDocument.FunctionGraph function = graph.function(id);
        if (function != null) return new Scope(function.nodes(), function.edges());
        diagnostics.add(error("function_not_found",
                "Function graph does not exist", id, ""));
        return null;
    }

    private static String allocate(String temporaryId, String kind,
                                   Map<String, String> resolved,
                                   List<GraphDiagnostic> diagnostics) {
        if (temporaryId == null || !temporaryId.startsWith("$")
                || temporaryId.length() > 128 || resolved.containsKey(temporaryId)) {
            diagnostics.add(error("invalid_temporary_id",
                    "Each new " + kind + " needs one unique $ temporary id", "", ""));
            return null;
        }
        String permanent = UUID.randomUUID().toString();
        resolved.put(temporaryId, permanent);
        return permanent;
    }

    private static String resolve(String id, Map<String, String> resolved) {
        String normalized = id == null ? "" : id.strip();
        return resolved.getOrDefault(normalized, normalized);
    }

    private record Scope(List<AdvancedGraphDocument.Node> nodes,
                         List<AdvancedGraphDocument.Edge> edges) {
    }
    private AdvancedGraphNodeFactory.Context factoryContext(
            AdvancedGraphDocument graph) {
        String pairId = controller.getGogglesTrackerPairs().stream().findFirst()
                .map(pair -> pair.id().toString()).orElse("");
        String firstVariable = graph.variables().keySet().stream()
                .findFirst().orElse("variable");
        return new AdvancedGraphNodeFactory.Context(pairId,
                nextVariableName(graph), firstVariable);
    }

    private static String nextVariableName(AdvancedGraphDocument graph) {
        Set<String> names = new LinkedHashSet<>(graph.variables().keySet());
        collectVariableSetNames(graph.nodes(), names);
        graph.functions().forEach(function ->
                collectVariableSetNames(function.nodes(), names));
        if (!names.contains("variable")) return "variable";
        int suffix = 2;
        while (names.contains("variable_" + suffix)) suffix++;
        return "variable_" + suffix;
    }

    private static void collectVariableSetNames(
            List<AdvancedGraphDocument.Node> nodes, Set<String> names) {
        for (AdvancedGraphDocument.Node node : nodes) {
            if ("variable_set".equals(node.type())) {
                String name = node.data().getString("Variable");
                if (!name.isBlank()) names.add(name);
            }
        }
    }

    private static List<GraphDiagnostic> convert(
            List<AdvancedGraphValidator.Diagnostic> diagnostics) {
        return diagnostics.stream().map(value -> new GraphDiagnostic(
                value.severity(), value.code(), value.message(),
                value.nodeId(), value.edgeId())).toList();
    }

    private static GraphEditResult failure(int revision, String code, String message) {
        return new GraphEditResult(false, false, false, revision, code, message,
                Map.of(), List.of(error(code, message, "", "")));
    }

    private static boolean missing(List<GraphDiagnostic> diagnostics,
                                   String code, String message,
                                   String nodeId, String edgeId) {
        diagnostics.add(error(code, message, nodeId, edgeId));
        return false;
    }

    private static GraphDiagnostic error(String code, String message,
                                         String nodeId, String edgeId) {
        return new GraphDiagnostic("error", code, message, nodeId, edgeId);
    }
}
