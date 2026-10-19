package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShippingScheduleGraph;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

// Server-authoritative ComputerCraft surface for the SCM-owned shipping Schedule Scratch document.
// It deliberately exposes schedule operations rather than raw Create NBT, so programs can create
// schedules without coupling to Create implementation tags or client-only Scratch widgets.
final class ComputerCraftScheduleBridge {
    static final int API_VERSION = 1;
    static final List<String> API_METHOD_NAMES = List.of(
            "getScheduleApiVersion",
            "listScheduleApiMethods",
            "getScheduleApiHelp",
            "getScheduleGraph",
            "listScheduleBlockTypes",
            "getScheduleStatus",
            "getScheduleBlockProperties",
            "getScheduleBlockInputs",
            "mutateScheduleGraph",
            "validateScheduleGraph",
            "applyScheduleGraph",
            "startSchedule",
            "pauseSchedule",
            "resumeSchedule",
            "stopSchedule",
            "restartSchedule",
            "skipSchedule",
            "readScheduleItem",
            "writeScheduleItem");

    private static final int MAX_MUTATIONS = 512;
    private static final int MAX_TEXT = 128;
    private static final int MAX_PROPERTY_TEXT = 1_024;
    private static final List<String> FLOW_KINDS = List.of("start", "end", "repeat", "loop");

    private final AdvancedContraptionControllerBlockEntity controller;

    ComputerCraftScheduleBridge(AdvancedContraptionControllerBlockEntity controller) {
        this.controller = controller;
    }

    Map<String, Object> graph(Optional<String> requestedView) throws LuaException {
        String view = requestedView.orElse("draft").strip().toLowerCase(Locale.ROOT);
        AdvancedGraphDocument graph = switch (view) {
            case "draft" -> controller.getShippingScheduleDraftGraph();
            case "active" -> controller.getShippingScheduleActiveGraph();
            default -> throw new LuaException("view must be 'draft' or 'active'");
        };
        return snapshot(graph, registries());
    }

    List<Map<String, Object>> blockTypes() {
        List<Map<String, Object>> result = new ArrayList<>();
        ShippingScheduleGraph.instructionTypes().forEach(id -> result.add(Map.of(
                "id", id.toString(), "type", ShippingScheduleGraph.instructionBlockType(id),
                "category", "instruction", "title", title(id))));
        ShippingScheduleGraph.conditionTypes().forEach(id -> result.add(Map.of(
                "id", id.toString(), "type", ShippingScheduleGraph.conditionBlockType(id),
                "category", "condition", "title", title(id))));
        for (String kind : FLOW_KINDS) {
            result.add(Map.of("id", kind, "type", ShippingScheduleGraph.flowBlockType(kind),
                    "category", "flow", "title", flowTitle(kind),
                    "cBlock", "repeat".equals(kind) || "loop".equals(kind)));
        }
        return List.copyOf(result);
    }

    Map<String, Object> status() {
        AdvancedGraphDocument draft = controller.getShippingScheduleDraftGraph();
        AdvancedGraphDocument active = controller.getShippingScheduleActiveGraph();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceAvailable", controller.hasShippingScheduleWorkspace());
        result.put("pilotPresent", controller.hasActiveShippingSchedulePilot());
        result.put("blazeBurnerPilot", controller.hasBlazeBurnerShippingPilot());
        result.put("hasSchedule", controller.hasShippingSchedule());
        result.put("status", controller.getShippingScheduleStatus());
        result.put("draftRevision", draft.revision());
        result.put("activeRevision", active.revision());
        result.put("apiVersion", API_VERSION);
        result.put("cyclic", cyclic(draft));
        result.put("phase", shippingString("shipping_phase"));
        result.put("currentEntry", shippingNumber("shipping_current_entry"));
        result.put("nextEntry", shippingNumber("shipping_next_entry"));
        result.put("currentStop", shippingString("shipping_current_stop"));
        result.put("targetStop", shippingString("shipping_target_stop"));
        result.put("nextStop", shippingString("shipping_next_stop"));
        result.put("progressPercent", shippingNumber("shipping_progress_percent"));
        result.put("etaSeconds", shippingNumber("shipping_eta_seconds"));
        result.put("distanceToTarget", shippingNumber("shipping_distance_to_target"));
        result.put("throttle", shippingNumber("shipping_throttle"));
        return result;
    }

    Map<String, String> blockProperties(String nodeId) throws LuaException {
        AdvancedGraphDocument graph = controller.getShippingScheduleDraftGraph();
        return ShippingScheduleGraph.editableProperties(graph, checkedText(nodeId, "node ID", MAX_TEXT),
                registries());
    }

    List<Map<String, Object>> blockInputs(String nodeId) throws LuaException {
        AdvancedGraphDocument graph = controller.getShippingScheduleDraftGraph();
        String checked = checkedText(nodeId, "node ID", MAX_TEXT);
        AdvancedGraphDocument.Node node = node(graph, checked);
        if (node == null) throw new LuaException("unknown schedule block '" + checked + "'");
        net.minecraft.core.HolderLookup.Provider registries = registries();
        List<Map<String, Object>> result = new ArrayList<>();
        int count = ShippingScheduleGraph.inputSlotCount(graph, checked, registries);
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = ShippingScheduleGraph.inputSlot(graph, checked, slot, registries);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", slot + 1);
            row.put("label", ShippingScheduleGraph.inputSlotLabel(node, slot));
            row.put("item", stack.isEmpty() ? "" : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
            row.put("count", stack.isEmpty() ? 0 : stack.getCount());
            result.add(row);
        }
        return List.copyOf(result);
    }

    Map<String, Object> mutate(IArguments arguments) throws LuaException {
        int expectedRevision = arguments.getInt(0);
        List<Map<String, Object>> operations = operations(arguments.getTableUnsafe(1));
        AdvancedGraphDocument working = controller.getShippingScheduleDraftGraph();
        if (!controller.hasShippingScheduleWorkspace()) {
            return result(false, working.revision(), "workspace_unavailable",
                    "A mounted Ship Control Module and adjacent pilot are required", Map.of());
        }
        if (working.revision() != expectedRevision) {
            return result(false, working.revision(), "revision_conflict",
                    "The schedule draft changed; reload it before committing", Map.of());
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        try {
            for (int index = 0; index < operations.size(); index++) {
                apply(working, operations.get(index), resolved, index + 1);
            }
        } catch (ScheduleMutationRejected failure) {
            return result(false, working.revision(), failure.code, failure.getMessage(), Map.of());
        }
        if (!controller.saveShippingScheduleGraph(working, expectedRevision)) {
            return result(false, controller.getShippingScheduleDraftGraph().revision(), "save_failed",
                    "The schedule could not be saved; check the SCM workspace and schedule entries", Map.of());
        }
        return result(true, expectedRevision + 1, "ok", "Schedule draft saved", resolved);
    }

    Map<String, Object> validate() {
        AdvancedGraphDocument graph = controller.getShippingScheduleDraftGraph();
        boolean hasSteps = !ShippingScheduleGraph.orderedNodes(graph).isEmpty();
        return result(false, graph.revision(), hasSteps ? "ok" : "empty_schedule",
                hasSteps ? "Schedule draft is ready" : "Schedule draft has no instruction blocks", Map.of(), hasSteps);
    }

    Map<String, Object> apply(int expectedRevision) {
        AdvancedGraphDocument graph = controller.getShippingScheduleDraftGraph();
        if (graph.revision() != expectedRevision) {
            return result(false, graph.revision(), "revision_conflict",
                    "The schedule draft changed; reload it before applying", Map.of());
        }
        // Schedule saves update the controller-owned active route immediately;
        // there is no second, hidden graph apply stage.
        return result(true, graph.revision(), "ok", "Schedule draft is already active", Map.of(), true);
    }

    boolean start() {
        return controller.startShippingScheduleGraph();
    }

    boolean control(String command) {
        return controller.controlShippingScheduleGraph(command);
    }

    boolean readItem() {
        return controller.readShippingScheduleFromPilot();
    }

    boolean writeItem() {
        return controller.writeShippingScheduleToPilot();
    }

    private void apply(
            AdvancedGraphDocument graph,
            Map<String, Object> operation,
            Map<String, String> resolved,
            int index
    ) throws LuaException, ScheduleMutationRejected {
        String op = checkedText(operation.get("op"), "operation " + index + " op", 64);
        net.minecraft.core.HolderLookup.Provider registries = registries();
        switch (op) {
            case "append_instruction" -> {
                fields(operation, "op", "id", "instruction", "x", "y");
                ResourceLocation instruction = resource(operation.get("instruction"), "instruction");
                String created = addedNode(graph, () -> ShippingScheduleGraph.appendInstruction(
                        graph, instruction, registries), "instruction");
                moveIfPresent(graph, created, operation);
                remember(operation, created, resolved);
            }
            case "append_condition" -> {
                fields(operation, "op", "id", "instructionId", "condition");
                String instructionId = reference(operation, "instructionId", resolved);
                ResourceLocation condition = resource(operation.get("condition"), "condition");
                String created = addedNode(graph, () -> ShippingScheduleGraph.appendCondition(
                        graph, instructionId, condition, registries), "condition");
                remember(operation, created, resolved);
            }
            case "append_detached_condition" -> {
                fields(operation, "op", "id", "condition", "x", "y");
                ResourceLocation condition = resource(operation.get("condition"), "condition");
                String created = ShippingScheduleGraph.appendDetachedCondition(graph, condition,
                        finite(operation, "x"), finite(operation, "y"), registries);
                if (created.isBlank()) reject("operation_rejected", "Could not create detached condition");
                remember(operation, created, resolved);
            }
            case "attach_condition" -> {
                fields(operation, "op", "id", "conditionId", "instructionId");
                String created = ShippingScheduleGraph.attachDetachedCondition(graph,
                        reference(operation, "conditionId", resolved),
                        reference(operation, "instructionId", resolved), registries);
                if (created.isBlank()) reject("operation_rejected", "Could not attach condition to schedule step");
                remember(operation, created, resolved);
            }
            case "move_condition" -> {
                fields(operation, "op", "id", "conditionId", "instructionId");
                String created = ShippingScheduleGraph.moveConditionToInstruction(graph,
                        reference(operation, "conditionId", resolved),
                        reference(operation, "instructionId", resolved), registries);
                if (created.isBlank()) reject("operation_rejected", "Could not move condition to schedule step");
                remember(operation, created, resolved);
            }
            case "detach_condition" -> {
                fields(operation, "op", "nodeId", "x", "y");
                if (!ShippingScheduleGraph.detachCondition(graph, reference(operation, "nodeId", resolved),
                        finite(operation, "x"), finite(operation, "y"), registries)) {
                    reject("operation_rejected", "Could not detach condition");
                }
            }
            case "add_flow" -> {
                fields(operation, "op", "id", "kind", "x", "y");
                String kind = checkedText(operation.get("kind"), "flow kind", 32).toLowerCase(Locale.ROOT);
                if (!FLOW_KINDS.contains(kind)) reject("invalid_flow", "Unknown schedule flow block '" + kind + "'");
                double x = finite(operation, "x");
                double y = finite(operation, "y");
                String created = addedNode(graph, () -> ShippingScheduleGraph.appendFlowBlock(graph, kind,
                        x, y), "flow block");
                remember(operation, created, resolved);
            }
            case "remove" -> {
                fields(operation, "op", "nodeId");
                String nodeId = reference(operation, "nodeId", resolved);
                AdvancedGraphDocument.Node node = node(graph, nodeId);
                boolean changed = node != null && (ShippingScheduleGraph.isInstructionBlock(node.type())
                        ? ShippingScheduleGraph.removeInstruction(graph, nodeId)
                        : ShippingScheduleGraph.isConditionBlock(node.type())
                        ? ShippingScheduleGraph.removeCondition(graph, nodeId, registries)
                        : ShippingScheduleGraph.isFlowBlock(node.type())
                        && ShippingScheduleGraph.removeFlowBlock(graph, nodeId));
                if (!changed) reject("unknown_block", "Unknown or non-removable schedule block '" + nodeId + "'");
            }
            case "move" -> {
                fields(operation, "op", "nodeId", "x", "y");
                if (!ShippingScheduleGraph.moveBlock(graph, reference(operation, "nodeId", resolved),
                        finite(operation, "x"), finite(operation, "y"))) {
                    reject("unknown_block", "Schedule block does not exist");
                }
            }
            case "reorder_instruction" -> {
                fields(operation, "op", "nodeId", "index");
                if (!ShippingScheduleGraph.moveInstructionToIndex(graph,
                        reference(operation, "nodeId", resolved), integer(operation, "index", 1, 65_536) - 1)) {
                    reject("operation_rejected", "Could not reorder schedule instruction");
                }
            }
            case "set_parent" -> {
                fields(operation, "op", "childId", "parentId");
                if (!ShippingScheduleGraph.setScratchParent(graph, reference(operation, "childId", resolved),
                        reference(operation, "parentId", resolved))) {
                    reject("operation_rejected", "Could not nest schedule block");
                }
            }
            case "clear_parent" -> {
                fields(operation, "op", "childId");
                if (!ShippingScheduleGraph.setScratchParent(graph,
                        reference(operation, "childId", resolved), "")) {
                    reject("operation_rejected", "Could not remove schedule block from its C block");
                }
            }
            case "place_child" -> {
                fields(operation, "op", "childId", "parentId", "index");
                if (!ShippingScheduleGraph.placeScratchChild(graph, reference(operation, "childId", resolved),
                        reference(operation, "parentId", resolved), integer(operation, "index", 1, 65_536) - 1)) {
                    reject("operation_rejected", "Could not place schedule block in C block");
                }
            }
            case "connect" -> {
                fields(operation, "op", "fromId", "toId");
                if (!ShippingScheduleGraph.connectScratchBlocks(graph, reference(operation, "fromId", resolved),
                        reference(operation, "toId", resolved))) {
                    reject("operation_rejected", "Could not connect schedule blocks");
                }
            }
            case "insert_before" -> {
                fields(operation, "op", "nodeId", "beforeId");
                if (!ShippingScheduleGraph.insertScratchBlockBefore(graph,
                        reference(operation, "nodeId", resolved), reference(operation, "beforeId", resolved))) {
                    reject("operation_rejected", "Could not insert schedule block");
                }
            }
            case "set_property" -> {
                fields(operation, "op", "nodeId", "key", "value");
                if (!ShippingScheduleGraph.setProperty(graph, reference(operation, "nodeId", resolved),
                        checkedText(operation.get("key"), "property key", MAX_TEXT),
                        checkedText(operation.get("value"), "property value", MAX_PROPERTY_TEXT), registries)) {
                    reject("operation_rejected", "Could not update schedule block property");
                }
            }
            case "set_input" -> {
                fields(operation, "op", "nodeId", "slot", "item", "count");
                String itemId = optionalText(operation.get("item"), "item", MAX_TEXT);
                int count = integer(operation, "count", 0, 1);
                if (itemId.isBlank() != (count == 0)) {
                    throw new LuaException("a schedule input is either empty or exactly one item");
                }
                ItemStack stack = itemId.isBlank() ? ItemStack.EMPTY : itemStack(itemId, count);
                if (!ShippingScheduleGraph.setInputSlot(graph, reference(operation, "nodeId", resolved),
                        integer(operation, "slot", 1, 2) - 1, stack, registries)) {
                    reject("operation_rejected", "Could not update schedule block input");
                }
            }
            case "set_cyclic" -> {
                fields(operation, "op", "value");
                if (!(operation.get("value") instanceof Boolean value)) {
                    throw new LuaException("cyclic value must be a boolean");
                }
                ShippingScheduleGraph.setCyclic(graph, value);
            }
            default -> throw new LuaException("operation " + index + " has unknown op '" + op + "'");
        }
    }

    private Map<String, Object> snapshot(
            AdvancedGraphDocument graph,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", AdvancedGraphDocument.CURRENT_VERSION);
        result.put("revision", graph.revision());
        result.put("template", graph.templateId());
        result.put("cyclic", cyclic(graph));
        result.put("nodes", graph.nodes().stream().map(node -> snapshotNode(graph, node, registries)).toList());
        result.put("edges", graph.edges().stream().map(edge -> Map.<String, Object>of(
                "id", edge.id(), "fromNode", edge.fromNode(), "fromPort", edge.fromPort(),
                "toNode", edge.toNode(), "toPort", edge.toPort())).toList());
        return result;
    }

    private Map<String, Object> snapshotNode(
            AdvancedGraphDocument graph,
            AdvancedGraphDocument.Node node,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", node.id());
        result.put("type", node.type());
        result.put("label", node.label());
        result.put("x", node.x());
        result.put("y", node.y());
        result.put("parentId", ShippingScheduleGraph.scratchParent(node));
        result.put("cBlock", ShippingScheduleGraph.isScratchContainer(node));
        result.put("properties", ShippingScheduleGraph.editableProperties(graph, node.id(), registries));
        int inputs = ShippingScheduleGraph.inputSlotCount(graph, node.id(), registries);
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int slot = 0; slot < inputs; slot++) {
            ItemStack stack = ShippingScheduleGraph.inputSlot(graph, node.id(), slot, registries);
            slots.add(Map.of("slot", slot + 1,
                    "label", ShippingScheduleGraph.inputSlotLabel(node, slot),
                    "item", stack.isEmpty() ? "" : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                    "count", stack.isEmpty() ? 0 : stack.getCount()));
        }
        result.put("inputs", slots);
        return result;
    }

    private boolean cyclic(AdvancedGraphDocument graph) {
        AdvancedGraphDocument.Value value = graph.variables().get(ShippingScheduleGraph.CYCLIC_VARIABLE);
        return value != null && value.asBoolean();
    }

    private String shippingString(String port) {
        return controller.getShipControlGraphValue(port).asString();
    }

    private double shippingNumber(String port) {
        return controller.getShipControlGraphValue(port).asNumber();
    }

    private net.minecraft.core.HolderLookup.Provider registries() throws LuaException {
        if (controller.getLevel() == null) throw new LuaException("schedule controller has no level");
        return controller.getLevel().registryAccess();
    }

    private static List<Map<String, Object>> operations(Map<?, ?> table) throws LuaException {
        if (table == null || table.size() > MAX_MUTATIONS) {
            throw new LuaException("at most " + MAX_MUTATIONS + " schedule operations are allowed");
        }
        TreeMap<Integer, Object> ordered = new TreeMap<>();
        for (Map.Entry<?, ?> entry : table.entrySet()) {
            if (!(entry.getKey() instanceof Number number)) {
                throw new LuaException("schedule operations must be a consecutive one-based array");
            }
            double key = number.doubleValue();
            if (!Double.isFinite(key) || key != Math.rint(key) || key < 1 || key > Integer.MAX_VALUE
                    || ordered.put((int) key, entry.getValue()) != null) {
                throw new LuaException("schedule operations must use consecutive numeric keys");
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(ordered.size());
        for (int index = 1; index <= ordered.size(); index++) {
            Object row = ordered.get(index);
            if (!(row instanceof Map<?, ?> operation)) {
                throw new LuaException("schedule operation " + index + " must be a table");
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> field : operation.entrySet()) {
                if (!(field.getKey() instanceof String key)) {
                    throw new LuaException("schedule operation fields must be strings");
                }
                copy.put(key, field.getValue());
            }
            result.add(Map.copyOf(copy));
        }
        return List.copyOf(result);
    }

    private static void fields(Map<String, Object> operation, String... allowed) throws LuaException {
        Set<String> names = Set.of(allowed);
        for (String field : operation.keySet()) {
            if (!names.contains(field)) throw new LuaException("unknown schedule operation field '" + field + "'");
        }
    }

    private static String reference(Map<String, Object> operation, String field,
                                    Map<String, String> resolved) throws LuaException {
        String raw = checkedText(operation.get(field), field, MAX_TEXT);
        return resolved.getOrDefault(raw, raw);
    }

    private static String checkedText(Object value, String name, int maximum) throws LuaException {
        if (!(value instanceof String text) || text.isBlank() || text.length() > maximum) {
            throw new LuaException(name + " must contain 1 to " + maximum + " characters");
        }
        return text.strip();
    }

    private static String optionalText(Object value, String name, int maximum) throws LuaException {
        if (value == null) return "";
        if (!(value instanceof String text) || text.length() > maximum) {
            throw new LuaException(name + " must contain at most " + maximum + " characters");
        }
        return text.strip();
    }

    private static double finite(Map<String, Object> operation, String field) throws LuaException {
        Object value = operation.get(field);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new LuaException(field + " must be a finite number");
        }
        return number.doubleValue();
    }

    private static int integer(Map<String, Object> operation, String field, int minimum, int maximum)
            throws LuaException {
        double value = finite(operation, field);
        if (value != Math.rint(value) || value < minimum || value > maximum) {
            throw new LuaException(field + " must be an integer from " + minimum + " to " + maximum);
        }
        return (int) value;
    }

    private static ResourceLocation resource(Object raw, String name) throws LuaException {
        String text = checkedText(raw, name, MAX_TEXT);
        try {
            return ResourceLocation.parse(text);
        } catch (RuntimeException failure) {
            throw new LuaException(name + " must be a valid resource ID");
        }
    }

    private static ItemStack itemStack(String itemId, int count) throws LuaException {
        ResourceLocation id = resource(itemId, "item");
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new LuaException("unknown item '" + id + "'");
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item, Math.max(1, count));
    }

    private static AdvancedGraphDocument.Node node(AdvancedGraphDocument graph, String id) {
        return graph.nodes().stream().filter(candidate -> id.equals(candidate.id())).findFirst().orElse(null);
    }

    private static String addedNode(AdvancedGraphDocument graph, BooleanOperation operation, String name)
            throws ScheduleMutationRejected {
        Set<String> before = graph.nodes().stream().map(AdvancedGraphDocument.Node::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!operation.apply()) reject("operation_rejected", "Could not create schedule " + name);
        return graph.nodes().stream().map(AdvancedGraphDocument.Node::id)
                .filter(id -> !before.contains(id)).findFirst().orElseThrow(() ->
                        new ScheduleMutationRejected("operation_rejected", "Schedule " + name + " was not created"));
    }

    private static void moveIfPresent(AdvancedGraphDocument graph, String id, Map<String, Object> operation)
            throws LuaException, ScheduleMutationRejected {
        if (!operation.containsKey("x") && !operation.containsKey("y")) return;
        if (!operation.containsKey("x") || !operation.containsKey("y")
                || !ShippingScheduleGraph.moveBlock(graph, id, finite(operation, "x"), finite(operation, "y"))) {
            reject("invalid_position", "Both x and y are required for a schedule block position");
        }
    }

    private static void remember(Map<String, Object> operation, String id, Map<String, String> resolved)
            throws LuaException {
        if (!operation.containsKey("id")) return;
        String temporary = checkedText(operation.get("id"), "temporary ID", MAX_TEXT);
        if (!temporary.startsWith("$") || resolved.putIfAbsent(temporary, id) != null) {
            throw new LuaException("temporary ID must begin with '$' and be unique in this mutation batch");
        }
    }

    private static void reject(String code, String message) throws ScheduleMutationRejected {
        throw new ScheduleMutationRejected(code, message);
    }

    private static Map<String, Object> result(boolean saved, int revision, String code,
                                              String message, Map<String, String> resolved) {
        return result(saved, revision, code, message, resolved, "ok".equals(code));
    }

    private static Map<String, Object> result(boolean saved, int revision, String code,
                                              String message, Map<String, String> resolved, boolean valid) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", saved);
        result.put("applied", saved && valid);
        result.put("valid", valid);
        result.put("revision", revision);
        result.put("code", code);
        result.put("message", message);
        result.put("resolvedIds", Map.copyOf(resolved));
        result.put("diagnostics", List.of());
        return result;
    }

    private static String title(ResourceLocation id) {
        StringBuilder result = new StringBuilder();
        for (String word : id.getPath().replace('-', '_').split("_")) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? id.toString() : result.toString();
    }

    private static String flowTitle(String kind) {
        return switch (kind) {
            case "start" -> "Start";
            case "end" -> "End";
            case "repeat" -> "Repeat N Times";
            case "loop" -> "Loop";
            default -> kind;
        };
    }

    @FunctionalInterface
    private interface BooleanOperation {
        boolean apply();
    }

    private static final class ScheduleMutationRejected extends Exception {
        private final String code;

        private ScheduleMutationRejected(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
