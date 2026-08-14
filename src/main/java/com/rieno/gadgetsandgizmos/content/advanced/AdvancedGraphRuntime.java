package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.create.CreateRotationSpeedControllerGraphCompat;
import com.rieno.gadgetsandgizmos.compat.create.CreateFantasizingGraphCompat;
import com.rieno.gadgetsandgizmos.compat.create.NavigationTableGraphCompat;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.control.math.PidControllerMath;
import com.rieno.gadgetsandgizmos.lib.control.math.AdrcControllerMath;
import com.rieno.gadgetsandgizmos.lib.control.math.AdrcControllerNthOrderMath;
import com.rieno.gadgetsandgizmos.lib.control.math.LqrControllerMath;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.Set;

// Run portable graph documents outside the live ACC while matching its node behavior and state rules
public final class AdvancedGraphRuntime {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MAX_OPERATIONS = 4096;
    public static final int MAX_EVENTS_PER_TICK = 64;
    public static final int MAX_LOOP_ITERATIONS = 256;
    public static final int MAX_SCHEDULED_EVENTS = 1024;
    private static final double DEFAULT_SMOOTHING_AMOUNT = 0.25D;
    private static final double TICKS_PER_SECOND = 20.0D;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Advanced graph controller
    private final AdvancedContraptionControllerBlockEntity controller;
    // Tracked events
    private final Queue<String> events = new ArrayDeque<>();
    // Tracked scheduled
    private final Queue<ScheduledEvent> scheduled = new PriorityQueue<>((a, b) -> Long.compare(a.tick(), b.tick()));
    // Tracked advanced graph state
    private final Map<String, AdvancedGraphDocument.Value> state = new LinkedHashMap<>();
    // Tracked binding states
    private final Map<String, Boolean> bindingStates = new LinkedHashMap<>();
    // Tracked live inputs
    private final Map<String, AdvancedGraphDocument.Value> liveInputs = new LinkedHashMap<>();
    // Tracked live outputs
    private final Map<String, AdvancedGraphDocument.Value> liveOutputs = new LinkedHashMap<>();
    // Tracked execution pulses
    private final Map<String, Long> executionPulses = new LinkedHashMap<>();
    // Tracked diagnostics
    private final List<AdvancedGraphValidator.Diagnostic> diagnostics = new ArrayList<>();
    // Active execution nodes
    private final Set<String> activeExecutionNodes = new HashSet<>();
    // Active output ports
    private final Set<String> activeOutputPorts = new HashSet<>();
    // Active curve sweeps
    private final Set<String> activeCurveSweeps = new LinkedHashSet<>();
    // Pending ship commands
    private final Set<String> pendingShipCommands = new LinkedHashSet<>();
    // Pending outputs
    private Map<String, Double> pendingOutputs;
    // Tracks whether reset is pending
    private boolean pendingReset;
    // Current event id
    private String currentEventId = "";
    // Last game time
    private long lastGameTime = Long.MIN_VALUE;
    // Current delta time in seconds
    private double currentDeltaTimeSeconds = 1.0D / TICKS_PER_SECOND;
    // Compiled graph
    private AdvancedGraphDocument compiledGraph;
    // Compiled program
    private AdvancedGraphProgram compiledProgram;
    // Compiled revision
    private int compiledRevision = -1;
    // Compiled node count
    private int compiledNodeCount = -1;
    // Compiled edge count
    private int compiledEdgeCount = -1;
    // Compiled topology hash
    private int compiledTopologyHash = 0;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph
    public AdvancedGraphRuntime(AdvancedContraptionControllerBlockEntity controller) {
        this.controller = controller;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Queue the advanced graph
    public void enqueue(String eventId) {
        if (eventId != null && events.size() < MAX_EVENTS_PER_TICK) {
            events.add(eventId);
        }
    }

    // Check if the set binding is active
    public boolean setBindingActive(String bindingId, boolean active) {
        if (bindingId == null || bindingId.isBlank()) return false;
        Boolean prev = bindingStates.put(bindingId, active);
        if (prev != null && prev == active) return false;
        enqueue("input:" + bindingId + ":" + (active ? "active" : "inactive"));
        return true;
    }

    // Check if this has pending work
    public boolean hasPendingWork() {
        return !events.isEmpty() || !scheduled.isEmpty() || !activeCurveSweeps.isEmpty()
                || !pendingShipCommands.isEmpty();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the advanced graph
    public void tick(AdvancedGraphDocument graph) {
        AdvancedGraphProgram program = programFor(graph);
        long gameTime = gameTime();
        currentDeltaTimeSeconds = updateDeltaTimeSeconds(gameTime);
        pollShipControlCommands(program);
        while (!scheduled.isEmpty() && scheduled.peek().tick() <= gameTime && events.size() < MAX_EVENTS_PER_TICK) {
            enqueue(scheduled.remove().eventId());
        }
        if (events.isEmpty()) {
            for (String nodeId : List.copyOf(activeCurveSweeps)) {
                enqueue("curve:" + nodeId);
            }
        }
        if (!program.tickNodes().isEmpty() || !program.periodicNodes().isEmpty()
                || !program.pulseOnChangeNodes().isEmpty()) {
            enqueue("tick");
        }
        int processed = 0;
        while (!events.isEmpty() && processed++ < MAX_EVENTS_PER_TICK) {
            execute(program, events.remove());
        }
        if (!program.hudNodes().isEmpty()) {
            updateHudInputs(program);
        }
    }

    // Poll the ship control commands
    private void pollShipControlCommands(AdvancedGraphProgram program) {
        if (controller == null || pendingShipCommands.isEmpty()) {
            return;
        }
        for (String nodeId : List.copyOf(pendingShipCommands)) {
            AdvancedGraphDocument.Node node = program.node(nodeId);
            if (node == null || !AdvancedGraphCatalog.isShipControlType(node.type())
                    || AdvancedGraphCatalog.isShipControlPassiveType(node.type())) {
                pendingShipCommands.remove(nodeId);
                state.remove(shipCommandSuccessKey(nodeId));
                continue;
            }
            if (controller.isShipControlGraphCommandComplete(node.id(), node.type())) {
                state.put(shipCommandSuccessKey(nodeId),
                        AdvancedGraphDocument.Value.bool(true));
                if (events.size() < MAX_EVENTS_PER_TICK) {
                    pendingShipCommands.remove(nodeId);
                    events.add("ship_complete:" + nodeId);
                }
            } else if (!controller.isShipControlGraphCommandPending(node.id(), node.type())) {
                pendingShipCommands.remove(nodeId);
                state.put(shipCommandSuccessKey(nodeId),
                        AdvancedGraphDocument.Value.bool(false));
            }
        }
    }

    // Get the ship command success key
    private static String shipCommandSuccessKey(String nodeId) {
        return nodeId + ":ship_command_success";
    }

    // Compile the advanced graph
    public void compile(AdvancedGraphDocument graph) {
        programFor(graph);
    }

    // Clear the advanced graph
    public void clear() {
        events.clear();
        scheduled.clear();
        activeCurveSweeps.clear();
        pendingShipCommands.clear();
        state.clear();
        bindingStates.clear();
        liveInputs.clear();
        liveOutputs.clear();
        executionPulses.clear();
        diagnostics.clear();
        activeExecutionNodes.clear();
        activeOutputPorts.clear();
        pendingOutputs = null;
        pendingReset = false;
        currentEventId = "";
        lastGameTime = Long.MIN_VALUE;
        currentDeltaTimeSeconds = 1.0D / TICKS_PER_SECOND;
        compiledGraph = null;
        compiledProgram = null;
        compiledRevision = -1;
        compiledNodeCount = -1;
        compiledEdgeCount = -1;
        compiledTopologyHash = 0;
    }

    // Get the program
    private AdvancedGraphProgram programFor(AdvancedGraphDocument graph) {
        if (graph == null) {
            graph = new AdvancedGraphDocument();
        }
        int topologyHash = graph.programFingerprint();
        if (compiledProgram == null
                || compiledGraph != graph
                || compiledRevision != graph.revision()
                || compiledNodeCount != graph.nodes().size()
                || compiledEdgeCount != graph.edges().size()
                || compiledTopologyHash != topologyHash) {
            compiledGraph = graph;
            compiledRevision = graph.revision();
            compiledNodeCount = graph.nodes().size();
            compiledEdgeCount = graph.edges().size();
            compiledTopologyHash = topologyHash;
            compiledProgram = AdvancedGraphProgram.compile(graph);
        }
        return compiledProgram;
    }

    // Update the HUD inputs
    private void updateHudInputs(AdvancedGraphProgram program) {
        Map<String, Map<String, AdvancedGraphDocument.Value>> cache = new HashMap<>();
        int[] operations = {0};
        for (AdvancedGraphDocument.Node node : program.hudNodes()) {
            try {
                boolean sampledDataField = false;
                for (String port : AdvancedGraphCatalog.inputs(node).keySet()) {
                    if (!"label".equals(port) && !"visible".equals(port)) {
                        sampledDataField = true;
                    }
                    valueFor(program, cache, node, port, operations);
                }
                if (!sampledDataField) {
                    valueFor(program, cache, node, "value", operations);
                }
            } catch (RuntimeException err) {
                diagnostics.add(new AdvancedGraphValidator.Diagnostic("warning", node.id(),
                        "Could not update HUD element inputs: " + err.getMessage(), ""));
            }
        }
    }

    // Get the diagnostics
    public List<AdvancedGraphValidator.Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    // Get the preview input
    public AdvancedGraphDocument.Value previewInput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        if (graph == null || node == null || port == null) return AdvancedGraphDocument.Value.number(0);
        AdvancedGraphProgram program = programFor(graph);
        try {
            return valueFor(program, new HashMap<>(), node, port, new int[]{0});
        } catch (RuntimeException ignored) {
            return AdvancedGraphDocument.Value.number(0);
        }
    }

    // Get the preview output
    public AdvancedGraphDocument.Value previewOutput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        if (graph == null || node == null || port == null) return AdvancedGraphDocument.Value.number(0);
        AdvancedGraphProgram program = programFor(graph);
        try {
            return outputFor(program, new HashMap<>(), node, port, new int[]{0});
        } catch (RuntimeException ignored) {
            return AdvancedGraphDocument.Value.number(0);
        }
    }

    // Get the live inputs
    public Map<String, AdvancedGraphDocument.Value> liveInputs() {
        return Map.copyOf(liveInputs);
    }

    // Get the live outputs
    public Map<String, AdvancedGraphDocument.Value> liveOutputs() {
        return Map.copyOf(liveOutputs);
    }

    // Get the execution pulses
    public Map<String, Long> executionPulses() {
        return Map.copyOf(executionPulses);
    }

    // Run the advanced graph
    private void execute(AdvancedGraphProgram program, String eventId) {
        // -----------------------------------------------------EVENT SETUP-----------------------------------------------------
        currentEventId = eventId;
        diagnostics.clear();
        pendingOutputs = new LinkedHashMap<>();
        pendingReset = false;
        Map<String, Map<String, AdvancedGraphDocument.Value>> cache = new HashMap<>();
        int[] operations = {0};
        try {
            // ------------------------------------TICK EVENTS------------------------------------
            if ("tick".equals(eventId)) {
                for (AdvancedGraphDocument.Node node : program.tickNodes()) {
                    followExec(program, cache, node.id(), "exec", operations);
                }
                for (AdvancedGraphDocument.Node node : program.periodicNodes()) {
                    if (controller == null || controller.getLevel() == null
                            || controller.getLevel().getGameTime() % Math.max(1,
                            (int) valueFor(program, cache, node, "period", operations).asNumber()) == 0) {
                        followExec(program, cache, node.id(), "exec", operations);
                    }
                }
                for (AdvancedGraphDocument.Node node : program.pulseOnChangeNodes()) {
                    AdvancedGraphDocument.Value val = valueFor(program, cache, node, "value", operations);
                    if ("event_value_change".equals(node.type())) {
                        cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("value", val);
                        liveOutputs.put(node.id() + ":value", val);
                    }
                    String key = node.id() + ":observed";
                    AdvancedGraphDocument.Value prev = state.put(key, val);
                    if (prev != null && !sameValue(prev, val)) {
                        followExec(program, cache, node.id(), "exec", operations);
                    }
                }
            }

            // ------------------------------------INPUT EVENTS------------------------------------
            for (AdvancedGraphDocument.Node node : program.triggerNodes(eventId)) {
                followExec(program, cache, node.id(), "exec", operations);
            }

            for (AdvancedGraphDocument.Node node : program.keyNodesForEvent(eventId, "key:")) {
                cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("pressed",
                        AdvancedGraphDocument.Value.bool(eventId.endsWith(":pressed")));
                followExec(program, cache, node.id(), "exec", operations);
            }

            for (AdvancedGraphDocument.Node node : program.mouseNodesForEvent(eventId, "mouse:")) {
                String input = mouseInput(node);
                Map<String, AdvancedGraphDocument.Value> nodeValues =
                        cache.computeIfAbsent(node.id(), ignored -> new HashMap<>());
                nodeValues.put("value", AdvancedGraphDocument.Value.number(controller.getMouseInputValue(input)));
                nodeValues.put("active", AdvancedGraphDocument.Value.bool(controller.isMouseInputActive(input)));
                followExec(program, cache, node.id(), "exec", operations);
            }

            for (AdvancedGraphDocument.Node node : program.inputNodesForEvent(eventId, "input:")) {
                if (inputPulseMatches(node, eventId)) {
                    followExec(program, cache, node.id(), "exec", operations);
                }
            }

            for (AdvancedGraphDocument.Node node : program.channelChangeNodesForEvent(eventId, "channel:")) {
                double val = controller.getGraphBindingValue(bindingId(node));
                cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("value",
                        AdvancedGraphDocument.Value.number(val));
                followExec(program, cache, node.id(), "exec", operations);
            }
            for (AdvancedGraphDocument.Node node : program.redstoneChangeNodesForEvent(eventId, "channel:")) {
                double val = Math.round(controller.getGraphBindingValue(bindingId(node)) * 15.0);
                cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("value",
                        AdvancedGraphDocument.Value.number(val));
                followExec(program, cache, node.id(), "exec", operations);
            }

            // ------------------------------------STATE EVENTS------------------------------------
            if (eventId.startsWith("variable:")) {
                String variable = eventId.substring("variable:".length());
                for (AdvancedGraphDocument.Node node : program.variableChangeNodes(variable)) {
                    if (!matchesVariableEvent(node, eventId)) continue;
                    cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("value",
                            program.graph().variables().getOrDefault(node.data().getString("Variable"),
                                    AdvancedGraphDocument.Value.number(0)));
                    followExec(program, cache, node.id(), "exec", operations);
                }
            } else if ("physical_interaction".equals(eventId)) {
                for (AdvancedGraphDocument.Node node : program.physicalInteractionNodes()) {
                    followExec(program, cache, node.id(), "exec", operations);
                }
            } else if (eventId.startsWith("ship_complete:")) {
                AdvancedGraphDocument.Node node = program.node(
                        eventId.substring("ship_complete:".length()));
                if (node != null) {
                    cache.computeIfAbsent(node.id(), ignored -> new HashMap<>())
                            .put("success", AdvancedGraphDocument.Value.bool(true));
                    followExec(program, cache, node.id(), "complete", operations);
                }
            } else if (eventId.startsWith("delayed:")) {
                AdvancedGraphDocument.Node node = program.node(eventId.substring("delayed:".length()));
                if (node != null) followExec(program, cache, node.id(), "exec", operations);
            } else if (eventId.startsWith("timer:")) {
                AdvancedGraphDocument.Node node = program.node(eventId.substring("timer:".length()));
                if (node != null) {
                    state.put(node.id() + ":running", AdvancedGraphDocument.Value.bool(false));
                    state.put(node.id() + ":elapsed", state.getOrDefault(node.id() + ":duration", AdvancedGraphDocument.Value.number(0)));
                    followExec(program, cache, node.id(), "complete", operations);
                }
            } else if (eventId.startsWith("curve:")) {
                String nodeId = eventId.substring("curve:".length());
                AdvancedGraphDocument.Node node = program.node(nodeId);
                if (node != null && "curve".equals(node.type())) {
                    executeNode(program, cache, node, "exec", operations);
                } else {
                    activeCurveSweeps.remove(nodeId);
                }
            } else if (eventId.startsWith("reset:")) {
                AdvancedGraphDocument.Node node = program.node(eventId.substring("reset:".length()));
                if (node != null) executeNode(program, cache, node, "reset", operations);
            }
            // ------------------------------------OUTPUT COMMIT------------------------------------
            if (pendingReset || !pendingOutputs.isEmpty()) {
                controller.setGraphBindingValues(pendingOutputs, pendingReset);
                AdvancedGraphOutputDelta.invalidateSamples(controller);
            }
        } catch (RuntimeException err) {
            diagnostics.add(new AdvancedGraphValidator.Diagnostic("error", "runtime", err.getMessage(), ""));
        } finally {
            pendingOutputs = null;
            pendingReset = false;
            currentEventId = "";
        }
    }

    // Follow the exec flow
    private void followExec(AdvancedGraphProgram program,
                            Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                            String fromNode, String fromPort, int[] operations) {
        for (AdvancedGraphDocument.Edge edge : program.outgoing(fromNode, fromPort)) {
            executionPulses.put(executionEdgeKey(edge), gameTime());
            executeNode(program, cache, program.node(edge.toNode()), edge.toPort(), operations);
        }
    }

    // Run the node
    private void executeNode(AdvancedGraphProgram program,
                             Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                             AdvancedGraphDocument.Node node, String incomingPort, int[] operations) {
        // ------------------------------------EXECUTION GUARDS------------------------------------
        requireOperation(operations);
        if (node == null) return;
        if (!activeExecutionNodes.add(node.id())) {
            diagnostics.add(new AdvancedGraphValidator.Diagnostic(
                    "error", "recursive_execution_path",
                    "Recursive execution path stopped at " + node.type(), node.id()));
            return;
        }
        try {
            // ------------------------------------SHIP COMMANDS------------------------------------
            if (AdvancedGraphCatalog.isShipControlType(node.type())
                && !AdvancedGraphCatalog.isShipControlPassiveType(node.type())) {
            Map<String, Double> parameters = new LinkedHashMap<>();
            Map<String, String> textParameters = new LinkedHashMap<>();
            AdvancedGraphCatalog.inputs(node).forEach((port, type) -> {
                if ("number".equals(type)) {
                    double val =
                            valueFor(program, cache, node, port, operations).asNumber();
                    parameters.put(port, AdvancedGraphCatalog.normalizeShipSpeedInput(
                            node, port, val, hasInput(program, node, port)));
                } else if ("boolean".equals(type)) {
                    parameters.put(port,
                            valueFor(program, cache, node, port, operations).asBoolean() ? 1.0D : 0.0D);
                } else if ("string".equals(type)) {
                    textParameters.put(
                            port, valueFor(program, cache, node, port, operations).asString());
                }
            });
            boolean accepted = controller != null
                    && controller.executeShipControlGraphCommand(
                            node.id(), node.type(), parameters, textParameters);
            boolean complete = accepted && controller != null
                    && controller.isShipControlGraphCommandComplete(
                            node.id(), node.type());
            boolean retained = "ship_follow".equals(node.type());
            boolean succeeded = retained ? accepted : complete;
            state.put(shipCommandSuccessKey(node.id()),
                    AdvancedGraphDocument.Value.bool(succeeded));
            if (accepted && !complete) {
                pendingShipCommands.add(node.id());
            } else {
                pendingShipCommands.remove(node.id());
            }
            cache.computeIfAbsent(node.id(), ignored -> new HashMap<>())
                    .put("success", AdvancedGraphDocument.Value.bool(succeeded));
            followExec(program, cache, node.id(), "exec", operations);
            if (complete) {
                followExec(program, cache, node.id(), "complete", operations);
            }
            return;
        }
            // ------------------------------------OUTPUTS / EFFECTS------------------------------------
            switch (node.type()) {
            case "reroute" -> followExec(program, cache, node.id(), "value", operations);
            case "controller_channel_output" -> {
                double val = valueFor(program, cache, node, "value", operations).asNumber();
                double clampedValue = Mth.clamp(val, 0.0, 1.0);
                pendingOutputs.put(bindingId(node), clampedValue);
                triggerConfiguredKeyOutput(bindingId(node), clampedValue);
                followExec(program, cache, node.id(), "exec", operations);
            }
            case "direct_target_output" -> {
                double val = valueFor(program, cache, node, "value", operations).asNumber();
                double clampedValue = Mth.clamp(val, 0.0, 1.0);
                String binding = bindingId(node);
                if (!binding.isBlank()) {
                    pendingOutputs.put(binding, clampedValue);
                }
                Set<String> activePorts = new LinkedHashSet<>();
                boolean hasValueInput = false;
                for (AdvancedGraphDocument.Edge edge : program.inputs(node.id())) {
                    if ("value".equals(edge.toPort())) {
                        hasValueInput = true;
                        continue;
                    }
                    if (directTargetDataPort(AdvancedGraphCatalog.inputs(node), edge.toPort())) {
                        activePorts.add(edge.toPort());
                    }
                }
                CompoundTag prefilledInputs = node.data().getCompound("PrefilledInputs");
                for (String port : node.data().getCompound("Defaults").getAllKeys()) {
                    if (!prefilledInputs.contains(port)
                            && directTargetDataPort(AdvancedGraphCatalog.inputs(node), port)) {
                        activePorts.add(port);
                    }
                }
                if (hasValueInput || activePorts.isEmpty()) {
                    activePorts.add("direct_signal");
                }
                Map<String, AdvancedGraphDocument.Value> desiredValues = new LinkedHashMap<>();
                for (String port : activePorts) {
                    desiredValues.put(port, "direct_signal".equals(port)
                            ? AdvancedGraphDocument.Value.number(clampedValue)
                            : valueFor(program, cache, node, port, operations));
                }
                desiredValues.put(AdvancedContraptionControllerBlockEntity.GRAPH_RAW_DIRECT_SIGNAL_PORT,
                        AdvancedGraphDocument.Value.number(val));
                Set<String> changedPorts = AdvancedGraphOutputDelta.changedPorts(controller, node, desiredValues);
                if (!changedPorts.isEmpty()) {
                    if (controller.setGraphTargetData(node, changedPorts, desiredValues::get)) {
                        AdvancedGraphOutputDelta.recordApplied(controller, node, changedPorts, desiredValues);
                    }
                }
                followExec(program, cache, node.id(), "exec", operations);
            }
            case "linker_face_output" -> {
                double val = valueFor(program, cache, node, "value", operations).asNumber();
                double normalizedValue = GraphSignalRange.toNormalizedRedstone(val);
                String binding = bindingId(node);
                if (!binding.isBlank()) {
                    pendingOutputs.put(binding, normalizedValue);
                }
                Set<String> activePorts = new LinkedHashSet<>();
                boolean hasValueInput = false;
                for (AdvancedGraphDocument.Edge edge : program.inputs(node.id())) {
                    if ("value".equals(edge.toPort())) {
                        hasValueInput = true;
                        continue;
                    }
                    if (directTargetDataPort(AdvancedGraphCatalog.inputs(node), edge.toPort())) {
                        activePorts.add(edge.toPort());
                    }
                }
                CompoundTag prefilledInputs = node.data().getCompound("PrefilledInputs");
                for (String port : node.data().getCompound("Defaults").getAllKeys()) {
                    if (!prefilledInputs.contains(port)
                            && directTargetDataPort(AdvancedGraphCatalog.inputs(node), port)) {
                        activePorts.add(port);
                    }
                }
                if (hasValueInput || activePorts.isEmpty()) {
                    activePorts.add("direct_signal");
                }
                Map<String, AdvancedGraphDocument.Value> desiredValues = new LinkedHashMap<>();
                for (String port : activePorts) {
                    desiredValues.put(port, "direct_signal".equals(port)
                            ? AdvancedGraphDocument.Value.number(normalizedValue)
                            : valueFor(program, cache, node, port, operations));
                }
                desiredValues.put(AdvancedContraptionControllerBlockEntity.GRAPH_RAW_DIRECT_SIGNAL_PORT,
                        AdvancedGraphDocument.Value.number(val));
                Set<String> changedPorts = AdvancedGraphOutputDelta.changedPorts(controller, node, desiredValues);
                if (!changedPorts.isEmpty()) {
                    if (controller.setGraphTargetData(node, changedPorts, desiredValues::get)) {
                        AdvancedGraphOutputDelta.recordApplied(controller, node, changedPorts, desiredValues);
                    }
                }
                followExec(program, cache, node.id(), "exec", operations);
            }
            case "local_redstone_output", "wireless_frequency_output" -> {
                double val = valueFor(program, cache, node, "value", operations).asNumber();
                pendingOutputs.put(bindingId(node), Mth.clamp(Math.round(val), 0.0, 15.0) / 15.0);
                followExec(program, cache, node.id(), "exec", operations);
            }
            case "set_block_data" -> {
                Set<String> activePorts = setDataWritePorts(node, program.inputs(node.id()));
                Map<String, AdvancedGraphDocument.Value> desiredValues = new LinkedHashMap<>();
                for (String port : activePorts) {
                    AdvancedGraphDocument.Value desired = valueFor(program, cache, node, port, operations);
                    desiredValues.put(port, desired);
                }
                Set<String> changedPorts = AdvancedGraphOutputDelta.changedPorts(controller, node, desiredValues);
                changedPorts.addAll(setDataForceWritePorts(node));
                changedPorts.addAll(CreateRotationSpeedControllerGraphCompat.portsRequiringWrite(node, activePorts));
                boolean success = changedPorts.isEmpty()
                        || controller.setGraphTargetData(node, changedPorts, desiredValues::get);
                if (success && !changedPorts.isEmpty()) {
                    AdvancedGraphOutputDelta.recordApplied(controller, node, changedPorts, desiredValues);
                }
                cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("success", AdvancedGraphDocument.Value.bool(success));
                followExec(program, cache, node.id(), "exec", operations);
            }
            case "play_sound" -> {
                if ("stop".equals(incomingPort)) {
                    controller.playGraphSound(node.id(), "", false,
                            0.0D, 0.0D, 0.0D, 0.0D, 1.0D, false, true);
                    followExec(program, cache, node.id(), "exec", operations);
                    return;
                }
                String sound = valueFor(program, cache, node, "sound", operations).asString();
                boolean world = valueFor(program, cache, node, "world", operations).asBoolean();
                double x = valueFor(program, cache, node, "x", operations).asNumber();
                double y = valueFor(program, cache, node, "y", operations).asNumber();
                double z = valueFor(program, cache, node, "z", operations).asNumber();
                double volume = valueFor(program, cache, node, "volume", operations).asNumber();
                double pitch = valueFor(program, cache, node, "pitch", operations).asNumber();
                boolean loop = valueFor(program, cache, node, "loop", operations).asBoolean();
                controller.playGraphSound(node.id(), sound, world, x, y, z, volume, pitch, loop, false);
                followExec(program, cache, node.id(), "exec", operations);
            }
            // ------------------------------------STATE / FLOW------------------------------------
            case "variable_set" -> {
                AdvancedGraphDocument.Value val = hasInput(program, node, "value")
                        ? valueFor(program, cache, node, "value", operations)
                        : valueFor(program, cache, node, "default", operations);
                program.graph().variables().put(node.data().getString("Variable"), val);
                cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("value", val);
                liveOutputs.put(node.id() + ":value", val);
                controller.markGraphRuntimeChanged(node.data().getString("Variable"));
                followExec(program, cache, node.id(), "exec", operations);
            }
            case "flip_flop" -> {
                String key = node.id() + ":value";
                AdvancedGraphDocument.Value val = AdvancedGraphDocument.Value.bool(
                        !state.getOrDefault(key, AdvancedGraphDocument.Value.bool(false)).asBoolean());
                state.put(key, val);
                cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("value", val);
                followExec(program, cache, node.id(), "exec", operations);
            }
            case "latch" -> {
                AdvancedGraphDocument.Value val = valueFor(program, cache, node, "value", operations);
                state.put(node.id() + ":value", val);
                cache.computeIfAbsent(node.id(), ignored -> new HashMap<>()).put("value", val);
                followExec(program, cache, node.id(), "exec", operations);
            }
            case "gate" -> {
                if (valueFor(program, cache, node, "open", operations).asBoolean()) {
                    followExec(program, cache, node.id(), "exec", operations);
                }
            }
            case "switch" -> {
                if (!AdvancedGraphCatalog.switchDataMode(node)) {
                    int selector = (int) Math.floor(valueFor(program, cache, node, "selector", operations).asNumber());
                    String output = AdvancedGraphCatalog.outputs(node).containsKey("case_" + selector) ? "case_" + selector : "default";
                    followExec(program, cache, node.id(), output, operations);
                }
            }
            case "do_once" -> {
                String key = node.id() + ":done";
                boolean legacyResetPulse = "reset".equals(incomingPort);
                boolean reset = legacyResetPulse
                        || valueFor(program, cache, node, "reset", operations).asBoolean();
                if (reset) {
                    state.put(key, AdvancedGraphDocument.Value.bool(false));
                }
                if (!legacyResetPulse
                        && !state.getOrDefault(key, AdvancedGraphDocument.Value.bool(false)).asBoolean()) {
                    state.put(key, AdvancedGraphDocument.Value.bool(true));
                    followExec(program, cache, node.id(), "exec", operations);
                }
            }
            case "do_n" -> {
                String key = node.id() + ":count";
                if ("reset".equals(incomingPort)) {
                    state.put(key, AdvancedGraphDocument.Value.number(0));
                    state.put(node.id() + ":index", AdvancedGraphDocument.Value.number(0));
                } else {
                    int limit = Mth.clamp((int) valueFor(program, cache, node, "count", operations).asNumber(),
                            0, MAX_LOOP_ITERATIONS);
                    int count = (int) state.getOrDefault(key, AdvancedGraphDocument.Value.number(0)).asNumber();
                    if (count < limit) {
                        state.put(node.id() + ":index", AdvancedGraphDocument.Value.number(count));
                        state.put(key, AdvancedGraphDocument.Value.number(count + 1));
                        followExec(program, cache, node.id(), "exec", operations);
                    }
                }
            }
            // ------------------------------------TIMING / EXECUTION------------------------------------
            case "timer" -> {
                scheduled.removeIf(evt -> evt.eventId().equals("timer:" + node.id()));
                if ("stop".equals(incomingPort)) {
                    state.put(node.id() + ":elapsed", AdvancedGraphDocument.Value.number(timerElapsed(node)));
                    state.put(node.id() + ":running", AdvancedGraphDocument.Value.bool(false));
                } else {
                    int duration = Math.max(1, (int) valueFor(program, cache, node, "duration", operations).asNumber());
                    state.put(node.id() + ":running", AdvancedGraphDocument.Value.bool(true));
                    state.put(node.id() + ":start", AdvancedGraphDocument.Value.number(gameTime()));
                    state.put(node.id() + ":duration", AdvancedGraphDocument.Value.number(duration));
                    schedule(node.id(), duration, "timer:");
                }
            }
            case "parallel_execution" -> followExecutionOutputs(program, cache, node, operations, false);
            case "sequenced_execution" -> followExecutionOutputs(program, cache, node, operations, true);
            case "exec_combine" -> followExec(program, cache, node.id(), "exec", operations);
            case "curve" -> executeCurvePulse(program, cache, node, operations);
            case "lerp", "ramp", "smoothing" -> {
                boolean activePulse = !currentEventId.endsWith(":inactive")
                        && !currentEventId.endsWith(":released") && !currentEventId.startsWith("reset:");
                if (activePulse) {
                    state.put(node.id() + ":last_pulse", AdvancedGraphDocument.Value.number(gameTime()));
                } else if (currentEventId.startsWith("reset:")) {
                    state.put(node.id() + ":last_pulse", AdvancedGraphDocument.Value.number(Long.MIN_VALUE));
                    if ("smoothing".equals(node.type())) {
                        state.put(node.id() + ":smooth", AdvancedGraphDocument.Value.number(0));
                    }
                } else {
                    scheduleReset(node);
                }
                followExec(program, cache, node.id(), "exec", operations);
                if (currentEventId.startsWith("reset:") && "ramp".equals(node.type())
                        && Math.abs(state.getOrDefault(node.id() + ":ramp", AdvancedGraphDocument.Value.number(0)).asNumber()) > 0.0001) {
                    schedule(node.id(), 1, "reset:");
                }
            }
            // ------------------------------------BRANCHING / SCHEDULING------------------------------------
            case "branch" -> {
                String selectedPort = valueFor(program, cache, node, "condition", operations).asBoolean()
                        ? "true" : "false";
                program.inactiveBranchOutputBindings(node.id(), selectedPort)
                        .forEach(binding -> pendingOutputs.putIfAbsent(binding, 0.0));
                followExec(program, cache, node.id(), selectedPort, operations);
            }
            case "bounded_loop" -> {
                int iterations = Mth.clamp((int) valueFor(program, cache, node, "iterations", operations).asNumber(),
                        0, MAX_LOOP_ITERATIONS);
                for (int idx = 0; idx < iterations; idx++) {
                    state.put(node.id() + ":index", AdvancedGraphDocument.Value.number(idx));
                    followExec(program, cache, node.id(), "body", operations);
                }
                followExec(program, cache, node.id(), "complete", operations);
            }
            case "delay", "debounce" -> {
                int ticks = Math.max(1, (int) valueFor(program, cache, node, "ticks", operations).asNumber());
                if (scheduled.size() >= MAX_SCHEDULED_EVENTS) {
                    throw new IllegalStateException("Graph exceeded the 1024 scheduled event limit");
                }
                long gameTime = controller.getLevel() == null ? 0 : controller.getLevel().getGameTime();
                if ("debounce".equals(node.type())) {
                    scheduled.removeIf(evt -> evt.eventId().equals("delayed:" + node.id()));
                }
                scheduled.add(new ScheduledEvent(gameTime + ticks, "delayed:" + node.id()));
            }
            case "reset_outputs" -> {
                pendingReset = true;
                pendingOutputs.clear();
                followExec(program, cache, node.id(), "exec", operations);
            }
                default -> followExec(program, cache, node.id(), "exec", operations);
            }
        } finally {
            activeExecutionNodes.remove(node.id());
        }
    }

    // Follow the execution outputs
    private void followExecutionOutputs(AdvancedGraphProgram program,
                                        Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                                        AdvancedGraphDocument.Node node, int[] operations, boolean sequenced) {
        List<String> outputs = new ArrayList<>();
        AdvancedGraphCatalog.outputs(node).forEach((port, type) -> {
            if ("exec".equals(type)) {
                outputs.add(port);
            }
        });
        if (!sequenced) {
            for (String output : outputs) {
                followExec(program, cache, node.id(), output, operations);
            }
            return;
        }
        for (String output : outputs) {
            followExec(program, cache, node.id(), output, operations);
        }
    }

    // Get the value
    private AdvancedGraphDocument.Value valueFor(AdvancedGraphProgram program,
                                                  Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                                                  AdvancedGraphDocument.Node node, String port, int[] operations) {
        for (AdvancedGraphDocument.Edge edge : program.inputs(node.id())) {
            if (edge.toPort().equals(port)) {
                AdvancedGraphDocument.Value val = outputFor(
                        program, cache, program.node(edge.fromNode()), edge.fromPort(), operations);
                String expectedType = AdvancedGraphCatalog.inputs(node).get(port);
                AdvancedGraphDocument.Value converted = expectedType == null || "any".equals(expectedType)
                        ? val : convertValue(val, expectedType);
                liveInputs.put(node.id() + ":" + port, converted);
                return converted;
            }
        }
        CompoundTag defaults = node.data().getCompound("Defaults");
        if (defaults.contains(port)) return AdvancedGraphDocument.Value.fromTag(defaults.getCompound(port));
        if (AdvancedGraphCatalog.isShipCouplerCommandType(node.type())
                && "endpoint".equals(port)) {
            return AdvancedGraphDocument.Value.number(-1.0D);
        }
        String property = port.isEmpty() ? port : Character.toUpperCase(port.charAt(0)) + port.substring(1);
        if (node.data().contains(property, net.minecraft.nbt.Tag.TAG_DOUBLE)
                || node.data().contains(property, net.minecraft.nbt.Tag.TAG_INT)
                || node.data().contains(property, net.minecraft.nbt.Tag.TAG_FLOAT)) {
            return AdvancedGraphDocument.Value.number(node.data().getDouble(property));
        }
        if (node.data().contains(property, net.minecraft.nbt.Tag.TAG_BYTE)) {
            return AdvancedGraphDocument.Value.bool(node.data().getBoolean(property));
        }
        if (node.data().contains(property, net.minecraft.nbt.Tag.TAG_STRING)) {
            return AdvancedGraphDocument.Value.string(node.data().getString(property));
        }
        return AdvancedGraphDocument.Value.number(0);
    }

    // Get the output
    private AdvancedGraphDocument.Value outputFor(AdvancedGraphProgram program,
                                                   Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                                                   AdvancedGraphDocument.Node node, String port, int[] operations) {
        // ------------------------------------CACHE / RECURSION------------------------------------
        requireOperation(operations);
        if (node == null) return AdvancedGraphDocument.Value.number(0);
        Map<String, AdvancedGraphDocument.Value> outputs = cache.computeIfAbsent(node.id(), ignored -> new HashMap<>());
        if (outputs.containsKey(port)) return outputs.get(port);
        String outputKey = node.id() + '\u0000' + port;
        if (!activeOutputPorts.add(outputKey)) {
            diagnostics.add(new AdvancedGraphValidator.Diagnostic(
                    "error", "recursive_data_path",
                    "Recursive data path stopped at " + node.type() + "." + port, node.id()));
            return GraphRuntime.defaultValue(AdvancedGraphCatalog.outputs(node).get(port));
        }
        try {
            // ------------------------------------SHIP VALUES------------------------------------
            if (AdvancedGraphCatalog.isShipControlPassiveType(node.type())) {
                double collisionDetectionDistance = collisionDetectionDistance(
                        program, cache, node, operations);
                double collisionPollRate = collisionPollRate(
                        program, cache, node, operations);
                AdvancedGraphDocument.Value val = controller == null
                        ? defaultShipControlValue(port, collisionDetectionDistance)
                        : controller.getShipControlGraphValue(
                                port, collisionDetectionDistance, collisionPollRate);
                outputs.put(port, val);
                return val;
            }
            if (AdvancedGraphCatalog.isShipControlType(node.type())) {
                AdvancedGraphDocument.Value val;
                if ("success".equals(port)) {
                    val = state.getOrDefault(
                            shipCommandSuccessKey(node.id()),
                            AdvancedGraphDocument.Value.bool(false));
                } else if (controller != null && ("progress".equals(port)
                        || "progress_percent".equals(port))) {
                    val = controller.getShipControlGraphCommandValue(node.id(), node.type(), port);
                } else if ("progress".equals(port)) {
                    val = AdvancedGraphDocument.Value.string("");
                } else {
                    val = AdvancedGraphDocument.Value.number(0.0D);
                }
                outputs.put(port, val);
                return val;
            }
            // ------------------------------------SOURCES / TARGETS------------------------------------
            AdvancedGraphDocument.Value res = switch (node.type()) {
            case "constant_number" -> AdvancedGraphDocument.Value.number(node.data().getDouble("Value"));
            case "constant_boolean" -> AdvancedGraphDocument.Value.bool(node.data().getBoolean("Value"));
            case "constant_string" -> AdvancedGraphDocument.Value.string(node.data().getString("Value"));
            case "variable_get", "variable_set" -> program.graph().variables()
                    .getOrDefault(node.data().getString("Variable"), AdvancedGraphDocument.Value.number(0));
            case "controller_channel_input", "gamepad_input" -> {
                String bindingId = bindingId(node);
                if ("active".equals(port)) yield AdvancedGraphDocument.Value.bool(isBindingActive(bindingId));
                yield AdvancedGraphDocument.Value.number(controller.getGraphBindingValue(bindingId));
            }
            case "discovered_target_input" -> {
                if (!"value".equals(port) && !"active".equals(port)) yield controller.getGraphTargetData(node, port);
                String bindingId = bindingId(node);
                if ("active".equals(port)) yield AdvancedGraphDocument.Value.bool(isBindingActive(bindingId));
                yield AdvancedGraphDocument.Value.number(controller.getGraphBindingValue(bindingId));
            }
            case "mouse_input" -> {
                String input = mouseInput(node);
                if ("active".equals(port)) {
                    yield AdvancedGraphDocument.Value.bool(controller.isMouseInputActive(input));
                }
                yield AdvancedGraphDocument.Value.number(controller.getMouseInputValue(input));
            }
            case "linker_face_input" -> {
                if (!"value".equals(port) && !"active".equals(port)) yield controller.getGraphTargetData(node, port);
                String bindingId = bindingId(node);
                if ("active".equals(port)) yield AdvancedGraphDocument.Value.bool(isBindingActive(bindingId));
                yield AdvancedGraphDocument.Value.number(
                        GraphSignalRange.fromNormalizedRedstone(controller.getGraphBindingValue(bindingId)));
            }
            case "local_redstone_input", "wireless_frequency_input" -> {
                String bindingId = bindingId(node);
                if ("active".equals(port)) yield AdvancedGraphDocument.Value.bool(isBindingActive(bindingId));
                yield AdvancedGraphDocument.Value.number(Math.round(controller.getGraphBindingValue(bindingId) * 15.0));
            }
            case "controller_tracker" -> controller == null
                    ? defaultTrackingValue(port) : controller.getPortableTrackingValue(port);
            case "portable_tracker" -> controller == null
                    ? defaultTrackingValue(port)
                    : controller.getGogglesTrackingValue(node.data().getString("GogglesPair"), port);
            case "profiler_fps", "profiler_mspt", "profiler_tps", "profiler_frametime" ->
                    AdvancedGraphDocument.Value.number(controller.getGraphProfilerValue(node.type()));
            case "event_delta_time" -> AdvancedGraphDocument.Value.number(currentDeltaTimeSeconds);
            case "get_block_data" -> controller.getGraphTargetData(node, port);
            case "set_block_data" -> {
                if ("success".equals(port)) yield outputs.getOrDefault(port, AdvancedGraphDocument.Value.bool(false));
                yield AdvancedGraphDocument.Value.number(0);
            }
            // ------------------------------------LOGIC / MATH------------------------------------
            case "not" -> AdvancedGraphDocument.Value.bool(!valueFor(program, cache, node, "value", operations).asBoolean());
            case "and" -> AdvancedGraphDocument.Value.bool(valueFor(program, cache, node, "a", operations).asBoolean()
                    && valueFor(program, cache, node, "b", operations).asBoolean());
            case "or" -> AdvancedGraphDocument.Value.bool(valueFor(program, cache, node, "a", operations).asBoolean()
                    || valueFor(program, cache, node, "b", operations).asBoolean());
            case "compare" -> AdvancedGraphDocument.Value.bool(compare(
                    valueFor(program, cache, node, "a", operations),
                    valueFor(program, cache, node, "b", operations),
                    valueFor(program, cache, node, "operator", operations).asString()));
            case "add" -> numberBinary(program, cache, node, operations, (a, b) -> a + b);
            case "subtract" -> numberBinary(program, cache, node, operations, (a, b) -> a - b);
            case "multiply" -> numberBinary(program, cache, node, operations, (a, b) -> a * b);
            case "divide" -> numberBinary(program, cache, node, operations, (a, b) -> b == 0 ? 0 : a / b);
            case "modulo" -> numberBinary(program, cache, node, operations, (a, b) -> b == 0 ? 0 : a % b);
            case "min" -> numberBinary(program, cache, node, operations, Math::min);
            case "max" -> numberBinary(program, cache, node, operations, Math::max);
            case "average" -> AdvancedGraphDocument.Value.number(
                    (valueFor(program, cache, node, "a", operations).asNumber()
                            + valueFor(program, cache, node, "b", operations).asNumber()) / 2.0D);
            case "absolute" -> AdvancedGraphDocument.Value.number(Math.abs(valueFor(program, cache, node, "value", operations).asNumber()));
            case "sin" -> AdvancedGraphDocument.Value.number(Math.sin(valueFor(program, cache, node, "value", operations).asNumber()));
            case "cos" -> AdvancedGraphDocument.Value.number(Math.cos(valueFor(program, cache, node, "value", operations).asNumber()));
            case "math_sqrt" -> AdvancedGraphDocument.Value.number(
                    Math.sqrt(valueFor(program, cache, node, "In", operations).asNumber()));
            case "math_tan" -> AdvancedGraphDocument.Value.number(
                    Math.tan(valueFor(program, cache, node, "In", operations).asNumber()));
            case "math_acos" -> AdvancedGraphDocument.Value.number(
                    Math.acos(valueFor(program, cache, node, "In", operations).asNumber()));
            case "math_asin" -> AdvancedGraphDocument.Value.number(
                    Math.asin(valueFor(program, cache, node, "In", operations).asNumber()));
            case "math_atan" -> AdvancedGraphDocument.Value.number(
                    Math.atan(valueFor(program, cache, node, "In", operations).asNumber()));
            case "math_atan2" -> AdvancedGraphDocument.Value.number(Math.atan2(
                    valueFor(program, cache, node, "Y", operations).asNumber(),
                    valueFor(program, cache, node, "X", operations).asNumber()));
            case "math_power" -> AdvancedGraphDocument.Value.number(Math.pow(
                    valueFor(program, cache, node, "Base", operations).asNumber(),
                    valueFor(program, cache, node, "Exp", operations).asNumber()));
            case "math_exp" -> AdvancedGraphDocument.Value.number(
                    Math.exp(valueFor(program, cache, node, "In", operations).asNumber()));
            case "math_ln" -> AdvancedGraphDocument.Value.number(
                    Math.log(valueFor(program, cache, node, "In", operations).asNumber()));
            case "math_log" -> AdvancedGraphDocument.Value.number(
                    Math.log(valueFor(program, cache, node, "In", operations).asNumber())
                            / Math.log(valueFor(program, cache, node, "Base", operations).asNumber()));
            case "math_average" -> AdvancedGraphNodeOperations.movingAverage(
                    state, node.id(), valueFor(program, cache, node, "In", operations).asNumber(),
                    valueFor(program, cache, node, "Samples", operations).asNumber());
            case "math_delta" -> AdvancedGraphNodeOperations.valueDelta(
                    state, node.id(), valueFor(program, cache, node, "In", operations).asNumber());
            case "math_peak_tracker" -> AdvancedGraphNodeOperations.peak(
                    state, node.id(), valueFor(program, cache, node, "In", operations).asNumber(), port);
            case "round" -> AdvancedGraphDocument.Value.number(Math.round(valueFor(program, cache, node, "value", operations).asNumber()));
            case "floor" -> AdvancedGraphDocument.Value.number(Math.floor(valueFor(program, cache, node, "value", operations).asNumber()));
            case "ceil" -> AdvancedGraphDocument.Value.number(Math.ceil(valueFor(program, cache, node, "value", operations).asNumber()));
            // ------------------------------------DATA FLOW------------------------------------
            case "lerp" -> {
                if (!recentlyPulsed(node)) yield AdvancedGraphDocument.Value.number(0);
                double a = valueFor(program, cache, node, "a", operations).asNumber();
                double b = valueFor(program, cache, node, "b", operations).asNumber();
                double amount = valueFor(program, cache, node, "amount", operations).asNumber();
                yield AdvancedGraphDocument.Value.number(Mth.lerp(amount, a, b));
            }
            case "data_branch" -> inferBodyOverride(valueFor(program, cache, node,
                    valueFor(program, cache, node, "condition", operations).asBoolean() ? "true" : "false", operations));
            case "switch" -> {
                if (!AdvancedGraphCatalog.switchDataMode(node)) {
                    yield AdvancedGraphDocument.Value.number(0);
                }
                int selector = (int) Math.floor(valueFor(program, cache, node, "selector", operations).asNumber());
                String selected = AdvancedGraphCatalog.inputs(node).containsKey("case_" + selector)
                        ? "case_" + selector : "default";
                yield "value".equals(port)
                        ? inferBodyOverride(valueFor(
                                program, cache, node, selected, operations))
                        : AdvancedGraphDocument.Value.number(0);
            }
            case "reroute", "event_value_change" -> valueFor(program, cache, node, "value", operations);
            case "edge_detector" -> edgeDetector(program, cache, node, port, operations);
            // ------------------------------------COLLECTIONS------------------------------------
            case "list_create" -> {
                CompoundTag values = new CompoundTag();
                int idx = 0;
                for (Map.Entry<String, String> input : AdvancedGraphCatalog.inputs(node).entrySet()) {
                    if ("exec".equals(input.getValue())) {
                        continue;
                    }
                    values.put(Integer.toString(idx++),
                            valueFor(program, cache, node, input.getKey(), operations).toTag());
                }
                yield AdvancedGraphDocument.Value.list(values);
            }
            case "list_get" -> {
                AdvancedGraphDocument.Value list = valueFor(program, cache, node, "list", operations);
                yield GraphRuntime.listValue(list,
                        valueFor(program, cache, node, "index", operations).asNumber());
            }
            case "arr_get" -> AdvancedGraphNodeOperations.get(
                    valueFor(program, cache, node, "Arr", operations),
                    valueFor(program, cache, node, "Index", operations).asNumber());
            case "arr_shuffle" -> AdvancedGraphNodeOperations.shuffle(
                    valueFor(program, cache, node, "Arr", operations));
            case "arr_sort" -> AdvancedGraphNodeOperations.sort(
                    valueFor(program, cache, node, "Arr", operations), false);
            case "arr_slice" -> AdvancedGraphNodeOperations.slice(
                    valueFor(program, cache, node, "Arr", operations),
                    valueFor(program, cache, node, "Start", operations).asNumber(),
                    valueFor(program, cache, node, "End", operations).asNumber());
            case "arr_append_arr" -> AdvancedGraphNodeOperations.append(
                    valueFor(program, cache, node, "Source", operations),
                    valueFor(program, cache, node, "Append", operations));
            case "collection_merge" -> AdvancedGraphNodeOperations.merge(
                    valueFor(program, cache, node, "A", operations),
                    valueFor(program, cache, node, "B", operations));
            case "arr_add" -> AdvancedGraphNodeOperations.add(
                    valueFor(program, cache, node, "Arr", operations),
                    valueFor(program, cache, node, "Item", operations), false);
            case "arr_add_unique" -> AdvancedGraphNodeOperations.add(
                    valueFor(program, cache, node, "Arr", operations),
                    valueFor(program, cache, node, "Item", operations), true);
            case "arr_insert" -> AdvancedGraphNodeOperations.insert(
                    valueFor(program, cache, node, "Arr", operations),
                    valueFor(program, cache, node, "Item", operations),
                    valueFor(program, cache, node, "Index", operations).asNumber());
            case "arr_remove" -> AdvancedGraphNodeOperations.remove(
                    valueFor(program, cache, node, "Arr", operations),
                    valueFor(program, cache, node, "Index", operations).asNumber());
            case "arr_clear" -> AdvancedGraphNodeOperations.slice(
                    valueFor(program, cache, node, "Arr", operations), 0.0D, 0.0D);
            case "arr_find" -> AdvancedGraphDocument.Value.number(AdvancedGraphNodeOperations.find(
                    valueFor(program, cache, node, "Arr", operations),
                    valueFor(program, cache, node, "Item", operations)));
            case "arr_contains" -> AdvancedGraphDocument.Value.bool(AdvancedGraphNodeOperations.find(
                    valueFor(program, cache, node, "Arr", operations),
                    valueFor(program, cache, node, "Item", operations)) >= 0);
            case "arr_length" -> AdvancedGraphDocument.Value.number(
                    AdvancedGraphNodeOperations.length(valueFor(program, cache, node, "Arr", operations)));
            case "arr_last_index" -> AdvancedGraphDocument.Value.number(
                    AdvancedGraphNodeOperations.length(
                            valueFor(program, cache, node, "Arr", operations)) - 1);
            case "arr_sort_desc" -> AdvancedGraphNodeOperations.sort(
                    valueFor(program, cache, node, "Arr", operations), true);
            case "arr_filter" -> AdvancedGraphNodeOperations.filter(
                    valueFor(program, cache, node, "Array", operations),
                    valueFor(program, cache, node, "Key", operations).asString(),
                    valueFor(program, cache, node, "Value", operations),
                    valueFor(program, cache, node, "Operation", operations).asString());
            case "map_create" -> {
                if (AdvancedGraphCatalog.isDynamicConstructor(node)) {
                    CompoundTag values = new CompoundTag();
                    for (Map.Entry<String, String> input : AdvancedGraphCatalog.inputs(node).entrySet()) {
                        if ("exec".equals(input.getValue())) {
                            continue;
                        }
                        String key = AdvancedGraphCatalog.constructorInputLabel(node, input.getKey());
                        values.put(key, valueFor(program, cache, node, input.getKey(), operations).toTag());
                    }
                    yield AdvancedGraphDocument.Value.map(values);
                }
                CompoundTag values = new CompoundTag();
                values.put(valueFor(program, cache, node, "key", operations).asString(),
                        valueFor(program, cache, node, "value", operations).toTag());
                yield AdvancedGraphDocument.Value.map(values);
            }
            case "map_get" -> dataValue(
                    valueFor(program, cache, node, "map", operations).payload(),
                    valueFor(program, cache, node, "key", operations).asString());
            case "split_list", "json_split", "break_out" -> GraphRuntime.structuredValue(
                    valueFor(program, cache, node, "value", operations), port);
            // ------------------------------------TEXT / CONVERSION------------------------------------
            case "string_concat" -> AdvancedGraphDocument.Value.string(
                    valueText(valueFor(program, cache, node, "a", operations))
                            + valueText(valueFor(program, cache, node, "b", operations)));
            case "split_string" -> GraphRuntime.splitString(
                    valueFor(program, cache, node, "string", operations).asString(),
                    valueFor(program, cache, node, "delimiter", operations).asString());
            case "str_split" -> AdvancedGraphNodeOperations.split(
                    valueFor(program, cache, node, "In", operations).asString(),
                    valueFor(program, cache, node, "Split On", operations).asString());
            case "str_reverse" -> AdvancedGraphDocument.Value.string(
                    new StringBuilder(valueFor(program, cache, node, "In", operations).asString())
                            .reverse().toString());
            case "str_append" -> AdvancedGraphDocument.Value.string(
                    valueFor(program, cache, node, "Source", operations).asString()
                            + valueFor(program, cache, node, "Separator", operations).asString()
                            + valueFor(program, cache, node, "Text", operations).asString());
            case "str_prepend" -> AdvancedGraphDocument.Value.string(
                    valueFor(program, cache, node, "Text", operations).asString()
                            + valueFor(program, cache, node, "Separator", operations).asString()
                            + valueFor(program, cache, node, "Source", operations).asString());
            case "str_trim" -> AdvancedGraphDocument.Value.string(
                    valueFor(program, cache, node, "In", operations).asString().strip());
            case "str_replace" -> AdvancedGraphDocument.Value.string(
                    AdvancedGraphNodeOperations.replace(
                            valueFor(program, cache, node, "In", operations).asString(),
                            valueFor(program, cache, node, "Find", operations).asString(),
                            valueFor(program, cache, node, "Replace", operations).asString()));
            case "str_length" -> {
                String val = valueFor(program, cache, node, "In", operations).asString();
                yield AdvancedGraphDocument.Value.number(val.codePointCount(0, val.length()));
            }
            case "str_contains" -> AdvancedGraphDocument.Value.bool(
                    valueFor(program, cache, node, "In", operations).asString()
                            .contains(valueFor(program, cache, node, "Search", operations).asString()));
            case "str_starts_with" -> AdvancedGraphDocument.Value.bool(
                    valueFor(program, cache, node, "In", operations).asString()
                            .startsWith(valueFor(program, cache, node, "Prefix", operations).asString()));
            case "str_ends_with" -> AdvancedGraphDocument.Value.bool(
                    valueFor(program, cache, node, "In", operations).asString()
                            .endsWith(valueFor(program, cache, node, "Suffix", operations).asString()));
            case "str_upper" -> AdvancedGraphDocument.Value.string(
                    valueFor(program, cache, node, "In", operations).asString()
                            .toUpperCase(java.util.Locale.ROOT));
            case "str_lower" -> AdvancedGraphDocument.Value.string(
                    valueFor(program, cache, node, "In", operations).asString()
                            .toLowerCase(java.util.Locale.ROOT));
            case "str_parse_array" -> AdvancedGraphNodeOperations.split(
                    valueFor(program, cache, node, "In", operations).asString(),
                    valueFor(program, cache, node, "Delimiter", operations).asString());
            case "str_join_array" -> AdvancedGraphDocument.Value.string(AdvancedGraphNodeOperations.join(
                    valueFor(program, cache, node, "Array", operations),
                    valueFor(program, cache, node, "Separator", operations).asString()));
            case "str_regex" -> AdvancedGraphNodeOperations.regex(
                    valueFor(program, cache, node, "In", operations).asString(),
                    valueFor(program, cache, node, "Pattern", operations).asString(), port);
            case "substring" -> AdvancedGraphDocument.Value.string(GraphRuntime.substring(
                    valueFor(program, cache, node, "string", operations).asString(),
                    valueFor(program, cache, node, "start_index", operations).asNumber(),
                    valueFor(program, cache, node, "end_index", operations).asNumber()));
            case "find_in_string" -> AdvancedGraphDocument.Value.number(GraphRuntime.findInString(
                    valueFor(program, cache, node, "string", operations).asString(),
                    valueFor(program, cache, node, "search", operations).asString()));
            case "convert_type" -> convertValue(
                    valueFor(program, cache, node, "value", operations),
                    node.data().getString("OutputType"));
            case "validate_number" -> {
                double val = valueFor(program, cache, node, "value", operations).asNumber();
                yield "valid".equals(port) ? AdvancedGraphDocument.Value.bool(Double.isFinite(val))
                        : AdvancedGraphDocument.Value.number(Double.isFinite(val) ? val : 0);
            }
            // ------------------------------------SIGNAL SHAPING / CONTROL------------------------------------
            case "clamp" -> AdvancedGraphDocument.Value.number(Mth.clamp(
                    valueFor(program, cache, node, "value", operations).asNumber(),
                    valueFor(program, cache, node, "min", operations).asNumber(),
                    valueFor(program, cache, node, "max", operations).asNumber()));
            case "map_range" -> {
                double val = valueFor(program, cache, node, "value", operations).asNumber();
                double inMin = valueFor(program, cache, node, "in_min", operations).asNumber();
                double inMax = valueFor(program, cache, node, "in_max", operations).asNumber();
                double outMin = valueFor(program, cache, node, "out_min", operations).asNumber();
                double outMax = valueFor(program, cache, node, "out_max", operations).asNumber();
                yield AdvancedGraphDocument.Value.number(
                        GraphRuntime.mapRange(val, inMin, inMax, outMin, outMax));
            }
            case "deadzone" -> {
                double val = valueFor(program, cache, node, "value", operations).asNumber();
                double start = valueFor(program, cache, node, "start", operations).asNumber();
                double resist = Math.abs(valueFor(program, cache, node, "resist", operations).asNumber());
                yield AdvancedGraphDocument.Value.number(
                        GraphRuntime.deadzone(val, start, resist));
            }
            case "step_response" -> {
                double target = valueFor(program, cache, node, "value", operations).asNumber();
                double step = Math.abs(valueFor(program, cache, node, "step", operations).asNumber());
                String key = node.id() + ":step";
                double prev = state.getOrDefault(key, AdvancedGraphDocument.Value.number(0)).asNumber();
                AdvancedGraphDocument.Value val = AdvancedGraphDocument.Value.number(
                        step <= 0 ? target : Mth.clamp(target, prev - step, prev + step));
                state.put(key, val);
                yield val;
            }
            case "smoothing" -> {
                double val = recentlyPulsed(node) ? valueFor(program, cache, node, "value", operations).asNumber() : 0;
                double amount = hasInput(program, node, "amount") || node.data().getCompound("Defaults").contains("amount")
                        ? valueFor(program, cache, node, "amount", operations).asNumber() : DEFAULT_SMOOTHING_AMOUNT;
                String stateKey = node.id() + ":smooth";
                double prev = state.getOrDefault(stateKey, AdvancedGraphDocument.Value.number(val)).asNumber();
                AdvancedGraphDocument.Value smoothed = AdvancedGraphDocument.Value.number(
                        GraphRuntime.smoothValue(prev, val, amount));
                state.put(stateKey, smoothed);
                yield smoothed;
            }
            case "ramp" -> {
                boolean active = recentlyPulsed(node);
                double target = active ? valueFor(program, cache, node, "value", operations).asNumber() : 0;
                double rate = Math.abs(valueFor(program, cache, node, active ? "rise" : "fall", operations).asNumber());
                String stateKey = node.id() + ":ramp";
                double prev = state.getOrDefault(stateKey, AdvancedGraphDocument.Value.number(0)).asNumber();
                AdvancedGraphDocument.Value ramped = AdvancedGraphDocument.Value.number(Mth.clamp(target, prev - rate, prev + rate));
                state.put(stateKey, ramped);
                yield ramped;
            }
            case "curve" -> {
                boolean useSweepInputs = state.getOrDefault(node.id() + ":curve_input_driven",
                        AdvancedGraphDocument.Value.bool(false)).asBoolean()
                        && state.containsKey(node.id() + ":curve_value")
                        && state.containsKey(node.id() + ":curve_min")
                        && state.containsKey(node.id() + ":curve_max");
                double minimum = useSweepInputs
                        ? state.get(node.id() + ":curve_min").asNumber()
                        : curveInput(program, cache, node, "min",
                        AdvancedGraphCurve.DEFAULT_MINIMUM, operations);
                double maximum = useSweepInputs
                        ? state.get(node.id() + ":curve_max").asNumber()
                        : curveInput(program, cache, node, "max",
                        AdvancedGraphCurve.DEFAULT_MAXIMUM, operations);
                double val = useSweepInputs
                        ? state.get(node.id() + ":curve_value").asNumber()
                        : curveInput(program, cache, node, "value",
                        AdvancedGraphCurve.DEFAULT_MAXIMUM, operations);
                yield AdvancedGraphDocument.Value.number(AdvancedGraphCurve.mapValue(node.data(),
                        state.getOrDefault(node.id() + ":curve_progress",
                                AdvancedGraphDocument.Value.number(0)).asNumber(),
                        val, minimum, maximum));
            }
            case "flip_flop", "latch" -> state.getOrDefault(node.id() + ":value", AdvancedGraphDocument.Value.bool(false));
            case "bounded_loop", "do_n" -> state.getOrDefault(node.id() + ":index", AdvancedGraphDocument.Value.number(0));
            case "timer" -> AdvancedGraphDocument.Value.number(timerElapsed(node));
            case "pid" -> AdvancedGraphDocument.Value.number(pidValue(program, cache, node, operations));
            case "lqr_controller" -> AdvancedGraphDocument.Value.number(LqrControllerMath.control(
                    valueFor(program, cache, node, "target", operations).asNumber(),
                    valueFor(program, cache, node, "actual", operations).asNumber(),
                    valueFor(program, cache, node, "gain", operations).asNumber(),
                    valueFor(program, cache, node, "feed_forward", operations).asNumber(),
                    valueFor(program, cache, node, "min", operations).asNumber(),
                    valueFor(program, cache, node, "max", operations).asNumber()));
            case "adrc" -> adrcValue(program, cache, outputs, node, port, operations);
            case "adrc_nth_order" -> adrcNthOrderValue(program, cache, outputs, node, port, operations);
            // ------------------------------------VECTORS / ROTATION------------------------------------
            case "vector_multiply" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "a", operations)).multiply(
                            AdvancedGraphMathValues.vector(valueFor(program, cache, node, "b", operations))));
            case "vector_subtract" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "a", operations)).subtract(
                            AdvancedGraphMathValues.vector(valueFor(program, cache, node, "b", operations))));
            case "vector_add" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "a", operations)).add(
                            AdvancedGraphMathValues.vector(valueFor(program, cache, node, "b", operations))));
            case "vector_invert" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "value", operations)).invert());
            case "vector_magnitude" -> AdvancedGraphDocument.Value.number(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "value", operations)).magnitude());
            case "vector_difference" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "a", operations)).difference(
                            AdvancedGraphMathValues.vector(valueFor(program, cache, node, "b", operations))));
            case "vector_distance" -> AdvancedGraphDocument.Value.number(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "a", operations)).distance(
                            AdvancedGraphMathValues.vector(valueFor(program, cache, node, "b", operations))));
            case "quaternion_to_euler" -> AdvancedGraphMathValues.vector(RotationMath.quaternionToEulerZxz(
                    AdvancedGraphMathValues.quaternion(valueFor(program, cache, node, "quaternion", operations))));
            case "quaternion_to_tait_bryan" -> AdvancedGraphMathValues.vector(RotationMath.quaternionToTaitBryanXyz(
                    AdvancedGraphMathValues.quaternion(valueFor(program, cache, node, "quaternion", operations))));
            case "euler_to_quaternion" -> AdvancedGraphMathValues.quaternion(RotationMath.eulerZxzToQuaternion(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "euler", operations))));
            case "tait_bryan_to_quaternion" -> AdvancedGraphMathValues.quaternion(RotationMath.taitBryanXyzToQuaternion(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "tait_bryan", operations))));
            case "euler_to_tait_bryan" -> AdvancedGraphMathValues.vector(RotationMath.eulerZxzToTaitBryanXyz(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "euler", operations))));
            case "tait_bryan_to_euler" -> AdvancedGraphMathValues.vector(RotationMath.taitBryanXyzToEulerZxz(
                    AdvancedGraphMathValues.vector(valueFor(program, cache, node, "tait_bryan", operations))));
            case "random", "random_int", "random_float_in_range", "random_int_in_range" -> {
                long seed = controller.getBlockPos().asLong() ^ node.id().hashCode()
                        ^ (controller.getLevel() == null ? 0 : controller.getLevel().getGameTime());
                double val = switch (node.type()) {
                    case "random_int" -> AdvancedGraphRandom.randomInt(seed,
                            valueFor(program, cache, node, "max", operations).asNumber());
                    case "random_float_in_range" -> AdvancedGraphRandom.randomFloatInRange(seed,
                            valueFor(program, cache, node, "min", operations).asNumber(),
                            valueFor(program, cache, node, "max", operations).asNumber());
                    case "random_int_in_range" -> AdvancedGraphRandom.randomIntInRange(seed,
                            valueFor(program, cache, node, "min", operations).asNumber(),
                            valueFor(program, cache, node, "max", operations).asNumber());
                    default -> AdvancedGraphRandom.randomFloat(seed);
                };
                yield AdvancedGraphDocument.Value.number(val);
            }
            default -> state.getOrDefault(node.id() + ":" + port, AdvancedGraphDocument.Value.number(0));
            };
            // ------------------------------------RESULT CACHE------------------------------------
            outputs.put(port, res);
            liveOutputs.put(node.id() + ":" + port, res);
            return res;
        } finally {
            activeOutputPorts.remove(outputKey);
        }
    }

    // Get the execution edge key
    public static String executionEdgeKey(AdvancedGraphDocument.Edge edge) {
        return edge.fromNode() + ":" + edge.fromPort() + ">" + edge.toNode() + ":" + edge.toPort();
    }

    // Get the data value
    private static AdvancedGraphDocument.Value dataValue(CompoundTag values, String key) {
        if (!values.contains(key)) return AdvancedGraphDocument.Value.number(0);
        return nbtValue(values.get(key));
    }

    // Get the split list outputs
    public static CompoundTag splitListOutputsFor(AdvancedGraphDocument.Value val) {
        CompoundTag outputs = new CompoundTag();
        splitListEntries(val).forEach((key, entry) -> outputs.putString(key, entry.type()));
        return outputs;
    }

    // Get the split list value
    private static AdvancedGraphDocument.Value splitListValue(AdvancedGraphDocument.Value val, String key) {
        return splitListEntries(val).getOrDefault(key, AdvancedGraphDocument.Value.number(0));
    }

    // Get the split list entries
    private static Map<String, AdvancedGraphDocument.Value> splitListEntries(AdvancedGraphDocument.Value val) {
        Map<String, AdvancedGraphDocument.Value> entries = new LinkedHashMap<>();
        if (val == null) return entries;
        val = inferBodyOverride(val);
        if ("map".equals(val.type()) || "list".equals(val.type())) {
            CompoundTag payload = val.payload();
            for (String key : payload.getAllKeys()) entries.put(key, dataValue(payload, key));
            return entries;
        }
        if ("string".equals(val.type())) {
            String text = val.asString();
            if (!text.isBlank()) {
                try {
                    return jsonEntries(JsonParser.parseString(text));
                } catch (RuntimeException ignored) {
                    return entries;
                }
            }
        }
        return entries;
    }

    // Get the NBT value
    private static AdvancedGraphDocument.Value nbtValue(net.minecraft.nbt.Tag val) {
        if (val == null) return AdvancedGraphDocument.Value.string("");
        if (val instanceof CompoundTag compound) {
            if (compound.contains("Type")) return AdvancedGraphDocument.Value.fromTag(compound);
            CompoundTag payload = new CompoundTag();
            for (String nestedKey : compound.getAllKeys()) {
                payload.put(nestedKey, nbtValue(compound.get(nestedKey)).toTag());
            }
            return AdvancedGraphDocument.Value.map(payload);
        }
        if (val instanceof ListTag list) {
            CompoundTag payload = new CompoundTag();
            for (int idx = 0; idx < list.size(); idx++) {
                payload.put(Integer.toString(idx), nbtValue(list.get(idx)).toTag());
            }
            return AdvancedGraphDocument.Value.list(payload);
        }
        return switch (val.getId()) {
            case net.minecraft.nbt.Tag.TAG_BYTE -> AdvancedGraphDocument.Value.bool(((net.minecraft.nbt.NumericTag) val).getAsByte() != 0);
            case net.minecraft.nbt.Tag.TAG_SHORT, net.minecraft.nbt.Tag.TAG_INT, net.minecraft.nbt.Tag.TAG_LONG,
                 net.minecraft.nbt.Tag.TAG_FLOAT, net.minecraft.nbt.Tag.TAG_DOUBLE -> AdvancedGraphDocument.Value.number(((net.minecraft.nbt.NumericTag) val).getAsDouble());
            case net.minecraft.nbt.Tag.TAG_STRING -> AdvancedGraphDocument.Value.string(val.getAsString());
            default -> AdvancedGraphDocument.Value.string(val.getAsString());
        };
    }

    // Get the JSON entries
    private static Map<String, AdvancedGraphDocument.Value> jsonEntries(JsonElement elm) {
        Map<String, AdvancedGraphDocument.Value> entries = new LinkedHashMap<>();
        if (elm == null || elm.isJsonNull()) return entries;
        if (elm.isJsonObject()) {
            for (var entry : elm.getAsJsonObject().entrySet()) {
                entries.put(entry.getKey(), jsonValue(entry.getValue()));
            }
        } else if (elm.isJsonArray()) {
            int idx = 0;
            for (JsonElement child : elm.getAsJsonArray()) {
                entries.put(Integer.toString(idx++), jsonValue(child));
            }
        }
        return entries;
    }

    // Get the JSON value
    private static AdvancedGraphDocument.Value jsonValue(JsonElement elm) {
        if (elm == null || elm.isJsonNull()) return AdvancedGraphDocument.Value.string("");
        if (elm.isJsonObject()) return AdvancedGraphDocument.Value.map(jsonPayload(jsonEntries(elm)));
        if (elm.isJsonArray()) return AdvancedGraphDocument.Value.list(jsonPayload(jsonEntries(elm)));
        if (elm.isJsonPrimitive()) {
            var primitive = elm.getAsJsonPrimitive();
            if (primitive.isBoolean()) return AdvancedGraphDocument.Value.bool(primitive.getAsBoolean());
            if (primitive.isNumber()) return AdvancedGraphDocument.Value.number(primitive.getAsDouble());
            return AdvancedGraphDocument.Value.string(primitive.getAsString());
        }
        return AdvancedGraphDocument.Value.string(elm.toString());
    }

    // Get the JSON payload
    private static CompoundTag jsonPayload(Map<String, AdvancedGraphDocument.Value> entries) {
        CompoundTag tag = new CompoundTag();
        entries.forEach((key, entry) -> tag.put(key, entry.toTag()));
        return tag;
    }

    // Apply the numeric binary operation
    private AdvancedGraphDocument.Value numberBinary(AdvancedGraphProgram program,
                                                      Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                                                      AdvancedGraphDocument.Node node, int[] operations,
                                                      java.util.function.DoubleBinaryOperator operator) {
        return AdvancedGraphDocument.Value.number(operator.applyAsDouble(
                valueFor(program, cache, node, "a", operations).asNumber(),
                valueFor(program, cache, node, "b", operations).asNumber()));
    }

    // Get the edge detector
    private AdvancedGraphDocument.Value edgeDetector(AdvancedGraphProgram program,
                                                      Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                                                      AdvancedGraphDocument.Node node, String port, int[] operations) {
        boolean current = valueFor(program, cache, node, "value", operations).asBoolean();
        String key = node.id() + ":edge";
        boolean prev = state.getOrDefault(key, AdvancedGraphDocument.Value.bool(current)).asBoolean();
        state.put(key, AdvancedGraphDocument.Value.bool(current));
        Map<String, AdvancedGraphDocument.Value> outputs = cache.computeIfAbsent(node.id(), ignored -> new HashMap<>());
        outputs.put("rising", AdvancedGraphDocument.Value.bool(!prev && current));
        outputs.put("falling", AdvancedGraphDocument.Value.bool(prev && !current));
        return outputs.getOrDefault(port, AdvancedGraphDocument.Value.bool(false));
    }

    // Get the PID value
    private double pidValue(AdvancedGraphProgram program,
                            Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                            AdvancedGraphDocument.Node node, int[] operations) {
        double error = valueFor(program, cache, node, "target", operations).asNumber()
                - valueFor(program, cache, node, "actual", operations).asNumber();
        String integralKey = node.id() + ":integral";
        String errorKey = node.id() + ":previous_error";
        boolean preventWindup = node.data().getBoolean(
                AdvancedGraphCatalog.PID_PREVENT_INTEGRAL_WINDUP_TAG);
        double integral = PidControllerMath.nextIntegral(
                state.getOrDefault(integralKey, AdvancedGraphDocument.Value.number(0)).asNumber(),
                error,
                preventWindup,
                preventWindup
                        ? valueFor(program, cache, node,
                        AdvancedGraphCatalog.PID_INTEGRAL_MIN_PORT, operations).asNumber()
                        : -MAX_OPERATIONS,
                preventWindup
                        ? valueFor(program, cache, node,
                        AdvancedGraphCatalog.PID_INTEGRAL_MAX_PORT, operations).asNumber()
                        : MAX_OPERATIONS,
                MAX_OPERATIONS);
        double derivative = error - state.getOrDefault(errorKey, AdvancedGraphDocument.Value.number(error)).asNumber();
        state.put(integralKey, AdvancedGraphDocument.Value.number(integral));
        state.put(errorKey, AdvancedGraphDocument.Value.number(error));
        return error * valueFor(program, cache, node, "p", operations).asNumber()
                + integral * valueFor(program, cache, node, "i", operations).asNumber()
                + derivative * valueFor(program, cache, node, "d", operations).asNumber();
    }

    // Get the ADRC value
    private AdvancedGraphDocument.Value adrcValue(AdvancedGraphProgram program,
                                                   Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                                                   Map<String, AdvancedGraphDocument.Value> outputs,
                                                   AdvancedGraphDocument.Node node, String port, int[] operations) {
        String prefix = node.id() + ":adrc:";
        AdrcControllerMath.State prev = new AdrcControllerMath.State(
                state.getOrDefault(prefix + "estimate", AdvancedGraphDocument.Value.number(
                        valueFor(program, cache, node, "actual", operations).asNumber())).asNumber(),
                state.getOrDefault(prefix + "disturbance", AdvancedGraphDocument.Value.number(0)).asNumber(),
                state.getOrDefault(prefix + "control", AdvancedGraphDocument.Value.number(0)).asNumber());
        AdrcControllerMath.Result res = AdrcControllerMath.step(prev,
                valueFor(program, cache, node, "target", operations).asNumber(),
                valueFor(program, cache, node, "actual", operations).asNumber(),
                valueFor(program, cache, node, "delta_time", operations).asNumber(),
                valueFor(program, cache, node, "controller_bandwidth", operations).asNumber(),
                valueFor(program, cache, node, "observer_bandwidth", operations).asNumber(),
                valueFor(program, cache, node, "plant_gain", operations).asNumber(),
                valueFor(program, cache, node, "output_limit", operations).asNumber());
        state.put(prefix + "estimate", AdvancedGraphDocument.Value.number(res.state().estimatedState()));
        state.put(prefix + "disturbance", AdvancedGraphDocument.Value.number(res.estimatedDisturbance()));
        state.put(prefix + "control", AdvancedGraphDocument.Value.number(res.control()));
        outputs.put("value", AdvancedGraphDocument.Value.number(res.control()));
        outputs.put("disturbance", AdvancedGraphDocument.Value.number(res.estimatedDisturbance()));
        return outputs.get(port);
    }

    // Get the nth-order ADRC value
    private AdvancedGraphDocument.Value adrcNthOrderValue(AdvancedGraphProgram program,
                                                           Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                                                           Map<String, AdvancedGraphDocument.Value> outputs,
                                                           AdvancedGraphDocument.Node node, String port, int[] operations) {
        String prefix = node.id() + ":adrc_nth_order:";
        double actual = valueFor(program, cache, node, "actual", operations).asNumber();
        int order = normalizedNthOrder(valueFor(program, cache, node, "order", operations).asNumber());
        AdrcControllerNthOrderMath.State prev = nthOrderState(state.get(prefix + "state"), order);
        if (prev == null) {
            prev = AdrcControllerNthOrderMath.State.initial(order, actual);
        }
        AdrcControllerNthOrderMath.Result res = AdrcControllerNthOrderMath.step(prev, order,
                valueFor(program, cache, node, "target", operations).asNumber(),
                actual,
                valueFor(program, cache, node, "delta_time", operations).asNumber(),
                valueFor(program, cache, node, "controller_bandwidth", operations).asNumber(),
                valueFor(program, cache, node, "observer_bandwidth", operations).asNumber(),
                valueFor(program, cache, node, "plant_gain", operations).asNumber(),
                valueFor(program, cache, node, "output_limit", operations).asNumber());
        state.put(prefix + "state", nthOrderStateValue(res.state()));
        state.put(prefix + "disturbance", AdvancedGraphDocument.Value.number(res.estimatedDisturbance()));
        state.put(prefix + "control", AdvancedGraphDocument.Value.number(res.control()));
        outputs.put("value", AdvancedGraphDocument.Value.number(res.control()));
        outputs.put("disturbance", AdvancedGraphDocument.Value.number(res.estimatedDisturbance()));
        return outputs.get(port);
    }

    // Get the normalized nth order
    private static int normalizedNthOrder(double requestedOrder) {
        if (!Double.isFinite(requestedOrder)) {
            return 1;
        }
        long rounded = Math.round(requestedOrder);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, rounded));
    }

    // Get the nth order state
    private static AdrcControllerNthOrderMath.State nthOrderState(
            AdvancedGraphDocument.Value stored, int order
    ) {
        if (stored == null || !"map".equals(stored.type())) {
            return null;
        }
        CompoundTag payload = stored.payload();
        if (!payload.contains("z", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return null;
        }
        AdvancedGraphDocument.Value zValue = AdvancedGraphDocument.Value.fromTag(payload.getCompound("z"));
        if (!"list".equals(zValue.type())) {
            return null;
        }
        CompoundTag zPayload = zValue.payload();
        int expectedLength = order + 1;
        if (zPayload.getAllKeys().size() != expectedLength) {
            return null;
        }
        double[] z = new double[expectedLength];
        for (int idx = 0; idx < expectedLength; idx++) {
            String key = Integer.toString(idx);
            if (!zPayload.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                return null;
            }
            z[idx] = AdvancedGraphDocument.Value.fromTag(zPayload.getCompound(key)).asNumber();
        }
        return new AdrcControllerNthOrderMath.State(z, payload.getDouble("previous_control"));
    }

    // Get the nth order state value
    private static AdvancedGraphDocument.Value nthOrderStateValue(AdrcControllerNthOrderMath.State state) {
        CompoundTag payload = new CompoundTag();
        CompoundTag z = new CompoundTag();
        double[] values = state.z();
        for (int idx = 0; idx < values.length; idx++) {
            z.put(Integer.toString(idx), AdvancedGraphDocument.Value.number(values[idx]).toTag());
        }
        payload.put("z", AdvancedGraphDocument.Value.list(z).toTag());
        payload.putDouble("previous_control", state.previousControl());
        return AdvancedGraphDocument.Value.map(payload);
    }

    // Get the timer elapsed
    private double timerElapsed(AdvancedGraphDocument.Node node) {
        if (!state.getOrDefault(node.id() + ":running", AdvancedGraphDocument.Value.bool(false)).asBoolean()) {
            return state.getOrDefault(node.id() + ":elapsed", AdvancedGraphDocument.Value.number(0)).asNumber();
        }
        double start = state.getOrDefault(node.id() + ":start", AdvancedGraphDocument.Value.number(gameTime())).asNumber();
        double duration = state.getOrDefault(node.id() + ":duration", AdvancedGraphDocument.Value.number(0)).asNumber();
        return Mth.clamp(gameTime() - start, 0, duration);
    }

    // Run the curve pulse
    private void executeCurvePulse(AdvancedGraphProgram program,
                                   Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                                   AdvancedGraphDocument.Node node, int[] operations) {
        String continuationEvent = "curve:" + node.id();
        boolean continuingInputSweep = continuationEvent.equals(currentEventId);
        boolean inputPulse = AdvancedGraphCurve.isInputPulse(currentEventId);
        boolean inputDriven = continuingInputSweep || inputPulse;
        boolean forward = continuingInputSweep
                ? state.getOrDefault(node.id() + ":curve_direction",
                AdvancedGraphDocument.Value.number(1)).asNumber() >= 0.0D
                : !inputPulse || AdvancedGraphCurve.isActiveInputPulse(currentEventId);
        if (inputPulse) {
            state.put(node.id() + ":curve_direction",
                    AdvancedGraphDocument.Value.number(forward ? 1.0D : -1.0D));
        }
        state.put(node.id() + ":curve_input_driven",
                AdvancedGraphDocument.Value.bool(inputDriven));
        if (inputDriven && forward) {
            state.put(node.id() + ":curve_value", AdvancedGraphDocument.Value.number(
                    curveInput(program, cache, node, "value",
                            AdvancedGraphCurve.DEFAULT_MAXIMUM, operations)));
            state.put(node.id() + ":curve_min", AdvancedGraphDocument.Value.number(
                    curveInput(program, cache, node, "min",
                            AdvancedGraphCurve.DEFAULT_MINIMUM, operations)));
            state.put(node.id() + ":curve_max", AdvancedGraphDocument.Value.number(
                    curveInput(program, cache, node, "max",
                            AdvancedGraphCurve.DEFAULT_MAXIMUM, operations)));
        }

        double progress = state.getOrDefault(node.id() + ":curve_progress",
                AdvancedGraphDocument.Value.number(0)).asNumber();
        double speed = Math.abs(curveInput(program, cache, node, "speed",
                AdvancedGraphCurve.DEFAULT_SPEED, operations));
        double nextProgress = AdvancedGraphCurve.advance(progress, speed, forward);
        state.put(node.id() + ":curve_progress", AdvancedGraphDocument.Value.number(nextProgress));

        if (inputDriven && speed > 0.0D
                && nextProgress > 0.0D && nextProgress < 1.0D) {
            activeCurveSweeps.add(node.id());
        } else if (inputDriven) {
            activeCurveSweeps.remove(node.id());
        }
        followExec(program, cache, node.id(), "exec", operations);
    }

    // Get the curve input
    private double curveInput(AdvancedGraphProgram program,
                              Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
                              AdvancedGraphDocument.Node node, String port,
                              double fallback, int[] operations) {
        return hasInput(program, node, port) || node.data().getCompound("Defaults").contains(port)
                ? valueFor(program, cache, node, port, operations).asNumber() : fallback;
    }

    // Compare the graph values
    static boolean compare(AdvancedGraphDocument.Value a, AdvancedGraphDocument.Value b, String operator) {
        String normalized = normalizeOperator(operator);
        boolean equal = sameValue(a, b);
        if (List.of("equal", "equals", "equal to", "eq", "is", "==", "===").contains(normalized)) return equal;
        if (List.of("not equal", "not equals", "not equal to", "ne", "is not", "!=", "!==", "~=").contains(normalized)) return !equal;
        int ordering;
        if ("number".equals(a.type()) && "number".equals(b.type())) ordering = Double.compare(a.asNumber(), b.asNumber());
        else if ("string".equals(a.type()) && "string".equals(b.type())) ordering = a.asString().compareTo(b.asString());
        else if ("boolean".equals(a.type()) && "boolean".equals(b.type())) ordering = Boolean.compare(a.asBoolean(), b.asBoolean());
        else {
            ordering = a.type().compareTo(b.type());
            if (ordering == 0) ordering = a.payload().toString().compareTo(b.payload().toString());
        }
        return switch (normalized) {
            case "less", "lt", "less than", "<" -> ordering < 0;
            case "less equal", "less or equal", "less than or equal", "less than or equal to", "less than or equals", "lte", "<=" -> ordering <= 0;
            case "greater", "gt", "greater than", ">" -> ordering > 0;
            case "greater equal", "greater or equal", "greater than or equal", "greater than or equal to", "greater than or equals", "gte", ">=" -> ordering >= 0;
            default -> false;
        };
    }

    // Normalize the operator
    private static String normalizeOperator(String operator) {
        if (operator == null) return "";
        String normalized = operator.trim().toLowerCase(java.util.Locale.ROOT);
        if (List.of("==", "===", "!=", "!==", "~=", "<", "<=", ">", ">=").contains(normalized)) return normalized;
        return normalized.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ");
    }

    // Check if the data port is writable
    private static boolean writableDataPort(java.util.Set<String> writablePorts, String port) {
        return writablePorts.contains(port) && !"exec".equals(port) && !"target".equals(port)
                && !"state_waterlogged".equals(port);
    }

    // Set the data write ports
    static Set<String> setDataWritePorts(
            AdvancedGraphDocument.Node node,
            Iterable<AdvancedGraphDocument.Edge> incoming
    ) {
        Set<String> activePorts = new LinkedHashSet<>();
        if (node == null) {
            return activePorts;
        }
        Set<String> writablePorts = AdvancedGraphCatalog.inputs(node).keySet();
        if (incoming != null) {
            for (AdvancedGraphDocument.Edge edge : incoming) {
                if (edge != null && writableDataPort(writablePorts, edge.toPort())) {
                    activePorts.add(edge.toPort());
                }
            }
        }
        CompoundTag prefilledInputs = node.data().getCompound("PrefilledInputs");
        for (String port : node.data().getCompound("Defaults").getAllKeys()) {
            if (!prefilledInputs.contains(port) && writableDataPort(writablePorts, port)) {
                activePorts.add(port);
            }
        }
        activePorts.addAll(setDataForceWritePorts(node));
        return activePorts;
    }

    // Set the data force write ports
    static Set<String> setDataForceWritePorts(AdvancedGraphDocument.Node node) {
        Set<String> forcedPorts = new LinkedHashSet<>();
        if (node == null) {
            return forcedPorts;
        }
        Set<String> writablePorts = AdvancedGraphCatalog.inputs(node).keySet();
        CompoundTag forceWriteInputs = node.data().getCompound("ForceWriteInputs");
        for (String port : forceWriteInputs.getAllKeys()) {
            if (forceWriteInputs.getBoolean(port) && writableDataPort(writablePorts, port)) {
                forcedPorts.add(port);
            }
        }
        forcedPorts.addAll(CreateFantasizingGraphCompat.portsRequiringWrite(node, writablePorts));
        forcedPorts.addAll(NavigationTableGraphCompat.portsRequiringWrite(node, writablePorts));
        return forcedPorts;
    }

    // Check if the port is a direct data target
    private static boolean directTargetDataPort(Map<String, String> inputTypes, String port) {
        String type = inputTypes.get(port);
        return type != null && !"exec".equals(type) && !Set.of("target", "direction").contains(type)
                && !"value".equals(port);
    }

    // Check if this uses the same value
    private static boolean sameValue(AdvancedGraphDocument.Value a, AdvancedGraphDocument.Value b) {
        return a.type().equals(b.type()) && a.payload().equals(b.payload());
    }

    // Get the value text
    private static String valueText(AdvancedGraphDocument.Value val) {
        return switch (val.type()) {
            case "boolean" -> Boolean.toString(val.asBoolean());
            case "number" -> {
                double num = val.asNumber();
                yield Double.isFinite(num) && num == Math.rint(num)
                        ? Long.toString(Math.round(num)) : Double.toString(num);
            }
            case "string", "direction" -> val.asString();
            default -> val.payload().toString();
        };
    }

    // Get the infer body override
    static AdvancedGraphDocument.Value inferBodyOverride(AdvancedGraphDocument.Value val) {
        if (val == null || !"any".equals(val.type())) return val;
        CompoundTag payload = val.payload();
        if (payload.contains("Value", net.minecraft.nbt.Tag.TAG_BYTE)) {
            return AdvancedGraphDocument.Value.bool(payload.getBoolean("Value"));
        }
        if (payload.contains("Value", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            double num = payload.getDouble("Value");
            return Double.isFinite(num) ? AdvancedGraphDocument.Value.number(num) : val;
        }
        if (!payload.contains("Value", net.minecraft.nbt.Tag.TAG_STRING)) return val;

        String raw = payload.getString("Value");
        String text = raw.trim();
        if ("true".equalsIgnoreCase(text)) return AdvancedGraphDocument.Value.bool(true);
        if ("false".equalsIgnoreCase(text)) return AdvancedGraphDocument.Value.bool(false);
        try {
            double num = Double.parseDouble(text);
            if (Double.isFinite(num)) return AdvancedGraphDocument.Value.number(num);
        } catch (NumberFormatException ignored) {
        }

        boolean mayBeJson = text.startsWith("{") || text.startsWith("[") || text.startsWith("\"");
        if (mayBeJson) {
            try {
                return jsonValue(JsonParser.parseString(text));
            } catch (RuntimeException ignored) {
            }
        }
        return AdvancedGraphDocument.Value.string(raw);
    }

    // Convert the value
    static AdvancedGraphDocument.Value convertValue(AdvancedGraphDocument.Value val, String targetType) {
        if (val == null) val = AdvancedGraphDocument.Value.string("");
        if (targetType == null || targetType.isBlank()) targetType = "string";
        if ("any".equals(targetType) || targetType.equals(val.type())) {
            return val;
        }
        return switch (targetType) {
            case "string" -> AdvancedGraphDocument.Value.string(valueText(val));
            case "number" -> AdvancedGraphDocument.Value.number(numberValue(val));
            case "boolean" -> AdvancedGraphDocument.Value.bool(booleanValue(val));
            case "direction" -> AdvancedGraphDocument.Value.direction(directionValue(val));
            case "frequency" -> AdvancedGraphDocument.Value.frequency(new CompoundTag());
            case "target" -> AdvancedGraphDocument.Value.target(new CompoundTag());
            case "list", "map" -> structuredConversion(val, targetType);
            default -> AdvancedGraphDocument.Value.string("");
        };
    }

    // Get the structured conversion
    private static AdvancedGraphDocument.Value structuredConversion(
            AdvancedGraphDocument.Value val, String targetType) {
        AdvancedGraphDocument.Value inferred = inferBodyOverride(val);
        if (targetType.equals(inferred.type())) {
            return inferred;
        }
        if ("string".equals(inferred.type())) {
            try {
                AdvancedGraphDocument.Value parsed = jsonValue(
                        JsonParser.parseString(inferred.asString().trim()));
                if (targetType.equals(parsed.type())) {
                    return parsed;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return "list".equals(targetType)
                ? AdvancedGraphDocument.Value.list(new CompoundTag())
                : AdvancedGraphDocument.Value.map(new CompoundTag());
    }

    // Create the default tracking value
    private static AdvancedGraphDocument.Value defaultTrackingValue(String port) {
        return switch (port == null ? "" : port) {
            case "available", "holding_player", "on_lectern", "is_player", "is_mannequin",
                 "is_armor_stand" -> AdvancedGraphDocument.Value.bool(false);
            case "dimension", "sub_level", "player_name", "player_uuid", "wearer_type",
                 "wearer_name", "wearer_uuid" -> AdvancedGraphDocument.Value.string("");
            case "player_facing", "looking_at" -> AdvancedGraphDocument.Value.map(new CompoundTag());
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Get the collision detection distance
    private double collisionDetectionDistance(
            AdvancedGraphProgram program,
            Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
            AdvancedGraphDocument.Node node,
            int[] operations
    ) {
        if (!"ship_telemetry".equals(node.type())) {
            return AdvancedGraphCatalog.DEFAULT_COLLISION_DETECTION_DISTANCE;
        }
        String input = "collision_detection_distance";
        double val = hasInput(program, node, input)
                || node.data().getCompound("Defaults").contains(input)
                ? valueFor(program, cache, node, input, operations).asNumber()
                : AdvancedGraphCatalog.DEFAULT_COLLISION_DETECTION_DISTANCE;
        return AdvancedGraphCatalog.normalizeCollisionDetectionDistance(val);
    }

    // Get the collision poll rate
    private double collisionPollRate(
            AdvancedGraphProgram program,
            Map<String, Map<String, AdvancedGraphDocument.Value>> cache,
            AdvancedGraphDocument.Node node,
            int[] operations
    ) {
        if (!"ship_telemetry".equals(node.type())) {
            return AdvancedGraphCatalog.DEFAULT_COLLISION_POLL_RATE;
        }
        String input = "collision_poll_rate";
        double val = hasInput(program, node, input)
                || node.data().getCompound("Defaults").contains(input)
                ? valueFor(program, cache, node, input, operations).asNumber()
                : AdvancedGraphCatalog.DEFAULT_COLLISION_POLL_RATE;
        return AdvancedGraphCatalog.normalizeCollisionPollRate(val);
    }

    // Create the default ship control value
    private static AdvancedGraphDocument.Value defaultShipControlValue(
            String port, double collisionDetectionDistance
    ) {
        return switch (port == null ? "" : port) {
            case "attached", "initialized", "initializing", "ready",
                 "any_coupled", "all_coupled" ->
                    AdvancedGraphDocument.Value.bool(false);
            case "status", "map_id", "coupler_status" ->
                    AdvancedGraphDocument.Value.string("");
            case "shipping_active", "shipping_pilot_present", "shipping_docked",
                 "shipping_waiting", "shipping_diverted", "shipping_needs_refuel",
                 "shipping_target_has_connector", "shipping_manifest_is_cyclic" ->
                    AdvancedGraphDocument.Value.bool(false);
            case "shipping_name", "shipping_schedule_title", "shipping_status",
                 "shipping_phase", "shipping_current_stop", "shipping_target_stop",
                 "shipping_next_stop", "shipping_manifest_title",
                 "shipping_manifest_current_stop", "shipping_manifest_target_stop",
                 "shipping_manifest_next_stop" -> AdvancedGraphDocument.Value.string("");
            case "shipping_manifest_stops" ->
                    AdvancedGraphDocument.Value.list(new CompoundTag());
            case "collision_scan_range" ->
                    AdvancedGraphDocument.Value.number(collisionDetectionDistance);
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Read the numeric value
    private static double numberValue(AdvancedGraphDocument.Value val) {
        if ("number".equals(val.type())) return Double.isFinite(val.asNumber()) ? val.asNumber() : 0;
        if ("boolean".equals(val.type())) return val.asBoolean() ? 1 : 0;
        if (!"string".equals(val.type())) return 0;
        try {
            double parsed = Double.parseDouble(val.asString().trim());
            return Double.isFinite(parsed) ? parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    // Resolve the boolean value
    private static boolean booleanValue(AdvancedGraphDocument.Value val) {
        if ("boolean".equals(val.type())) return val.asBoolean();
        if ("number".equals(val.type())) return Double.isFinite(val.asNumber()) && val.asNumber() != 0;
        if (!"string".equals(val.type())) return false;
        String text = val.asString().trim();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text) || text.isEmpty()) return false;
        try {
            double parsed = Double.parseDouble(text);
            return Double.isFinite(parsed) && parsed != 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // Get the direction value
    private static String directionValue(AdvancedGraphDocument.Value val) {
        if (!"string".equals(val.type()) && !"direction".equals(val.type())) return "";
        String dir = val.asString().trim().toLowerCase(java.util.Locale.ROOT);
        return List.of("north", "east", "south", "west", "up", "down").contains(dir) ? dir : "";
    }

    // Require the operation
    private static void requireOperation(int[] operations) {
        if (++operations[0] > MAX_OPERATIONS) {
            throw new IllegalStateException("Graph exceeded the 4096 operation event limit");
        }
    }

    // Get the binding id
    private static String bindingId(AdvancedGraphDocument.Node node) {
        String binding = node.data().getString("BindingId");
        if (binding.isBlank()) binding = node.data().getString("RouteBindingId");
        return binding.isBlank() ? node.data().getString("Channel") : binding;
    }

    // Check if the binding is active
    private boolean isBindingActive(String bindingId) {
        return bindingStates.getOrDefault(bindingId, controller.isGraphBindingActive(bindingId));
    }

    // Check if the input pulse matches the event
    private static boolean inputPulseMatches(
            AdvancedGraphDocument.Node node, String eventId
    ) {
        String behavior = node.data().getString("PulseBehavior")
                .trim().toLowerCase(java.util.Locale.ROOT);
        return switch (behavior) {
            case "rising", "rising_edge" -> eventId.endsWith(":active");
            case "falling", "falling_edge" -> eventId.endsWith(":inactive");
            default -> true;
        };
    }

    // Trigger the configured key output
    private void triggerConfiguredKeyOutput(String bindingId, double val) {
        if (bindingId == null || bindingId.isBlank()) {
            return;
        }
        boolean active = val > 1.0E-4D;
        if (currentEventId.startsWith("input:" + bindingId + ":")
                || currentEventId.startsWith("key:" + bindingId + ":")) {
            bindingStates.put(bindingId, active);
            return;
        }
        if (!setBindingActive(bindingId, active)) {
            enqueue("input:" + bindingId + ":" + (active ? "active" : "inactive"));
        }
        enqueue("key:" + bindingId + ":" + (active ? "pressed" : "released"));
    }

    // Get the game time
    private long gameTime() {
        return controller == null || controller.getLevel() == null ? 0 : controller.getLevel().getGameTime();
    }

    // Update the delta time seconds
    private double updateDeltaTimeSeconds(long gameTime) {
        double deltaTicks = lastGameTime == Long.MIN_VALUE ? 1.0D : Math.max(0.0D, gameTime - lastGameTime);
        lastGameTime = gameTime;
        return deltaTicks / TICKS_PER_SECOND;
    }

    // Check if the graph node pulsed recently
    private boolean recentlyPulsed(AdvancedGraphDocument.Node node) {
        long delay = Math.max(2, node.data().getInt("ResetDelay"));
        long lastPulse = (long) state.getOrDefault(node.id() + ":last_pulse",
                AdvancedGraphDocument.Value.number(Long.MIN_VALUE)).asNumber();
        return gameTime() - lastPulse <= delay;
    }

    // Schedule the reset
    private void scheduleReset(AdvancedGraphDocument.Node node) {
        int delay = Math.max(2, node.data().getInt("ResetDelay"));
        schedule(node.id(), delay, "reset:");
    }

    // Schedule the advanced graph
    private void schedule(String nodeId, int ticks, String prefix) {
        if (scheduled.size() >= MAX_SCHEDULED_EVENTS) {
            throw new IllegalStateException("Graph exceeded the 1024 scheduled event limit");
        }
        scheduled.removeIf(evt -> evt.eventId().equals(prefix + nodeId));
        scheduled.add(new ScheduledEvent(gameTime() + Math.max(1, ticks), prefix + nodeId));
    }

    // Check if this has input
    private static boolean hasInput(AdvancedGraphProgram program,
                                    AdvancedGraphDocument.Node node, String port) {
        return program.hasInput(node, port);
    }

    // Handle mouse input
    private static String mouseInput(AdvancedGraphDocument.Node node) {
        String input = AdvancedContraptionControllerBlockEntity.normalizeMouseInput(
                node.data().getString("MouseInput"));
        return input.isBlank() ? "left_click" : input;
    }

    // Get the inactive branch output bindings
    static Set<String> inactiveBranchOutputBindings(AdvancedGraphDocument graph, String branchId, String selectedPort) {
        if (graph == null) return Set.of();
        return AdvancedGraphProgram.compile(graph).inactiveBranchOutputBindings(branchId, selectedPort);
    }

    // Check if this matches variable event
    private static boolean matchesVariableEvent(AdvancedGraphDocument.Node node, String eventId) {
        String variable = node.data().getString("Variable");
        return !variable.isBlank() && eventId.equals("variable:" + variable);
    }

    // Store the scheduled event
    private record ScheduledEvent(long tick, String eventId) {
    }

}
