package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rieno.gadgetsandgizmos.compat.create.CreateFantasizingGraphCompat;
import com.rieno.gadgetsandgizmos.compat.create.CreateRotationSpeedControllerGraphCompat;
import com.rieno.gadgetsandgizmos.compat.create.NavigationTableGraphCompat;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.control.math.PidControllerMath;
import com.rieno.gadgetsandgizmos.lib.control.math.AdrcControllerMath;
import com.rieno.gadgetsandgizmos.lib.control.math.AdrcControllerNthOrderMath;
import com.rieno.gadgetsandgizmos.lib.control.math.LqrControllerMath;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.rieno.gadgetsandgizmos.lib.graph.GraphEventScheduler;
import com.rieno.gadgetsandgizmos.lib.graph.GraphApi;
import com.rieno.gadgetsandgizmos.lib.graph.GraphExecutionContext;
import com.rieno.gadgetsandgizmos.lib.graph.GraphHostServices;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeExecutor;
import com.rieno.gadgetsandgizmos.lib.graph.GraphServiceKey;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Run the live ACC graph and keep delayed work, SCM commands and output state bounded per tick
public final class GraphRuntime {
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
    private static final int PASSIVE_SAMPLE_SPREAD_TICKS = 4;
    private static final int MAX_PASSIVE_NODES_PER_TICK = 12;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Graph controller
    private final AdvancedContraptionControllerBlockEntity controller;
    // Tracks whether simulation only is set
    private final boolean simulationOnly;
    // Tracks whether lifecycle managed graph is set
    private final boolean lifecycleManagedGraph;
    // Event scheduler
    private final GraphEventScheduler<RuntimeEvent> eventScheduler =
            new GraphEventScheduler<>(MAX_EVENTS_PER_TICK, MAX_SCHEDULED_EVENTS);
    // Tracked graph state
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
    // Active curve sweeps
    private final Set<String> activeCurveSweeps = new LinkedHashSet<>();
    // Pending outputs
    private Map<String, Double> pendingOutputs;
    // Tracks whether reset is pending
    private boolean pendingReset;
    // Tracks whether graph ready is pending
    private boolean graphReadyPending;
    // Current event id
    private String currentEventId = "";
    // Current event player id
    private @Nullable UUID currentEventPlayerId;
    // Current game time
    private long currentGameTime;
    // Last game time
    private long lastGameTime = Long.MIN_VALUE;
    // Current delta time in seconds
    private double currentDeltaTimeSeconds = 1.0D / TICKS_PER_SECOND;
    // Compiled graph
    private AdvancedGraphDocument compiledGraph;
    // Compiled program
    private CompiledProgram compiledProgram = CompiledProgram.empty(new AdvancedGraphDocument());
    // Compiled revision
    private int compiledRevision = -1;
    // Compiled node count
    private int compiledNodeCount = -1;
    // Compiled edge count
    private int compiledEdgeCount = -1;
    // Compiled topology hash
    private int compiledTopologyHash;
    // Current preview frame
    private Frame previewFrame;
    // Current preview frame program
    private CompiledProgram previewFrameProgram;
    // Current function preview source
    private AdvancedGraphDocument functionPreviewSource;
    // Current function preview id
    private String functionPreviewId = "";
    // Current function preview fingerprint
    private int functionPreviewFingerprint;
    // Current function preview program
    private CompiledProgram functionPreviewProgram;
    // Current function preview frame
    private Frame functionPreviewFrame;
    // Current live value revision
    private long liveValueRevision;
    // Current passive sample cursor
    private int passiveSampleCursor;
    // Tracks whether shutdown is prepared
    private boolean shutdownPrepared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph
    public GraphRuntime(AdvancedContraptionControllerBlockEntity controller) {
        this(controller, false);
    }

    // Initialize the graph
    public GraphRuntime(AdvancedContraptionControllerBlockEntity controller, boolean simulationOnly) {
        this.controller = controller;
        this.simulationOnly = simulationOnly;
        this.lifecycleManagedGraph = controller != null && !simulationOnly;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Queue the graph
    public void enqueue(String eventId) {
        tryEnqueue(eventId, null);
    }

    // Queue the graph
    public void enqueue(String eventId, @Nullable UUID triggeringPlayerId) {
        tryEnqueue(eventId, triggeringPlayerId);
    }

    // Try to queue the graph
    public boolean tryEnqueue(String eventId, @Nullable UUID triggeringPlayerId) {
        return !shutdownPrepared && eventId != null
                && eventScheduler.enqueue(new RuntimeEvent(eventId,
                AdvancedGraphDocument.Value.number(0), triggeringPlayerId));
    }

    // Queue the current controller-session state so interaction nodes never depend on a menu opening
    public void enqueuePhysicalInteraction(ServerPlayer player, boolean active,
                                           boolean remote, String keyPressed) {
        if (shutdownPrepared || player == null) {
            return;
        }
        eventScheduler.enqueue(new RuntimeEvent("physical_interaction",
                AdvancedGraphDocument.Value.map(physicalInteractionData(
                        player, active, remote, keyPressed)), player.getUUID()));
    }

    // Queue the named controller event
    public void enqueueNamedControllerEvent(String name, AdvancedGraphDocument.Value data) {
        if (shutdownPrepared || name == null || name.isBlank()
                || eventScheduler.immediateSize() >= MAX_EVENTS_PER_TICK) {
            return;
        }
        eventScheduler.enqueue(new RuntimeEvent("named:" + name.trim(),
                data == null ? AdvancedGraphDocument.Value.number(0) : data, null));
    }

    // Queue the HUD interaction
    public boolean enqueueHudInteraction(String nodeId, String interactionId,
                                         AdvancedGraphDocument.Value val) {
        if (shutdownPrepared || nodeId == null || nodeId.isBlank()
                || interactionId == null || interactionId.isBlank()) {
            return false;
        }
        return eventScheduler.enqueue(new RuntimeEvent("hud:" + nodeId + ":" + interactionId,
                val == null ? AdvancedGraphDocument.Value.number(0) : val, null));
    }

    // Queue one momentary HUD button press and its later release
    public boolean enqueueHudButtonInteraction(String nodeId, String interactionId,
                                               long serverGameTime) {
        if (shutdownPrepared || nodeId == null || nodeId.isBlank()
                || interactionId == null || interactionId.isBlank()) {
            return false;
        }
        String eventId = "hud:" + nodeId + ":" + interactionId;
        return eventScheduler.enqueueAndSchedule(
                new RuntimeEvent(eventId, AdvancedGraphDocument.Value.bool(true), null),
                serverGameTime + 2L,
                new RuntimeEvent(eventId, AdvancedGraphDocument.Value.bool(false), null));
    }

    // Queue an authoritative HUD toggle operation
    public boolean enqueueHudToggleInteraction(String nodeId, String interactionId) {
        if (shutdownPrepared || nodeId == null || nodeId.isBlank()
                || interactionId == null || interactionId.isBlank()) {
            return false;
        }
        return eventScheduler.enqueue(new RuntimeEvent(
                "hud_toggle:" + nodeId + ":" + interactionId,
                AdvancedGraphDocument.Value.bool(true), null));
    }

    // Check if the set binding is active
    public boolean setBindingActive(String bindingId, boolean active) {
        if (shutdownPrepared || bindingId == null || bindingId.isBlank()) {
            return false;
        }
        Boolean prev = bindingStates.put(bindingId, active);
        if (prev != null && prev == active) {
            return false;
        }
        enqueue("input:" + bindingId + ":" + (active ? "active" : "inactive"));
        return true;
    }

    // Check if this has pending work
    public boolean hasPendingWork() {
        return graphReadyPending || eventScheduler.hasWork() || !activeCurveSweeps.isEmpty();
    }

    // Check if this needs regular tick
    public boolean needsRegularTick(AdvancedGraphDocument graph) {
        return needsRegularTick(graph, true);
    }

    // Check if this needs regular tick
    public boolean needsRegularTick(
            AdvancedGraphDocument graph,
            boolean samplePassiveOutputs
    ) {
        return programFor(graph).needsRegularTick(samplePassiveOutputs);
    }

    // Check if this needs simulation tick
    public boolean needsSimulationTick(AdvancedGraphDocument graph) {
        return programFor(graph).hasAutomaticEventNodes();
    }

    // Check if this needs binding polling
    public boolean needsBindingPolling(AdvancedGraphDocument graph) {
        return !programFor(graph).polledBindings().isEmpty();
    }

    // Get the polled bindings
    public Set<String> polledBindings(AdvancedGraphDocument graph) {
        return programFor(graph).polledBindings();
    }

    // Get the graph owned bindings
    public Set<String> graphOwnedBindings(AdvancedGraphDocument graph) {
        return programFor(graph).graphOwnedBindings();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the graph
    public void tick(AdvancedGraphDocument graph) {
        tick(graph, true);
    }

    // Update the graph
    public void tick(AdvancedGraphDocument graph, boolean sampleUnconnectedPassiveOutputs) {
        if (shutdownPrepared) {
            return;
        }
        invalidatePreviewFrame();
        CompiledProgram program = programFor(graph);
        long gameTime = gameTime();
        currentGameTime = gameTime;
        currentDeltaTimeSeconds = updateDeltaTimeSeconds(gameTime);
        eventScheduler.release(gameTime);
        if (graphReadyPending && eventScheduler.enqueue(new RuntimeEvent("graph_ready",
                AdvancedGraphDocument.Value.bool(true), null))) {
            graphReadyPending = false;
        }
        if (!eventScheduler.hasImmediate()) {
            for (String nodeId : activeCurveSweeps) {
                enqueue("curve:" + nodeId);
            }
        }
        if (program.hasAutomaticEventNodes()) {
            enqueue("tick");
        }
        int processed = 0;
        Frame executionFrame = eventScheduler.hasImmediate() ? new Frame(program) : null;
        while (eventScheduler.hasImmediate() && processed++ < MAX_EVENTS_PER_TICK) {
            execute(program, eventScheduler.poll(), executionFrame);
        }
        pollShipControlCommands(program);
        Frame samplingFrame = null;
        if (sampleUnconnectedPassiveOutputs) {
            samplingFrame = new Frame(program);
            updatePassiveOutputs(program, true, samplingFrame);
        }
        if (program.profilerNodes().length > 0) {
            if (samplingFrame == null) samplingFrame = new Frame(program);
            updateProfilerOutputs(program, samplingFrame);
        }
        if (sampleUnconnectedPassiveOutputs && program.needsHudInputSampling()) {
            if (samplingFrame == null) samplingFrame = new Frame(program);
            updateHudInputs(program, samplingFrame);
        }
    }

    // Compile the graph
    public void compile(AdvancedGraphDocument graph) {
        if (lifecycleManagedGraph) {
            compiledGraph = null;
        }
        programFor(graph);
    }

    // Clear the graph
    public void clear() {
        eventScheduler.clear();
        activeCurveSweeps.clear();
        state.clear();
        bindingStates.clear();
        if (!liveInputs.isEmpty() || !liveOutputs.isEmpty() || !executionPulses.isEmpty()) {
            liveValueRevision++;
        }
        liveInputs.clear();
        liveOutputs.clear();
        executionPulses.clear();
        diagnostics.clear();
        pendingOutputs = null;
        pendingReset = false;
        graphReadyPending = false;
        currentEventId = "";
        currentGameTime = 0L;
        lastGameTime = Long.MIN_VALUE;
        currentDeltaTimeSeconds = 1.0D / TICKS_PER_SECOND;
        compiledGraph = null;
        compiledProgram = CompiledProgram.empty(new AdvancedGraphDocument());
        compiledRevision = -1;
        compiledNodeCount = -1;
        compiledEdgeCount = -1;
        compiledTopologyHash = 0;
        passiveSampleCursor = 0;
        shutdownPrepared = false;
        invalidatePreviewFrame();
    }

    // Get the live value revision
    public long liveValueRevision() {
        return liveValueRevision;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                       LIFECYCLE / CACHE
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the shutdown snapshot
    public CompoundTag createShutdownSnapshot() {
        CompoundTag snapshot = new CompoundTag();
        CompoundTag runtimeState = new CompoundTag();
        state.forEach((key, val) -> runtimeState.put(key, val.toTag()));
        snapshot.put("State", runtimeState);
        CompoundTag bindings = new CompoundTag();
        bindingStates.forEach(bindings::putBoolean);
        snapshot.put("Bindings", bindings);
        snapshot.put("LiveInputs", valueMapTag(liveInputs));
        snapshot.put("LiveOutputs", valueMapTag(liveOutputs));
        CompoundTag pulses = new CompoundTag();
        executionPulses.forEach(pulses::putLong);
        snapshot.put("ExecutionPulses", pulses);
        CompoundTag curveSweeps = new CompoundTag();
        activeCurveSweeps.forEach(curve -> curveSweeps.putBoolean(curve, true));
        snapshot.put("ActiveCurveSweeps", curveSweeps);
        ListTag queuedEvents = new ListTag();
        for (RuntimeEvent evt : eventScheduler.immediateSnapshot()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", evt.id());
            entry.put("Data", evt.data().toTag());
            queuedEvents.add(entry);
        }
        snapshot.put("Events", queuedEvents);
        ListTag scheduledEvents = new ListTag();
        for (GraphEventScheduler.Scheduled<RuntimeEvent> scheduledEvent : eventScheduler.scheduledSnapshot()) {
            RuntimeEvent evt = scheduledEvent.event();
            CompoundTag entry = new CompoundTag();
            entry.putLong("Tick", scheduledEvent.tick());
            entry.putString("Id", evt.id());
            entry.put("Data", evt.data().toTag());
            scheduledEvents.add(entry);
        }
        snapshot.put("Scheduled", scheduledEvents);
        snapshot.putString("CurrentEvent", currentEventId);
        snapshot.putInt("PassiveCursor", passiveSampleCursor);
        return snapshot;
    }

    // Restore the shutdown snapshot
    public void restoreShutdownSnapshot(CompoundTag snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        shutdownPrepared = false;
        state.clear();
        CompoundTag runtimeState = snapshot.getCompound("State");
        for (String key : runtimeState.getAllKeys()) {
            if (runtimeState.contains(key, Tag.TAG_COMPOUND)) {
                state.put(key, AdvancedGraphDocument.Value.fromTag(
                        runtimeState.getCompound(key)));
            }
        }
        bindingStates.clear();
        CompoundTag bindings = snapshot.getCompound("Bindings");
        for (String key : bindings.getAllKeys()) {
            bindingStates.put(key, bindings.getBoolean(key));
        }
        liveInputs.clear();
        restoreValueMap(snapshot.getCompound("LiveInputs"), liveInputs);
        liveOutputs.clear();
        restoreValueMap(snapshot.getCompound("LiveOutputs"), liveOutputs);
        executionPulses.clear();
        CompoundTag pulses = snapshot.getCompound("ExecutionPulses");
        for (String key : pulses.getAllKeys()) {
            executionPulses.put(key, pulses.getLong(key));
        }
        activeCurveSweeps.clear();
        activeCurveSweeps.addAll(snapshot.getCompound("ActiveCurveSweeps").getAllKeys());
        liveValueRevision++;
        eventScheduler.clear();
        ListTag queuedEvents = snapshot.getList("Events", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < queuedEvents.size(); idx++) {
            CompoundTag entry = queuedEvents.getCompound(idx);
            eventScheduler.enqueue(new RuntimeEvent(entry.getString("Id"),
                    AdvancedGraphDocument.Value.fromTag(entry.getCompound("Data")), null));
        }
        ListTag scheduledEvents = snapshot.getList("Scheduled", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < scheduledEvents.size(); idx++) {
            CompoundTag entry = scheduledEvents.getCompound(idx);
            AdvancedGraphDocument.Value data = entry.contains("Data", Tag.TAG_COMPOUND)
                    ? AdvancedGraphDocument.Value.fromTag(entry.getCompound("Data"))
                    : AdvancedGraphDocument.Value.number(0);
            eventScheduler.schedule(entry.getLong("Tick"),
                    new RuntimeEvent(entry.getString("Id"), data, null));
        }
        currentEventId = snapshot.getString("CurrentEvent");
        passiveSampleCursor = Math.max(0, snapshot.getInt("PassiveCursor"));
        graphReadyPending = compiledProgram != null && compiledProgram.graphReadyNodes().length > 0;
    }

    // Get the value map tag
    private static CompoundTag valueMapTag(
            Map<String, AdvancedGraphDocument.Value> values
    ) {
        CompoundTag res = new CompoundTag();
        values.forEach((key, val) -> res.put(key, val.toTag()));
        return res;
    }

    // Restore the value map
    private static void restoreValueMap(
            CompoundTag src,
            Map<String, AdvancedGraphDocument.Value> destination
    ) {
        for (String key : src.getAllKeys()) {
            if (src.contains(key, Tag.TAG_COMPOUND)) {
                destination.put(key, AdvancedGraphDocument.Value.fromTag(
                        src.getCompound(key)));
            }
        }
    }

    // Prepare the server shutdown
    public void prepareForServerShutdown() {
        shutdownPrepared = true;
        eventScheduler.clear();
        activeCurveSweeps.clear();
        pendingOutputs = null;
        pendingReset = false;
        invalidatePreviewFrame();
    }

    // Get the diagnostics
    public List<AdvancedGraphValidator.Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    // Get the preview input
    public AdvancedGraphDocument.Value previewInput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        if (graph == null || node == null || port == null) {
            return AdvancedGraphDocument.Value.number(0);
        }
        CompiledProgram program = programForPreview(graph);
        NodeInstruction instruction = program.node(node.id());
        if (instruction == null) {
            return AdvancedGraphDocument.Value.number(0);
        }
        try {
            return previewFrameFor(program).value(instruction, port, new int[]{0});
        } catch (RuntimeException ignored) {
            return AdvancedGraphDocument.Value.number(0);
        }
    }

    // Get the preview output
    public AdvancedGraphDocument.Value previewOutput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        if (graph == null || node == null || port == null) {
            return AdvancedGraphDocument.Value.number(0);
        }
        CompiledProgram program = programForPreview(graph);
        NodeInstruction instruction = program.node(node.id());
        if (instruction == null) {
            return AdvancedGraphDocument.Value.number(0);
        }
        try {
            return previewFrameFor(program).output(instruction, port, new int[]{0});
        } catch (RuntimeException ignored) {
            return AdvancedGraphDocument.Value.number(0);
        }
    }

    // Get the preview function input
    public AdvancedGraphDocument.Value previewFunctionInput(
            AdvancedGraphDocument graph, String functionId,
            AdvancedGraphDocument.Node node, String port) {
        return previewFunctionPort(graph, functionId, node, port, false);
    }

    // Get the preview function output
    public AdvancedGraphDocument.Value previewFunctionOutput(
            AdvancedGraphDocument graph, String functionId,
            AdvancedGraphDocument.Node node, String port) {
        return previewFunctionPort(graph, functionId, node, port, true);
    }

    // Get the preview function port
    private AdvancedGraphDocument.Value previewFunctionPort(
            AdvancedGraphDocument graph, String functionId,
            AdvancedGraphDocument.Node node, String port, boolean output) {
        if (graph == null || functionId == null || functionId.isBlank()
                || node == null || port == null) {
            return AdvancedGraphDocument.Value.number(0);
        }
        CompiledProgram program = programForPreview(graph);
        Frame frame = previewFrameFor(program);
        try {
            NodeInstruction instruction = program.functionNode(functionId, node.id());
            if (instruction != null) {
                return output ? frame.output(instruction, port, new int[]{0})
                        : frame.value(instruction, port, new int[]{0});
            }
            NodeInstruction call = program.functionCall(functionId);
            if (call != null && output && AdvancedGraphFunctions.INPUT_TYPE.equals(node.type())) {
                String type = AdvancedGraphCatalog.outputs(node).get(port);
                String internalPort = "exec".equals(type)
                        ? AdvancedGraphFunctions.entryPort(port)
                        : AdvancedGraphFunctions.argumentPort(port);
                return frame.output(call, internalPort, new int[]{0});
            }
            if (call != null && !output && AdvancedGraphFunctions.OUTPUT_TYPE.equals(node.type())) {
                return frame.value(call, AdvancedGraphFunctions.returnPort(port), new int[]{0});
            }
            CompiledProgram localProgram = functionProgramForPreview(graph, functionId);
            NodeInstruction localInstruction = localProgram.node(node.id());
            if (localInstruction != null) {
                return output
                        ? functionPreviewFrame.output(localInstruction, port, new int[]{0})
                        : functionPreviewFrame.value(localInstruction, port, new int[]{0});
            }
        } catch (RuntimeException ignored) {
            return AdvancedGraphDocument.Value.number(0);
        }
        return AdvancedGraphDocument.Value.number(0);
    }

    // Begin the preview sample
    public void beginPreviewSample(AdvancedGraphDocument graph) {
        CompiledProgram program = programFor(graph);
        previewFrame = new Frame(program, false);
        previewFrameProgram = program;
    }

    // Get the live input
    public AdvancedGraphDocument.Value liveInput(String nodeId, String port) {
        return liveInputs.get(nodeId + ":" + port);
    }

    // Get the live output
    public AdvancedGraphDocument.Value liveOutput(String nodeId, String port) {
        return liveOutputs.get(nodeId + ":" + port);
    }

    // Get the execution pulse
    public long executionPulse(String edgeKey) {
        return executionPulses.getOrDefault(edgeKey, Long.MIN_VALUE);
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

    // Get the execution edge key
    public static String executionEdgeKey(AdvancedGraphDocument.Edge edge) {
        return edge.fromNode() + ":" + edge.fromPort()
                + ">" + edge.toNode() + ":" + edge.toPort();
    }

    // Get the split list outputs
    public static CompoundTag splitListOutputsFor(AdvancedGraphDocument.Value val) {
        CompoundTag outputs = new CompoundTag();
        splitListEntries(val).forEach((key, entry) -> outputs.putString(key, entry.type()));
        return outputs;
    }

    // Put the live input
    private boolean putLiveInput(String key, AdvancedGraphDocument.Value val) {
        AdvancedGraphDocument.Value prev = liveInputs.put(key, val);
        if (Objects.equals(prev, val)) {
            return false;
        }
        liveValueRevision++;
        return true;
    }

    // Put the live output
    private boolean putLiveOutput(String key, AdvancedGraphDocument.Value val) {
        AdvancedGraphDocument.Value prev = liveOutputs.put(key, val);
        if (Objects.equals(prev, val)) {
            return false;
        }
        liveValueRevision++;
        return true;
    }

    // Put the execution pulse
    private void putExecutionPulse(String key, long tick) {
        Long prev = executionPulses.put(key, tick);
        if (prev == null || prev != tick) {
            liveValueRevision++;
        }
    }

    // Get the program
    private CompiledProgram programFor(AdvancedGraphDocument graph) {
        if (graph == null) {
            graph = new AdvancedGraphDocument();
        }
        int topologyHash = lifecycleManagedGraph ? 0 : graph.programFingerprint();
        if (compiledGraph == graph
                && compiledRevision == graph.revision()
                && compiledNodeCount == graph.nodes().size()
                && compiledEdgeCount == graph.edges().size()
                && (lifecycleManagedGraph || compiledTopologyHash == topologyHash)) {
            return compiledProgram;
        }
        compiledGraph = graph;
        compiledRevision = graph.revision();
        compiledNodeCount = graph.nodes().size();
        compiledEdgeCount = graph.edges().size();
        compiledTopologyHash = topologyHash;
        invalidatePreviewFrame();
        compiledProgram = CompiledProgram.compile(
                graph, AdvancedGraphFunctions.expandForRuntime(graph));
        graphReadyPending = compiledProgram.graphReadyNodes().length > 0;
        passiveSampleCursor = 0;
        seedPersistentPortState(compiledProgram);
        return compiledProgram;
    }

    // Seed the persistent port state
    private void seedPersistentPortState(CompiledProgram program) {
        for (NodeInstruction node : program.nodes().values()) {
            for (String port : node.inputTypes().keySet()) {
                AdvancedGraphDocument.Value val =
                        AdvancedGraphPortState.persistentValue(node.source(), port, false);
                String key = node.id() + ":" + port;
                if (val != null && !liveInputs.containsKey(key)) {
                    putLiveInput(key, val);
                }
            }
            for (String port : node.outputTypes().keySet()) {
                AdvancedGraphDocument.Value val =
                        AdvancedGraphPortState.persistentValue(node.source(), port, true);
                if (val != null) {
                    state.putIfAbsent(node.id() + ":" + port, val);
                    String key = node.id() + ":" + port;
                    if (!liveOutputs.containsKey(key)) {
                        putLiveOutput(key, val);
                    }
                }
            }
        }
    }

    // Get the preview frame
    private Frame previewFrameFor(CompiledProgram program) {
        if (previewFrame == null || previewFrameProgram != program) {
            previewFrame = new Frame(program, false);
            previewFrameProgram = program;
        }
        return previewFrame;
    }

    // Get the program for preview
    private CompiledProgram programForPreview(AdvancedGraphDocument graph) {
        if (previewFrameProgram != null && previewFrameProgram.graph() == graph) {
            return previewFrameProgram;
        }
        return programFor(graph);
    }

    // Get the function program for preview
    private CompiledProgram functionProgramForPreview(
            AdvancedGraphDocument graph, String functionId) {
        int fingerprint = graph.simulationFingerprint();
        if (functionPreviewProgram != null && functionPreviewSource == graph
                && functionPreviewId.equals(functionId)
                && functionPreviewFingerprint == fingerprint) {
            return functionPreviewProgram;
        }
        AdvancedGraphDocument scoped = graph.copy();
        AdvancedGraphDocument.FunctionGraph function = scoped.function(functionId);
        scoped.nodes().clear();
        scoped.edges().clear();
        if (function != null) {
            scoped.nodes().addAll(function.nodes());
            scoped.edges().addAll(function.edges());
        }
        functionPreviewSource = graph;
        functionPreviewId = functionId;
        functionPreviewFingerprint = fingerprint;
        functionPreviewProgram = CompiledProgram.compile(
                graph, AdvancedGraphFunctions.expandForRuntime(scoped));
        functionPreviewFrame = new Frame(functionPreviewProgram, false);
        return functionPreviewProgram;
    }

    // Invalidate the preview frame
    private void invalidatePreviewFrame() {
        previewFrame = null;
        previewFrameProgram = null;
        functionPreviewSource = null;
        functionPreviewId = "";
        functionPreviewFingerprint = 0;
        functionPreviewProgram = null;
        functionPreviewFrame = null;
    }

    // Update the HUD inputs
    private void updateHudInputs(CompiledProgram program, Frame frame) {
        int[] operations = frame.resetOperations();
        boolean sampleStaticInputs = program.beginHudInputSample();
        for (NodeInstruction node : program.hudNodes()) {
            try {
                String[] ports = sampleStaticInputs
                        ? node.inputPorts() : node.connectedInputPorts();
                for (String port : ports) {
                    frame.value(node, port, operations);
                }
            } catch (RuntimeException err) {
                diagnostics.add(new AdvancedGraphValidator.Diagnostic("warning", node.id(),
                        "Could not update HUD element inputs: " + err.getMessage(), ""));
            }
        }
    }

    // Update the profiler outputs
    private void updateProfilerOutputs(CompiledProgram program, Frame frame) {
        int[] operations = frame.resetOperations();
        for (NodeInstruction node : program.profilerNodes()) {
            try {
                frame.output(node, "value", operations);
            } catch (RuntimeException err) {
                diagnostics.add(new AdvancedGraphValidator.Diagnostic("warning", "profiler_output",
                        "Could not update profiler output: " + err.getMessage(), node.id()));
            }
        }
    }

    // Update the passive outputs
    private void updatePassiveOutputs(
            CompiledProgram program, boolean sampleUnconnectedOutputs, Frame frame
    ) {
        int[] operations = frame.resetOperations();
        NodeInstruction[] passiveNodes = program.passiveNodes();
        if (passiveNodes.length == 0) {
            passiveSampleCursor = 0;
            return;
        }
        int sampleCount = Math.min(MAX_PASSIVE_NODES_PER_TICK,
                Math.max(1, (passiveNodes.length + PASSIVE_SAMPLE_SPREAD_TICKS - 1)
                        / PASSIVE_SAMPLE_SPREAD_TICKS));
        int start = Math.floorMod(passiveSampleCursor, passiveNodes.length);
        passiveSampleCursor = (start + sampleCount) % passiveNodes.length;
        for (int offset = 0; offset < sampleCount; offset++) {
            int idx = (start + offset) % passiveNodes.length;
            NodeInstruction node = passiveNodes[idx];
            if ("variable_get".equals(node.type())) {
                if (sampleUnconnectedOutputs || node.hasConnectedOutput("value")) {
                    putLiveOutput(node.id() + ":value",
                            variableValue(program, node.variable()));
                }
                continue;
            }
            for (Map.Entry<String, String> output : node.outputTypes().entrySet()) {
                if ("exec".equals(output.getValue())) {
                    continue;
                }
                if (!sampleUnconnectedOutputs && !node.hasConnectedOutput(output.getKey())) {
                    continue;
                }
                try {
                    frame.output(node, output.getKey(), operations);
                } catch (RuntimeException err) {
                    diagnostics.add(new AdvancedGraphValidator.Diagnostic("warning", "tracker_output",
                            "Could not update tracking output: " + err.getMessage(), node.id()));
                }
            }
        }
    }

    // Poll the ship control commands
    private void pollShipControlCommands(CompiledProgram program) {
        if (controller == null || program.shipCommandNodes().length == 0) {
            return;
        }
        Frame frame = new Frame(program);
        int[] operations = frame.resetOperations();
        pendingOutputs = null;
        pendingReset = false;
        try {
            for (NodeInstruction node : program.shipCommandNodes()) {

                if ("ship_initialize".equals(node.type())) {
                    for (String port : List.of("progress", "progress_percent")) {
                        AdvancedGraphDocument.Value val =
                                controller.getShipControlGraphCommandValue(node.id(), node.type(), port);
                        frame.seedOutput(node, port, val);
                    }
                }

                String pendingKey = shipCommandPendingKey(node);
                String successKey = shipCommandSuccessKey(node);
                boolean pending = state.getOrDefault(
                        pendingKey, AdvancedGraphDocument.Value.bool(false)).asBoolean();
                boolean succeeded = state.getOrDefault(
                        successKey, AdvancedGraphDocument.Value.bool(false)).asBoolean();
                if (pending && controller.isShipControlGraphCommandComplete(node.id(), node.type())) {
                    state.put(pendingKey, AdvancedGraphDocument.Value.bool(false));
                    state.put(successKey, AdvancedGraphDocument.Value.bool(true));
                    frame.seedOutput(node, "success", AdvancedGraphDocument.Value.bool(true));
                    if (!succeeded && node.hasExecOutput("complete")) {
                        followExec(frame, node, "complete", operations);
                    }
                } else if (pending
                        && !controller.isShipControlGraphCommandPending(node.id(), node.type())) {
                    state.put(pendingKey, AdvancedGraphDocument.Value.bool(false));
                    state.put(successKey, AdvancedGraphDocument.Value.bool(false));
                    frame.seedOutput(node, "success", AdvancedGraphDocument.Value.bool(false));
                } else {
                    frame.seedOutput(node, "success", AdvancedGraphDocument.Value.bool(succeeded));
                }
            }
            if (pendingReset || hasPendingOutputs()) {
                controller.setGraphBindingValues(pendingOutputsOrEmpty(), pendingReset);
                AdvancedGraphOutputDelta.invalidateSamples(controller);
            }
        } catch (RuntimeException err) {
            diagnostics.add(new AdvancedGraphValidator.Diagnostic(
                    "error", "ship_control_poll", err.getMessage(), ""));
        } finally {
            pendingOutputs = null;
            pendingReset = false;
        }
    }

    // Queue the binding output
    private void queueBindingOutput(String binding, double val) {
        if (pendingOutputs == null) {
            pendingOutputs = new LinkedHashMap<>();
        }
        pendingOutputs.put(binding, val);
    }

    // Queue the binding output if absent
    private void queueBindingOutputIfAbsent(String binding, double val) {
        if (pendingOutputs == null) {
            pendingOutputs = new LinkedHashMap<>();
        }
        pendingOutputs.putIfAbsent(binding, val);
    }

    // Check if this has pending outputs
    private boolean hasPendingOutputs() {
        return pendingOutputs != null && !pendingOutputs.isEmpty();
    }

    // Get the pending outputs or an empty map
    private Map<String, Double> pendingOutputsOrEmpty() {
        return pendingOutputs == null ? Map.of() : pendingOutputs;
    }

    // Run the ship control command
    private boolean executeShipControlCommand(
            Frame frame,
            NodeInstruction node,
            int[] operations
    ) {
        Map<String, Double> parameters = new LinkedHashMap<>();
        Map<String, String> textParameters = new LinkedHashMap<>();
        AdvancedGraphCatalog.inputs(node.source()).forEach((port, type) -> {
            if ("number".equals(type)) {
                double val = frame.value(node, port, operations).asNumber();
                parameters.put(port, AdvancedGraphCatalog.normalizeShipSpeedInput(
                        node.source(), port, val, node.hasInput(port)));
            } else if ("boolean".equals(type)) {
                parameters.put(port, frame.value(node, port, operations).asBoolean() ? 1.0D : 0.0D);
            } else if ("string".equals(type)) {
                textParameters.put(port, frame.value(node, port, operations).asString());
            }
        });
        return simulationOnly || controller != null
                && controller.executeShipControlGraphCommand(
                        currentEventPlayerId, node.id(), node.type(), parameters, textParameters);
    }

    // Run the shipping schedule command
    private boolean executeShippingScheduleCommand(NodeInstruction node) {
        return simulationOnly || controller != null
                && controller.executeShippingScheduleGraphCommand(node.type());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           EXECUTION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the graph
    private void execute(CompiledProgram program, RuntimeEvent evt, Frame frame) {
        // -----------------------------------------------------EVENT SETUP-----------------------------------------------------
        String eventId = evt.id();
        currentEventId = eventId;
        currentEventPlayerId = evt.triggeringPlayerId();
        diagnostics.clear();
        pendingOutputs = null;
        pendingReset = false;
        frame.reset();
        int[] operations = frame.resetOperations();
        try {
            // ------------------------------------TICK EVENTS------------------------------------
            if ("tick".equals(eventId)) {
                for (NodeInstruction node : program.tickNodes()) {
                    followExec(frame, node, "exec", operations);
                }
                for (NodeInstruction node : program.periodicNodes()) {
                    if (controller == null || controller.getLevel() == null
                            || controller.getLevel().getGameTime() % Math.max(1,
                            (int) frame.value(node, "period", operations).asNumber()) == 0) {
                        followExec(frame, node, "exec", operations);
                    }
                }
                for (NodeInstruction node : program.pulseOnChangeNodes()) {
                    AdvancedGraphDocument.Value val = frame.value(node, "value", operations);
                    if ("event_value_change".equals(node.type())) {
                        frame.seedOutput(node, "value", val);
                    }
                    String key = node.id() + ":observed";
                    AdvancedGraphDocument.Value prev = state.put(key, val);
                    if (prev != null && !sameValue(prev, val)) {
                        followExec(frame, node, "exec", operations);
                    }
                }
            }

            // ------------------------------------INPUT EVENTS------------------------------------
            for (NodeInstruction node : program.triggerNodes(eventId)) {
                if ("event_named_controller".equals(node.type())) {
                    state.put(namedCtrlEventStateKey(node), evt.data());
                    frame.seedOutput(node, "data", evt.data());
                }
                followExec(frame, node, "exec", operations);
            }

            for (NodeInstruction node : program.keyNodesForEvent(eventId)) {
                frame.seedOutput(node, "pressed", AdvancedGraphDocument.Value.bool(eventId.endsWith(":pressed")));
                followExec(frame, node, "exec", operations);
            }

            for (NodeInstruction node : program.mouseNodesForEvent(eventId)) {
                String input = mouseInput(node.source());
                double val = controller == null ? 0.0D : controller.getMouseInputValue(input);
                boolean active = controller != null && controller.isMouseInputActive(input);
                frame.seedOutput(node, "value", AdvancedGraphDocument.Value.number(val));
                frame.seedOutput(node, "active", AdvancedGraphDocument.Value.bool(active));
                followExec(frame, node, "exec", operations);
            }

            for (NodeInstruction node : program.inputNodesForEvent(eventId)) {
                if (inputPulseMatches(node.source(), eventId)) {
                    followExec(frame, node, "exec", operations);
                }
            }

            for (NodeInstruction node : program.wirelessInputNodesForChannelEvent(eventId)) {
                followExec(frame, node, "exec", operations);
            }

            for (NodeInstruction node : program.channelChangeNodesForEvent(eventId)) {
                double val = controller.getGraphBindingValue(node.bindingId());
                frame.seedOutput(node, "value", AdvancedGraphDocument.Value.number(val));
                followExec(frame, node, "exec", operations);
            }

            for (NodeInstruction node : program.redstoneChangeNodesForEvent(eventId)) {
                double val = Math.round(controller.getGraphBindingValue(node.bindingId()) * 15.0);
                frame.seedOutput(node, "value", AdvancedGraphDocument.Value.number(val));
                followExec(frame, node, "exec", operations);
            }

            // ------------------------------------STATE EVENTS------------------------------------
            if (eventId.startsWith("variable:")) {
                String variable = eventId.substring("variable:".length());
                for (NodeInstruction node : program.variableChangeNodes(variable)) {
                    frame.seedOutput(node, "value",
                            variableValue(program, node.variable()));
                    followExec(frame, node, "exec", operations);
                }
            } else if ("physical_interaction".equals(eventId)) {
                for (NodeInstruction node : program.physicalInteractionNodes()) {
                    seedPhysicalInteractionOutputs(frame, node, evt.data());
                    followExec(frame, node, "exec", operations);
                }
            } else if ("graph_ready".equals(eventId)) {
                for (NodeInstruction node : program.graphReadyNodes()) {
                    frame.seedOutput(node, "ready", AdvancedGraphDocument.Value.bool(true));
                    followExec(frame, node, "exec", operations);
                }
            } else if (eventId.startsWith("delayed:")) {
                NodeInstruction node = program.node(eventId.substring("delayed:".length()));
                if (node != null) {
                    followExec(frame, node, "exec", operations);
                }
            } else if (eventId.startsWith("timer:")) {
                NodeInstruction node = program.node(eventId.substring("timer:".length()));
                if (node != null) {
                    state.put(node.id() + ":running", AdvancedGraphDocument.Value.bool(false));
                    state.put(node.id() + ":elapsed",
                            state.getOrDefault(node.id() + ":duration", AdvancedGraphDocument.Value.number(0)));
                    followExec(frame, node, "complete", operations);
                }
            } else if (eventId.startsWith("curve:")) {
                String nodeId = eventId.substring("curve:".length());
                NodeInstruction node = program.node(nodeId);
                if (node != null && "curve".equals(node.type())) {
                    executeNode(frame, node, "exec", operations);
                } else {
                    activeCurveSweeps.remove(nodeId);
                }
            } else if (eventId.startsWith("reset:")) {
                NodeInstruction node = program.node(eventId.substring("reset:".length()));
                if (node != null) {
                    executeNode(frame, node, "reset", operations);
                }
            } else if (eventId.startsWith("hud:")
                    || eventId.startsWith("hud_toggle:")) {
                executeHudInteraction(program, frame, evt, operations);
            }

            // ------------------------------------OUTPUT COMMIT------------------------------------
            if (!simulationOnly && (pendingReset || hasPendingOutputs())) {
                controller.setGraphBindingValues(pendingOutputsOrEmpty(), pendingReset);
                AdvancedGraphOutputDelta.invalidateSamples(controller);
            }
        } catch (RuntimeException err) {
            diagnostics.add(new AdvancedGraphValidator.Diagnostic("error", "runtime", err.getMessage(), ""));
        } finally {
            pendingOutputs = null;
            pendingReset = false;
            currentEventId = "";
            currentEventPlayerId = null;
        }
    }

    // Seed the physical interaction outputs
    private void seedPhysicalInteractionOutputs(Frame frame, NodeInstruction node,
                                                AdvancedGraphDocument.Value eventData) {
        Map<String, String> outputs = AdvancedGraphCatalog.outputs(node.source());
        for (Map.Entry<String, String> output : outputs.entrySet()) {
            if ("exec".equals(output.getKey())) {
                continue;
            }
            AdvancedGraphDocument.Value val = physicalInteractionValue(
                    eventData, output.getKey(), output.getValue());
            state.put(physicalInteractionStateKey(node, output.getKey()), val);
            frame.seedOutput(node, output.getKey(), val);
        }
    }

    // Get the physical interaction value
    private static AdvancedGraphDocument.Value physicalInteractionValue(
            AdvancedGraphDocument.Value eventData, String port, String type
    ) {
        if (eventData == null || !"map".equals(eventData.type())) {
            return defaultValue(type);
        }
        CompoundTag encoded = eventData.payload().getCompound(port);
        return encoded.isEmpty() ? defaultValue(type) : AdvancedGraphDocument.Value.fromTag(encoded);
    }

    // Get the physical interaction state key
    private static String physicalInteractionStateKey(NodeInstruction node, String port) {
        return node.id() + ":physical_interaction:" + port;
    }

    // Get the physical interaction data
    private static CompoundTag physicalInteractionData(ServerPlayer player, boolean active,
                                                       boolean remote, String keyPressed) {
        CompoundTag values = new CompoundTag();
        String evt = active ? "enter" : "exit";
        Vec3 look = player.getLookAngle();
        CompoundTag lookDirection = new CompoundTag();
        putValue(lookDirection, "x", AdvancedGraphDocument.Value.number(look.x));
        putValue(lookDirection, "y", AdvancedGraphDocument.Value.number(look.y));
        putValue(lookDirection, "z", AdvancedGraphDocument.Value.number(look.z));
        putValue(lookDirection, "yaw", AdvancedGraphDocument.Value.number(player.getYRot()));
        putValue(lookDirection, "pitch", AdvancedGraphDocument.Value.number(player.getXRot()));

        CompoundTag playerData = new CompoundTag();
        putValue(playerData, "name", AdvancedGraphDocument.Value.string(player.getGameProfile().getName()));
        putValue(playerData, "uuid", AdvancedGraphDocument.Value.string(player.getUUID().toString()));
        putValue(playerData, "health", AdvancedGraphDocument.Value.number(player.getHealth()));
        putValue(playerData, "x", AdvancedGraphDocument.Value.number(player.getX()));
        putValue(playerData, "y", AdvancedGraphDocument.Value.number(player.getY()));
        putValue(playerData, "z", AdvancedGraphDocument.Value.number(player.getZ()));
        putValue(playerData, "sneaking", AdvancedGraphDocument.Value.bool(player.isShiftKeyDown()));
        putValue(playerData, "creative", AdvancedGraphDocument.Value.bool(player.isCreative()));
        putValue(playerData, "remote", AdvancedGraphDocument.Value.bool(remote));
        putValue(playerData, "look_direction", AdvancedGraphDocument.Value.map(lookDirection));

        putValue(values, "event", AdvancedGraphDocument.Value.string(evt));
        putValue(values, "entered", AdvancedGraphDocument.Value.bool(active));
        putValue(values, "exited", AdvancedGraphDocument.Value.bool(!active));
        putValue(values, "player", AdvancedGraphDocument.Value.map(playerData));
        putValue(values, "player_name", AdvancedGraphDocument.Value.string(player.getGameProfile().getName()));
        putValue(values, "player_uuid", AdvancedGraphDocument.Value.string(player.getUUID().toString()));
        putValue(values, "key_pressed", AdvancedGraphDocument.Value.string(
                keyPressed == null ? "" : keyPressed));
        putValue(values, "hand", AdvancedGraphDocument.Value.string("main_hand"));
        putValue(values, "look_direction", AdvancedGraphDocument.Value.map(lookDirection));
        putValue(values, "look_x", AdvancedGraphDocument.Value.number(look.x));
        putValue(values, "look_y", AdvancedGraphDocument.Value.number(look.y));
        putValue(values, "look_z", AdvancedGraphDocument.Value.number(look.z));
        putValue(values, "health", AdvancedGraphDocument.Value.number(player.getHealth()));
        putValue(values, "is_interacting", AdvancedGraphDocument.Value.bool(active));
        putValue(values, "remote", AdvancedGraphDocument.Value.bool(remote));
        putValue(values, "sneaking", AdvancedGraphDocument.Value.bool(player.isShiftKeyDown()));
        putValue(values, "creative", AdvancedGraphDocument.Value.bool(player.isCreative()));
        putValue(values, "x", AdvancedGraphDocument.Value.number(player.getX()));
        putValue(values, "y", AdvancedGraphDocument.Value.number(player.getY()));
        putValue(values, "z", AdvancedGraphDocument.Value.number(player.getZ()));
        return values;
    }

    // Put the value
    private static void putValue(CompoundTag values, String key, AdvancedGraphDocument.Value val) {
        values.put(key, val.toTag());
    }

    // Run the HUD interaction
    private void executeHudInteraction(CompiledProgram program, Frame frame,
                                       RuntimeEvent evt, int[] operations) {
        String[] parts = evt.id().split(":", 3);
        if (parts.length != 3) {
            return;
        }
        NodeInstruction node = program.node(parts[1]);
        if (node == null) {
            return;
        }
        if (!("advanced_hud_element".equals(node.type())
                || "acc_display_widget".equals(node.type())
                || "acc_hologram_widget".equals(node.type()))) {
            return;
        }
        ListTag elements = node.data().getList("WidgetElements", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < elements.size(); idx++) {
            CompoundTag elm = elements.getCompound(idx);
            if (!parts[2].equals(elm.getString("InteractionId"))) {
                continue;
            }
            String valuePort = elm.getString("ValuePort");
            String execPort = elm.getString("ExecPort");
            boolean toggleEvent = evt.id().startsWith("hud_toggle:");
            if (toggleEvent && !"toggle".equals(elm.getString("Type"))) {
                return;
            }
            AdvancedGraphDocument.Value interactionValue = evt.data();
            if (toggleEvent && !valuePort.isBlank()) {
                String stateKey = node.id() + ":hud:" + valuePort;
                AdvancedGraphDocument.Value previous = state.get(stateKey);
                if (previous == null) {
                    previous = liveOutputs.getOrDefault(
                            node.portKey(valuePort),
                            AdvancedGraphDocument.Value.bool(false));
                }
                interactionValue = AdvancedGraphDocument.Value.bool(
                        !previous.asBoolean());
            }
            if (!valuePort.isBlank()) {
                state.put(node.id() + ":hud:" + valuePort, interactionValue);
                frame.seedOutput(node, valuePort, interactionValue);
            }
            boolean buttonRelease = "button".equals(elm.getString("Type"))
                    && "boolean".equals(interactionValue.type())
                    && !interactionValue.asBoolean();
            if (!buttonRelease && !execPort.isBlank()) {
                followExec(frame, node, execPort, operations);
            }
            return;
        }
    }

    // Follow the exec flow
    private void followExec(Frame frame, NodeInstruction fromNode, String fromPort, int[] operations) {
        for (ExecEdge edge : fromNode.execTargets(fromPort)) {
            putExecutionPulse(edge.edgeKey(), currentGameTime);
            executeNode(frame, edge.target(), edge.incomingPort(), operations);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         NODE EXECUTION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the node
    private void executeNode(Frame frame, NodeInstruction node, String incomingPort, int[] operations) {
        if (node == null) {
            return;
        }
        requireOperation(operations);
        if (!frame.beginExecution(node)) {
            diagnostics.add(new AdvancedGraphValidator.Diagnostic(
                    "error", "recursive_execution_path",
                    "Recursive execution path stopped at " + node.type(), node.id()));
            return;
        }
        try {
            // ------------------------------------COMMANDS------------------------------------
            if (AdvancedGraphCatalog.isShippingScheduleControlType(node.type())) {
                boolean success = executeShippingScheduleCommand(node);
                frame.seedOutput(node, "success", AdvancedGraphDocument.Value.bool(success));
                followExec(frame, node, "exec", operations);
                return;
            }
            if (AdvancedGraphCatalog.isShipControlType(node.type())
                    && !AdvancedGraphCatalog.isShipControlPassiveType(node.type())) {
                if ("ship_initialize".equals(node.type())
                        && shipControlInitActive()
                        && state.getOrDefault(shipCommandPendingKey(node),
                        AdvancedGraphDocument.Value.bool(false)).asBoolean()) {
                    return;
                }
                boolean retained = "ship_follow".equals(node.type());
                boolean success = executeShipControlCommand(frame, node, operations);
                boolean complete = success && !simulationOnly && controller != null
                        && controller.isShipControlGraphCommandComplete(node.id(), node.type());
                state.put(shipCommandPendingKey(node),
                        AdvancedGraphDocument.Value.bool(success && !complete));
                state.put(shipCommandSuccessKey(node),
                        AdvancedGraphDocument.Value.bool(retained ? success : complete));
                frame.seedOutput(node, "success",
                        AdvancedGraphDocument.Value.bool(retained ? success : complete));
                if (shipControlInitActive()) {
                    return;
                }
                followExec(frame, node, "exec", operations);
                if (complete && node.hasExecOutput("complete")) {
                    followExec(frame, node, "complete", operations);
                }
                return;
            }
            if (AdvancedGraphFunctions.CALL_TYPE.equals(node.type())) {
                if (AdvancedGraphFunctions.isReturnPort(incomingPort)) {
                    followExec(frame, node, AdvancedGraphFunctions.externalPort(incomingPort), operations);
                } else {
                    followExec(frame, node, AdvancedGraphFunctions.entryPort(incomingPort), operations);
                }
                return;
            }
            // ------------------------------------NODE LOGIC------------------------------------
            switch (node.type()) {
            case "reroute" -> followExec(frame, node, "value", operations);
            case "send_named_controller_event" -> {
                AdvancedGraphDocument.Value data = frame.value(node, "data", operations);
                int distance = (int) Math.round(frame.value(node, "distance", operations).asNumber());
                if (!simulationOnly && controller != null) {
                    controller.publishNamedControllerEvent(node.triggerEvent().trim(), data, distance);
                }
                followExec(frame, node, "exec", operations);
            }
            case "controller_channel_output" -> {
                double val = frame.value(node, "value", operations).asNumber();
                double clampedValue = Mth.clamp(val, 0.0, 1.0);
                queueBindingOutput(node.bindingId(), clampedValue);
                triggerConfiguredKeyOutput(node.bindingId(), clampedValue);
                followExec(frame, node, "exec", operations);
            }
            case "direct_target_output" -> {
                double val = frame.value(node, "value", operations).asNumber();
                double clampedValue = Mth.clamp(val, 0.0, 1.0);
                if (!node.bindingId().isBlank()) {
                    queueBindingOutput(node.bindingId(), clampedValue);
                }
                Map<String, AdvancedGraphDocument.Value> desiredValues = new LinkedHashMap<>();
                for (String port : node.targetWritePorts()) {
                    desiredValues.put(port, "direct_signal".equals(port)
                            ? AdvancedGraphDocument.Value.number(clampedValue)
                            : frame.value(node, port, operations));
                }
                desiredValues.put(AdvancedContraptionControllerBlockEntity.GRAPH_RAW_DIRECT_SIGNAL_PORT,
                        AdvancedGraphDocument.Value.number(val));
                if (!simulationOnly) {
                    Set<String> changedPorts = AdvancedGraphOutputDelta.changedPorts(
                            controller, node.source(), desiredValues);
                    if (!changedPorts.isEmpty()) {
                        if (controller.setGraphTargetData(node.source(), changedPorts, desiredValues::get)) {
                            AdvancedGraphOutputDelta.recordApplied(
                                    controller, node.source(), changedPorts, desiredValues);
                        }
                    }
                }
                followExec(frame, node, "exec", operations);
            }
            case "linker_face_output" -> {
                double val = frame.value(node, "value", operations).asNumber();
                double normalizedValue = GraphSignalRange.toNormalizedRedstone(val);
                if (!node.bindingId().isBlank()) {
                    queueBindingOutput(node.bindingId(), normalizedValue);
                }
                Map<String, AdvancedGraphDocument.Value> desiredValues = new LinkedHashMap<>();
                for (String port : node.targetWritePorts()) {
                    desiredValues.put(port, "direct_signal".equals(port)
                            ? AdvancedGraphDocument.Value.number(normalizedValue)
                            : frame.value(node, port, operations));
                }
                desiredValues.put(AdvancedContraptionControllerBlockEntity.GRAPH_RAW_DIRECT_SIGNAL_PORT,
                        AdvancedGraphDocument.Value.number(val));
                if (!simulationOnly) {
                    Set<String> changedPorts = AdvancedGraphOutputDelta.changedPorts(
                            controller, node.source(), desiredValues);
                    if (!changedPorts.isEmpty()) {
                        if (controller.setGraphTargetData(node.source(), changedPorts, desiredValues::get)) {
                            AdvancedGraphOutputDelta.recordApplied(
                                    controller, node.source(), changedPorts, desiredValues);
                        }
                    }
                }
                followExec(frame, node, "exec", operations);
            }
            case "local_redstone_output", "wireless_frequency_output" -> {
                double val = frame.value(node, "value", operations).asNumber();
                queueBindingOutput(node.bindingId(), Mth.clamp(Math.round(val), 0.0, 15.0) / 15.0);
                followExec(frame, node, "exec", operations);
            }
            case "set_block_data" -> {
                Map<String, AdvancedGraphDocument.Value> desiredValues = new LinkedHashMap<>();
                Set<String> changedPorts = new LinkedHashSet<>();
                for (String port : node.targetWritePorts()) {
                    AdvancedGraphDocument.Value desired = frame.value(node, port, operations);
                    desiredValues.put(port, desired);
                }
                changedPorts.addAll(AdvancedGraphOutputDelta.changedPorts(
                        controller, node.source(), desiredValues));
                changedPorts.addAll(setDataForceWritePorts(node.source()));
                changedPorts.addAll(CreateRotationSpeedControllerGraphCompat.portsRequiringWrite(
                        node.source(), node.targetWritePorts()));
                boolean success = simulationOnly || changedPorts.isEmpty()
                        || controller.setGraphTargetData(node.source(), changedPorts, desiredValues::get);
                if (!simulationOnly && success && !changedPorts.isEmpty()) {
                    AdvancedGraphOutputDelta.recordApplied(
                            controller, node.source(), changedPorts, desiredValues);
                }
                frame.seedOutput(node, "success", AdvancedGraphDocument.Value.bool(success));
                followExec(frame, node, "exec", operations);
            }
            case "play_sound" -> {
                if ("stop".equals(incomingPort)) {
                    if (!simulationOnly) {
                        controller.playGraphSound(node.id(), "", false,
                                0.0D, 0.0D, 0.0D, 0.0D, 1.0D, false, true);
                    }
                    followExec(frame, node, "exec", operations);
                    return;
                }
                String sound = frame.value(node, "sound", operations).asString();
                boolean world = frame.value(node, "world", operations).asBoolean();
                double x = frame.value(node, "x", operations).asNumber();
                double y = frame.value(node, "y", operations).asNumber();
                double z = frame.value(node, "z", operations).asNumber();
                double volume = frame.value(node, "volume", operations).asNumber();
                double pitch = frame.value(node, "pitch", operations).asNumber();
                boolean loop = frame.value(node, "loop", operations).asBoolean();
                if (!simulationOnly) {
                    controller.playGraphSound(node.id(), sound, world, x, y, z, volume, pitch, loop, false);
                }
                followExec(frame, node, "exec", operations);
            }
            // ------------------------------------STATE / FLOW------------------------------------
            case "variable_set" -> {
                AdvancedGraphDocument.Value val = node.hasInput("value")
                        ? frame.value(node, "value", operations)
                        : frame.value(node, "default", operations);
                AdvancedGraphDocument.Value prev = variableValue(frame.program(), node.variable());
                boolean changed = !Objects.equals(prev, val);
                if (simulationOnly) {
                    if (changed) {
                        state.put(variableStateKey(node.variable()), val);
                    }
                } else {
                    if (changed) {
                        frame.program().graph().variables().put(node.variable(), val);
                    }
                }
                frame.seedOutput(node, "value", val);
                putLiveOutput(node.id() + ":value", val);
                if (changed) {
                    if (simulationOnly) {
                        enqueue("variable:" + node.variable());
                    } else {
                        controller.markGraphRuntimeChanged(node.variable());
                    }
                }
                followExec(frame, node, "exec", operations);
            }
            case "acc_display_mode" -> {
                String requestedMode = frame.value(node, "mode", operations).asString();
                boolean success = simulationOnly || controller != null
                        && controller.setGraphDisplayMode(node.source(), requestedMode);
                String actualMode = simulationOnly || controller == null
                        ? AccDisplayBlockEntity.normalizeDisplayMode(requestedMode)
                        : controller.getGraphTargetData(node.source(), "display_mode").asString();
                frame.seedOutput(node, "mode", AdvancedGraphDocument.Value.string(actualMode));
                frame.seedOutput(node, "success", AdvancedGraphDocument.Value.bool(success));
                followExec(frame, node, "exec", operations);
            }
            case "flip_flop" -> {
                String key = node.id() + ":value";
                AdvancedGraphDocument.Value val = AdvancedGraphDocument.Value.bool(
                        !state.getOrDefault(key, AdvancedGraphDocument.Value.bool(false)).asBoolean());
                state.put(key, val);
                frame.seedOutput(node, "value", val);
                followExec(frame, node, "exec", operations);
            }
            case "latch" -> {
                AdvancedGraphDocument.Value val = frame.value(node, "value", operations);
                state.put(node.id() + ":value", val);
                frame.seedOutput(node, "value", val);
                followExec(frame, node, "exec", operations);
            }
            case "gate" -> {
                if (frame.value(node, "open", operations).asBoolean()) {
                    followExec(frame, node, "exec", operations);
                }
            }
            case "switch" -> {
                if (!AdvancedGraphCatalog.switchDataMode(node.source())) {
                    int selector = (int) Math.floor(frame.value(node, "selector", operations).asNumber());
                    String output = node.hasExecOutput("case_" + selector) ? "case_" + selector : "default";
                    followExec(frame, node, output, operations);
                }
            }
            case "do_once" -> {
                String key = node.id() + ":done";
                boolean legacyResetPulse = "reset".equals(incomingPort);
                boolean reset = legacyResetPulse || frame.value(node, "reset", operations).asBoolean();
                if (reset) {
                    state.put(key, AdvancedGraphDocument.Value.bool(false));
                }
                if (!legacyResetPulse
                        && !state.getOrDefault(key, AdvancedGraphDocument.Value.bool(false)).asBoolean()) {
                    state.put(key, AdvancedGraphDocument.Value.bool(true));
                    followExec(frame, node, "exec", operations);
                }
            }
            case "do_n" -> {
                String key = node.id() + ":count";
                if ("reset".equals(incomingPort)) {
                    state.put(key, AdvancedGraphDocument.Value.number(0));
                    state.put(node.id() + ":index", AdvancedGraphDocument.Value.number(0));
                } else {
                    int limit = Mth.clamp((int) frame.value(node, "count", operations).asNumber(),
                            0, MAX_LOOP_ITERATIONS);
                    int count = (int) state.getOrDefault(key, AdvancedGraphDocument.Value.number(0)).asNumber();
                    if (count < limit) {
                        state.put(node.id() + ":index", AdvancedGraphDocument.Value.number(count));
                        state.put(key, AdvancedGraphDocument.Value.number(count + 1));
                        followExec(frame, node, "exec", operations);
                    }
                }
            }
            // ------------------------------------TIMING------------------------------------
            case "timer" -> {
                eventScheduler.removeScheduledIf(evt -> evt.id().equals("timer:" + node.id()));
                if ("stop".equals(incomingPort)) {
                    state.put(node.id() + ":elapsed", AdvancedGraphDocument.Value.number(timerElapsed(node)));
                    state.put(node.id() + ":running", AdvancedGraphDocument.Value.bool(false));
                } else {
                    int duration = Math.max(1, (int) frame.value(node, "duration", operations).asNumber());
                    state.put(node.id() + ":running", AdvancedGraphDocument.Value.bool(true));
                    state.put(node.id() + ":start", AdvancedGraphDocument.Value.number(gameTime()));
                    state.put(node.id() + ":duration", AdvancedGraphDocument.Value.number(duration));
                    schedule(node.id(), duration, "timer:");
                }
            }
            case "parallel_execution", "sequenced_execution" -> {
                for (String output : node.execOutputPorts()) {
                    followExec(frame, node, output, operations);
                }
            }
            case "exec_combine" -> followExec(frame, node, "exec", operations);
            case "curve" -> executeCurvePulse(frame, node, operations);
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
                followExec(frame, node, "exec", operations);
                if (currentEventId.startsWith("reset:") && "ramp".equals(node.type())
                        && Math.abs(state.getOrDefault(node.id() + ":ramp", AdvancedGraphDocument.Value.number(0)).asNumber()) > 0.0001) {
                    schedule(node.id(), 1, "reset:");
                }
            }
            case "branch" -> {
                String selectedPort = frame.value(node, "condition", operations).asBoolean() ? "true" : "false";
                node.inactiveBranchOutputBindings(selectedPort)
                        .forEach(binding -> queueBindingOutputIfAbsent(binding, 0.0D));
                followExec(frame, node, selectedPort, operations);
            }
            case "bounded_loop" -> {
                int iterations = Mth.clamp((int) frame.value(node, "iterations", operations).asNumber(),
                        0, MAX_LOOP_ITERATIONS);
                for (int idx = 0; idx < iterations; idx++) {
                    state.put(node.id() + ":index", AdvancedGraphDocument.Value.number(idx));
                    followExec(frame, node, "body", operations);
                }
                followExec(frame, node, "complete", operations);
            }
            case "delay", "debounce" -> {
                int ticks = Math.max(1, (int) frame.value(node, "ticks", operations).asNumber());
                if (eventScheduler.scheduledSize() >= MAX_SCHEDULED_EVENTS) {
                    throw new IllegalStateException("Graph exceeded the 1024 scheduled event limit");
                }
                if ("debounce".equals(node.type())) {
                    eventScheduler.removeScheduledIf(evt -> evt.id().equals("delayed:" + node.id()));
                }
                eventScheduler.schedule(gameTime() + ticks,
                        new RuntimeEvent("delayed:" + node.id(),
                                AdvancedGraphDocument.Value.number(0), null));
            }
            case "reset_outputs" -> {
                pendingReset = true;
                if (pendingOutputs != null) {
                    pendingOutputs.clear();
                }
                followExec(frame, node, "exec", operations);
            }
                default -> {
                    executeLibraryNode(frame, node, null, operations);
                    followExec(frame, node, "exec", operations);
                }
            }
        } finally {
            frame.endExecution(node);
        }
    }

    // Check if the ship control init is active
    private boolean shipControlInitActive() {
        return !simulationOnly && controller != null && controller.isShipControlInitializing();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                       OUTPUT EVALUATION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the output
    private AdvancedGraphDocument.Value outputFor(Frame frame, NodeInstruction node, String port, int[] operations) {
        if (node == null) {
            return AdvancedGraphDocument.Value.number(0);
        }
        AdvancedGraphDocument.Value cached = frame.cachedOutput(node, port);
        if (cached != null) {
            return cached;
        }
        requireOperation(operations);
        if (!frame.beginOutput(node, port)) {
            diagnostics.add(new AdvancedGraphValidator.Diagnostic(
                    "error", "recursive_data_path",
                    "Recursive data path stopped at " + node.type() + "." + port, node.id()));
            return defaultValue(AdvancedGraphCatalog.outputs(node.source()).get(port));
        }
        try {
            if (AdvancedGraphCatalog.COLLAPSED_OUTPUT_MAP_PORT.equals(port)
                    && node.data().getBoolean(AdvancedGraphCatalog.COLLAPSE_OUTPUTS_TO_MAP_TAG)) {
                CompoundTag values = new CompoundTag();
                for (Map.Entry<String, String> output : node.outputTypes().entrySet()) {
                    if (!"exec".equals(output.getValue())
                            && !AdvancedGraphCatalog.COLLAPSED_OUTPUT_MAP_PORT.equals(output.getKey())) {
                        values.put(output.getKey(), frame.output(node, output.getKey(), operations).toTag());
                    }
                }
                AdvancedGraphDocument.Value value = AdvancedGraphDocument.Value.map(values);
                frame.seedOutput(node, port, value);
                return value;
            }
            AdvancedGraphDocument.Value inlineMapValue = inlineMapOutput(frame, node, port, operations);
            if (inlineMapValue != null) {
                frame.seedOutput(node, port, inlineMapValue);
                return inlineMapValue;
            }
            if (AdvancedGraphCatalog.isShipControlPassiveType(node.type())) {
                double collisionDetectionDistance = collisionDetectionDistance(
                        frame, node, operations);
                double collisionPollRate = collisionPollRate(frame, node, operations);
                AdvancedGraphDocument.Value val = controller == null
                        ? defaultShipControlValue(port, collisionDetectionDistance)
                        : controller.getShipControlGraphValue(
                                port, collisionDetectionDistance, collisionPollRate);
                frame.seedOutput(node, port, val);
                return val;
            }
            if (AdvancedGraphCatalog.isShipControlType(node.type())) {
                AdvancedGraphDocument.Value val;
                if ("success".equals(port)) {
                    val = state.getOrDefault(shipCommandSuccessKey(node),
                            AdvancedGraphDocument.Value.bool(false));
                } else if (controller != null && ("progress".equals(port)
                        || "progress_percent".equals(port))) {
                    val = controller.getShipControlGraphCommandValue(node.id(), node.type(), port);
                } else if ("progress".equals(port)) {
                    val = AdvancedGraphDocument.Value.string("");
                } else {
                    val = AdvancedGraphDocument.Value.number(0.0D);
                }
                frame.seedOutput(node, port, val);
                return val;
            }
            // ------------------------------------NODE VALUES------------------------------------
            AdvancedGraphDocument.Value res = switch (node.type()) {
            case "constant_number" -> AdvancedGraphDocument.Value.number(node.data().getDouble("Value"));
            case "constant_boolean" -> AdvancedGraphDocument.Value.bool(node.data().getBoolean("Value"));
            case "constant_string" -> AdvancedGraphDocument.Value.string(node.data().getString("Value"));
            case "variable_get", "variable_set" -> variableValue(frame.program(), node.variable());
            case "controller_channel_input", "gamepad_input" -> graphBindingValue(node.bindingId(), port, false);
            case "discovered_target_input" -> {
                if (!"value".equals(port) && !"active".equals(port)) {
                    yield controller.getGraphTargetData(node.source(), port);
                }
                yield graphBindingValue(node.bindingId(), port, false);
            }
            case "linker_face_input" -> {
                if (!"value".equals(port) && !"active".equals(port)) {
                    yield controller.getGraphTargetData(node.source(), port);
                }
                yield graphBindingValue(node.bindingId(), port, true);
            }
            case "local_redstone_input", "wireless_frequency_input" -> graphBindingValue(node.bindingId(), port, true);
            case "mouse_input" -> {
                AdvancedGraphDocument.Value val = frame.cachedOutput(node, port);
                if (val != null) yield val;
                String input = mouseInput(node.source());
                if ("active".equals(port)) {
                    yield AdvancedGraphDocument.Value.bool(
                            controller != null && controller.isMouseInputActive(input));
                }
                yield AdvancedGraphDocument.Value.number(
                        controller == null ? 0.0D : controller.getMouseInputValue(input));
            }
            case "event_key" -> frame.cachedOutput(node, "pressed") == null
                    ? AdvancedGraphDocument.Value.bool(false) : frame.cachedOutput(node, "pressed");
            case "event_graph_ready" -> frame.cachedOutput(node, "ready") == null
                    ? AdvancedGraphDocument.Value.bool(false) : frame.cachedOutput(node, "ready");
            case "event_channel_change", "event_redstone_change", "event_variable_change" -> {
                AdvancedGraphDocument.Value val = frame.cachedOutput(node, "value");
                yield val == null ? AdvancedGraphDocument.Value.number(0) : val;
            }
            case "event_physical_interaction" -> state.getOrDefault(
                    physicalInteractionStateKey(node, port),
                    defaultValue(AdvancedGraphCatalog.outputs(node.source()).get(port)));
            case "event_named_controller" -> {
                AdvancedGraphDocument.Value val = frame.cachedOutput(node, "data");
                yield val == null
                        ? state.getOrDefault(namedCtrlEventStateKey(node),
                        AdvancedGraphDocument.Value.number(0))
                        : val;
            }
            case "function_call" -> {
                if (AdvancedGraphFunctions.isArgumentPort(port)) {
                    yield frame.value(node, AdvancedGraphFunctions.externalPort(port), operations);
                }
                String returnPort = AdvancedGraphFunctions.returnPort(port);
                if (node.inputTypes().containsKey(returnPort)) {
                    yield frame.value(node, returnPort, operations);
                }
                yield AdvancedGraphDocument.Value.number(0);
            }
            case "advanced_hud_element", "acc_display_widget", "acc_hologram_widget" -> {
                String type = AdvancedGraphCatalog.outputs(node.source()).get(port);
                AdvancedGraphDocument.Value fallback = "boolean".equals(type)
                        ? AdvancedGraphDocument.Value.bool(false)
                        : "string".equals(type) ? AdvancedGraphDocument.Value.string("")
                        : AdvancedGraphDocument.Value.number(0);
                yield state.getOrDefault(node.id() + ":hud:" + port, fallback);
            }
            case "portable_tracker" -> controller == null
                    ? defaultPortableTrackingValue(port)
                    : controller.getGogglesTrackingValue(node.data().getString("GogglesPair"), port);
            case "controller_tracker" -> controller == null
                    ? defaultPortableTrackingValue(port)
                    : controller.getPortableTrackingValue(port);
            case "profiler_fps", "profiler_mspt", "profiler_tps", "profiler_frametime" ->
                    AdvancedGraphDocument.Value.number(
                            controller == null ? 0.0D : controller.getGraphProfilerValue(node.type()));
            case "event_delta_time" -> AdvancedGraphDocument.Value.number(currentDeltaTimeSeconds);
            case "get_block_data" -> controller.getGraphTargetData(node.source(), port);
            case "set_block_data" -> {
                if ("success".equals(port)) {
                    AdvancedGraphDocument.Value val = frame.cachedOutput(node, "success");
                    yield val == null ? AdvancedGraphDocument.Value.bool(false) : val;
                }
                yield AdvancedGraphDocument.Value.number(0);
            }
            case "acc_display_mode" -> {
                AdvancedGraphDocument.Value modeValue = frame.cachedOutput(node, port);
                if (modeValue != null) {
                    yield modeValue;
                }
                if ("mode".equals(port) && controller != null) {
                    yield controller.getGraphTargetData(node.source(), "display_mode");
                }
                yield "success".equals(port)
                        ? AdvancedGraphDocument.Value.bool(false)
                        : AdvancedGraphDocument.Value.string("");
            }
            // ------------------------------------LOGIC / MATH------------------------------------
            case "not" -> AdvancedGraphDocument.Value.bool(!frame.value(node, "value", operations).asBoolean());
            case "and" -> AdvancedGraphDocument.Value.bool(frame.value(node, "a", operations).asBoolean()
                    && frame.value(node, "b", operations).asBoolean());
            case "or" -> AdvancedGraphDocument.Value.bool(frame.value(node, "a", operations).asBoolean()
                    || frame.value(node, "b", operations).asBoolean());
            case "compare" -> AdvancedGraphDocument.Value.bool(compare(
                    frame.value(node, "a", operations),
                    frame.value(node, "b", operations),
                    frame.value(node, "operator", operations).asString()));
            case "add" -> numberBinary(frame, node, operations, (a, b) -> a + b);
            case "subtract" -> numberBinary(frame, node, operations, (a, b) -> a - b);
            case "multiply" -> numberBinary(frame, node, operations, (a, b) -> a * b);
            case "divide" -> numberBinary(frame, node, operations, (a, b) -> b == 0 ? 0 : a / b);
            case "modulo" -> numberBinary(frame, node, operations, (a, b) -> b == 0 ? 0 : a % b);
            case "min" -> numberBinary(frame, node, operations, Math::min);
            case "max" -> numberBinary(frame, node, operations, Math::max);
            case "average" -> AdvancedGraphDocument.Value.number(
                    (frame.value(node, "a", operations).asNumber()
                            + frame.value(node, "b", operations).asNumber()) / 2.0D);
            case "absolute" -> AdvancedGraphDocument.Value.number(Math.abs(frame.value(node, "value", operations).asNumber()));
            case "sin" -> AdvancedGraphDocument.Value.number(Math.sin(frame.value(node, "value", operations).asNumber()));
            case "cos" -> AdvancedGraphDocument.Value.number(Math.cos(frame.value(node, "value", operations).asNumber()));
            case "math_sqrt" -> AdvancedGraphDocument.Value.number(
                    Math.sqrt(frame.value(node, "In", operations).asNumber()));
            case "math_tan" -> AdvancedGraphDocument.Value.number(
                    Math.tan(frame.value(node, "In", operations).asNumber()));
            case "math_acos" -> AdvancedGraphDocument.Value.number(
                    Math.acos(frame.value(node, "In", operations).asNumber()));
            case "math_asin" -> AdvancedGraphDocument.Value.number(
                    Math.asin(frame.value(node, "In", operations).asNumber()));
            case "math_atan" -> AdvancedGraphDocument.Value.number(
                    Math.atan(frame.value(node, "In", operations).asNumber()));
            case "math_atan2" -> AdvancedGraphDocument.Value.number(Math.atan2(
                    frame.value(node, "Y", operations).asNumber(),
                    frame.value(node, "X", operations).asNumber()));
            case "math_power" -> AdvancedGraphDocument.Value.number(Math.pow(
                    frame.value(node, "Base", operations).asNumber(),
                    frame.value(node, "Exp", operations).asNumber()));
            case "math_exp" -> AdvancedGraphDocument.Value.number(
                    Math.exp(frame.value(node, "In", operations).asNumber()));
            case "math_ln" -> AdvancedGraphDocument.Value.number(
                    Math.log(frame.value(node, "In", operations).asNumber()));
            case "math_log" -> AdvancedGraphDocument.Value.number(
                    Math.log(frame.value(node, "In", operations).asNumber())
                            / Math.log(frame.value(node, "Base", operations).asNumber()));
            case "math_average" -> AdvancedGraphNodeOperations.movingAverage(
                    state, node.id(), frame.value(node, "In", operations).asNumber(),
                    frame.value(node, "Samples", operations).asNumber());
            case "math_delta" -> AdvancedGraphNodeOperations.valueDelta(
                    state, node.id(), frame.value(node, "In", operations).asNumber());
            case "math_peak_tracker" -> AdvancedGraphNodeOperations.peak(
                    state, node.id(), frame.value(node, "In", operations).asNumber(), port);
            case "round" -> AdvancedGraphDocument.Value.number(Math.round(frame.value(node, "value", operations).asNumber()));
            case "floor" -> AdvancedGraphDocument.Value.number(Math.floor(frame.value(node, "value", operations).asNumber()));
            case "ceil" -> AdvancedGraphDocument.Value.number(Math.ceil(frame.value(node, "value", operations).asNumber()));
            case "-x" -> AdvancedGraphDocument.Value.number(-frame.value(node, "value", operations).asNumber());
            case "lerp" -> {
                if (!recentlyPulsed(node)) {
                    yield AdvancedGraphDocument.Value.number(0);
                }
                double a = frame.value(node, "a", operations).asNumber();
                double b = frame.value(node, "b", operations).asNumber();
                double amount = frame.value(node, "amount", operations).asNumber();
                yield AdvancedGraphDocument.Value.number(Mth.lerp(amount, a, b));
            }
            case "data_branch" -> inferBodyOverride(frame.value(node,
                    frame.value(node, "condition", operations).asBoolean() ? "true" : "false", operations));
            case "switch" -> {
                if (!AdvancedGraphCatalog.switchDataMode(node.source())) {
                    yield AdvancedGraphDocument.Value.number(0);
                }
                int selector = (int) Math.floor(frame.value(node, "selector", operations).asNumber());
                String selected = node.inputTypes().containsKey("case_" + selector)
                        ? "case_" + selector : "default";
                yield "value".equals(port)
                        ? inferBodyOverride(
                                frame.value(node, selected, operations))
                        : AdvancedGraphDocument.Value.number(0);
            }
            case "reroute", "event_value_change" -> frame.value(node, "value", operations);
            case "edge_detector" -> edgeDetector(frame, node, port, operations);
            // ------------------------------------COLLECTIONS------------------------------------
            case "list_create" -> {
                CompoundTag values = new CompoundTag();
                int idx = 0;
                for (Map.Entry<String, String> input : AdvancedGraphCatalog.inputs(node.source()).entrySet()) {
                    if ("exec".equals(input.getValue())) {
                        continue;
                    }
                    values.put(Integer.toString(idx++),
                            frame.value(node, input.getKey(), operations).toTag());
                }
                yield AdvancedGraphDocument.Value.list(values);
            }
            case "list_get" -> {
                AdvancedGraphDocument.Value list = frame.value(node, "list", operations);
                yield listValue(list, frame.value(node, "index", operations).asNumber());
            }
            case "arr_get" -> AdvancedGraphNodeOperations.get(
                    frame.value(node, "Arr", operations),
                    frame.value(node, "Index", operations).asNumber());
            case "arr_shuffle" -> AdvancedGraphNodeOperations.shuffle(frame.value(node, "Arr", operations));
            case "arr_sort" -> AdvancedGraphNodeOperations.sort(frame.value(node, "Arr", operations), false);
            case "arr_slice" -> AdvancedGraphNodeOperations.slice(
                    frame.value(node, "Arr", operations),
                    frame.value(node, "Start", operations).asNumber(),
                    frame.value(node, "End", operations).asNumber());
            case "arr_append_arr" -> AdvancedGraphNodeOperations.append(
                    frame.value(node, "Source", operations),
                    frame.value(node, "Append", operations));
            case "collection_merge" -> AdvancedGraphNodeOperations.merge(
                    frame.value(node, "A", operations), frame.value(node, "B", operations));
            case "arr_add" -> AdvancedGraphNodeOperations.add(
                    frame.value(node, "Arr", operations), frame.value(node, "Item", operations), false);
            case "arr_add_unique" -> AdvancedGraphNodeOperations.add(
                    frame.value(node, "Arr", operations), frame.value(node, "Item", operations), true);
            case "arr_insert" -> AdvancedGraphNodeOperations.insert(
                    frame.value(node, "Arr", operations), frame.value(node, "Item", operations),
                    frame.value(node, "Index", operations).asNumber());
            case "arr_remove" -> AdvancedGraphNodeOperations.remove(
                    frame.value(node, "Arr", operations),
                    frame.value(node, "Index", operations).asNumber());
            case "arr_clear" -> AdvancedGraphNodeOperations.slice(
                    frame.value(node, "Arr", operations), 0.0D, 0.0D);
            case "arr_find" -> AdvancedGraphDocument.Value.number(AdvancedGraphNodeOperations.find(
                    frame.value(node, "Arr", operations), frame.value(node, "Item", operations)));
            case "arr_contains" -> AdvancedGraphDocument.Value.bool(AdvancedGraphNodeOperations.find(
                    frame.value(node, "Arr", operations), frame.value(node, "Item", operations)) >= 0);
            case "arr_length" -> AdvancedGraphDocument.Value.number(
                    AdvancedGraphNodeOperations.length(frame.value(node, "Arr", operations)));
            case "arr_last_index" -> AdvancedGraphDocument.Value.number(
                    AdvancedGraphNodeOperations.length(frame.value(node, "Arr", operations)) - 1);
            case "arr_sort_desc" -> AdvancedGraphNodeOperations.sort(
                    frame.value(node, "Arr", operations), true);
            case "arr_filter" -> AdvancedGraphNodeOperations.filter(
                    frame.value(node, "Array", operations),
                    frame.value(node, "Key", operations).asString(),
                    frame.value(node, "Value", operations),
                    frame.value(node, "Operation", operations).asString());
            case "map_create" -> {
                if (AdvancedGraphCatalog.isDynamicConstructor(node.source())) {
                    CompoundTag values = new CompoundTag();
                    for (Map.Entry<String, String> input : AdvancedGraphCatalog.inputs(node.source()).entrySet()) {
                        if ("exec".equals(input.getValue())) {
                            continue;
                        }
                        String key = AdvancedGraphCatalog.constructorInputLabel(node.source(), input.getKey());
                        values.put(key, frame.value(node, input.getKey(), operations).toTag());
                    }
                    yield AdvancedGraphDocument.Value.map(values);
                }
                CompoundTag values = new CompoundTag();
                values.put(frame.value(node, "key", operations).asString(),
                        frame.value(node, "value", operations).toTag());
                yield AdvancedGraphDocument.Value.map(values);
            }
            case "map_get" -> dataValue(
                    frame.value(node, "map", operations).payload(),
                    frame.value(node, "key", operations).asString());
            case "split_list", "json_split", "break_out" ->
                    structuredValue(frame.value(node, "value", operations), port);
            // ------------------------------------TEXT------------------------------------
            case "string_concat" -> AdvancedGraphDocument.Value.string(
                    valueText(frame.value(node, "a", operations))
                            + valueText(frame.value(node, "b", operations)));
            case "split_string" -> splitString(
                    frame.value(node, "string", operations).asString(),
                    frame.value(node, "delimiter", operations).asString());
            case "str_split" -> AdvancedGraphNodeOperations.split(
                    frame.value(node, "In", operations).asString(),
                    frame.value(node, "Split On", operations).asString());
            case "str_reverse" -> AdvancedGraphDocument.Value.string(
                    new StringBuilder(frame.value(node, "In", operations).asString()).reverse().toString());
            case "str_append" -> AdvancedGraphDocument.Value.string(
                    frame.value(node, "Source", operations).asString()
                            + frame.value(node, "Separator", operations).asString()
                            + frame.value(node, "Text", operations).asString());
            case "str_prepend" -> AdvancedGraphDocument.Value.string(
                    frame.value(node, "Text", operations).asString()
                            + frame.value(node, "Separator", operations).asString()
                            + frame.value(node, "Source", operations).asString());
            case "str_trim" -> AdvancedGraphDocument.Value.string(
                    frame.value(node, "In", operations).asString().strip());
            case "str_replace" -> AdvancedGraphDocument.Value.string(
                    AdvancedGraphNodeOperations.replace(
                            frame.value(node, "In", operations).asString(),
                            frame.value(node, "Find", operations).asString(),
                            frame.value(node, "Replace", operations).asString()));
            case "str_length" -> AdvancedGraphDocument.Value.number(
                    frame.value(node, "In", operations).asString().codePointCount(
                            0, frame.value(node, "In", operations).asString().length()));
            case "str_contains" -> AdvancedGraphDocument.Value.bool(
                    frame.value(node, "In", operations).asString()
                            .contains(frame.value(node, "Search", operations).asString()));
            case "str_starts_with" -> AdvancedGraphDocument.Value.bool(
                    frame.value(node, "In", operations).asString()
                            .startsWith(frame.value(node, "Prefix", operations).asString()));
            case "str_ends_with" -> AdvancedGraphDocument.Value.bool(
                    frame.value(node, "In", operations).asString()
                            .endsWith(frame.value(node, "Suffix", operations).asString()));
            case "str_upper" -> AdvancedGraphDocument.Value.string(
                    frame.value(node, "In", operations).asString().toUpperCase(java.util.Locale.ROOT));
            case "str_lower" -> AdvancedGraphDocument.Value.string(
                    frame.value(node, "In", operations).asString().toLowerCase(java.util.Locale.ROOT));
            case "str_parse_array" -> AdvancedGraphNodeOperations.split(
                    frame.value(node, "In", operations).asString(),
                    frame.value(node, "Delimiter", operations).asString());
            case "str_join_array" -> AdvancedGraphDocument.Value.string(AdvancedGraphNodeOperations.join(
                    frame.value(node, "Array", operations),
                    frame.value(node, "Separator", operations).asString()));
            case "str_regex" -> AdvancedGraphNodeOperations.regex(
                    frame.value(node, "In", operations).asString(),
                    frame.value(node, "Pattern", operations).asString(), port);
            case "substring" -> AdvancedGraphDocument.Value.string(substring(
                    frame.value(node, "string", operations).asString(),
                    frame.value(node, "start_index", operations).asNumber(),
                    frame.value(node, "end_index", operations).asNumber()));
            case "find_in_string" -> AdvancedGraphDocument.Value.number(findInString(
                    frame.value(node, "string", operations).asString(),
                    frame.value(node, "search", operations).asString()));
            case "convert_type" -> convertValue(
                    frame.value(node, "value", operations),
                    node.data().getString("OutputType"));
            case "validate_number" -> {
                double val = frame.value(node, "value", operations).asNumber();
                yield "valid".equals(port) ? AdvancedGraphDocument.Value.bool(Double.isFinite(val))
                        : AdvancedGraphDocument.Value.number(Double.isFinite(val) ? val : 0);
            }
            case "clamp" -> AdvancedGraphDocument.Value.number(Mth.clamp(
                    frame.value(node, "value", operations).asNumber(),
                    frame.value(node, "min", operations).asNumber(),
                    frame.value(node, "max", operations).asNumber()));
            case "map_range" -> {
                double val = frame.value(node, "value", operations).asNumber();
                double inMin = frame.value(node, "in_min", operations).asNumber();
                double inMax = frame.value(node, "in_max", operations).asNumber();
                double outMin = frame.value(node, "out_min", operations).asNumber();
                double outMax = frame.value(node, "out_max", operations).asNumber();
                yield AdvancedGraphDocument.Value.number(
                        mapRange(val, inMin, inMax, outMin, outMax));
            }
            case "deadzone" -> {
                double val = frame.value(node, "value", operations).asNumber();
                double start = frame.value(node, "start", operations).asNumber();
                double resist = Math.abs(frame.value(node, "resist", operations).asNumber());
                yield AdvancedGraphDocument.Value.number(deadzone(val, start, resist));
            }
            // ------------------------------------CONTROL STATE------------------------------------
            case "step_response" -> {
                double target = frame.value(node, "value", operations).asNumber();
                double step = Math.abs(frame.value(node, "step", operations).asNumber());
                String key = node.id() + ":step";
                double prev = state.getOrDefault(key, AdvancedGraphDocument.Value.number(0)).asNumber();
                AdvancedGraphDocument.Value val = AdvancedGraphDocument.Value.number(
                        step <= 0 ? target : Mth.clamp(target, prev - step, prev + step));
                state.put(key, val);
                yield val;
            }
            case "smoothing" -> {
                double val = recentlyPulsed(node) ? frame.value(node, "value", operations).asNumber() : 0;
                double amount = node.hasInput("amount") || node.defaultValue("amount") != null
                        ? frame.value(node, "amount", operations).asNumber() : DEFAULT_SMOOTHING_AMOUNT;
                String stateKey = node.id() + ":smooth";
                double prev = state.getOrDefault(stateKey, AdvancedGraphDocument.Value.number(val)).asNumber();
                AdvancedGraphDocument.Value smoothed = AdvancedGraphDocument.Value.number(
                        smoothValue(prev, val, amount));
                state.put(stateKey, smoothed);
                yield smoothed;
            }
            case "ramp" -> {
                boolean active = recentlyPulsed(node);
                double target = active ? frame.value(node, "value", operations).asNumber() : 0;
                double rate = Math.abs(frame.value(node, active ? "rise" : "fall", operations).asNumber());
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
                        : curveInput(frame, node, "min",
                        AdvancedGraphCurve.DEFAULT_MINIMUM, operations);
                double maximum = useSweepInputs
                        ? state.get(node.id() + ":curve_max").asNumber()
                        : curveInput(frame, node, "max",
                        AdvancedGraphCurve.DEFAULT_MAXIMUM, operations);
                double val = useSweepInputs
                        ? state.get(node.id() + ":curve_value").asNumber()
                        : curveInput(frame, node, "value",
                        AdvancedGraphCurve.DEFAULT_MAXIMUM, operations);
                yield AdvancedGraphDocument.Value.number(AdvancedGraphCurve.mapValue(node.data(),
                        state.getOrDefault(node.id() + ":curve_progress",
                                AdvancedGraphDocument.Value.number(0)).asNumber(),
                        val, minimum, maximum));
            }
            case "flip_flop", "latch" -> state.getOrDefault(node.id() + ":value",
                    persistentOutputOr(node, "value", AdvancedGraphDocument.Value.bool(false)));
            case "bounded_loop", "do_n" -> state.getOrDefault(node.id() + ":index", AdvancedGraphDocument.Value.number(0));
            case "timer" -> AdvancedGraphDocument.Value.number(timerElapsed(node));
            case "pid" -> AdvancedGraphDocument.Value.number(pidValue(frame, node, operations));
            case "lqr_controller" -> AdvancedGraphDocument.Value.number(LqrControllerMath.control(
                    frame.value(node, "target", operations).asNumber(),
                    frame.value(node, "actual", operations).asNumber(),
                    frame.value(node, "gain", operations).asNumber(),
                    frame.value(node, "feed_forward", operations).asNumber(),
                    frame.value(node, "min", operations).asNumber(),
                    frame.value(node, "max", operations).asNumber()));
            case "adrc" -> adrcValue(frame, node, port, operations);
            case "adrc_nth_order" -> adrcNthOrderValue(frame, node, port, operations);
            // ------------------------------------VECTORS / ROTATION------------------------------------
            case "vector_multiply" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(frame.value(node, "a", operations)).multiply(
                            AdvancedGraphMathValues.vector(frame.value(node, "b", operations))));
            case "vector_subtract" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(frame.value(node, "a", operations)).subtract(
                            AdvancedGraphMathValues.vector(frame.value(node, "b", operations))));
            case "vector_add" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(frame.value(node, "a", operations)).add(
                            AdvancedGraphMathValues.vector(frame.value(node, "b", operations))));
            case "vector_invert" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(frame.value(node, "value", operations)).invert());
            case "vector_magnitude" -> AdvancedGraphDocument.Value.number(
                    AdvancedGraphMathValues.vector(frame.value(node, "value", operations)).magnitude());
            case "vector_difference" -> AdvancedGraphMathValues.vector(
                    AdvancedGraphMathValues.vector(frame.value(node, "a", operations)).difference(
                            AdvancedGraphMathValues.vector(frame.value(node, "b", operations))));
            case "vector_distance" -> AdvancedGraphDocument.Value.number(
                    AdvancedGraphMathValues.vector(frame.value(node, "a", operations)).distance(
                            AdvancedGraphMathValues.vector(frame.value(node, "b", operations))));
            case "quaternion_to_euler" -> AdvancedGraphMathValues.vector(RotationMath.quaternionToEulerZxz(
                    AdvancedGraphMathValues.quaternion(frame.value(node, "quaternion", operations))));
            case "quaternion_to_tait_bryan" -> AdvancedGraphMathValues.vector(RotationMath.quaternionToTaitBryanXyz(
                    AdvancedGraphMathValues.quaternion(frame.value(node, "quaternion", operations))));
            case "euler_to_quaternion" -> AdvancedGraphMathValues.quaternion(RotationMath.eulerZxzToQuaternion(
                    AdvancedGraphMathValues.vector(frame.value(node, "euler", operations))));
            case "tait_bryan_to_quaternion" -> AdvancedGraphMathValues.quaternion(RotationMath.taitBryanXyzToQuaternion(
                    AdvancedGraphMathValues.vector(frame.value(node, "tait_bryan", operations))));
            case "euler_to_tait_bryan" -> AdvancedGraphMathValues.vector(RotationMath.eulerZxzToTaitBryanXyz(
                    AdvancedGraphMathValues.vector(frame.value(node, "euler", operations))));
            case "tait_bryan_to_euler" -> AdvancedGraphMathValues.vector(RotationMath.taitBryanXyzToEulerZxz(
                    AdvancedGraphMathValues.vector(frame.value(node, "tait_bryan", operations))));
            case "random", "random_int", "random_float_in_range", "random_int_in_range" -> {
                long seed = controller.getBlockPos().asLong() ^ node.id().hashCode()
                        ^ (controller.getLevel() == null ? 0 : controller.getLevel().getGameTime());
                double val = switch (node.type()) {
                    case "random_int" -> AdvancedGraphRandom.randomInt(seed,
                            frame.value(node, "max", operations).asNumber());
                    case "random_float_in_range" -> AdvancedGraphRandom.randomFloatInRange(seed,
                            frame.value(node, "min", operations).asNumber(),
                            frame.value(node, "max", operations).asNumber());
                    case "random_int_in_range" -> AdvancedGraphRandom.randomIntInRange(seed,
                            frame.value(node, "min", operations).asNumber(),
                            frame.value(node, "max", operations).asNumber());
                    default -> AdvancedGraphRandom.randomFloat(seed);
                };
                yield AdvancedGraphDocument.Value.number(val);
            }
            default -> executeLibraryNode(frame, node, port, operations);
            };
            frame.seedOutput(node, port, res);
            return res;
        } finally {
            frame.endOutput(node, port);
        }
    }

    // Run the library node
    private AdvancedGraphDocument.Value executeLibraryNode(
            Frame frame, NodeInstruction node, String requestedPort, int[] operations
    ) {
        GraphNodeExecutor executor = GraphApi.runtimes().get(node.type());
        AdvancedGraphDocument.Value fallback = requestedPort == null
                ? AdvancedGraphDocument.Value.number(0)
                : state.getOrDefault(node.id() + ":" + requestedPort,
                persistentOutputOr(node, requestedPort, AdvancedGraphDocument.Value.number(0)));
        if (executor == null) return fallback;
        Map<String, GraphValue> inputs = new LinkedHashMap<>();
        AdvancedGraphCatalog.inputs(node.source()).forEach((name, type) -> {
            if (!"exec".equals(type)) inputs.put(name, toLibraryValue(frame.value(node, name, operations)));
        });
        String statePrefix = node.id() + ":library:";
        GraphExecutionContext ctx = new GraphExecutionContext() {
            // Update the graph
            @Override public long tick() { return gameTime(); }
            // Get the state
            @Override public GraphValue state(String key) {
                return toLibraryValue(state.get(statePrefix + key));
            }
            // Set the graph runtime state
            @Override public void state(String key, GraphValue val) {
                state.put(statePrefix + key, fromLibraryValue(val));
            }
            // Get the service
            @Override public <T> Optional<T> service(GraphServiceKey<T> key) {
                if (controller == null || !GraphHostServices.BLOCK_ENTITY.equals(key)) {
                    return Optional.empty();
                }
                return Optional.of(key.type().cast(controller));
            }
        };
        Map<String, GraphValue> outputs = executor.execute(ctx, Map.copyOf(inputs));
        if (outputs != null) outputs.forEach((name, val) -> frame.seedOutput(node, name, fromLibraryValue(val)));
        AdvancedGraphDocument.Value res = requestedPort == null ? null : frame.cachedOutput(node, requestedPort);
        return res == null ? fallback : res;
    }

    // Convert the graph to library value
    public static GraphValue toLibraryValue(AdvancedGraphDocument.Value val) {
        if (val == null) return new GraphValue("any", null);
        return switch (val.type()) {
            case "number" -> GraphValue.number(val.asNumber());
            case "boolean" -> GraphValue.bool(val.asBoolean());
            case "string", "direction" -> GraphValue.string(val.asString());
            case "list" -> GraphValue.list(toLibraryList(val.payload()));
            case "map" -> GraphValue.map(toLibraryMap(val.payload()));
            case "target", "frequency" -> new GraphValue(val.type(), toLibraryRawMap(val.payload()));
            default -> new GraphValue(val.type(), val.payload().toString());
        };
    }

    // Create the graph from library value
    public static AdvancedGraphDocument.Value fromLibraryValue(GraphValue val) {
        if (val == null) return AdvancedGraphDocument.Value.number(0);
        return switch (val.type()) {
            case "number" -> AdvancedGraphDocument.Value.number(val.asNumber());
            case "boolean" -> AdvancedGraphDocument.Value.bool(val.asBoolean());
            case "direction" -> AdvancedGraphDocument.Value.direction(val.asString());
            case "string" -> AdvancedGraphDocument.Value.string(val.asString());
            case "list" -> AdvancedGraphDocument.Value.list(fromLibraryList(val.value()));
            case "map" -> AdvancedGraphDocument.Value.map(fromLibraryMap(val.value()));
            case "target" -> AdvancedGraphDocument.Value.target(fromLibraryRawMap(val.value()));
            case "frequency" -> AdvancedGraphDocument.Value.frequency(fromLibraryRawMap(val.value()));
            default -> AdvancedGraphDocument.Value.string(val.asString());
        };
    }

    // Convert the graph to library list
    private static List<Object> toLibraryList(CompoundTag payload) {
        List<String> keys = new ArrayList<>(payload.getAllKeys());
        keys.removeIf(key -> !key.chars().allMatch(Character::isDigit));
        keys.sort((first, second) -> Integer.compare(Integer.parseInt(first), Integer.parseInt(second)));
        List<Object> res = new ArrayList<>(keys.size());
        for (String key : keys) res.add(toLibraryEntry(payload.get(key)));
        return List.copyOf(res);
    }

    // Convert the graph to library map
    private static Map<String, Object> toLibraryMap(CompoundTag payload) {
        Map<String, Object> res = new LinkedHashMap<>();
        for (String key : payload.getAllKeys()) res.put(key, toLibraryEntry(payload.get(key)));
        return Map.copyOf(res);
    }

    // Convert the graph to library entry
    private static Object toLibraryEntry(Tag tag) {
        if (isGraphValueTag(tag)) {
            CompoundTag compound = (CompoundTag) tag;
            return toLibraryValue(AdvancedGraphDocument.Value.fromTag(compound));
        }
        return toLibraryRaw(tag);
    }

    // Convert the graph to library raw map
    private static Map<String, Object> toLibraryRawMap(CompoundTag payload) {
        Map<String, Object> res = new LinkedHashMap<>();
        for (String key : payload.getAllKeys()) res.put(key, toLibraryRaw(payload.get(key)));
        return Map.copyOf(res);
    }

    // Convert the graph to library raw
    private static Object toLibraryRaw(Tag tag) {
        if (tag instanceof CompoundTag compound) return toLibraryRawMap(compound);
        if (tag instanceof ListTag list) {
            List<Object> res = new ArrayList<>(list.size());
            for (Tag entry : list) res.add(toLibraryRaw(entry));
            return List.copyOf(res);
        }
        if (tag instanceof net.minecraft.nbt.NumericTag num) return num.getAsDouble();
        return tag == null ? "" : tag.getAsString();
    }

    // Create the graph from library map
    private static CompoundTag fromLibraryMap(Object raw) {
        CompoundTag payload = new CompoundTag();
        if (raw instanceof Map<?, ?> values) values.forEach((key, entry) ->
                payload.put(String.valueOf(key), fromLibraryObject(entry).toTag()));
        return payload;
    }

    // Create the graph from library list
    private static CompoundTag fromLibraryList(Object raw) {
        CompoundTag payload = new CompoundTag();
        if (raw instanceof Iterable<?> values) {
            int idx = 0;
            for (Object entry : values) payload.put(Integer.toString(idx++), fromLibraryObject(entry).toTag());
        }
        return payload;
    }

    // Create the graph from library object
    private static AdvancedGraphDocument.Value fromLibraryObject(Object val) {
        if (val instanceof GraphValue graphValue) return fromLibraryValue(graphValue);
        if (val instanceof Boolean bool) return AdvancedGraphDocument.Value.bool(bool);
        if (val instanceof Number num) return AdvancedGraphDocument.Value.number(num.doubleValue());
        if (val instanceof Map<?, ?>) return AdvancedGraphDocument.Value.map(fromLibraryMap(val));
        if (val instanceof Iterable<?>) return AdvancedGraphDocument.Value.list(fromLibraryList(val));
        return AdvancedGraphDocument.Value.string(val == null ? "" : String.valueOf(val));
    }

    // Create the graph from library raw map
    private static CompoundTag fromLibraryRawMap(Object raw) {
        CompoundTag res = new CompoundTag();
        if (raw instanceof Map<?, ?> values) values.forEach((key, val) ->
                res.put(String.valueOf(key), fromLibraryRaw(val)));
        return res;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         LIBRARY NODES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the graph from library raw
    private static Tag fromLibraryRaw(Object val) {
        if (val instanceof Map<?, ?>) return fromLibraryRawMap(val);
        if (val instanceof Iterable<?> values) {
            ListTag list = new ListTag();
            for (Object entry : values) list.add(fromLibraryRaw(entry));
            return list;
        }
        if (val instanceof Boolean bool) return ByteTag.valueOf(bool);
        if (val instanceof Number num) return DoubleTag.valueOf(num.doubleValue());
        return StringTag.valueOf(val == null ? "" : String.valueOf(val));
    }

    // Create the default value
    static AdvancedGraphDocument.Value defaultValue(String type) {
        return switch (type == null ? "" : type) {
            case "boolean" -> AdvancedGraphDocument.Value.bool(false);
            case "string" -> AdvancedGraphDocument.Value.string("");
            case "direction" -> AdvancedGraphDocument.Value.direction("");
            case "frequency" -> AdvancedGraphDocument.Value.frequency(new CompoundTag());
            case "target" -> AdvancedGraphDocument.Value.target(new CompoundTag());
            case "list" -> AdvancedGraphDocument.Value.list(new CompoundTag());
            case "map" -> AdvancedGraphDocument.Value.map(new CompoundTag());
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Get the persistent output
    private static AdvancedGraphDocument.Value persistentOutputOr(
            NodeInstruction node, String port, AdvancedGraphDocument.Value fallback
    ) {
        AdvancedGraphDocument.Value persistent =
                AdvancedGraphPortState.persistentValue(node.source(), port, true);
        return persistent == null ? fallback : persistent;
    }

    // Map the range
    static double mapRange(double val, double inputMinimum, double inputMaximum,
                           double outputMinimum, double outputMaximum) {
        if (inputMaximum == inputMinimum) {
            return outputMinimum;
        }
        double clamped = Mth.clamp(val,
                Math.min(inputMinimum, inputMaximum),
                Math.max(inputMinimum, inputMaximum));
        double amount = (clamped - inputMinimum) / (inputMaximum - inputMinimum);
        return Mth.lerp(amount, outputMinimum, outputMaximum);
    }

    // Get the deadzone
    static double deadzone(double val, double start, double resist) {
        return Math.abs(val - start) < Math.abs(resist) ? start : val;
    }

    // Get the variable value
    private AdvancedGraphDocument.Value variableValue(CompiledProgram program, String variable) {
        AdvancedGraphDocument.Value initial = program.graph().variables()
                .getOrDefault(variable, AdvancedGraphDocument.Value.number(0));
        return simulationOnly ? state.getOrDefault(variableStateKey(variable), initial) : initial;
    }

    // Get the variable state key
    private static String variableStateKey(String variable) {
        return "variable:" + variable;
    }

    // Get the ship command pending key
    private static String shipCommandPendingKey(NodeInstruction node) {
        return node.id() + ":ship_command_pending";
    }

    // Get the ship command success key
    private static String shipCommandSuccessKey(NodeInstruction node) {
        return node.id() + ":ship_command_success";
    }

    // Get the named ctrl event state key
    private static String namedCtrlEventStateKey(NodeInstruction node) {
        return node.id() + ":named_controller_event:" + node.triggerEvent();
    }

    // Get the graph binding value
    private AdvancedGraphDocument.Value graphBindingValue(String bindingId, String port, boolean redstone) {
        if ("active".equals(port)) {
            return AdvancedGraphDocument.Value.bool(isBindingActive(bindingId));
        }
        double val = controller.getGraphBindingValue(bindingId);
        return AdvancedGraphDocument.Value.number(redstone ? GraphSignalRange.fromNormalizedRedstone(val) : val);
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

    // Apply the numeric binary operation
    private AdvancedGraphDocument.Value numberBinary(Frame frame, NodeInstruction node, int[] operations,
                                                     java.util.function.DoubleBinaryOperator operator) {
        return AdvancedGraphDocument.Value.number(operator.applyAsDouble(
                frame.value(node, "a", operations).asNumber(),
                frame.value(node, "b", operations).asNumber()));
    }

    // Get the edge detector
    private AdvancedGraphDocument.Value edgeDetector(Frame frame, NodeInstruction node, String port, int[] operations) {
        boolean current = frame.value(node, "value", operations).asBoolean();
        String key = node.id() + ":edge";
        boolean prev = state.getOrDefault(key, AdvancedGraphDocument.Value.bool(current)).asBoolean();
        state.put(key, AdvancedGraphDocument.Value.bool(current));
        frame.seedOutput(node, "rising", AdvancedGraphDocument.Value.bool(!prev && current));
        frame.seedOutput(node, "falling", AdvancedGraphDocument.Value.bool(prev && !current));
        AdvancedGraphDocument.Value val = frame.cachedOutput(node, port);
        return val == null ? AdvancedGraphDocument.Value.bool(false) : val;
    }

    // Get the PID value
    private double pidValue(Frame frame, NodeInstruction node, int[] operations) {
        if (frame.value(node, AdvancedGraphCatalog.CONTROLLER_RESET_PORT, operations).asBoolean()) {
            state.remove(node.id() + ":integral");
            state.remove(node.id() + ":previous_error");
            return 0.0D;
        }
        double error = frame.value(node, "target", operations).asNumber()
                - frame.value(node, "actual", operations).asNumber();
        String integralKey = node.id() + ":integral";
        String errorKey = node.id() + ":previous_error";
        boolean preventWindup = node.data().getBoolean(
                AdvancedGraphCatalog.PID_PREVENT_INTEGRAL_WINDUP_TAG);
        double integral = PidControllerMath.nextIntegral(
                state.getOrDefault(integralKey, AdvancedGraphDocument.Value.number(0)).asNumber(),
                error,
                preventWindup,
                preventWindup
                        ? frame.value(node, AdvancedGraphCatalog.PID_INTEGRAL_MIN_PORT, operations).asNumber()
                        : -MAX_OPERATIONS,
                preventWindup
                        ? frame.value(node, AdvancedGraphCatalog.PID_INTEGRAL_MAX_PORT, operations).asNumber()
                        : MAX_OPERATIONS,
                MAX_OPERATIONS);
        double derivative = error - state.getOrDefault(errorKey, AdvancedGraphDocument.Value.number(error)).asNumber();
        state.put(integralKey, AdvancedGraphDocument.Value.number(integral));
        state.put(errorKey, AdvancedGraphDocument.Value.number(error));
        return error * frame.value(node, "p", operations).asNumber()
                + integral * frame.value(node, "i", operations).asNumber()
                + derivative * frame.value(node, "d", operations).asNumber();
    }

    // Get the ADRC value
    private AdvancedGraphDocument.Value adrcValue(Frame frame, NodeInstruction node, String port, int[] operations) {
        String prefix = node.id() + ":adrc:";
        if (frame.value(node, AdvancedGraphCatalog.CONTROLLER_RESET_PORT, operations).asBoolean()) {
            state.remove(prefix + "estimate");
            state.remove(prefix + "disturbance");
            state.remove(prefix + "control");
            frame.seedOutput(node, "value", AdvancedGraphDocument.Value.number(0.0D));
            frame.seedOutput(node, "disturbance", AdvancedGraphDocument.Value.number(0.0D));
            return frame.cachedOutput(node, port);
        }
        AdrcControllerMath.State prev = new AdrcControllerMath.State(
                state.getOrDefault(prefix + "estimate", AdvancedGraphDocument.Value.number(
                        frame.value(node, "actual", operations).asNumber())).asNumber(),
                state.getOrDefault(prefix + "disturbance", AdvancedGraphDocument.Value.number(0)).asNumber(),
                state.getOrDefault(prefix + "control", AdvancedGraphDocument.Value.number(0)).asNumber());
        AdrcControllerMath.Result res = AdrcControllerMath.step(prev,
                frame.value(node, "target", operations).asNumber(),
                frame.value(node, "actual", operations).asNumber(),
                frame.value(node, "delta_time", operations).asNumber(),
                frame.value(node, "controller_bandwidth", operations).asNumber(),
                frame.value(node, "observer_bandwidth", operations).asNumber(),
                frame.value(node, "plant_gain", operations).asNumber(),
                frame.value(node, "output_limit", operations).asNumber());
        state.put(prefix + "estimate", AdvancedGraphDocument.Value.number(res.state().estimatedState()));
        state.put(prefix + "disturbance", AdvancedGraphDocument.Value.number(res.estimatedDisturbance()));
        state.put(prefix + "control", AdvancedGraphDocument.Value.number(res.control()));
        frame.seedOutput(node, "value", AdvancedGraphDocument.Value.number(res.control()));
        frame.seedOutput(node, "disturbance", AdvancedGraphDocument.Value.number(res.estimatedDisturbance()));
        return frame.cachedOutput(node, port);
    }

    // Get the nth-order ADRC value
    private AdvancedGraphDocument.Value adrcNthOrderValue(
            Frame frame, NodeInstruction node, String port, int[] operations
    ) {
        String prefix = node.id() + ":adrc_nth_order:";
        if (frame.value(node, AdvancedGraphCatalog.CONTROLLER_RESET_PORT, operations).asBoolean()) {
            state.remove(prefix + "state");
            state.remove(prefix + "disturbance");
            state.remove(prefix + "control");
            frame.seedOutput(node, "value", AdvancedGraphDocument.Value.number(0.0D));
            frame.seedOutput(node, "disturbance", AdvancedGraphDocument.Value.number(0.0D));
            return frame.cachedOutput(node, port);
        }
        double actual = frame.value(node, "actual", operations).asNumber();
        int order = normalizedNthOrder(frame.value(node, "order", operations).asNumber());
        AdrcControllerNthOrderMath.State prev = nthOrderState(state.get(prefix + "state"), order);
        if (prev == null) {
            prev = AdrcControllerNthOrderMath.State.initial(order, actual);
        }
        AdrcControllerNthOrderMath.Result res = AdrcControllerNthOrderMath.step(prev, order,
                frame.value(node, "target", operations).asNumber(),
                actual,
                frame.value(node, "delta_time", operations).asNumber(),
                frame.value(node, "controller_bandwidth", operations).asNumber(),
                frame.value(node, "observer_bandwidth", operations).asNumber(),
                frame.value(node, "plant_gain", operations).asNumber(),
                frame.value(node, "output_limit", operations).asNumber());
        state.put(prefix + "state", nthOrderStateValue(res.state()));
        state.put(prefix + "disturbance", AdvancedGraphDocument.Value.number(res.estimatedDisturbance()));
        state.put(prefix + "control", AdvancedGraphDocument.Value.number(res.control()));
        frame.seedOutput(node, "value", AdvancedGraphDocument.Value.number(res.control()));
        frame.seedOutput(node, "disturbance", AdvancedGraphDocument.Value.number(res.estimatedDisturbance()));
        return frame.cachedOutput(node, port);
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
        if (!payload.contains("z", Tag.TAG_COMPOUND)) {
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
            if (!zPayload.contains(key, Tag.TAG_COMPOUND)) {
                return null;
            }
            z[idx] = AdvancedGraphDocument.Value.fromTag(zPayload.getCompound(key)).asNumber();
        }
        return new AdrcControllerNthOrderMath.State(z, payload.getDouble("previous_control"));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                          MATH / STATE
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

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
    private double timerElapsed(NodeInstruction node) {
        if (!state.getOrDefault(node.id() + ":running", AdvancedGraphDocument.Value.bool(false)).asBoolean()) {
            return state.getOrDefault(node.id() + ":elapsed", AdvancedGraphDocument.Value.number(0)).asNumber();
        }
        double start = state.getOrDefault(node.id() + ":start", AdvancedGraphDocument.Value.number(gameTime())).asNumber();
        double duration = state.getOrDefault(node.id() + ":duration", AdvancedGraphDocument.Value.number(0)).asNumber();
        return Mth.clamp(gameTime() - start, 0, duration);
    }

    // Run the curve pulse
    private void executeCurvePulse(Frame frame, NodeInstruction node, int[] operations) {
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
                    curveInput(frame, node, "value",
                            AdvancedGraphCurve.DEFAULT_MAXIMUM, operations)));
            state.put(node.id() + ":curve_min", AdvancedGraphDocument.Value.number(
                    curveInput(frame, node, "min",
                            AdvancedGraphCurve.DEFAULT_MINIMUM, operations)));
            state.put(node.id() + ":curve_max", AdvancedGraphDocument.Value.number(
                    curveInput(frame, node, "max",
                            AdvancedGraphCurve.DEFAULT_MAXIMUM, operations)));
        }

        double progress = state.getOrDefault(node.id() + ":curve_progress",
                AdvancedGraphDocument.Value.number(0)).asNumber();
        double speed = Math.abs(curveInput(frame, node, "speed",
                AdvancedGraphCurve.DEFAULT_SPEED, operations));
        double nextProgress = AdvancedGraphCurve.advance(progress, speed, forward);
        state.put(node.id() + ":curve_progress", AdvancedGraphDocument.Value.number(nextProgress));

        if (inputDriven && speed > 0.0D
                && nextProgress > 0.0D && nextProgress < 1.0D) {
            activeCurveSweeps.add(node.id());
        } else if (inputDriven) {
            activeCurveSweeps.remove(node.id());
        }
        followExec(frame, node, "exec", operations);
    }

    // Get the curve input
    private double curveInput(Frame frame, NodeInstruction node, String port,
                              double fallback, int[] operations) {
        return node.hasInput(port) || node.defaultValue(port) != null
                ? frame.value(node, port, operations).asNumber() : fallback;
    }

    // Get the smooth value
    static double smoothValue(double prev, double val, double smoothing) {
        return Mth.lerp(1.0D - Mth.clamp(smoothing, 0.0D, 1.0D), prev, val);
    }

    // Check if the binding is active
    private boolean isBindingActive(String bindingId) {
        return bindingStates.getOrDefault(bindingId, controller.isGraphBindingActive(bindingId));
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
    private boolean recentlyPulsed(NodeInstruction node) {
        long delay = Math.max(2, node.data().getInt("ResetDelay"));
        long lastPulse = (long) state.getOrDefault(node.id() + ":last_pulse",
                AdvancedGraphDocument.Value.number(Long.MIN_VALUE)).asNumber();
        return gameTime() - lastPulse <= delay;
    }

    // Schedule the reset
    private void scheduleReset(NodeInstruction node) {
        int delay = Math.max(2, node.data().getInt("ResetDelay"));
        schedule(node.id(), delay, "reset:");
    }

    // Schedule the graph
    private void schedule(String nodeId, int ticks, String prefix) {
        if (eventScheduler.scheduledSize() >= MAX_SCHEDULED_EVENTS) {
            throw new IllegalStateException("Graph exceeded the 1024 scheduled event limit");
        }
        eventScheduler.removeScheduledIf(evt -> evt.id().equals(prefix + nodeId));
        eventScheduler.schedule(gameTime() + Math.max(1, ticks),
                new RuntimeEvent(prefix + nodeId, AdvancedGraphDocument.Value.number(0), null));
    }

    // Require the operation
    private static void requireOperation(int[] operations) {
        if (++operations[0] > MAX_OPERATIONS) {
            throw new IllegalStateException("Graph exceeded the 4096 operation event limit");
        }
    }

    // Check if the data port is writable
    private static boolean writableDataPort(Set<String> writablePorts, String port) {
        return writablePorts.contains(port)
                && !"exec".equals(port)
                && !"target".equals(port)
                && !"face".equals(port)
                && !"state_waterlogged".equals(port);
    }

    // Get the Set Data write ports
    private static Set<String> setDataWritePorts(
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
        return retainGroupedDataPorts(node, activePorts);
    }

    // Retain generated MAP writes until their present keys can be expanded safely
    private static Set<String> retainGroupedDataPorts(AdvancedGraphDocument.Node node,
                                                      Set<String> requestedPorts) {
        Set<String> retained = new LinkedHashSet<>();
        if (requestedPorts == null || requestedPorts.isEmpty()) {
            return retained;
        }
        for (String requestedPort : requestedPorts) {
            retained.add(inlineMapInputSource(node, requestedPort));
        }
        return retained;
    }

    // Get the parent MAP input for one exposed inline field
    private static String inlineMapInputSource(AdvancedGraphDocument.Node node, String port) {
        if (node == null || port == null || port.isBlank()) return port;
        String source = node.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG)
                .getCompound(port).getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG);
        return source.isBlank() ? port : source;
    }

    // Get the Set Data force-write ports
    private static Set<String> setDataForceWritePorts(AdvancedGraphDocument.Node node) {
        Set<String> forcedPorts = new LinkedHashSet<>();
        if (node == null) {
            return forcedPorts;
        }

        Set<String> writablePorts = AdvancedGraphCatalog.inputs(node).keySet();
        CompoundTag forceWriteInputs = node.data().getCompound("ForceWriteInputs");
        for (String port : forceWriteInputs.getAllKeys()) {
            if (forceWriteInputs.getBoolean(port)
                    && writableDataPort(writablePorts, port)) {
                forcedPorts.add(port);
            }
        }

        forcedPorts.addAll(CreateFantasizingGraphCompat.portsRequiringWrite(
                node, writablePorts));
        forcedPorts.addAll(NavigationTableGraphCompat.portsRequiringWrite(
                node, writablePorts));
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

    // Compare the graph values
    private static boolean compare(AdvancedGraphDocument.Value a, AdvancedGraphDocument.Value b, String operator) {
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

    // Get the data value
    private static AdvancedGraphDocument.Value dataValue(CompoundTag values, String key) {
        if (!values.contains(key)) return AdvancedGraphDocument.Value.number(0);
        return nbtValue(values.get(key));
    }

    // Get the list size
    public static int listSize(AdvancedGraphDocument.Value list) {
        return orderedListKeys(list).size();
    }

    // Get the list value
    public static AdvancedGraphDocument.Value listValue(AdvancedGraphDocument.Value list, double requestedIndex) {
        List<String> keys = orderedListKeys(list);
        if (keys.isEmpty()) return AdvancedGraphDocument.Value.number(0);
        int idx = Double.isFinite(requestedIndex) ? (int) Math.floor(requestedIndex) : 0;
        idx = Mth.clamp(idx, 0, keys.size() - 1);
        return dataValue(list.payload(), keys.get(idx));
    }

    // Get the split string
    static AdvancedGraphDocument.Value splitString(String input, String delimiter) {
        String val = input == null ? "" : input;
        String separator = delimiter == null ? "" : delimiter;
        CompoundTag values = new CompoundTag();
        if (separator.isEmpty()) {
            values.put("0", AdvancedGraphDocument.Value.string(val).toTag());
            return AdvancedGraphDocument.Value.list(values);
        }
        int part = 0;
        int start = 0;
        int separatorIndex;
        while ((separatorIndex = val.indexOf(separator, start)) >= 0) {
            values.put(Integer.toString(part++),
                    AdvancedGraphDocument.Value.string(val.substring(start, separatorIndex)).toTag());
            start = separatorIndex + separator.length();
        }
        values.put(Integer.toString(part),
                AdvancedGraphDocument.Value.string(val.substring(start)).toTag());
        return AdvancedGraphDocument.Value.list(values);
    }

    // Get the substring
    static String substring(String input, double requestedStart, double requestedEnd) {
        String val = input == null ? "" : input;
        int start = stringIndex(requestedStart, val.length());
        int end = stringIndex(requestedEnd, val.length());
        return end < start ? "" : val.substring(start, end);
    }

    // Find text within a string
    static int findInString(String input, String search) {
        String val = input == null ? "" : input;
        String needle = search == null ? "" : search;
        return needle.isEmpty() ? -1 : val.indexOf(needle);
    }

    // Get the string index
    private static int stringIndex(double requestedIndex, int maximum) {
        int idx = Double.isFinite(requestedIndex) ? (int) Math.floor(requestedIndex) : 0;
        return Mth.clamp(idx, 0, Math.max(0, maximum));
    }

    // Get the ordered list keys
    private static List<String> orderedListKeys(AdvancedGraphDocument.Value list) {
        List<String> keys = new ArrayList<>();
        if (list == null) return keys;
        for (String key : list.payload().getAllKeys()) {
            try {
                if (Integer.parseInt(key) >= 0) keys.add(key);
            } catch (NumberFormatException ignored) {
            }
        }
        keys.sort((first, second) -> Integer.compare(Integer.parseInt(first), Integer.parseInt(second)));
        return keys;
    }

    // Get the structured value
    // Resolve a dynamically exposed field from an inline MAP output.
    private static AdvancedGraphDocument.Value inlineMapOutput(
            Frame frame, NodeInstruction node, String port, int[] operations
    ) {
        CompoundTag mapping = node.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG)
                .getCompound(port);
        String source = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG);
        String key = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG);
        if (source.isBlank() || key.isBlank() || source.equals(port)) {
            return null;
        }
        return structuredValue(frame.output(node, source, operations), key);
    }

    public static AdvancedGraphDocument.Value structuredValue(AdvancedGraphDocument.Value val, String key) {
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
            if (isGraphValueTag(compound)) return AdvancedGraphDocument.Value.fromTag(compound);
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
            case Tag.TAG_BYTE -> AdvancedGraphDocument.Value.bool(((net.minecraft.nbt.NumericTag) val).getAsByte() != 0);
            case Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG,
                 Tag.TAG_FLOAT, Tag.TAG_DOUBLE -> AdvancedGraphDocument.Value.number(((net.minecraft.nbt.NumericTag) val).getAsDouble());
            case Tag.TAG_STRING -> AdvancedGraphDocument.Value.string(val.getAsString());
            default -> AdvancedGraphDocument.Value.string(val.getAsString());
        };
    }

    // Check whether an NBT compound encodes one graph value
    private static boolean isGraphValueTag(Tag tag) {
        return tag instanceof CompoundTag compound
                && compound.contains("Type", Tag.TAG_STRING)
                && compound.contains("Payload", Tag.TAG_COMPOUND);
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

    // Infer the concrete value stored inside an Any value
    private static AdvancedGraphDocument.Value inferBodyOverride(
            AdvancedGraphDocument.Value val
    ) {
        if (val == null || !"any".equals(val.type())) {
            return val;
        }

        CompoundTag payload = val.payload();
        if (payload.contains("Value", Tag.TAG_BYTE)) {
            return AdvancedGraphDocument.Value.bool(payload.getBoolean("Value"));
        }
        if (payload.contains("Value", Tag.TAG_ANY_NUMERIC)) {
            double number = payload.getDouble("Value");
            return Double.isFinite(number)
                    ? AdvancedGraphDocument.Value.number(number)
                    : val;
        }
        if (!payload.contains("Value", Tag.TAG_STRING)) {
            return val;
        }

        String raw = payload.getString("Value");
        String text = raw.trim();
        if ("true".equalsIgnoreCase(text)) {
            return AdvancedGraphDocument.Value.bool(true);
        }
        if ("false".equalsIgnoreCase(text)) {
            return AdvancedGraphDocument.Value.bool(false);
        }

        try {
            double number = Double.parseDouble(text);
            if (Double.isFinite(number)) {
                return AdvancedGraphDocument.Value.number(number);
            }
        } catch (NumberFormatException ignored) {
        }

        boolean mayBeJson = text.startsWith("{")
                || text.startsWith("[")
                || text.startsWith("\"");
        if (mayBeJson) {
            try {
                return jsonValue(JsonParser.parseString(text));
            } catch (RuntimeException ignored) {
            }
        }
        return AdvancedGraphDocument.Value.string(raw);
    }

    // Convert a graph value to the requested port type
    static AdvancedGraphDocument.Value convertValue(
            AdvancedGraphDocument.Value val,
            String targetType
    ) {
        if (val == null) {
            val = AdvancedGraphDocument.Value.string("");
        }
        if (targetType == null || targetType.isBlank()) {
            targetType = "string";
        }
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

    // Convert a String/Any value into a List or Map when it contains JSON
    private static AdvancedGraphDocument.Value structuredConversion(
            AdvancedGraphDocument.Value val,
            String targetType
    ) {
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

    // Read a graph value as a finite Number
    private static double numberValue(AdvancedGraphDocument.Value val) {
        if ("number".equals(val.type())) {
            return Double.isFinite(val.asNumber()) ? val.asNumber() : 0.0D;
        }
        if ("boolean".equals(val.type())) {
            return val.asBoolean() ? 1.0D : 0.0D;
        }
        if (!"string".equals(val.type())) {
            return 0.0D;
        }
        try {
            double parsed = Double.parseDouble(val.asString().trim());
            return Double.isFinite(parsed) ? parsed : 0.0D;
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    // Read a graph value as a Boolean
    private static boolean booleanValue(AdvancedGraphDocument.Value val) {
        if ("boolean".equals(val.type())) {
            return val.asBoolean();
        }
        if ("number".equals(val.type())) {
            return Double.isFinite(val.asNumber()) && val.asNumber() != 0.0D;
        }
        if (!"string".equals(val.type())) {
            return false;
        }

        String text = val.asString().trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || text.isEmpty()) {
            return false;
        }
        try {
            double parsed = Double.parseDouble(text);
            return Double.isFinite(parsed) && parsed != 0.0D;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // Read a graph value as a valid Direction ID
    private static String directionValue(AdvancedGraphDocument.Value val) {
        if (!"string".equals(val.type()) && !"direction".equals(val.type())) {
            return "";
        }
        String direction = val.asString().trim().toLowerCase(java.util.Locale.ROOT);
        return List.of("north", "east", "south", "west", "up", "down")
                .contains(direction) ? direction : "";
    }

    // Expose the value expression
    private interface ValueExpression {
        // Evaluate the value expression
        AdvancedGraphDocument.Value evaluate(Frame frame, int[] operations);
    }

    // Hold one graph evaluation frame
    private final class Frame {
        // Program
        private final CompiledProgram program;
        // Tracked outputs
        private final Map<String, AdvancedGraphDocument.Value> outputs = new HashMap<>();
        // Tracked evaluating outputs
        private final Set<String> evaluatingOutputs = new HashSet<>();
        // Executing nodes
        private final boolean[] executingNodes;
        // Operations
        private final int[] operations = {0};
        // Tracks whether live values are being recorded
        private final boolean recordLiveValues;

        // Initialize the frame
        private Frame(CompiledProgram program) {
            this(program, true);
        }

        // Initialize the frame
        private Frame(CompiledProgram program, boolean recordLiveValues) {
            this.program = program;
            this.executingNodes = new boolean[program.nodeCount()];
            this.recordLiveValues = recordLiveValues;
        }

        // Get the program
        private CompiledProgram program() {
            return program;
        }

        // Reset the frame
        private void reset() {
            outputs.clear();
            evaluatingOutputs.clear();
            java.util.Arrays.fill(executingNodes, false);
        }

        // Reset the operations
        private int[] resetOperations() {
            operations[0] = 0;
            return operations;
        }

        // Get the value
        private AdvancedGraphDocument.Value value(NodeInstruction node, String port, int[] operations) {
            String key = node.portKey(port);
            AdvancedGraphDocument.Value val = node.input(port).evaluate(this, operations);
            if (!node.hasInput(port)
                    && node.data().getBoolean(AdvancedGraphCatalog.COLLAPSE_INPUTS_TO_MAP_TAG)
                    && !AdvancedGraphCatalog.COLLAPSED_INPUT_MAP_PORT.equals(port)
                    && node.hasInput(AdvancedGraphCatalog.COLLAPSED_INPUT_MAP_PORT)) {
                AdvancedGraphDocument.Value map = node.input(AdvancedGraphCatalog.COLLAPSED_INPUT_MAP_PORT)
                        .evaluate(this, operations);
                if ("map".equals(map.type()) && map.payload().contains(port, Tag.TAG_COMPOUND)) {
                    val = convertValue(AdvancedGraphDocument.Value.fromTag(map.payload().getCompound(port)),
                            node.inputTypes().get(port));
                }
            }
            if (!node.hasInput(port)
                    && (node.defaultValue(port) == null
                    || node.data().getCompound("PrefilledInputs").contains(port))) {
                String group = AdvancedContraptionControllerBlockEntity.dataPortGroupFor(
                        node.source(), port);
                if (!group.isBlank()
                        && (node.hasInput(group) || node.defaultValue(group) != null)) {
                    AdvancedGraphDocument.Value map = value(node, group, operations);
                    if ("map".equals(map.type()) && map.payload().contains(port, Tag.TAG_COMPOUND)) {
                        val = convertValue(AdvancedGraphDocument.Value.fromTag(
                                map.payload().getCompound(port)), node.inputTypes().get(port));
                    }
                }
            }
            val = inlineMapInput(this, node, port, val, operations);
            recordLiveInput(node, key, port, val);
            return val;
        }

        // Combine explicitly supplied inline fields into their parent MAP input.
        private AdvancedGraphDocument.Value inlineMapInput(
                Frame frame, NodeInstruction node, String port,
                AdvancedGraphDocument.Value current, int[] operations
        ) {
            CompoundTag mappings = node.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG);
            if (mappings.isEmpty()) {
                return current;
            }
            AdvancedGraphDocument.Value normalized = inferBodyOverride(current);
            CompoundTag values = "map".equals(normalized.type())
                    ? normalized.payload().copy() : new CompoundTag();
            boolean matched = false;
            boolean changed = false;
            for (String inlinePort : mappings.getAllKeys()) {
                CompoundTag mapping = mappings.getCompound(inlinePort);
                if (!port.equals(mapping.getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG))) {
                    continue;
                }
                matched = true;
                if (!node.hasInput(inlinePort) && node.defaultValue(inlinePort) == null) {
                    continue;
                }
                String key = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG);
                if (key.isBlank()) {
                    continue;
                }
                values.put(key, frame.value(node, inlinePort, operations).toTag());
                changed = true;
            }
            if (!matched || !changed) {
                return current;
            }
            return AdvancedGraphDocument.Value.map(values);
        }

        // Get the output
        private AdvancedGraphDocument.Value output(NodeInstruction node, String port, int[] operations) {
            return outputFor(this, node, port, operations);
        }

        // Record the live input
        private void recordLiveInput(NodeInstruction node, String key, String port,
                                     AdvancedGraphDocument.Value val) {
            if (recordLiveValues) {
                boolean changed = putLiveInput(key, val);
                if (changed && !simulationOnly && controller != null) {
                    controller.persistGraphPortValue(
                            node.id(), node.data(), port, false, val);
                }
            }
        }

        // Seed the output
        private void seedOutput(NodeInstruction node, String port, AdvancedGraphDocument.Value val) {
            String key = node.portKey(port);
            outputs.put(key, val);
            if (recordLiveValues) {
                boolean changed = putLiveOutput(key, val);
                if (changed && !simulationOnly && controller != null) {
                    controller.persistGraphPortValue(
                            node.id(), node.data(), port, true, val);
                }
            }
        }

        // Get the cached output
        private AdvancedGraphDocument.Value cachedOutput(NodeInstruction node, String port) {
            return outputs.get(node.portKey(port));
        }

        // Begin the output
        private boolean beginOutput(NodeInstruction node, String port) {
            return evaluatingOutputs.add(node.portKey(port));
        }

        // End the output
        private void endOutput(NodeInstruction node, String port) {
            evaluatingOutputs.remove(node.portKey(port));
        }

        // Begin the execution
        private boolean beginExecution(NodeInstruction node) {
            int idx = node.index();
            if (executingNodes[idx]) {
                return false;
            }
            executingNodes[idx] = true;
            return true;
        }

        // End the execution
        private void endExecution(NodeInstruction node) {
            executingNodes[node.index()] = false;
        }
    }

    // Store the constant expression
    private record ConstantExpression(AdvancedGraphDocument.Value value) implements ValueExpression {
        // Evaluate the constant expression
        @Override
        public AdvancedGraphDocument.Value evaluate(Frame frame, int[] operations) {
            return value;
        }
    }

    // Handle the edge expression
    private static final class EdgeExpression implements ValueExpression {
        // Source object
        private final NodeInstruction from;
        // From port
        private final String fromPort;
        // Expected type
        private final String expectedType;

        // Initialize the edge expression
        private EdgeExpression(NodeInstruction from, String fromPort, String expectedType) {
            this.from = from;
            this.fromPort = fromPort;
            this.expectedType = expectedType;
        }

        // Evaluate the edge expression
        @Override
        public AdvancedGraphDocument.Value evaluate(Frame frame, int[] operations) {
            AdvancedGraphDocument.Value val = frame.output(from, fromPort, operations);
            return expectedType == null || "any".equals(expectedType)
                    ? val : convertValue(val, expectedType);
        }
    }

    // Store the exec edge
    private record ExecEdge(String edgeKey, NodeInstruction target, String incomingPort) {
    }

    // Handle the node instruction
    private static final class NodeInstruction {
        private static final ValueExpression ZERO = new ConstantExpression(AdvancedGraphDocument.Value.number(0));
        private static final ExecEdge[] NO_EXEC_EDGES = new ExecEdge[0];
        private static final String[] NO_PORTS = new String[0];
        // Index
        private final int index;
        // Node instruction source
        private final AdvancedGraphDocument.Node source;
        // Binding id
        private final String bindingId;
        // Variable
        private final String variable;
        // Trigger event
        private final String triggerEvent;
        // Input types
        private final Map<String, String> inputTypes;
        // Output types
        private final Map<String, String> outputTypes;
        // Tracked inputs
        private final Map<String, ValueExpression> inputs = new HashMap<>();
        // Tracked defaults
        private final Map<String, AdvancedGraphDocument.Value> defaults = new HashMap<>();
        // Tracked exec targets
        private final Map<String, ExecEdge[]> execTargets = new HashMap<>();
        // Tracked connected ports
        private final Set<String> connectedPorts = new HashSet<>();
        // Tracked connected output ports
        private final Set<String> connectedOutputPorts = new HashSet<>();
        // Tracked exec output ports
        private final Set<String> execOutputPorts = new LinkedHashSet<>();
        // Target write ports
        private final Set<String> targetWritePorts = new LinkedHashSet<>();
        // Tracked inactive branch outputs
        private final Map<String, Set<String>> inactiveBranchOutputs = new HashMap<>();
        // Tracked port keys
        private final Map<String, String> portKeys = new HashMap<>();
        // Input ports
        private final String[] inputPorts;
        // Current connected input ports
        private String[] connectedInputPorts = NO_PORTS;

        // Initialize the node instruction
        private NodeInstruction(int idx, AdvancedGraphDocument.Node src, String bindingId,
                                String variable, String triggerEvent, Map<String, String> inputTypes,
                                Map<String, String> outputTypes) {
            this.index = idx;
            this.source = src;
            this.bindingId = bindingId;
            this.variable = variable;
            this.triggerEvent = triggerEvent;
            this.inputTypes = Map.copyOf(inputTypes);
            this.outputTypes = Map.copyOf(outputTypes);
            this.inputPorts = this.inputTypes.keySet().toArray(String[]::new);
            for (String port : this.inputTypes.keySet()) {
                portKeys.put(port, src.id() + ":" + port);
            }
            for (String port : this.outputTypes.keySet()) {
                portKeys.putIfAbsent(port, src.id() + ":" + port);
            }
        }

        // Get the index
        private int index() {
            return index;
        }

        // Get the source
        private AdvancedGraphDocument.Node source() {
            return source;
        }

        // Get the id
        private String id() {
            return source.id();
        }

        // Get the type
        private String type() {
            return source.type();
        }

        // Get the data
        private CompoundTag data() {
            return source.data();
        }

        // Get the binding id
        private String bindingId() {
            return bindingId;
        }

        // Get the variable
        private String variable() {
            return variable;
        }

        // Trigger the event
        private String triggerEvent() {
            return triggerEvent;
        }

        // Get the input types
        private Map<String, String> inputTypes() {
            return inputTypes;
        }

        // Get the output types
        private Map<String, String> outputTypes() {
            return outputTypes;
        }

        // Get the input ports
        private String[] inputPorts() {
            return inputPorts;
        }

        // Get the connected input ports
        private String[] connectedInputPorts() {
            return connectedInputPorts;
        }

        // Compile the connected input ports
        private void compileConnectedInputPorts() {
            if (connectedPorts.isEmpty()) {
                connectedInputPorts = NO_PORTS;
                return;
            }
            List<String> ports = new ArrayList<>();
            for (String port : inputPorts) {
                if (connectedPorts.contains(port)) {
                    ports.add(port);
                }
            }
            connectedInputPorts = ports.toArray(String[]::new);
        }

        // Get the port key
        private String portKey(String port) {
            return portKeys.computeIfAbsent(port, key -> id() + ":" + key);
        }

        // Get the input
        private ValueExpression input(String port) {
            return inputs.getOrDefault(port, ZERO);
        }

        // Check if this has input
        private boolean hasInput(String port) {
            return connectedPorts.contains(port);
        }

        // Check if this has connected output
        private boolean hasConnectedOutput(String port) {
            return connectedOutputPorts.contains(port);
        }

        // Create the default value
        private AdvancedGraphDocument.Value defaultValue(String port) {
            return defaults.get(port);
        }

        // Get the exec targets
        private ExecEdge[] execTargets(String port) {
            return execTargets.getOrDefault(port, NO_EXEC_EDGES);
        }

        // Check if this has exec output
        private boolean hasExecOutput(String port) {
            return execOutputPorts.contains(port);
        }

        // Get the exec output ports
        private Set<String> execOutputPorts() {
            return execOutputPorts;
        }

        // Get the target write ports
        private Set<String> targetWritePorts() {
            return targetWritePorts;
        }

        // Get the inactive branch output bindings
        private Set<String> inactiveBranchOutputBindings(String selectedPort) {
            return inactiveBranchOutputs.getOrDefault(selectedPort, Set.of());
        }
    }

    // Get the output bindings which must be cleared for an inactive branch
    private static Set<String> inactiveBranchOutputBindings(
            AdvancedGraphDocument graph,
            String branchId,
            String selectedPort
    ) {
        if (graph == null) {
            return Set.of();
        }
        return AdvancedGraphProgram.compile(graph)
                .inactiveBranchOutputBindings(branchId, selectedPort);
    }

    // Handle the compiled program
    private static final class CompiledProgram {
        private static final NodeInstruction[] NO_NODES = new NodeInstruction[0];
        // Graph
        private final AdvancedGraphDocument graph;
        // Tracked nodes
        private final Map<String, NodeInstruction> nodes;
        // Tick nodes
        private final NodeInstruction[] tickNodes;
        // Periodic nodes
        private final NodeInstruction[] periodicNodes;
        // Graph ready nodes
        private final NodeInstruction[] graphReadyNodes;
        // Input nodes
        private final NodeInstruction[] inputNodes;
        // Pulse on change nodes
        private final NodeInstruction[] pulseOnChangeNodes;
        // Physical interaction nodes
        private final NodeInstruction[] physicalInteractionNodes;
        // Profiler nodes
        private final NodeInstruction[] profilerNodes;
        // HUD nodes
        private final NodeInstruction[] hudNodes;
        // Passive nodes
        private final NodeInstruction[] passiveNodes;
        // Ship command nodes
        private final NodeInstruction[] shipCommandNodes;
        // Tracked trigger nodes
        private final Map<String, NodeInstruction[]> triggerNodes;
        // Tracked key nodes
        private final Map<String, NodeInstruction[]> keyNodes;
        // Tracked mouse nodes
        private final Map<String, NodeInstruction[]> mouseNodes;
        // Input nodes by binding
        private final Map<String, NodeInstruction[]> inputNodesByBinding;
        // Wireless input nodes indexed by binding
        private final Map<String, NodeInstruction[]> wirelessInputNodesByBinding;
        // Tracked channel change nodes
        private final Map<String, NodeInstruction[]> channelChangeNodes;
        // Tracked redstone change nodes
        private final Map<String, NodeInstruction[]> redstoneChangeNodes;
        // Tracked variable change nodes
        private final Map<String, NodeInstruction[]> variableChangeNodes;
        // Tracked polled bindings
        private final Set<String> polledBindings;
        // Tracked graph owned bindings
        private final Set<String> graphOwnedBindings;
        // Tracks whether connected HUD inputs are available
        private final boolean hasConnectedHudInputs;
        // Tracks whether HUD inputs sampled is set
        private boolean hudInputsSampled;

        // Initialize the compiled program
        private CompiledProgram(AdvancedGraphDocument graph,
                                Map<String, NodeInstruction> nodes,
                                NodeInstruction[] tickNodes,
                                NodeInstruction[] periodicNodes,
                                NodeInstruction[] graphReadyNodes,
                                NodeInstruction[] inputNodes,
                                NodeInstruction[] pulseOnChangeNodes,
                                 NodeInstruction[] physicalInteractionNodes,
                                 NodeInstruction[] profilerNodes,
                                 NodeInstruction[] hudNodes,
                                 NodeInstruction[] passiveNodes,
                                 NodeInstruction[] shipCommandNodes,
                                 Map<String, NodeInstruction[]> triggerNodes,
                                 Map<String, NodeInstruction[]> keyNodes,
                                 Map<String, NodeInstruction[]> mouseNodes,
                                 Map<String, NodeInstruction[]> inputNodesByBinding,
                                Map<String, NodeInstruction[]> wirelessInputNodesByBinding,
                                Map<String, NodeInstruction[]> channelChangeNodes,
                                Map<String, NodeInstruction[]> redstoneChangeNodes,
                                Map<String, NodeInstruction[]> variableChangeNodes,
                                Set<String> polledBindings,
                                Set<String> graphOwnedBindings) {
            this.graph = graph;
            this.nodes = Map.copyOf(nodes);
            this.tickNodes = tickNodes;
            this.periodicNodes = periodicNodes;
            this.graphReadyNodes = graphReadyNodes;
            this.inputNodes = inputNodes;
            this.pulseOnChangeNodes = pulseOnChangeNodes;
            this.physicalInteractionNodes = physicalInteractionNodes;
            this.profilerNodes = profilerNodes;
            this.hudNodes = hudNodes;
            this.passiveNodes = passiveNodes;
            this.shipCommandNodes = shipCommandNodes;
            this.triggerNodes = Map.copyOf(triggerNodes);
            this.keyNodes = Map.copyOf(keyNodes);
            this.mouseNodes = Map.copyOf(mouseNodes);
            this.inputNodesByBinding = Map.copyOf(inputNodesByBinding);
            this.wirelessInputNodesByBinding = Map.copyOf(wirelessInputNodesByBinding);
            this.channelChangeNodes = Map.copyOf(channelChangeNodes);
            this.redstoneChangeNodes = Map.copyOf(redstoneChangeNodes);
            this.variableChangeNodes = Map.copyOf(variableChangeNodes);
            this.polledBindings = Set.copyOf(polledBindings);
            this.graphOwnedBindings = Set.copyOf(graphOwnedBindings);
            boolean connectedHudInputs = false;
            for (NodeInstruction node : hudNodes) {
                if (node.connectedInputPorts().length > 0) {
                    connectedHudInputs = true;
                    break;
                }
            }
            this.hasConnectedHudInputs = connectedHudInputs;
        }

        // Create an empty compiled program
        private static CompiledProgram empty(AdvancedGraphDocument graph) {
            return compile(graph, graph);
        }

        // Compile the compiled program
        private static CompiledProgram compile(AdvancedGraphDocument sourceGraph,
                                               AdvancedGraphDocument executionGraph) {
            if (sourceGraph == null) {
                sourceGraph = new AdvancedGraphDocument();
            }
            if (executionGraph == null) {
                executionGraph = sourceGraph;
            }
            // ------------------------------------PROGRAM STATE------------------------------------
            Map<String, Integer> indexes = new LinkedHashMap<>();
            Map<String, NodeInstruction> nodes = new LinkedHashMap<>();
            Map<String, List<AdvancedGraphDocument.Edge>> incoming = new HashMap<>();
            Map<PortKey, List<AdvancedGraphDocument.Edge>> outgoing = new HashMap<>();
            List<NodeInstruction> tickNodes = new ArrayList<>();
            List<NodeInstruction> periodicNodes = new ArrayList<>();
            List<NodeInstruction> graphReadyNodes = new ArrayList<>();
            List<NodeInstruction> inputNodes = new ArrayList<>();
            List<NodeInstruction> pulseOnChangeNodes = new ArrayList<>();
            List<NodeInstruction> physicalInteractionNodes = new ArrayList<>();
            List<NodeInstruction> profilerNodes = new ArrayList<>();
            List<NodeInstruction> hudNodes = new ArrayList<>();
            List<NodeInstruction> passiveNodes = new ArrayList<>();
            List<NodeInstruction> shipCommandNodes = new ArrayList<>();
            Map<String, List<NodeInstruction>> triggerNodes = new HashMap<>();
            Map<String, List<NodeInstruction>> keyNodes = new HashMap<>();
            Map<String, List<NodeInstruction>> mouseNodes = new HashMap<>();
            Map<String, List<NodeInstruction>> inputNodesByBinding = new HashMap<>();
            Map<String, List<NodeInstruction>> wirelessInputNodesByBinding = new HashMap<>();
            Map<String, List<NodeInstruction>> channelChangeNodes = new HashMap<>();
            Map<String, List<NodeInstruction>> redstoneChangeNodes = new HashMap<>();
            Map<String, List<NodeInstruction>> variableChangeNodes = new HashMap<>();
            Set<String> polledBindings = new LinkedHashSet<>();
            Set<String> graphOwnedBindings = new LinkedHashSet<>();

            // ------------------------------------NODE INSTRUCTIONS------------------------------------
            int idx = 0;
            for (AdvancedGraphDocument.Node src : executionGraph.nodes()) {
                if (src == null) {
                    continue;
                }
                indexes.put(src.id(), idx);
                String binding = bindingId(src);
                NodeInstruction node = new NodeInstruction(idx++, src, binding,
                        src.data().getString("Variable"),
                        src.data().getString("Event"),
                        AdvancedGraphCatalog.inputs(src),
                        AdvancedGraphCatalog.outputs(src));
                nodes.put(src.id(), node);
                if ("variable_get".equals(src.type())
                        || "portable_tracker".equals(src.type())
                        || "controller_tracker".equals(src.type())
                        || AdvancedGraphCatalog.isShipControlPassiveType(src.type())) {
                    passiveNodes.add(node);
                } else if (AdvancedGraphCatalog.isShipControlType(src.type())) {
                    shipCommandNodes.add(node);
                }
                if (isGraphOwnedBinding(src, binding)) {
                    graphOwnedBindings.add(binding);
                }
                switch (src.type()) {
                    case "event_tick" -> tickNodes.add(node);
                    case "event_graph_ready" -> graphReadyNodes.add(node);
                    case "event_periodic" -> periodicNodes.add(node);
                    case "event_trigger" -> add(triggerNodes, node.triggerEvent(), node);
                    case "event_named_controller" -> add(triggerNodes, "named:" + node.triggerEvent().trim(), node);
                    case "event_key" -> add(keyNodes, binding, node);
                    case "mouse_input" -> add(mouseNodes, mouseInput(src), node);
                    case "controller_channel_input", "gamepad_input", "local_redstone_input",
                         "discovered_target_input", "linker_face_input" -> {
                        inputNodes.add(node);
                        add(inputNodesByBinding, binding, node);
                        if (!binding.isBlank()) {
                            polledBindings.add(binding);
                        }
                    }
                    case "wireless_frequency_input" -> {
                        inputNodes.add(node);
                        add(inputNodesByBinding, binding, node);
                        if (!binding.isBlank()) {
                            polledBindings.add(binding);
                        }
                    }
                    case "event_channel_change" -> {
                        add(channelChangeNodes, binding, node);
                        if (!binding.isBlank()) {
                            polledBindings.add(binding);
                        }
                    }
                    case "event_redstone_change" -> {
                        add(redstoneChangeNodes, binding, node);
                        if (!binding.isBlank()) {
                            polledBindings.add(binding);
                        }
                    }
                    case "event_variable_change" -> add(variableChangeNodes, node.variable(), node);
                    case "event_physical_interaction" -> physicalInteractionNodes.add(node);
                    case "pulse_on_change", "event_value_change" -> pulseOnChangeNodes.add(node);
                    case "profiler_fps", "profiler_mspt", "profiler_tps", "profiler_frametime" ->
                            profilerNodes.add(node);
                    case "hud_element", "advanced_hud_element", "acc_display_widget", "acc_hologram_widget",
                            "acc_display_graph", "acc_display_plotter", "acc_display_external",
                            "acc_display_crn" ->
                            hudNodes.add(node);
                    default -> {
                    }
                }
            }

            // ------------------------------------EDGE INDEX------------------------------------
            for (AdvancedGraphDocument.Edge edge : executionGraph.edges()) {
                if (edge == null || !nodes.containsKey(edge.fromNode()) || !nodes.containsKey(edge.toNode())) {
                    continue;
                }
                incoming.computeIfAbsent(edge.toNode(), ignored -> new ArrayList<>()).add(edge);
                outgoing.computeIfAbsent(new PortKey(edge.fromNode(), edge.fromPort()), ignored -> new ArrayList<>()).add(edge);
                NodeInstruction src = nodes.get(edge.fromNode());
                if (src != null && !"exec".equals(src.outputTypes().get(edge.fromPort()))) {
                    src.connectedOutputPorts.add(edge.fromPort());
                }
            }

            // ------------------------------------PASSIVE NODES------------------------------------
            for (NodeInstruction node : nodes.values()) {
                compileInputs(node, nodes, incoming.getOrDefault(node.id(), List.of()));
                node.compileConnectedInputPorts();
                compileExecOutputs(node, nodes, outgoing);
                compileTargetWrites(node, incoming.getOrDefault(node.id(), List.of()));
                if ("branch".equals(node.type())) {
                    node.inactiveBranchOutputs.put("true",
                            inactiveBranchOutputBindings(executionGraph, node.id(), "true"));
                    node.inactiveBranchOutputs.put("false",
                            inactiveBranchOutputBindings(executionGraph, node.id(), "false"));
                }
            }

            // ------------------------------------COMPILED PROGRAM------------------------------------
            return new CompiledProgram(sourceGraph, nodes,
                    array(tickNodes), array(periodicNodes), array(graphReadyNodes), array(inputNodes), array(pulseOnChangeNodes),
                    array(physicalInteractionNodes), array(profilerNodes), array(hudNodes),
                    array(passiveNodes), array(shipCommandNodes),
                    freeze(triggerNodes), freeze(keyNodes), freeze(mouseNodes), freeze(inputNodesByBinding),
                    freeze(wirelessInputNodesByBinding),
                    freeze(channelChangeNodes), freeze(redstoneChangeNodes), freeze(variableChangeNodes),
                    polledBindings, graphOwnedBindings);
        }

        // Compile the inputs
        private static void compileInputs(NodeInstruction node, Map<String, NodeInstruction> nodes,
                                          List<AdvancedGraphDocument.Edge> incoming) {
            Map<String, AdvancedGraphDocument.Edge> dataInputs = new HashMap<>();
            for (AdvancedGraphDocument.Edge edge : incoming) {
                String expectedType = node.inputTypes.get(edge.toPort());
                if ("exec".equals(expectedType)) {
                    continue;
                }
                dataInputs.putIfAbsent(edge.toPort(), edge);
                node.connectedPorts.add(edge.toPort());
            }
            Set<String> ports = new LinkedHashSet<>(node.inputTypes.keySet());
            ports.addAll(dataInputs.keySet());
            CompoundTag defaults = node.data().getCompound("Defaults");
            ports.addAll(defaults.getAllKeys());
            for (String port : ports) {
                node.inputs.put(port, compileInput(node, nodes, dataInputs.get(port), port));
                AdvancedGraphDocument.Value defaultValue = defaultValue(node.source(), port);
                if (defaultValue != null) {
                    node.defaults.put(port, defaultValue);
                }
            }
        }

        // Compile the input
        private static ValueExpression compileInput(NodeInstruction node, Map<String, NodeInstruction> nodes,
                                                    AdvancedGraphDocument.Edge edge, String port) {
            if (edge != null) {
                NodeInstruction from = nodes.get(edge.fromNode());
                if (from != null) {
                    return new EdgeExpression(from, edge.fromPort(), node.inputTypes.get(port));
                }
            }
            AdvancedGraphDocument.Value defaultValue = defaultValue(node.source(), port);
            return new ConstantExpression(defaultValue == null ? AdvancedGraphDocument.Value.number(0) : defaultValue);
        }

        // Compile the exec outputs
        private static void compileExecOutputs(NodeInstruction node,
                                               Map<String, NodeInstruction> nodes,
                                               Map<PortKey, List<AdvancedGraphDocument.Edge>> outgoing) {
            Map<String, String> outputTypes = AdvancedGraphCatalog.outputs(node.source());
            for (Map.Entry<String, String> entry : outputTypes.entrySet()) {
                if ("exec".equals(entry.getValue())) {
                    node.execOutputPorts.add(entry.getKey());
                }
            }
            for (String port : node.execOutputPorts) {
                List<ExecEdge> edges = new ArrayList<>();
                for (AdvancedGraphDocument.Edge edge : outgoing.getOrDefault(new PortKey(node.id(), port), List.of())) {
                    NodeInstruction target = nodes.get(edge.toNode());
                    if (target != null) {
                        edges.add(new ExecEdge(executionEdgeKey(edge), target, edge.toPort()));
                    }
                }
                node.execTargets.put(port, edges.toArray(ExecEdge[]::new));
            }
        }

        // Compile the target writes
        private static void compileTargetWrites(NodeInstruction node, List<AdvancedGraphDocument.Edge> incoming) {
            if ("set_block_data".equals(node.type())) {
                node.targetWritePorts.addAll(
                        setDataWritePorts(node.source(), incoming));
                return;
            }
            if ("direct_target_output".equals(node.type()) || "linker_face_output".equals(node.type())) {
                boolean hasValueInput = false;
                for (AdvancedGraphDocument.Edge edge : incoming) {
                    if ("value".equals(edge.toPort())) {
                        hasValueInput = true;
                        continue;
                    }
                    if (directTargetDataPort(node.inputTypes, edge.toPort())) {
                        node.targetWritePorts.add(edge.toPort());
                    }
                }
                CompoundTag prefilledInputs = node.data().getCompound("PrefilledInputs");
                for (String port : node.data().getCompound("Defaults").getAllKeys()) {
                    if (!prefilledInputs.contains(port) && directTargetDataPort(node.inputTypes, port)) {
                        node.targetWritePorts.add(port);
                    }
                }
                if (hasValueInput || node.targetWritePorts.isEmpty()) {
                    node.targetWritePorts.add("direct_signal");
                }
            }
        }

        // Create the default value
        private static AdvancedGraphDocument.Value defaultValue(AdvancedGraphDocument.Node node, String port) {
            CompoundTag defaults = node.data().getCompound("Defaults");
            if (defaults.contains(port)) {
                return AdvancedGraphDocument.Value.fromTag(defaults.getCompound(port));
            }
            if (AdvancedGraphCatalog.isShipCouplerCommandType(node.type())
                    && "endpoint".equals(port)) {
                return AdvancedGraphDocument.Value.number(-1.0D);
            }
            if (port == null || port.isEmpty()) {
                return null;
            }
            String property = Character.toUpperCase(port.charAt(0)) + port.substring(1);
            if (node.data().contains(property, Tag.TAG_DOUBLE)
                    || node.data().contains(property, Tag.TAG_INT)
                    || node.data().contains(property, Tag.TAG_FLOAT)) {
                return AdvancedGraphDocument.Value.number(node.data().getDouble(property));
            }
            if (node.data().contains(property, Tag.TAG_BYTE)) {
                return AdvancedGraphDocument.Value.bool(node.data().getBoolean(property));
            }
            if (node.data().contains(property, Tag.TAG_STRING)) {
                return AdvancedGraphDocument.Value.string(node.data().getString(property));
            }
            return null;
        }

        // Get the graph
        private AdvancedGraphDocument graph() {
            return graph;
        }

        // Get the node count
        private int nodeCount() {
            return nodes.size();
        }

        // Get the nodes
        private Map<String, NodeInstruction> nodes() {
            return nodes;
        }

        // Get the node
        private NodeInstruction node(String id) {
            return nodes.get(id);
        }

        // Get the function node
        private NodeInstruction functionNode(String functionId, String sourceNodeId) {
            for (NodeInstruction node : nodes.values()) {
                if (functionId.equals(node.data().getString(AdvancedGraphFunctions.RUNTIME_FUNCTION_ID))
                        && sourceNodeId.equals(node.data().getString(
                        AdvancedGraphFunctions.RUNTIME_SOURCE_NODE_ID))) {
                    return node;
                }
            }
            return null;
        }

        // Get the function call
        private NodeInstruction functionCall(String functionId) {
            for (NodeInstruction node : nodes.values()) {
                if (AdvancedGraphFunctions.CALL_TYPE.equals(node.type())
                        && functionId.equals(node.data().getString(AdvancedGraphFunctions.FUNCTION_ID))) {
                    return node;
                }
            }
            return null;
        }

        // Update the nodes
        private NodeInstruction[] tickNodes() {
            return tickNodes;
        }

        // Get the periodic nodes
        private NodeInstruction[] periodicNodes() {
            return periodicNodes;
        }

        // Get the graph ready nodes
        private NodeInstruction[] graphReadyNodes() {
            return graphReadyNodes;
        }

        // Get the input nodes
        private NodeInstruction[] inputNodes() {
            return inputNodes;
        }

        // Get the pulse on change nodes
        private NodeInstruction[] pulseOnChangeNodes() {
            return pulseOnChangeNodes;
        }

        // Get the physical interaction nodes
        private NodeInstruction[] physicalInteractionNodes() {
            return physicalInteractionNodes;
        }

        // Get the profiler nodes
        private NodeInstruction[] profilerNodes() {
            return profilerNodes;
        }

        // Get the HUD nodes
        private NodeInstruction[] hudNodes() {
            return hudNodes;
        }

        // Get the passive nodes
        private NodeInstruction[] passiveNodes() {
            return passiveNodes;
        }

        // Get the ship command nodes
        private NodeInstruction[] shipCommandNodes() {
            return shipCommandNodes;
        }

        // Get the polled bindings
        private Set<String> polledBindings() {
            return polledBindings;
        }

        // Get the graph owned bindings
        private Set<String> graphOwnedBindings() {
            return graphOwnedBindings;
        }

        // Check if this has automatic event nodes
        private boolean hasAutomaticEventNodes() {
            return tickNodes.length > 0 || periodicNodes.length > 0 || pulseOnChangeNodes.length > 0;
        }

        // Check if this needs regular tick
        private boolean needsRegularTick(boolean samplePassiveOutputs) {
            return hasAutomaticEventNodes() || profilerNodes.length > 0
                    || samplePassiveOutputs && needsHudInputSampling()
                    || samplePassiveOutputs && passiveNodes.length > 0
                    || shipCommandNodes.length > 0;
        }

        // Check if this needs HUD input sampling
        private boolean needsHudInputSampling() {
            return hudNodes.length > 0 && (!hudInputsSampled || hasConnectedHudInputs);
        }

        // Begin the HUD input sample
        private boolean beginHudInputSample() {
            boolean sampleStaticInputs = !hudInputsSampled;
            hudInputsSampled = true;
            return sampleStaticInputs;
        }

        // Trigger the nodes
        private NodeInstruction[] triggerNodes(String eventId) {
            return triggerNodes.getOrDefault(eventId, NO_NODES);
        }

        // Handle key nodes for event
        private NodeInstruction[] keyNodesForEvent(String eventId) {
            return nodesMatchingEvent(keyNodes, eventId, "key:");
        }

        // Handle mouse nodes for event
        private NodeInstruction[] mouseNodesForEvent(String eventId) {
            return nodesMatchingEvent(mouseNodes, eventId, "mouse:");
        }

        // Get the input nodes for event
        private NodeInstruction[] inputNodesForEvent(String eventId) {
            return nodesMatchingEvent(inputNodesByBinding, eventId, "input:");
        }

        // Get the wireless input nodes for channel event
        private NodeInstruction[] wirelessInputNodesForChannelEvent(String eventId) {
            return nodesMatchingEvent(wirelessInputNodesByBinding, eventId, "channel:");
        }

        // Get the channel change nodes for event
        private NodeInstruction[] channelChangeNodesForEvent(String eventId) {
            return nodesMatchingEvent(channelChangeNodes, eventId, "channel:");
        }

        // Get the redstone change nodes for event
        private NodeInstruction[] redstoneChangeNodesForEvent(String eventId) {
            return nodesMatchingEvent(redstoneChangeNodes, eventId, "channel:");
        }

        // Get the variable change nodes
        private NodeInstruction[] variableChangeNodes(String variable) {
            return variableChangeNodes.getOrDefault(variable, NO_NODES);
        }

        // Get the nodes matching event
        private static NodeInstruction[] nodesMatchingEvent(
                Map<String, NodeInstruction[]> src,
                String eventId,
                String prefix) {
            if (src == null || src.isEmpty() || eventId == null || prefix == null || !eventId.startsWith(prefix)) {
                return NO_NODES;
            }
            List<NodeInstruction> matches = new ArrayList<>();
            src.forEach((binding, nodes) -> {
                if (eventMatchesBinding(eventId, prefix, binding)) {
                    matches.addAll(List.of(nodes));
                }
            });
            return matches.toArray(NodeInstruction[]::new);
        }

        // Check if the event matches the binding
        private static boolean eventMatchesBinding(String eventId, String prefix, String binding) {
            return binding != null && !binding.isBlank() && eventId.startsWith(prefix + binding)
                    && (eventId.length() == prefix.length() + binding.length()
                    || eventId.charAt(prefix.length() + binding.length()) == ':');
        }

        // Add the compiled program
        private static void add(Map<String, List<NodeInstruction>> target, String key, NodeInstruction node) {
            if (target == null || node == null || key == null || key.isBlank()) {
                return;
            }
            target.computeIfAbsent(key, ignored -> new ArrayList<>()).add(node);
        }

        // Get the array
        private static NodeInstruction[] array(List<NodeInstruction> nodes) {
            return nodes.toArray(NodeInstruction[]::new);
        }

        // Get the freeze
        private static Map<String, NodeInstruction[]> freeze(Map<String, List<NodeInstruction>> src) {
            Map<String, NodeInstruction[]> frozen = new HashMap<>();
            src.forEach((key, val) -> frozen.put(key, array(val)));
            return frozen;
        }
    }

    // Get the binding id
    private static String bindingId(AdvancedGraphDocument.Node node) {
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

    // Check if this is a graph owned binding
    private static boolean isGraphOwnedBinding(AdvancedGraphDocument.Node node, String binding) {
        return node.data().getBoolean("GraphOwnedBinding")
                || (binding != null && binding.startsWith("graph_"))
                || node.data().getString("RouteBindingId").startsWith("graph_");
    }

    // Store the port key
    private record PortKey(String nodeId, String port) {
    }

    // Store the runtime event
    private record RuntimeEvent(String id, AdvancedGraphDocument.Value data,
                                @Nullable UUID triggeringPlayerId) {
    }

    // Create the default portable tracking value
    private static AdvancedGraphDocument.Value defaultPortableTrackingValue(String port) {
        return switch (port == null ? "" : port) {
            case "available", "holding_player", "on_lectern", "is_player", "is_mannequin",
                 "is_armor_stand" -> AdvancedGraphDocument.Value.bool(false);
            case "dimension", "sub_level", "player_name", "player_uuid", "wearer_type",
                 "wearer_name", "wearer_uuid" ->
                    AdvancedGraphDocument.Value.string("");
            case "player_facing", "looking_at" -> AdvancedGraphDocument.Value.map(new CompoundTag());
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Get the collision detection distance
    private double collisionDetectionDistance(
            Frame frame, NodeInstruction node, int[] operations
    ) {
        if (!"ship_telemetry".equals(node.type())) {
            return AdvancedGraphCatalog.DEFAULT_COLLISION_DETECTION_DISTANCE;
        }
        String input = "collision_detection_distance";
        double val = node.hasInput(input) || node.defaultValue(input) != null
                ? frame.value(node, input, operations).asNumber()
                : AdvancedGraphCatalog.DEFAULT_COLLISION_DETECTION_DISTANCE;
        return AdvancedGraphCatalog.normalizeCollisionDetectionDistance(val);
    }

    // Get the collision poll rate
    private double collisionPollRate(
            Frame frame, NodeInstruction node, int[] operations
    ) {
        if (!"ship_telemetry".equals(node.type())) {
            return AdvancedGraphCatalog.DEFAULT_COLLISION_POLL_RATE;
        }
        String input = "collision_poll_rate";
        double val = node.hasInput(input) || node.defaultValue(input) != null
                ? frame.value(node, input, operations).asNumber()
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
}
