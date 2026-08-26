package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataValue;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDocumentSnapshot;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphEditResult;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphMutation;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphNodeAlias;
import dan200.computercraft.api.lua.LuaException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// Bound every table crossing the CC and ACC graph boundary
public final class ComputerCraftGraphCodec {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_ENTRIES = 1_024;
    private static final int MAX_MUTATIONS = 512;
    private static final int MAX_ID = 128;
    private static final int MAX_LABEL = 1_024;

    private ComputerCraftGraphCodec() {
    }

    public static Map<String, Object> snapshot(GraphDocumentSnapshot value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", value.version());
        result.put("revision", value.revision());
        result.put("template", value.templateId());
        result.put("viewport", Map.of("x", value.viewportX(),
                "y", value.viewportY(), "zoom", value.viewportZoom()));
        result.put("nodes", value.nodes().stream()
                .map(ComputerCraftGraphCodec::node).toList());
        result.put("edges", value.edges().stream()
                .map(ComputerCraftGraphCodec::edge).toList());
        result.put("functions", value.functions().stream()
                .map(ComputerCraftGraphCodec::function).toList());
        Map<String, Object> variables = new LinkedHashMap<>();
        value.variables().forEach((name, entry) ->
                variables.put(name, graphValue(entry)));
        result.put("variables", variables);
        return result;
    }

    public static List<Map<String, Object>> nodeTypes(
            List<GraphNodeDefinition> definitions) {
        return definitions.stream().map(definition -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("category", definition.category());
            row.put("inputs", definition.inputs());
            row.put("outputs", definition.outputs());
            row.put("stateful", definition.stateful());
            return row;
        }).toList();
    }

    public static Map<String, Object> result(GraphEditResult value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", value.saved());
        result.put("applied", value.applied());
        result.put("valid", value.valid());
        result.put("revision", value.revision());
        result.put("code", value.code());
        result.put("message", value.message());
        result.put("resolvedIds", value.resolvedIds());
        result.put("diagnostics", value.diagnostics().stream().map(diagnostic ->
                Map.<String, Object>of("severity", diagnostic.severity(),
                        "code", diagnostic.code(), "message", diagnostic.message(),
                        "nodeId", diagnostic.nodeId(), "edgeId", diagnostic.edgeId()))
                .toList());
        return result;
    }

    private static Map<String, Object> node(GraphDocumentSnapshot.Node value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.id());
        result.put("type", value.type());
        result.put("label", value.label());
        result.put("alias", value.alias());
        result.put("x", value.x());
        result.put("y", value.y());
        result.put("inputs", value.inputs());
        result.put("outputs", value.outputs());
        result.put("data", graphData(value.data()));
        return result;
    }

    private static Map<String, Object> edge(GraphDocumentSnapshot.Edge value) {
        return Map.of("id", value.id(), "fromNode", value.fromNode(),
                "fromPort", value.fromPort(), "toNode", value.toNode(),
                "toPort", value.toPort());
    }

    private static Map<String, Object> function(
            GraphDocumentSnapshot.FunctionGraph value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.id());
        result.put("name", value.name());
        result.put("viewport", Map.of("x", value.viewportX(),
                "y", value.viewportY(), "zoom", value.viewportZoom()));
        result.put("nodes", value.nodes().stream()
                .map(ComputerCraftGraphCodec::node).toList());
        result.put("edges", value.edges().stream()
                .map(ComputerCraftGraphCodec::edge).toList());
        return result;
    }
    public static List<GraphMutation> mutations(Map<?, ?> table)
            throws LuaException {
        Budget budget = new Budget();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        enter(table, seen);
        budget.take(table.size());
        List<Object> rows = array(table, true, budget, 0, seen);
        if (rows == null) {
            seen.remove(table);
            throw new LuaException("mutations must be a consecutive one-based array");
        }
        if (rows.size() > MAX_MUTATIONS) {
            throw new LuaException("at most " + MAX_MUTATIONS + " mutations are allowed");
        }
        List<GraphMutation> result = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            if (!(rows.get(index) instanceof Map<?, ?> operation)) {
                throw new LuaException("mutation " + (index + 1) + " must be a table");
            }
            result.add(mutation(operation, budget, seen, index + 1));
        }
        seen.remove(table);
        return List.copyOf(result);
    }

    private static GraphMutation mutation(Map<?, ?> table, Budget budget,
                                          IdentityHashMap<Object, Boolean> seen,
                                          int index) throws LuaException {
        enter(table, seen);
        budget.take(table.size());
        String op = text(table, "op", null, MAX_ID);
        String functionId = optionalText(table, "functionId", "", MAX_ID);
        GraphMutation result = switch (op) {
            case "add_node" -> {
                fields(table, "op", "functionId", "id", "type", "label", "alias",
                        "x", "y", "data");
                Object rawData = table.containsKey("data")
                        ? table.get("data") : Map.of();
                GraphDataValue parsed = data(rawData, budget, 1, seen);
                if (!(parsed instanceof GraphDataValue.CompoundValue data)) {
                    throw new LuaException("add_node data must be an NBT compound");
                }
                yield new GraphMutation.AddNode(functionId,
                        text(table, "id", null, MAX_ID),
                        text(table, "type", null, MAX_ID),
                        optionalText(table, "label", "", MAX_LABEL),
                        optionalText(table, "alias", "", GraphNodeAlias.MAX_LENGTH),
                        finite(table, "x"), finite(table, "y"), data);
            }
            case "remove_node" -> {
                fields(table, "op", "functionId", "nodeId", "node");
                yield new GraphMutation.RemoveNode(functionId,
                        nodeReference(table));
            }
            case "move_node" -> {
                fields(table, "op", "functionId", "nodeId", "node", "x", "y");
                yield new GraphMutation.MoveNode(functionId,
                        nodeReference(table),
                        finite(table, "x"), finite(table, "y"));
            }
            case "rename_node" -> {
                fields(table, "op", "functionId", "nodeId", "node", "label");
                yield new GraphMutation.RenameNode(functionId,
                        nodeReference(table),
                        optionalText(table, "label", "", MAX_LABEL));
            }
            case "set_node_alias" -> {
                fields(table, "op", "functionId", "nodeId", "node", "alias");
                yield new GraphMutation.SetNodeAlias(functionId,
                        nodeReference(table),
                        optionalText(table, "alias", "", GraphNodeAlias.MAX_LENGTH));
            }
            case "set_node_data" -> {
                fields(table, "op", "functionId", "nodeId", "node", "key", "value");
                if (!table.containsKey("value")) {
                    throw new LuaException("mutation " + index + " needs value");
                }
                yield new GraphMutation.SetNodeData(functionId,
                        nodeReference(table),
                        text(table, "key", null, MAX_ID),
                        data(table.get("value"), budget, 1, seen));
            }
            case "remove_node_data" -> {
                fields(table, "op", "functionId", "nodeId", "node", "key");
                yield new GraphMutation.RemoveNodeData(functionId,
                        nodeReference(table),
                        text(table, "key", null, MAX_ID));
            }
            case "add_edge" -> {
                fields(table, "op", "functionId", "id", "fromNode",
                        "fromPort", "toNode", "toPort");
                yield new GraphMutation.AddEdge(functionId,
                        text(table, "id", null, MAX_ID),
                        text(table, "fromNode", null, MAX_ID),
                        text(table, "fromPort", null, MAX_ID),
                        text(table, "toNode", null, MAX_ID),
                        text(table, "toPort", null, MAX_ID));
            }
            case "remove_edge" -> {
                fields(table, "op", "functionId", "edge");
                yield new GraphMutation.RemoveEdge(functionId,
                        text(table, "edge", null, MAX_ID));
            }
            case "add_function" -> {
                fields(table, "op", "id", "name");
                yield new GraphMutation.AddFunction(
                        text(table, "id", null, MAX_ID),
                        text(table, "name", null, 64));
            }
            case "rename_function" -> {
                fields(table, "op", "functionId", "name");
                yield new GraphMutation.RenameFunction(functionId,
                        text(table, "name", null, 64));
            }
            case "remove_function" -> {
                fields(table, "op", "functionId");
                yield new GraphMutation.RemoveFunction(functionId);
            }
            case "set_variable" -> {
                fields(table, "op", "name", "value");
                if (!table.containsKey("value")) {
                    throw new LuaException("mutation " + index + " needs value");
                }
                yield new GraphMutation.SetVariable(
                        text(table, "name", null, 64),
                        runtimeValue(table.get("value"), budget, 1, seen));
            }
            case "remove_variable" -> {
                fields(table, "op", "name");
                yield new GraphMutation.RemoveVariable(
                        text(table, "name", null, 64));
            }
            default -> throw new LuaException(
                    "mutation " + index + " has unknown op '" + op + "'");
        };
        seen.remove(table);
        return result;
    }
    private static Object graphData(GraphDataValue value) {
        if (value instanceof GraphDataValue.NumericValue numeric) {
            Object encoded = numeric.kind() == GraphDataValue.NumberKind.LONG
                    ? numeric.value().toString() : numeric.value();
            return Map.of("__nbt", numeric.kind().name().toLowerCase(Locale.ROOT),
                    "value", encoded);
        }
        if (value instanceof GraphDataValue.StringValue string) return string.value();
        if (value instanceof GraphDataValue.ListValue list) {
            return Map.of("__nbt", "list", "value", list.values().stream()
                    .map(ComputerCraftGraphCodec::graphData).toList());
        }
        if (value instanceof GraphDataValue.CompoundValue compound) {
            Map<String, Object> result = new LinkedHashMap<>();
            compound.values().forEach((key, entry) -> result.put(key, graphData(entry)));
            return Map.of("__nbt", "compound", "value", result);
        }
        if (value instanceof GraphDataValue.ByteArrayValue array) {
            return Map.of("__nbt", "byte_array", "value", array.values());
        }
        if (value instanceof GraphDataValue.IntArrayValue array) {
            return Map.of("__nbt", "int_array", "value", array.values());
        }
        if (value instanceof GraphDataValue.LongArrayValue array) {
            return Map.of("__nbt", "long_array", "value", array.values().stream()
                    .map(String::valueOf).toList());
        }
        throw new IllegalArgumentException("Unsupported graph data value: " + value);
    }

    private static GraphDataValue data(Object raw, Budget budget, int depth,
                                       IdentityHashMap<Object, Boolean> seen)
            throws LuaException {
        depth(depth);
        if (raw instanceof Boolean bool) {
            return new GraphDataValue.NumericValue(
                    GraphDataValue.NumberKind.BYTE, bool ? 1 : 0);
        }
        if (raw instanceof Number number) {
            return new GraphDataValue.NumericValue(
                    GraphDataValue.NumberKind.DOUBLE, finite(number));
        }
        if (raw instanceof String string) return new GraphDataValue.StringValue(string);
        if (!(raw instanceof Map<?, ?> table)) {
            throw new LuaException("graph data supports only boolean, number, string or table");
        }
        enter(table, seen);
        try {
            budget.take(table.size());
            Object tagName = table.get("__nbt");
            if (tagName != null) return tagged(table, String.valueOf(tagName), budget, depth, seen);
            List<Object> array = array(table, false, budget, depth, seen);
            if (array != null) {
                List<GraphDataValue> values = new ArrayList<>(array.size());
                for (Object entry : array) values.add(data(entry, budget, depth + 1, seen));
                try {
                    return new GraphDataValue.ListValue(values);
                } catch (IllegalArgumentException failure) {
                    throw new LuaException(failure.getMessage());
                }
            }
            return compoundContents(table, budget, depth, seen);
        } finally {
            seen.remove(table);
        }
    }

    private static GraphDataValue.CompoundValue compound(
            Object raw, Budget budget, int depth,
            IdentityHashMap<Object, Boolean> seen) throws LuaException {
        depth(depth);
        if (!(raw instanceof Map<?, ?> table)) {
            throw new LuaException("node data must be a string-keyed table");
        }
        enter(table, seen);
        try {
            budget.take(table.size());
            return compoundContents(table, budget, depth, seen);
        } finally {
            seen.remove(table);
        }
    }

    private static GraphDataValue.CompoundValue compoundContents(
            Map<?, ?> table, Budget budget, int depth,
            IdentityHashMap<Object, Boolean> seen) throws LuaException {
        Map<String, GraphDataValue> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : table.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || key.length() > MAX_ID) {
                throw new LuaException("compound graph data requires string keys");
            }
            values.put(key, data(entry.getValue(), budget, depth + 1, seen));
        }
        return new GraphDataValue.CompoundValue(values);
    }

    private static GraphDataValue tagged(Map<?, ?> table, String rawKind,
                                         Budget budget, int depth,
                                         IdentityHashMap<Object, Boolean> seen)
            throws LuaException {
        fields(table, "__nbt", "value");
        String kind = rawKind.strip().toLowerCase(Locale.ROOT);
        Object raw = table.get("value");
        try {
            return switch (kind) {
                case "byte" -> numeric(GraphDataValue.NumberKind.BYTE, raw);
                case "short" -> numeric(GraphDataValue.NumberKind.SHORT, raw);
                case "int" -> numeric(GraphDataValue.NumberKind.INT, raw);
                case "long" -> numeric(GraphDataValue.NumberKind.LONG, raw);
                case "float" -> numeric(GraphDataValue.NumberKind.FLOAT, raw);
                case "double" -> numeric(GraphDataValue.NumberKind.DOUBLE, raw);
                case "list" -> listValue(raw, budget, depth, seen);
                case "compound" -> compound(raw, budget, depth + 1, seen);
                case "byte_array" -> new GraphDataValue.ByteArrayValue(
                        boxedBytes(arrayValue(raw, budget, depth, seen)));
                case "int_array" -> new GraphDataValue.IntArrayValue(
                        boxedInts(arrayValue(raw, budget, depth, seen)));
                case "long_array" -> new GraphDataValue.LongArrayValue(
                        boxedLongs(arrayValue(raw, budget, depth, seen)));
                default -> throw new LuaException("unknown __nbt kind '" + kind + "'");
            };
        } catch (ArithmeticException | IllegalArgumentException failure) {
            throw new LuaException("invalid " + kind + " value: " + failure.getMessage());
        }
    }

    private static GraphDataValue.NumericValue numeric(
            GraphDataValue.NumberKind kind, Object raw) throws LuaException {
        if (kind == GraphDataValue.NumberKind.LONG && raw instanceof String text) {
            try {
                return new GraphDataValue.NumericValue(kind,
                        new BigDecimal(text.strip()).longValueExact());
            } catch (NumberFormatException | ArithmeticException failure) {
                throw new LuaException("long value must be an exact decimal integer");
            }
        }
        if (!(raw instanceof Number number)) {
            throw new LuaException("numeric value required");
        }
        return new GraphDataValue.NumericValue(kind, number);
    }

    private static GraphDataValue.ListValue listValue(
            Object raw, Budget budget, int depth,
            IdentityHashMap<Object, Boolean> seen) throws LuaException {
        List<GraphDataValue> values = new ArrayList<>();
        for (Object entry : arrayValue(raw, budget, depth, seen)) {
            values.add(data(entry, budget, depth + 1, seen));
        }
        try {
            return new GraphDataValue.ListValue(values);
        } catch (IllegalArgumentException failure) {
            throw new LuaException(failure.getMessage());
        }
    }

    private static List<Object> arrayValue(Object raw, Budget budget, int depth,
                                            IdentityHashMap<Object, Boolean> seen)
            throws LuaException {
        if (!(raw instanceof Map<?, ?> table)) throw new LuaException("array table required");
        depth(depth);
        enter(table, seen);
        try {
            budget.take(table.size());
            List<Object> result = array(table, true, budget, depth, seen);
            if (result == null) throw new LuaException("array table requires numeric keys");
            return result;
        } finally {
            seen.remove(table);
        }
    }

    // Encode one runtime graph value for ComputerCraft
    public static Object graphValue(GraphValue value) {
        return Map.of("type", value.type(), "value", runtimeRaw(value.value()));
    }

    // Encode one graph value as an ordinary ComputerCraft value
    public static Object plainGraphValue(GraphValue value) {
        if (value == null) return 0.0D;
        return switch (value.type()) {
            case "number", "boolean", "string", "direction" -> value.value();
            case "list", "map", "target", "frequency" -> plainRuntimeRaw(value.value());
            default -> graphValue(value);
        };
    }

    // Decode one ComputerCraft value for the graph runtime
    public static GraphValue runtimeValue(Object raw) throws LuaException {
        return runtimeValue(raw, new Budget(), 0, new IdentityHashMap<>());
    }

    // Decode one ordinary ComputerCraft value without graph type wrappers
    public static GraphValue plainRuntimeValue(Object raw) throws LuaException {
        return plainRuntimeValue(raw, new Budget(), 0, new IdentityHashMap<>());
    }

    private static Object runtimeRaw(Object value) {
        if (value instanceof GraphValue graphValue) return graphValue(graphValue);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, entry) ->
                    result.put(String.valueOf(key), runtimeRaw(entry)));
            return Map.of("__graph", "map", "value", result);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(entry -> result.add(runtimeRaw(entry)));
            return Map.of("__graph", "list", "value", List.copyOf(result));
        }
        return value == null ? "" : value;
    }

    // Encode nested graph values without the graph bridge's type wrappers
    private static Object plainRuntimeRaw(Object value) {
        if (value instanceof GraphValue graphValue) return plainGraphValue(graphValue);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, entry) ->
                    result.put(String.valueOf(key), plainRuntimeRaw(entry)));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            Map<Integer, Object> result = new LinkedHashMap<>();
            int index = 1;
            for (Object entry : iterable) {
                result.put(index++, plainRuntimeRaw(entry));
            }
            return result;
        }
        return value == null ? "" : value;
    }

    private static GraphValue runtimeValue(Object raw, Budget budget, int depth,
                                           IdentityHashMap<Object, Boolean> seen)
            throws LuaException {
        depth(depth);
        if (raw instanceof Boolean bool) return GraphValue.bool(bool);
        if (raw instanceof Number number) return GraphValue.number(finite(number));
        if (raw instanceof String string) return GraphValue.string(string);
        if (!(raw instanceof Map<?, ?> table)) {
            throw new LuaException("variable value must be boolean, number, string or table");
        }
        enter(table, seen);
        try {
            budget.take(table.size());
            if (table.get("type") instanceof String type && table.containsKey("value")) {
                fields(table, "type", "value");
                if (type.isBlank() || type.length() > MAX_ID) {
                    throw new LuaException("variable type must contain 1 to "
                            + MAX_ID + " characters");
                }
                return new GraphValue(type, runtimeRawInput(
                        table.get("value"), budget, depth + 1, seen));
            }
            Object value = runtimeTableContents(table, budget, depth, seen, false);
            return value instanceof List<?> list
                    ? GraphValue.list(list) : GraphValue.map((Map<String, Object>) value);
        } finally {
            seen.remove(table);
        }
    }

    // Decode one ordinary ComputerCraft value
    private static GraphValue plainRuntimeValue(Object raw, Budget budget, int depth,
                                                IdentityHashMap<Object, Boolean> seen)
            throws LuaException {
        depth(depth);
        if (raw == null) return GraphValue.number(0.0D);
        if (raw instanceof Boolean bool) return GraphValue.bool(bool);
        if (raw instanceof Number number) return GraphValue.number(finite(number));
        if (raw instanceof String string) return GraphValue.string(string);
        if (!(raw instanceof Map<?, ?> table)) {
            throw new LuaException("Named Event data supports only boolean, number, string or table");
        }
        enter(table, seen);
        try {
            budget.take(table.size());
            List<Object> array = array(table, false, budget, depth, seen);
            if (array != null) {
                List<GraphValue> result = new ArrayList<>(array.size());
                for (Object entry : array) {
                    result.add(plainRuntimeValue(entry, budget, depth + 1, seen));
                }
                return GraphValue.list(result);
            }
            Map<String, GraphValue> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : table.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.length() > MAX_ID) {
                    throw new LuaException("Named Event map data requires string keys");
                }
                result.put(key, plainRuntimeValue(entry.getValue(), budget, depth + 1, seen));
            }
            return GraphValue.map(result);
        } finally {
            seen.remove(table);
        }
    }

    private static Object runtimeRawInput(Object raw, Budget budget, int depth,
                                          IdentityHashMap<Object, Boolean> seen)
            throws LuaException {
        depth(depth);
        if (raw instanceof Boolean || raw instanceof String) return raw;
        if (raw instanceof Number number) return finite(number);
        if (!(raw instanceof Map<?, ?> table)) {
            throw new LuaException("nested variable value is not Lua-safe");
        }
        enter(table, seen);
        try {
            budget.take(table.size());
            Object kind = table.get("__graph");
            if (kind != null) {
                fields(table, "__graph", "value");
                if (!(kind instanceof String name)
                        || !(table.get("value") instanceof Map<?, ?> nested)) {
                    throw new LuaException("graph collection wrapper requires a table value");
                }
                return runtimeCollection(nested, name, budget, depth + 1, seen);
            }
            return runtimeTableContents(table, budget, depth, seen, false);
        } finally {
            seen.remove(table);
        }
    }

    private static Object runtimeCollection(
            Map<?, ?> table, String rawKind, Budget budget, int depth,
            IdentityHashMap<Object, Boolean> seen) throws LuaException {
        depth(depth);
        enter(table, seen);
        try {
            budget.take(table.size());
            String kind = rawKind.strip().toLowerCase(Locale.ROOT);
            if (!"list".equals(kind) && !"map".equals(kind)) {
                throw new LuaException("unknown graph collection kind '" + kind + "'");
            }
            if ("map".equals(kind)) {
                for (Object key : table.keySet()) {
                    if (!(key instanceof String)) {
                        throw new LuaException("map wrapper requires string keys");
                    }
                }
            }
            return runtimeTableContents(table, budget, depth, seen,
                    "list".equals(kind));
        } finally {
            seen.remove(table);
        }
    }

    private static Object runtimeTableContents(
            Map<?, ?> table, Budget budget, int depth,
            IdentityHashMap<Object, Boolean> seen, boolean forceList)
            throws LuaException {
        depth(depth);
        List<Object> array = array(table, false, budget, depth, seen);
        if (forceList && table.isEmpty()) array = List.of();
        if (forceList && array == null) {
            throw new LuaException("list wrapper requires consecutive numeric keys");
        }
        if (array != null) {
            List<Object> result = new ArrayList<>(array.size());
            for (Object entry : array) {
                result.add(runtimeRawInput(entry, budget, depth + 1, seen));
            }
            return List.copyOf(result);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : table.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || key.length() > MAX_ID) {
                throw new LuaException("map variable values require string keys");
            }
            result.put(key, runtimeRawInput(entry.getValue(), budget, depth + 1, seen));
        }
        return result;
    }

    private static List<Object> array(Map<?, ?> table, boolean emptyIsArray,
                                      Budget budget, int depth,
                                      IdentityHashMap<Object, Boolean> seen)
            throws LuaException {
        depth(depth);
        if (table.isEmpty()) return emptyIsArray ? List.of() : null;
        TreeMap<Integer, Object> ordered = new TreeMap<>();
        for (Map.Entry<?, ?> entry : table.entrySet()) {
            if (!(entry.getKey() instanceof Number number)) return null;
            double key = finite(number);
            if (key != Math.rint(key) || key < 1 || key > Integer.MAX_VALUE) {
                throw new LuaException("array keys must be consecutive positive integers");
            }
            if (ordered.put((int) key, entry.getValue()) != null) {
                throw new LuaException("duplicate array key " + (int) key);
            }
        }
        for (int index = 1; index <= ordered.size(); index++) {
            if (!ordered.containsKey(index)) throw new LuaException("array keys cannot have gaps");
        }
        return List.copyOf(ordered.values());
    }

    private static void fields(Map<?, ?> table, String... allowed)
            throws LuaException {
        Set<String> names = Set.of(allowed);
        for (Object key : table.keySet()) {
            if (!(key instanceof String name) || !names.contains(name)) {
                throw new LuaException("unknown mutation field '" + key + "'");
            }
        }
    }

    private static String text(Map<?, ?> table, String key,
                               String fallback, int maximum) throws LuaException {
        Object raw = table.get(key);
        if (raw == null && fallback != null) return fallback;
        if (!(raw instanceof String value) || value.isBlank() || value.length() > maximum) {
            throw new LuaException(key + " must contain 1 to " + maximum + " characters");
        }
        return value.strip();
    }

    private static String optionalText(Map<?, ?> table, String key,
                                       String fallback, int maximum)
            throws LuaException {
        Object raw = table.get(key);
        if (raw == null) return fallback;
        if (!(raw instanceof String value) || value.length() > maximum) {
            throw new LuaException(key + " must contain at most "
                    + maximum + " characters");
        }
        return value.strip();
    }

    private static String nodeReference(Map<?, ?> table) throws LuaException {
        if (table.containsKey("nodeId") && table.containsKey("node")) {
            throw new LuaException("use nodeId; do not provide both nodeId and legacy node");
        }
        return table.containsKey("nodeId")
                ? text(table, "nodeId", null, MAX_ID)
                : text(table, "node", null, MAX_ID);
    }

    private static double finite(Map<?, ?> table, String key) throws LuaException {
        Object raw = table.get(key);
        if (!(raw instanceof Number number)) throw new LuaException(key + " must be a number");
        return finite(number);
    }

    private static double finite(Number value) throws LuaException {
        double result = value.doubleValue();
        if (!Double.isFinite(result)) throw new LuaException("number must be finite");
        return result;
    }

    private static List<Byte> boxedBytes(List<Object> raw) throws LuaException {
        List<Byte> result = new ArrayList<>(raw.size());
        for (Object value : raw) result.add(numeric(
                GraphDataValue.NumberKind.BYTE, value).value().byteValue());
        return List.copyOf(result);
    }

    private static List<Integer> boxedInts(List<Object> raw) throws LuaException {
        List<Integer> result = new ArrayList<>(raw.size());
        for (Object value : raw) result.add(numeric(
                GraphDataValue.NumberKind.INT, value).value().intValue());
        return List.copyOf(result);
    }

    private static List<Long> boxedLongs(List<Object> raw) throws LuaException {
        List<Long> result = new ArrayList<>(raw.size());
        for (Object value : raw) result.add(numeric(
                GraphDataValue.NumberKind.LONG, value).value().longValue());
        return List.copyOf(result);
    }

    private static void depth(int depth) throws LuaException {
        if (depth > MAX_DEPTH) throw new LuaException("table depth exceeds " + MAX_DEPTH);
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> seen)
            throws LuaException {
        if (seen.put(value, Boolean.TRUE) != null) {
            throw new LuaException("cyclic tables are not supported");
        }
    }

    private static final class Budget {
        private int entries;

        private void take(int amount) throws LuaException {
            entries += Math.max(0, amount);
            if (entries > MAX_ENTRIES) {
                throw new LuaException("table entry limit exceeds " + MAX_ENTRIES);
            }
        }
    }
}
