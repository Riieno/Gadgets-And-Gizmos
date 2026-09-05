package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Resolve nested graph functions without letting recursion or missing documents break execution
public final class AdvancedGraphFunctions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String CALL_TYPE = "function_call";
    public static final String INPUT_TYPE = "function_input";
    public static final String OUTPUT_TYPE = "function_output";
    public static final String FUNCTION_ID = "FunctionId";
    public static final String RUNTIME_FUNCTION_ID = "RuntimeFunctionId";
    public static final String RUNTIME_SOURCE_NODE_ID = "RuntimeSourceNodeId";
    public static final String SCM_DISPATCH_ACTION = "ScmDispatchAction";
    private static final String EXPANSION_STACK = "FunctionExpansionStack";
    private static final String ARGUMENT_PREFIX = "__function_arg__";
    private static final String ENTRY_PREFIX = "__function_entry__";
    private static final String RETURN_PREFIX = "__function_return__";
    private static final int MAX_EXPANSION_DEPTH = 16;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph functions
    private AdvancedGraphFunctions() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the inputs
    public static Map<String, String> inputs(AdvancedGraphDocument.FunctionGraph function) {
        Map<String, String> res = new LinkedHashMap<>();
        if (function == null) {
            return res;
        }
        for (AdvancedGraphDocument.Node node : function.nodes()) {
            if (!INPUT_TYPE.equals(node.type())) {
                continue;
            }
            visiblePorts(AdvancedGraphCatalog.outputs(node)).forEach(res::putIfAbsent);
        }
        return res;
    }

    // Get the outputs
    public static Map<String, String> outputs(AdvancedGraphDocument.FunctionGraph function) {
        Map<String, String> res = new LinkedHashMap<>();
        if (function == null) {
            return res;
        }
        for (AdvancedGraphDocument.Node node : function.nodes()) {
            if (!OUTPUT_TYPE.equals(node.type())) {
                continue;
            }
            visiblePorts(AdvancedGraphCatalog.inputs(node)).forEach(res::putIfAbsent);
        }
        return res;
    }

    // Configure the call
    public static void configureCall(AdvancedGraphDocument.Node call,
                                     AdvancedGraphDocument.FunctionGraph function) {
        if (call == null || !CALL_TYPE.equals(call.type()) || function == null) {
            return;
        }
        call.data().putString(FUNCTION_ID, function.id());
        putPorts(call.data(), "DynamicInputs", inputs(function));
        putPorts(call.data(), "DynamicOutputs", outputs(function));
        call.data().putString("FunctionName", function.name());
    }

    // Sync the calls
    public static void synchronizeCalls(AdvancedGraphDocument graph) {
        if (graph == null) {
            return;
        }
        synchronizeCalls(graph, graph.nodes(), graph.edges());
        for (AdvancedGraphDocument.FunctionGraph function : graph.functions()) {
            synchronizeCalls(graph, function.nodes(), function.edges());
        }
    }

    // Remove one function and every call node which targets it
    public static boolean removeFunction(
            AdvancedGraphDocument graph, String functionId) {
        if (graph == null || functionId == null || functionId.isBlank()
                || graph.function(functionId) == null) {
            return false;
        }
        removeFunctionCalls(graph.nodes(), graph.edges(), functionId);
        for (AdvancedGraphDocument.FunctionGraph function : graph.functions()) {
            if (!function.id().equals(functionId)) {
                removeFunctionCalls(function.nodes(), function.edges(), functionId);
            }
        }
        boolean removed = graph.functions().removeIf(
                function -> function.id().equals(functionId));
        if (removed) {
            graph.scmActionFunctions().entrySet().removeIf(entry -> functionId.equals(entry.getValue()));
            synchronizeCalls(graph);
        }
        return removed;
    }

    // Remove call nodes and their attached edges for one deleted function
    private static void removeFunctionCalls(
            List<AdvancedGraphDocument.Node> nodes,
            List<AdvancedGraphDocument.Edge> edges,
            String functionId) {
        Set<String> removed = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node node : nodes) {
            if (CALL_TYPE.equals(node.type())
                    && functionId.equals(node.data().getString(FUNCTION_ID))) {
                removed.add(node.id());
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        nodes.removeIf(node -> removed.contains(node.id()));
        edges.removeIf(edge -> removed.contains(edge.fromNode())
                || removed.contains(edge.toNode()));
    }

    // Sync the calls
    private static void synchronizeCalls(AdvancedGraphDocument graph,
                                         List<AdvancedGraphDocument.Node> nodes,
                                         List<AdvancedGraphDocument.Edge> edges) {
        for (AdvancedGraphDocument.Node node : nodes) {
            if (!CALL_TYPE.equals(node.type())) {
                continue;
            }
            AdvancedGraphDocument.FunctionGraph function = graph.function(node.data().getString(FUNCTION_ID));
            if (function == null) {
                continue;
            }
            configureCall(node, function);
            Map<String, String> inputs = AdvancedGraphCatalog.inputs(node);
            Map<String, String> outputs = AdvancedGraphCatalog.outputs(node);
            edges.removeIf(edge -> node.id().equals(edge.toNode()) && !inputs.containsKey(edge.toPort())
                    || node.id().equals(edge.fromNode()) && !outputs.containsKey(edge.fromPort()));
        }
    }

    // Get the expand for runtime
    public static AdvancedGraphDocument expandForRuntime(AdvancedGraphDocument src) {
        AdvancedGraphDocument expanded = new AdvancedGraphDocument();
        if (src == null) {
            return expanded;
        }
        expanded.setRevision(src.revision());
        expanded.setTemplateId(src.templateId());
        expanded.setViewport(src.viewportX(), src.viewportY(), src.viewportZoom());
        src.variables().forEach((key, val) -> expanded.variables().put(key, val));
        src.nodes().forEach(node -> expanded.nodes().add(copyNode(node)));
        src.edges().forEach(edge -> expanded.edges().add(copyEdge(edge)));
        promoteScmActionCalls(src, expanded);

        for (int depth = 0; depth < MAX_EXPANSION_DEPTH; depth++) {
            List<AdvancedGraphDocument.Node> calls = expanded.nodes().stream()
                    .filter(node -> CALL_TYPE.equals(node.type())
                            && !node.data().getBoolean("FunctionExpanded"))
                    .toList();
            if (calls.isEmpty()) {
                break;
            }
            boolean expandedAny = false;
            for (AdvancedGraphDocument.Node call : calls) {
                AdvancedGraphDocument.FunctionGraph function =
                        src.function(call.data().getString(FUNCTION_ID));
                if (function == null || expansionStack(call).contains(function.id())) {
                    call.data().putBoolean("FunctionExpanded", true);
                    continue;
                }
                expandCall(expanded, call, function);
                expandedAny = true;
            }
            if (!expandedAny) {
                break;
            }
        }
        return expanded;
    }

    // Promote configured public SCM actions in the main graph to normal function
    // call-sites. Function bodies intentionally keep their explicit SCM nodes: that
    // gives the author a non-recursive way to compose the underlying built-in action.
    private static void promoteScmActionCalls(AdvancedGraphDocument src,
                                              AdvancedGraphDocument expanded) {
        for (int index = 0; index < expanded.nodes().size(); index++) {
            AdvancedGraphDocument.Node node = expanded.nodes().get(index);
            if (!AdvancedGraphCatalog.isScmActionDispatchType(node.type())) {
                continue;
            }
            String functionId = src.scmActionFunction(node.type());
            AdvancedGraphDocument.FunctionGraph function = src.function(functionId);
            if (function == null || !hasActionSignature(node.type(), function)) {
                continue;
            }
            CompoundTag data = node.data().copy();
            data.putString(SCM_DISPATCH_ACTION, node.type());
            AdvancedGraphDocument.Node call = new AdvancedGraphDocument.Node(
                    node.id(), CALL_TYPE, node.label(), node.x(), node.y(), data);
            configureCall(call, function);
            expanded.nodes().set(index, call);
        }
    }

    // SCM action functions deliberately mirror their public node signature. This
    // lets an action binding preserve all existing wires and execution semantics.
    public static boolean hasActionSignature(String actionType,
                                             AdvancedGraphDocument.FunctionGraph function) {
        if (!AdvancedGraphCatalog.isScmActionDispatchType(actionType) || function == null) {
            return false;
        }
        AdvancedGraphCatalog.Definition definition = AdvancedGraphCatalog.get(actionType);
        return definition != null
                && definition.inputs().equals(inputs(function))
                && definition.outputs().equals(outputs(function));
    }

    // Expand the call
    private static void expandCall(AdvancedGraphDocument expanded,
                                   AdvancedGraphDocument.Node call,
                                   AdvancedGraphDocument.FunctionGraph function) {
        // ------------------------------------EXPANSION STATE------------------------------------
        configureCall(call, function);
        call.data().putBoolean("FunctionExpanded", true);
        Set<String> stack = expansionStack(call);
        stack.add(function.id());
        String serializedStack = String.join("\n", stack);

        // -----------------------------------------------------CLONE NODES-----------------------------------------------------
        Map<String, String> cloneIds = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : function.nodes()) {
            if (INPUT_TYPE.equals(node.type()) || OUTPUT_TYPE.equals(node.type())) {
                continue;
            }
            String cloneId = call.id() + "/" + node.id();
            cloneIds.put(node.id(), cloneId);
            CompoundTag data = node.data().copy();
            data.putString(EXPANSION_STACK, serializedStack);
            data.putString(RUNTIME_FUNCTION_ID, function.id());
            data.putString(RUNTIME_SOURCE_NODE_ID, node.id());
            data.remove("FunctionExpanded");
            AdvancedGraphDocument.Node clone = new AdvancedGraphDocument.Node(
                    cloneId, node.type(), node.label(), node.x(), node.y(), data);
            if (CALL_TYPE.equals(clone.type())) {
                AdvancedGraphDocument.FunctionGraph nested =
                        expanded.function(clone.data().getString(FUNCTION_ID));
                if (nested != null) {
                    configureCall(clone, nested);
                }
            }
            expanded.nodes().add(clone);
        }

        // ------------------------------------FUNCTION PORTS------------------------------------
        Map<String, String> inputTypes = inputs(function);
        Map<String, String> outputTypes = outputs(function);
        CompoundTag hiddenInputs = call.data().getCompound("DynamicInputs");
        CompoundTag hiddenOutputs = call.data().getCompound("DynamicOutputs");
        inputTypes.forEach((port, type) -> hiddenOutputs.putString(argumentPort(port, type), type));
        outputTypes.forEach((port, type) -> hiddenInputs.putString(returnPort(port), type));
        call.data().put("DynamicInputs", hiddenInputs);
        call.data().put("DynamicOutputs", hiddenOutputs);

        // -----------------------------------------------------CLONE EDGES-----------------------------------------------------
        Map<String, AdvancedGraphDocument.Node> functionNodes = new LinkedHashMap<>();
        function.nodes().forEach(node -> functionNodes.put(node.id(), node));
        for (AdvancedGraphDocument.Edge edge : function.edges()) {
            AdvancedGraphDocument.Node from = functionNodes.get(edge.fromNode());
            AdvancedGraphDocument.Node to = functionNodes.get(edge.toNode());
            if (from == null || to == null) {
                continue;
            }
            String fromNode;
            String fromPort;
            if (INPUT_TYPE.equals(from.type())) {
                String type = inputTypes.get(edge.fromPort());
                if (type == null) {
                    continue;
                }
                fromNode = call.id();
                fromPort = argumentPort(edge.fromPort(), type);
            } else {
                fromNode = cloneIds.get(edge.fromNode());
                fromPort = edge.fromPort();
            }

            String toNode;
            String toPort;
            if (OUTPUT_TYPE.equals(to.type())) {
                if (!outputTypes.containsKey(edge.toPort())) {
                    continue;
                }
                toNode = call.id();
                toPort = returnPort(edge.toPort());
            } else {
                toNode = cloneIds.get(edge.toNode());
                toPort = edge.toPort();
            }
            if (fromNode == null || toNode == null) {
                continue;
            }
            expanded.edges().add(new AdvancedGraphDocument.Edge(
                    call.id() + "/" + edge.id(), fromNode, fromPort, toNode, toPort));
        }
    }

    // Check if this is an argument port
    public static boolean isArgumentPort(String port) {
        return port != null && port.startsWith(ARGUMENT_PREFIX);
    }

    // Check if this is an entry port
    public static boolean isEntryPort(String port) {
        return port != null && port.startsWith(ENTRY_PREFIX);
    }

    // Check if this is a return port
    public static boolean isReturnPort(String port) {
        return port != null && port.startsWith(RETURN_PREFIX);
    }

    // Get the external port
    public static String externalPort(String internalPort) {
        if (isArgumentPort(internalPort)) {
            return internalPort.substring(ARGUMENT_PREFIX.length());
        }
        if (isEntryPort(internalPort)) {
            return internalPort.substring(ENTRY_PREFIX.length());
        }
        if (isReturnPort(internalPort)) {
            return internalPort.substring(RETURN_PREFIX.length());
        }
        return internalPort == null ? "" : internalPort;
    }

    // Get the argument port
    public static String argumentPort(String port) {
        return ARGUMENT_PREFIX + port;
    }

    // Get the entry port
    public static String entryPort(String port) {
        return ENTRY_PREFIX + port;
    }

    // Get the return port
    public static String returnPort(String port) {
        return RETURN_PREFIX + port;
    }

    // Get the argument port
    private static String argumentPort(String port, String type) {
        return "exec".equals(type) ? entryPort(port) : argumentPort(port);
    }

    // Get the expansion stack
    private static Set<String> expansionStack(AdvancedGraphDocument.Node call) {
        Set<String> stack = new LinkedHashSet<>();
        String serialized = call.data().getString(EXPANSION_STACK);
        if (!serialized.isBlank()) {
            for (String val : serialized.split("\\R")) {
                if (!val.isBlank()) {
                    stack.add(val);
                }
            }
        }
        return stack;
    }

    // Get the visible ports
    private static Map<String, String> visiblePorts(Map<String, String> ports) {
        Map<String, String> visible = new LinkedHashMap<>();
        ports.forEach((port, type) -> {
            if (!port.startsWith("__")) {
                visible.put(port, type);
            }
        });
        return visible;
    }

    // Put the ports
    private static void putPorts(CompoundTag data, String key, Map<String, String> ports) {
        CompoundTag tag = new CompoundTag();
        ports.forEach(tag::putString);
        if (tag.isEmpty()) {
            data.remove(key);
        } else {
            data.put(key, tag);
        }
    }

    // Copy the node
    private static AdvancedGraphDocument.Node copyNode(AdvancedGraphDocument.Node node) {
        return new AdvancedGraphDocument.Node(
                node.id(), node.type(), node.label(), node.x(), node.y(), node.data().copy());
    }

    // Copy the edge
    private static AdvancedGraphDocument.Edge copyEdge(AdvancedGraphDocument.Edge edge) {
        return new AdvancedGraphDocument.Edge(
                edge.id(), edge.fromNode(), edge.fromPort(), edge.toNode(), edge.toPort());
    }

    // Get the unique port
    public static String uniquePort(String requested, Set<String> used) {
        String base = requested == null ? "value" : requested.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = "value";
        }
        String candidate = base;
        for (int suffix = 2; used.contains(candidate); suffix++) {
            candidate = base + "_" + suffix;
        }
        used.add(candidate);
        return candidate;
    }

    // Get the edge
    public static AdvancedGraphDocument.Edge edge(String fromNode, String fromPort,
                                                   String toNode, String toPort) {
        return new AdvancedGraphDocument.Edge(
                UUID.randomUUID().toString(), fromNode, fromPort, toNode, toPort);
    }
}
