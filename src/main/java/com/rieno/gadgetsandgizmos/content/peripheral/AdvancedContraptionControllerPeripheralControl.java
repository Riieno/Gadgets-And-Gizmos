package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptGraphCodec;
import com.rieno.gadgetsandgizmos.lib.script.ScriptArguments;
import com.rieno.gadgetsandgizmos.lib.script.ScriptDocumentation;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunctionBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethodDoc;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralNetwork;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphEditingService;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.export.AdvancedGraphLuaExporter;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphMutation;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphNodeAlias;
import com.rieno.gadgetsandgizmos.lib.graph.edit.VersionedGraphEditor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Expose Advanced Contraption Controller controls and telemetry to ComputerCraft
@PeripheralTypeDoc("advanced_contraption_controller")
public class AdvancedContraptionControllerPeripheralControl extends AnalogueContraptionControllerPeripheralControl {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Graph API version
    private static final int GRAPH_API_VERSION = 4;

    // Stable graph API method order
    private static final List<String> GRAPH_API_METHOD_NAMES = List.of(
            "getGraphApiVersion",
            "listGraphApiMethods",
            "getGraphApiHelp",
            "getGraph",
            "getGraphNodeOutputs",
            "listGraphNodeTypes",
            "mutateGraph",
            "validateGraph",
            "applyGraph",
            "exportGraphLua",
            "getGraphStatus",
            "listGraphVariables",
            "getGraphVariable",
            "setGraphVariable",
            "publishNamedEvent",
            "receiveNamedEvent",
            "triggerGraphEvent",
            "tryTriggerGraphEvent",
            "getGraphDiagnostics",
            "validateDraft",
            "applyDraft");

    // Advanced
    private final AdvancedContraptionControllerBlockEntity advanced;

    // Versioned graph editor
    private final AdvancedGraphEditingService graphEditor;

    // Deterministic ACC graph to Lua exporter
    private final AdvancedGraphLuaExporter graphExporter;

    // Server-authoritative SCM schedule bridge
    private final ControllerScheduleControl scheduleBridge;

    // Native wireless target hub
    private ScriptPeripheralNetwork network;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller peripheral
    public AdvancedContraptionControllerPeripheralControl(AdvancedContraptionControllerBlockEntity blockEntity) {
        super(blockEntity, "advanced_contraption_controller");
        this.advanced = blockEntity;
        this.graphEditor = new AdvancedGraphEditingService(blockEntity);
        this.graphExporter = new AdvancedGraphLuaExporter(blockEntity);
        this.scheduleBridge = new ControllerScheduleControl(blockEntity);
        
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Expose the native hub contract without changing the ACC peripheral type
    @Override
    public Set<String> getAdditionalTypes(){
        return Set.of("peripheral_hub");
    }
// List the configured targets which currently expose a CC peripheral
    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "getPeripherals", signature = "getPeripherals(): table",
            description = "Maps linked target IDs such as thruster_0 to native names accepted by peripheral.wrap.")
    public final Map<String, String> getPeripherals(){
        return network.getPeripherals();
    }

    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "peripheral", signature = "peripheral(id: string): string",
            description = "Returns the same native peripheral name as getPeripherals()[id]; errors for unavailable targets.")
    public final String peripheral(String id) throws ScriptException{
        return network.peripheral(id);
    }

    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "getNamesRemote", signature = "getNamesRemote(): table",
            description = "Lists native names for the ACC's currently available target peripherals.")
    public final List<String> getNamesRemote(){
        return network.getNamesRemote();
    }

    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "isPresentRemote", signature = "isPresentRemote(name: string): boolean",
            description = "Checks whether a native target name is available through this ACC.")
    public final boolean isPresentRemote(String name){
        return network.isPresentRemote(name);
    }

    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "getTypeRemote", signature = "getTypeRemote(name: string): string...|nil",
            description = "Returns all native types of an available target peripheral.")
    public final Object[] getTypeRemote(String name){
        return network.getTypeRemote(name);
    }

    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "hasTypeRemote", signature = "hasTypeRemote(name: string, type: string): boolean|nil",
            description = "Checks the native types of an available target peripheral.")
    public final Boolean hasTypeRemote(String name, String type){
        return network.hasTypeRemote(name, type);
    }

    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "getMethodsRemote", signature = "getMethodsRemote(name: string): table|nil",
            description = "Lists the target's native CC methods, including methods from other mods.")
    public final List<String> getMethodsRemote(String name){
        return network.getMethodsRemote(name);
    }

    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "callRemote", signature = "callRemote(name: string, method: string, ...): any...",
            description = "Forwards a native peripheral call, preserving its arguments, return values and yields.")
    public final Object[] callRemote(ScriptArguments args) throws ScriptException{
        return network.callRemote(args);
    }

    // Get the graph API version
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getGraphApiVersion", signature = "getGraphApiVersion(): number",
            description = "Returns the graph API version.")
    public final int getGraphApiVersion() {
        return GRAPH_API_VERSION;
    }

    // Get the graph API methods
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "listGraphApiMethods", signature = "listGraphApiMethods(): table",
            description = "Returns the graph API methods.")
    public final List<String> listGraphApiMethods() {
        Map<String, ScriptDocumentation.Entry> entries = graphApiEntries();
        return GRAPH_API_METHOD_NAMES.stream()
                .map(name -> entries.get(name).signature()).toList();
    }

    // Get the graph API help
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getGraphApiHelp", signature = "getGraphApiHelp([method: string]): table|string",
            description = "Returns the graph API help.")
    public final Object getGraphApiHelp(Optional<String> method) throws ScriptException {
        Map<String, String> help = graphApiHelp();
        if (method.isEmpty()) {
            return help;
        }
        String selected = method.get().strip();
        String description = help.get(selected);
        if (description == null) {
            throw new ScriptException("unknown graph method '" + selected + "'");
        }
        return description;
    }

    // Get the graph status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getGraphStatus", signature = "getGraphStatus(): table",
            description = "Returns the graph status.")
    public final Map<String, Object> getGraphStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("draftRevision", advanced.getDraftGraph().revision());
        status.put("activeRevision", advanced.getActiveGraph().revision());
        status.put("apiVersion", GRAPH_API_VERSION);
        status.put("template", advanced.getDraftGraph().templateId());
        status.put("valid", advanced.validateDraft().valid());
        return status;
    }

    // Get the list graph variables
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "listGraphVariables", signature = "listGraphVariables(): table",
            description = "Returns the list graph variables.")
    public final List<String> listGraphVariables() {
        return List.copyOf(advanced.getActiveGraph().variables().keySet());
    }

    // Get the graph variable
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getGraphVariable", signature = "getGraphVariable(name: string): any",
            description = "Returns the graph variable.")
    public final Object getGraphVariable(String name) throws ScriptException {
        AdvancedGraphDocument.Value val = advanced.getActiveGraph().variables().get(name);
        if (val == null) throw new ScriptException("unknown graph variable '" + name + "'");
        return switch (val.type()) {
            case "boolean" -> val.asBoolean();
            case "string", "direction" -> val.asString();
            case "number" -> val.asNumber();
            default -> ScriptGraphCodec.graphValue(GraphRuntime.toLibraryValue(val));
        };
    }

    // Set the graph variable
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setGraphVariable", signature = "setGraphVariable(name: string, value: any)",
            description = "Sets the graph variable.")
    public final void setGraphVariable(String name, Object value) throws ScriptException {
        AdvancedGraphDocument.Value graphValue = GraphRuntime.fromLibraryValue(
                ScriptGraphCodec.runtimeValue(value));
        if (!advanced.setGraphVariable(name, graphValue)) {
            throw new ScriptException("invalid graph variable name '" + name + "'");
        }
    }

    // Publish the named controller event
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "publishNamedEvent", signature = "publishNamedEvent(name: string, data: any, [maximumDistance: number])",
            description = "Publishes a Named Event through ACC graphs, installed shared transports and optional CC rednet listeners.")
    public final void publishNamedEvent(String name, Object data,
                                        Optional<Integer> maximumDistance) throws ScriptException {
        advanced.publishNamedControllerEvent(namedEventName(name), namedEventData(data),
                Math.max(0, maximumDistance.orElse(0)));
    }

    // Receive the named controller event from an external transport
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "receiveNamedEvent", signature = "receiveNamedEvent(name: string, data: any)",
            description = "Delivers an external Named Event to this ACC without retransmitting it.")
    public final void receiveNamedEvent(String name, Object data) throws ScriptException {
        advanced.receiveNamedControllerEvent(namedEventName(name), namedEventData(data));
    }

    // Trigger the graph event
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "triggerGraphEvent", signature = "triggerGraphEvent(eventId: string)",
            description = "Triggers the graph event.")
    public final void triggerGraphEvent(String eventId) {
        advanced.triggerGraphEvent(eventId);
    }

    // Try to trigger the graph event
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "tryTriggerGraphEvent", signature = "tryTriggerGraphEvent(eventId: string): boolean",
            description = "Attempts to trigger the graph event.")
    public final boolean tryTriggerGraphEvent(String eventId) {
        return advanced.tryTriggerGraphEvent(eventId, null);
    }

    // Get the graph diagnostics
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getGraphDiagnostics", signature = "getGraphDiagnostics(): table",
            description = "Returns the graph diagnostics.")
    public final List<Map<String, String>> getGraphDiagnostics() {
        return advanced.getGraphDiagnostics().stream().map(diagnostic -> Map.of(
                "severity", diagnostic.severity(),
                "code", diagnostic.code(),
                "message", diagnostic.message(),
                "nodeId", diagnostic.nodeId())).toList();
    }

    // Validate the draft
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "validateDraft", signature = "validateDraft(): boolean",
            description = "Validates the draft.")
    public final boolean validateDraft() {
        return advanced.validateDraft().valid();
    }

    // Apply the draft
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "applyDraft", signature = "applyDraft(): boolean",
            description = "Applies the draft.")
    public final boolean applyDraft() {
        return advanced.applyDraft();
    }

    // Get one graph view
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getGraph", signature = "getGraph([view: string]): table",
            description = "Returns one graph view.")
    public final Map<String, Object> getGraph(Optional<String> view) throws ScriptException {
        VersionedGraphEditor.View selected = switch (
                view.orElse("draft").strip().toLowerCase(Locale.ROOT)) {
            case "draft" -> VersionedGraphEditor.View.DRAFT;
            case "active" -> VersionedGraphEditor.View.ACTIVE;
            default -> throw new ScriptException("view must be 'draft' or 'active'");
        };
        return ScriptGraphCodec.snapshot(graphEditor.snapshot(selected));
    }

    // Get the output data for one active graph node
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getGraphNodeOutputs", signature = "getGraphNodeOutputs(nodeIdOrAlias: string): table",
            description = "Returns every current public data output for one active root graph node, keyed by port name.")
    public final Map<String, Object> getGraphNodeOutputs(String nodeIdOrAlias) throws ScriptException {
        String reference = nodeIdOrAlias == null ? "" : nodeIdOrAlias.strip();
        if (reference.isEmpty() || reference.length() > 128) {
            throw new ScriptException("node ID or alias must contain 1 to 128 characters");
        }
        List<AdvancedGraphDocument.Node> nodes = advanced.getActiveGraph().nodes();
        AdvancedGraphDocument.Node node = nodes.stream()
                .filter(candidate -> reference.equals(candidate.id()))
                .findFirst().orElse(null);
        if (node == null) {
            List<AdvancedGraphDocument.Node> aliases = nodes.stream()
                    .filter(candidate -> reference.equals(GraphNodeAlias.normalize(
                            candidate.data().getString(GraphNodeAlias.DATA_KEY))))
                    .toList();
            if (aliases.size() > 1) {
                throw new ScriptException("ambiguous active root graph node alias '"
                        + reference + "'");
            }
            node = aliases.isEmpty() ? null : aliases.getFirst();
        }
        if (node == null) {
            throw new ScriptException("unknown active root graph node ID or alias '"
                    + reference + "'");
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        advanced.getGraphNodeOutputsSnapshot(node).forEach((port, value) ->
                outputs.put(port, ScriptGraphCodec.plainGraphValue(
                        GraphRuntime.toLibraryValue(value))));
        return outputs;
    }

    // Get every registered graph node type
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "listGraphNodeTypes", signature = "listGraphNodeTypes(): table",
            description = "Returns every registered graph node type.")
    public final List<Map<String, Object>> listGraphNodeTypes() {
        return ScriptGraphCodec.nodeTypes(graphEditor.nodeTypes());
    }

    // Mutate the draft graph atomically
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "mutateGraph", signature = "mutateGraph(expectedRevision: number, operations: table): table",
            description = "Mutates the draft graph atomically.")
    public final Map<String, Object> mutateGraph(ScriptArguments arguments) throws ScriptException {
        int expectedRevision = arguments.getInt(0);
        List<GraphMutation> mutations = ScriptGraphCodec.mutations(
                arguments.getTableUnsafe(1));
        return ScriptGraphCodec.result(
                graphEditor.mutateDraft(expectedRevision, mutations));
    }

    // Validate the draft graph
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "validateGraph", signature = "validateGraph(): table",
            description = "Validates the draft graph.")
    public final Map<String, Object> validateGraph() {
        return ScriptGraphCodec.result(graphEditor.validateDraft());
    }

    // Apply the draft graph when its revision still matches
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "applyGraph", signature = "applyGraph(expectedRevision: number): table",
            description = "Applies the draft graph when its revision still matches.")
    public final Map<String, Object> applyGraph(int expectedRevision) {
        return ScriptGraphCodec.result(graphEditor.applyDraft(expectedRevision));
    }

    // Export one graph view as bounded Lua source
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "exportGraphLua", signature = "exportGraphLua(view: string, [options: table]): string",
            description = "Exports one graph view as bounded Lua source.")
    public final String exportGraphLua(
            String view,
            Optional<Map<?, ?>> options
    ) throws ScriptException {
        AdvancedGraphLuaExporter.Result result = graphExporter.export(
                view,
                options.orElseGet(Map::of));
        if (!result.success()) {
            throw new ScriptException(result.message());
        }
        return result.source();
    }

    // Get the Schedule Scratch API version
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getScheduleApiVersion", signature = "getScheduleApiVersion(): number",
            description = "Returns the SCM shipping schedule API version.")
    public final int getScheduleApiVersion() {
        return ControllerScheduleControl.API_VERSION;
    }

    // Get the documented Schedule Scratch API methods
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "listScheduleApiMethods", signature = "listScheduleApiMethods(): table",
            description = "Returns stable SCM shipping schedule API method signatures.")
    public final List<String> listScheduleApiMethods() {
        Map<String, ScriptDocumentation.Entry> entries = scheduleApiEntries();
        return ControllerScheduleControl.API_METHOD_NAMES.stream()
                .map(name -> entries.get(name).signature()).toList();
    }

    // Get Schedule Scratch API help
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getScheduleApiHelp", signature = "getScheduleApiHelp([method: string]): table|string",
            description = "Returns all SCM shipping schedule API help or help for one method.")
    public final Object getScheduleApiHelp(Optional<String> method) throws ScriptException {
        Map<String, String> help = scheduleApiHelp();
        if (method.isEmpty()) return help;
        String selected = method.get().strip();
        String description = help.get(selected);
        if (description == null) throw new ScriptException("unknown schedule method '" + selected + "'");
        return description;
    }

    // Get one SCM-owned Schedule Scratch graph view
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getScheduleGraph", signature = "getScheduleGraph([view: string]): table",
            description = "Returns the controller-owned shipping schedule Scratch graph view.")
    public final Map<String, Object> getScheduleGraph(Optional<String> view) throws ScriptException {
        return scheduleBridge.graph(view);
    }

    // List every available Create instruction, wait condition and Scratch flow block
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "listScheduleBlockTypes", signature = "listScheduleBlockTypes(): table",
            description = "Returns the available shipping schedule instruction, condition and flow block types.")
    public final List<Map<String, Object>> listScheduleBlockTypes() {
        return scheduleBridge.blockTypes();
    }

    // Get SCM shipping schedule state and live route telemetry
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getScheduleStatus", signature = "getScheduleStatus(): table",
            description = "Returns SCM schedule availability, revisions, route state and live telemetry.")
    public final Map<String, Object> getScheduleStatus() {
        return scheduleBridge.status();
    }

    // Get one block's editable schedule properties
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getScheduleBlockProperties", signature = "getScheduleBlockProperties(nodeId: string): table",
            description = "Returns the editable simple properties for one shipping schedule block.")
    public final Map<String, String> getScheduleBlockProperties(String nodeId) throws ScriptException {
        return scheduleBridge.blockProperties(nodeId);
    }

    // Get one block's item or frequency inputs
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getScheduleBlockInputs", signature = "getScheduleBlockInputs(nodeId: string): table",
            description = "Returns the item and frequency input slots for one shipping schedule block.")
    public final List<Map<String, Object>> getScheduleBlockInputs(String nodeId) throws ScriptException {
        return scheduleBridge.blockInputs(nodeId);
    }

    // Mutate the SCM-owned Schedule Scratch graph atomically
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "mutateScheduleGraph", signature = "mutateScheduleGraph(expectedRevision: number, operations: table): table",
            description = "Applies one bounded atomic shipping schedule mutation batch to the SCM-owned graph.")
    public final Map<String, Object> mutateScheduleGraph(ScriptArguments arguments) throws ScriptException {
        return scheduleBridge.mutate(arguments);
    }

    // Validate the Schedule Scratch graph
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "validateScheduleGraph", signature = "validateScheduleGraph(): table",
            description = "Validates that the controller-owned schedule graph contains executable schedule steps.")
    public final Map<String, Object> validateScheduleGraph() {
        return scheduleBridge.validate();
    }

    // Apply a Schedule Scratch graph revision
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "applyScheduleGraph", signature = "applyScheduleGraph(expectedRevision: number): table",
            description = "Confirms an SCM schedule revision; schedule saves are active immediately.")
    public final Map<String, Object> applyScheduleGraph(int expectedRevision) {
        return scheduleBridge.apply(expectedRevision);
    }

    // Start the SCM-owned schedule
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "startSchedule", signature = "startSchedule(): boolean",
            description = "Starts or restarts the controller-owned schedule without requiring a schedule item.")
    public final boolean startSchedule() {
        return scheduleBridge.start();
    }

    // Pause the SCM-owned schedule
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "pauseSchedule", signature = "pauseSchedule(): boolean",
            description = "Pauses the installed SCM shipping schedule.")
    public final boolean pauseSchedule() {
        return scheduleBridge.control("shipping_pause");
    }

    // Resume the SCM-owned schedule
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "resumeSchedule", signature = "resumeSchedule(): boolean",
            description = "Resumes the installed SCM shipping schedule.")
    public final boolean resumeSchedule() {
        return scheduleBridge.control("shipping_resume");
    }

    // Stop the SCM-owned schedule and reset its cursor
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "stopSchedule", signature = "stopSchedule(): boolean",
            description = "Stops the installed SCM shipping schedule and resets it to its first step.")
    public final boolean stopSchedule() {
        return scheduleBridge.control("shipping_stop");
    }

    // Restart the SCM-owned schedule
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "restartSchedule", signature = "restartSchedule(): boolean",
            description = "Restarts the installed SCM shipping schedule from its first step.")
    public final boolean restartSchedule() {
        return scheduleBridge.control("shipping_restart");
    }

    // Skip the current SCM schedule step
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "skipSchedule", signature = "skipSchedule(): boolean",
            description = "Skips the current SCM shipping schedule step.")
    public final boolean skipSchedule() {
        return scheduleBridge.control("shipping_skip");
    }

    // Explicitly import the held schedule item
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "readScheduleItem", signature = "readScheduleItem(): boolean",
            description = "Reads the adjacent pilot's held shipping schedule item into the SCM-owned graph.")
    public final boolean readScheduleItem() {
        return scheduleBridge.readItem();
    }

    // Explicitly write the SCM-owned graph to the held schedule item
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "writeScheduleItem", signature = "writeScheduleItem(): boolean",
            description = "Writes the SCM-owned schedule graph to the adjacent pilot's held shipping schedule item.")
    public final boolean writeScheduleItem() {
        return scheduleBridge.writeItem();
    }

    // Get the documented graph API methods
    private static Map<String, ScriptDocumentation.Entry> graphApiEntries() {
        Map<String, ScriptDocumentation.Entry> entries = new LinkedHashMap<>();
        ScriptDocumentation.catalog(AdvancedContraptionControllerPeripheralControl.class)
                .entries().forEach(entry -> entries.put(entry.name(), entry));
        return Map.copyOf(entries);
    }

    // Build the graph API help from the peripheral annotations
    private static Map<String, String> graphApiHelp() {
        Map<String, String> help = new LinkedHashMap<>();
        Map<String, ScriptDocumentation.Entry> entries = graphApiEntries();
        for (String name : GRAPH_API_METHOD_NAMES) {
            ScriptDocumentation.Entry entry = entries.get(name);
            help.put(name, entry.signature() + " - " + entry.description());
        }
        return help;
    }

    // Get the documented schedule API methods
    private static Map<String, ScriptDocumentation.Entry> scheduleApiEntries() {
        Map<String, ScriptDocumentation.Entry> entries = new LinkedHashMap<>();
        ScriptDocumentation.catalog(AdvancedContraptionControllerPeripheralControl.class)
                .entries().forEach(entry -> entries.put(entry.name(), entry));
        return Map.copyOf(entries);
    }

    // Build schedule API help from the method annotations
    private static Map<String, String> scheduleApiHelp() {
        Map<String, String> help = new LinkedHashMap<>();
        Map<String, ScriptDocumentation.Entry> entries = scheduleApiEntries();
        for (String name : ControllerScheduleControl.API_METHOD_NAMES) {
            ScriptDocumentation.Entry entry = entries.get(name);
            help.put(name, entry.signature() + " - " + entry.description());
        }
        return help;
    }

    // Get the checked named controller event name
    private static String namedEventName(String name) throws ScriptException {
        String checked = name == null ? "" : name.strip();
        if (checked.isEmpty() || checked.length() > 128) {
            throw new ScriptException("named event name must contain 1 to 128 characters");
        }
        return checked;
    }

    // Get the named controller event data
    private static AdvancedGraphDocument.Value namedEventData(Object data) throws ScriptException {
        return GraphRuntime.fromLibraryValue(ScriptGraphCodec.runtimeValue(data));
    }

    public void network(ScriptPeripheralNetwork network){ this.network = network; }
}
