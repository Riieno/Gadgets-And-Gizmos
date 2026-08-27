package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDocumentation;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphEditingService;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.export.AdvancedGraphLuaExporter;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphMutation;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphNodeAlias;
import com.rieno.gadgetsandgizmos.lib.graph.edit.VersionedGraphEditor;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Expose Advanced Contraption Controller controls and telemetry to ComputerCraft
@PeripheralTypeDoc("advanced_contraption_controller")
public class AdvancedContraptionControllerPeripheral extends AnalogueContraptionControllerPeripheral {
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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller peripheral
    public AdvancedContraptionControllerPeripheral(AdvancedContraptionControllerBlockEntity blockEntity) {
        super(blockEntity, "advanced_contraption_controller");
        this.advanced = blockEntity;
        this.graphEditor = new AdvancedGraphEditingService(blockEntity);
        this.graphExporter = new AdvancedGraphLuaExporter(blockEntity);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the graph API version
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphApiVersion", signature = "getGraphApiVersion(): number",
            description = "Returns the graph API version.")
    public final int getGraphApiVersion() {
        return GRAPH_API_VERSION;
    }

    // Get the graph API methods
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listGraphApiMethods", signature = "listGraphApiMethods(): table",
            description = "Returns the graph API methods.")
    public final List<String> listGraphApiMethods() {
        Map<String, PeripheralDocumentation.Entry> entries = graphApiEntries();
        return GRAPH_API_METHOD_NAMES.stream()
                .map(name -> entries.get(name).signature()).toList();
    }

    // Get the graph API help
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphApiHelp", signature = "getGraphApiHelp([method: string]): table|string",
            description = "Returns the graph API help.")
    public final Object getGraphApiHelp(Optional<String> method) throws LuaException {
        Map<String, String> help = graphApiHelp();
        if (method.isEmpty()) {
            return help;
        }
        String selected = method.get().strip();
        String description = help.get(selected);
        if (description == null) {
            throw new LuaException("unknown graph method '" + selected + "'");
        }
        return description;
    }

    // Get the graph status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphStatus", signature = "getGraphStatus(): table",
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listGraphVariables", signature = "listGraphVariables(): table",
            description = "Returns the list graph variables.")
    public final List<String> listGraphVariables() {
        return List.copyOf(advanced.getActiveGraph().variables().keySet());
    }

    // Get the graph variable
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphVariable", signature = "getGraphVariable(name: string): any",
            description = "Returns the graph variable.")
    public final Object getGraphVariable(String name) throws LuaException {
        AdvancedGraphDocument.Value val = advanced.getActiveGraph().variables().get(name);
        if (val == null) throw new LuaException("unknown graph variable '" + name + "'");
        return switch (val.type()) {
            case "boolean" -> val.asBoolean();
            case "string", "direction" -> val.asString();
            case "number" -> val.asNumber();
            default -> ComputerCraftGraphCodec.graphValue(GraphRuntime.toLibraryValue(val));
        };
    }

    // Set the graph variable
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setGraphVariable", signature = "setGraphVariable(name: string, value: any)",
            description = "Sets the graph variable.")
    public final void setGraphVariable(String name, Object value) throws LuaException {
        AdvancedGraphDocument.Value graphValue = GraphRuntime.fromLibraryValue(
                ComputerCraftGraphCodec.runtimeValue(value));
        if (!advanced.setGraphVariable(name, graphValue)) {
            throw new LuaException("invalid graph variable name '" + name + "'");
        }
    }

    // Publish the named controller event
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "publishNamedEvent", signature = "publishNamedEvent(name: string, data: any, [maximumDistance: number])",
            description = "Publishes a Named Event through ACC graphs, installed shared transports and optional CC rednet listeners.")
    public final void publishNamedEvent(String name, Object data,
                                        Optional<Integer> maximumDistance) throws LuaException {
        advanced.publishNamedControllerEvent(namedEventName(name), namedEventData(data),
                Math.max(0, maximumDistance.orElse(0)));
    }

    // Receive the named controller event from an external transport
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "receiveNamedEvent", signature = "receiveNamedEvent(name: string, data: any)",
            description = "Delivers an external Named Event to this ACC without retransmitting it.")
    public final void receiveNamedEvent(String name, Object data) throws LuaException {
        advanced.receiveNamedControllerEvent(namedEventName(name), namedEventData(data));
    }

    // Trigger the graph event
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "triggerGraphEvent", signature = "triggerGraphEvent(eventId: string)",
            description = "Triggers the graph event.")
    public final void triggerGraphEvent(String eventId) {
        advanced.triggerGraphEvent(eventId);
    }

    // Try to trigger the graph event
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "tryTriggerGraphEvent", signature = "tryTriggerGraphEvent(eventId: string): boolean",
            description = "Attempts to trigger the graph event.")
    public final boolean tryTriggerGraphEvent(String eventId) {
        return advanced.tryTriggerGraphEvent(eventId, null);
    }

    // Get the graph diagnostics
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphDiagnostics", signature = "getGraphDiagnostics(): table",
            description = "Returns the graph diagnostics.")
    public final List<Map<String, String>> getGraphDiagnostics() {
        return advanced.getGraphDiagnostics().stream().map(diagnostic -> Map.of(
                "severity", diagnostic.severity(),
                "code", diagnostic.code(),
                "message", diagnostic.message(),
                "nodeId", diagnostic.nodeId())).toList();
    }

    // Validate the draft
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "validateDraft", signature = "validateDraft(): boolean",
            description = "Validates the draft.")
    public final boolean validateDraft() {
        return advanced.validateDraft().valid();
    }

    // Apply the draft
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "applyDraft", signature = "applyDraft(): boolean",
            description = "Applies the draft.")
    public final boolean applyDraft() {
        return advanced.applyDraft();
    }

    // Get one graph view
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraph", signature = "getGraph([view: string]): table",
            description = "Returns one graph view.")
    public final Map<String, Object> getGraph(Optional<String> view) throws LuaException {
        VersionedGraphEditor.View selected = switch (
                view.orElse("draft").strip().toLowerCase(Locale.ROOT)) {
            case "draft" -> VersionedGraphEditor.View.DRAFT;
            case "active" -> VersionedGraphEditor.View.ACTIVE;
            default -> throw new LuaException("view must be 'draft' or 'active'");
        };
        return ComputerCraftGraphCodec.snapshot(graphEditor.snapshot(selected));
    }

    // Get the output data for one active graph node
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphNodeOutputs", signature = "getGraphNodeOutputs(nodeIdOrAlias: string): table",
            description = "Returns every current public data output for one active root graph node, keyed by port name.")
    public final Map<String, Object> getGraphNodeOutputs(String nodeIdOrAlias) throws LuaException {
        String reference = nodeIdOrAlias == null ? "" : nodeIdOrAlias.strip();
        if (reference.isEmpty() || reference.length() > 128) {
            throw new LuaException("node ID or alias must contain 1 to 128 characters");
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
                throw new LuaException("ambiguous active root graph node alias '"
                        + reference + "'");
            }
            node = aliases.isEmpty() ? null : aliases.getFirst();
        }
        if (node == null) {
            throw new LuaException("unknown active root graph node ID or alias '"
                    + reference + "'");
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        advanced.getGraphNodeOutputsSnapshot(node).forEach((port, value) ->
                outputs.put(port, ComputerCraftGraphCodec.plainGraphValue(
                        GraphRuntime.toLibraryValue(value))));
        return outputs;
    }

    // Get every registered graph node type
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listGraphNodeTypes", signature = "listGraphNodeTypes(): table",
            description = "Returns every registered graph node type.")
    public final List<Map<String, Object>> listGraphNodeTypes() {
        return ComputerCraftGraphCodec.nodeTypes(graphEditor.nodeTypes());
    }

    // Mutate the draft graph atomically
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "mutateGraph", signature = "mutateGraph(expectedRevision: number, operations: table): table",
            description = "Mutates the draft graph atomically.")
    public final Map<String, Object> mutateGraph(IArguments arguments) throws LuaException {
        int expectedRevision = arguments.getInt(0);
        List<GraphMutation> mutations = ComputerCraftGraphCodec.mutations(
                arguments.getTableUnsafe(1));
        return ComputerCraftGraphCodec.result(
                graphEditor.mutateDraft(expectedRevision, mutations));
    }

    // Validate the draft graph
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "validateGraph", signature = "validateGraph(): table",
            description = "Validates the draft graph.")
    public final Map<String, Object> validateGraph() {
        return ComputerCraftGraphCodec.result(graphEditor.validateDraft());
    }

    // Apply the draft graph when its revision still matches
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "applyGraph", signature = "applyGraph(expectedRevision: number): table",
            description = "Applies the draft graph when its revision still matches.")
    public final Map<String, Object> applyGraph(int expectedRevision) {
        return ComputerCraftGraphCodec.result(graphEditor.applyDraft(expectedRevision));
    }

    // Export one graph view as bounded Lua source
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "exportGraphLua", signature = "exportGraphLua(view: string, [options: table]): string",
            description = "Exports one graph view as bounded Lua source.")
    public final String exportGraphLua(
            String view,
            Optional<Map<?, ?>> options
    ) throws LuaException {
        AdvancedGraphLuaExporter.Result result = graphExporter.export(
                view,
                options.orElseGet(Map::of));
        if (!result.success()) {
            throw new LuaException(result.message());
        }
        return result.source();
    }

    // Get the documented graph API methods
    private static Map<String, PeripheralDocumentation.Entry> graphApiEntries() {
        Map<String, PeripheralDocumentation.Entry> entries = new LinkedHashMap<>();
        PeripheralDocumentation.catalog(AdvancedContraptionControllerPeripheral.class)
                .entries().forEach(entry -> entries.put(entry.name(), entry));
        return Map.copyOf(entries);
    }

    // Build the graph API help from the peripheral annotations
    private static Map<String, String> graphApiHelp() {
        Map<String, String> help = new LinkedHashMap<>();
        Map<String, PeripheralDocumentation.Entry> entries = graphApiEntries();
        for (String name : GRAPH_API_METHOD_NAMES) {
            PeripheralDocumentation.Entry entry = entries.get(name);
            help.put(name, entry.signature() + " - " + entry.description());
        }
        return help;
    }

    // Get the checked named controller event name
    private static String namedEventName(String name) throws LuaException {
        String checked = name == null ? "" : name.strip();
        if (checked.isEmpty() || checked.length() > 128) {
            throw new LuaException("named event name must contain 1 to 128 characters");
        }
        return checked;
    }

    // Get the named controller event data
    private static AdvancedGraphDocument.Value namedEventData(Object data) throws LuaException {
        return GraphRuntime.fromLibraryValue(ComputerCraftGraphCodec.runtimeValue(data));
    }
}
