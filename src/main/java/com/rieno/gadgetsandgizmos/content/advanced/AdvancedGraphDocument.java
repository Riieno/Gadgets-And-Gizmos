package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.graph.GraphModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Store one graph document and keep its node, wire and nested function data safe to copy
public final class AdvancedGraphDocument
        implements GraphModel<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int CURRENT_VERSION = 9;
    public static final int DEFAULT_MAX_NODES = 512;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the maximum nodes
    public static int maxNodes() {
        try {
            return CTConfigs.COMMON.advancedControllerMaxNodes.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_MAX_NODES;
        }
    }
    public static final int MAX_EDGES = 512;
    public static final int MAX_SERIALIZED_BYTES = 512 * 1024;
    public static final String EDITOR_COLLAPSED_KEY = "EditorCollapsed";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current version
    private int version = CURRENT_VERSION;
    // Current revision
    private int revision;
    // Current template id
    private String templateId = "";
    // Current viewport x
    private float viewportX = 150.0f;
    // Current viewport y
    private float viewportY = 80.0f;
    // Current viewport zoom
    private float viewportZoom = 1.0f;
    // Tracked nodes
    private final List<Node> nodes = new ArrayList<>();
    // Tracked edges
    private final List<Edge> edges = new ArrayList<>();
    // Tracked variables
    private final Map<String, Value> variables = new LinkedHashMap<>();
    // Tracked groups
    private final List<CompoundTag> groups = new ArrayList<>();
    // Tracked notes
    private final List<CompoundTag> notes = new ArrayList<>();
    // Tracked functions
    private final List<FunctionGraph> functions = new ArrayList<>();

    // Get the revision
    public int revision() {
        return revision;
    }

    // Set the revision
    public void setRevision(int revision) {
        this.revision = Math.max(0, revision);
    }

    // Get the template id
    public String templateId() {
        return templateId;
    }

    // Set the template id
    public void setTemplateId(String templateId) {
        this.templateId = templateId == null ? "" : templateId.trim();
    }

    // Get the nodes
    public List<Node> nodes() {
        return nodes;
    }

    // Get the edges
    public List<Edge> edges() {
        return edges;
    }

    // Get the variables
    public Map<String, Value> variables() {
        return variables;
    }

    // Remove the unused variables
    public void removeUnusedVariables() {
        Set<String> referenced = new LinkedHashSet<>();
        collectReferencedVars(nodes, referenced);
        for (FunctionGraph function : functions) {
            collectReferencedVars(function.nodes(), referenced);
        }
        variables.keySet().removeIf(variable -> !referenced.contains(variable));
    }

    // Collect the referenced vars
    private static void collectReferencedVars(List<Node> src, Set<String> referenced) {
        for (Node node : src) {
            if (!"variable_get".equals(node.type()) && !"variable_set".equals(node.type())
                    && !"event_variable_change".equals(node.type())) continue;
            String variable = node.data().getString("Variable");
            if (!variable.isBlank()) referenced.add(variable);
        }
    }

    // Check if the variable name is valid
    public static boolean isValidVariableName(String variable) {
        if (variable == null) {
            return false;
        }
        String normalized = variable.strip();
        return !normalized.isBlank() && normalized.length() <= 64
                && normalized.chars().noneMatch(Character::isISOControl);
    }

    // Get the program fingerprint
    public int programFingerprint() {
        int hash = 1;
        for (Node node : nodes) {
            if (node == null) {
                hash = 31 * hash;
                continue;
            }
            hash = 31 * hash + node.id().hashCode();
            hash = 31 * hash + node.type().hashCode();
            hash = 31 * hash + tagFingerprint(node.data());
        }
        for (Edge edge : edges) {
            if (edge == null) {
                hash = 31 * hash;
                continue;
            }
            hash = 31 * hash + edge.fromNode().hashCode();
            hash = 31 * hash + edge.fromPort().hashCode();
            hash = 31 * hash + edge.toNode().hashCode();
            hash = 31 * hash + edge.toPort().hashCode();
        }
        for (FunctionGraph function : functions) {
            hash = 31 * hash + function.id().hashCode();
            hash = 31 * hash + function.name().hashCode();
            hash = 31 * hash + tagFingerprint(function.toTag());
        }
        return hash;
    }

    // Get the simulation fingerprint
    public int simulationFingerprint() {
        int hash = programFingerprint();
        for (Map.Entry<String, Value> entry : variables.entrySet()) {
            hash = 31 * hash + entry.getKey().hashCode();
            Value val = entry.getValue();
            if (val != null) {
                hash = 31 * hash + val.type().hashCode();
                hash = 31 * hash + tagFingerprint(val.payload());
            }
        }
        return hash;
    }

    // Get the tag fingerprint
    private static int tagFingerprint(Tag tag) {
        if (tag == null) {
            return 0;
        }
        if (tag instanceof CompoundTag compound) {
            int hash = Tag.TAG_COMPOUND;
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            for (String key : keys) {
                if (AdvancedGraphImageAssets.BASE64_CHUNKS.equals(key)
                        || EDITOR_COLLAPSED_KEY.equals(key)) {
                    continue;
                }
                hash = 31 * hash + key.hashCode();
                hash = 31 * hash + tagFingerprint(compound.get(key));
            }
            return hash;
        }
        if (tag instanceof ListTag list) {
            int hash = Tag.TAG_LIST;
            for (Tag entry : list) {
                hash = 31 * hash + tagFingerprint(entry);
            }
            return hash;
        }
        return 31 * tag.getId() + tag.toString().hashCode();
    }

    // Get the groups
    public List<CompoundTag> groups() {
        return groups;
    }

    // Get the notes
    public List<CompoundTag> notes() {
        return notes;
    }

    // Get the functions
    public List<FunctionGraph> functions() {
        return functions;
    }

    // Get the function
    public FunctionGraph function(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (FunctionGraph function : functions) {
            if (id.equals(function.id())) {
                return function;
            }
        }
        return null;
    }

    // Get the total node count
    public int totalNodeCount() {
        int count = nodes.size();
        for (FunctionGraph function : functions) {
            count += function.nodes().size();
        }
        return count;
    }

    // Get the total edge count
    public int totalEdgeCount() {
        int count = edges.size();
        for (FunctionGraph function : functions) {
            count += function.edges().size();
        }
        return count;
    }

    // Get the viewport x
    public float viewportX() {
        return viewportX;
    }

    // Get the viewport y
    public float viewportY() {
        return viewportY;
    }

    // Get the viewport zoom
    public float viewportZoom() {
        return viewportZoom;
    }

    // Set the viewport
    public void setViewport(double x, double y, double zoom) {
        viewportX = (float) x;
        viewportY = (float) y;
        viewportZoom = (float) Math.max(0.05D, Math.min(1.75D, zoom));
    }

    // Copy the advanced graph document
    public AdvancedGraphDocument copy() {
        return fromTag(toTag());
    }

    // Write the advanced graph document data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", version);
        tag.putInt("Revision", revision);
        tag.putString("Template", templateId);
        tag.putFloat("ViewportX", viewportX);
        tag.putFloat("ViewportY", viewportY);
        tag.putFloat("ViewportZoom", viewportZoom);
        ListTag nodeTags = new ListTag();
        nodes.forEach(node -> nodeTags.add(node.toTag()));
        tag.put("Nodes", nodeTags);
        ListTag edgeTags = new ListTag();
        edges.forEach(edge -> edgeTags.add(edge.toTag()));
        tag.put("Edges", edgeTags);
        CompoundTag variableTags = new CompoundTag();
        variables.forEach((id, val) -> variableTags.put(id, val.toTag()));
        tag.put("Variables", variableTags);
        ListTag groupTags = new ListTag();
        groups.forEach(group -> groupTags.add(group.copy()));
        tag.put("Groups", groupTags);
        ListTag noteTags = new ListTag();
        notes.forEach(note -> noteTags.add(note.copy()));
        tag.put("Notes", noteTags);
        ListTag functionTags = new ListTag();
        functions.forEach(function -> functionTags.add(function.toTag()));
        tag.put("Functions", functionTags);
        return tag;
    }

    // Read the advanced graph document data
    public static AdvancedGraphDocument fromTag(CompoundTag tag) {
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        if (tag == null || tag.isEmpty()) {
            return graph;
        }
        // -----------------------------------------------------GRAPH HEADER-----------------------------------------------------
        int storedVersion = Math.max(1, tag.getInt("Version"));
        graph.version = Math.max(storedVersion, CURRENT_VERSION);
        graph.revision = Math.max(0, tag.getInt("Revision"));
        graph.templateId = tag.getString("Template");
        if (storedVersion >= 3) {
            graph.viewportX = tag.getFloat("ViewportX");
            graph.viewportY = tag.getFloat("ViewportY");
            graph.viewportZoom = tag.contains("ViewportZoom") ? tag.getFloat("ViewportZoom") : 1.0f;
        }
        // -----------------------------------------------------NODES / EDGES-----------------------------------------------------
        ListTag nodes = tag.getList("Nodes", Tag.TAG_COMPOUND);
        Set<String> migratedKeyNodeIds = new LinkedHashSet<>();
        for (int i = 0; i < nodes.size() && graph.nodes.size() < maxNodes(); i++) {
            Node node = Node.fromTag(nodes.getCompound(i));
            if ("event_key".equals(node.type())) {
                migratedKeyNodeIds.add(node.id());
                node = new Node(node.id(), "controller_channel_input",
                        node.label(), node.x(), node.y(), node.data());
            }
            graph.nodes.add(node);
        }
        if (storedVersion < 2) {
            graph.nodes.replaceAll(node -> "random".equals(node.type())
                    ? new Node(node.id(), "random_float_in_range", node.label(), node.x(), node.y(), node.data())
                    : node);
        }
        ListTag edges = tag.getList("Edges", Tag.TAG_COMPOUND);
        for (int i = 0; i < edges.size() && graph.edges.size() < MAX_EDGES; i++) {
            Edge edge = Edge.fromTag(edges.getCompound(i));
            if (migratedKeyNodeIds.contains(edge.fromNode())
                    && "pressed".equals(edge.fromPort())) {
                edge = new Edge(edge.id(), edge.fromNode(), "active",
                        edge.toNode(), edge.toPort());
            }
            graph.edges.add(edge);
        }
        // -----------------------------------------------------GRAPH DATA-----------------------------------------------------
        CompoundTag variables = tag.getCompound("Variables");
        for (String key : variables.getAllKeys()) {
            graph.variables.put(key, Value.fromTag(variables.getCompound(key)));
        }
        readCopiedTags(tag, "Groups", graph.groups);
        readCopiedTags(tag, "Notes", graph.notes);
        ListTag functions = tag.getList("Functions", Tag.TAG_COMPOUND);
        for (int i = 0; i < functions.size(); i++) {
            FunctionGraph function = FunctionGraph.fromTag(functions.getCompound(i));
            if (!function.id().isBlank()
                    && graph.functions.stream().noneMatch(existing -> existing.id().equals(function.id()))) {
                graph.functions.add(function);
            }
        }
        // ------------------------------------VERSION MIGRATION------------------------------------
        if (storedVersion < 6) {
            migrateShipSpeedPcts(graph.nodes, graph.edges);
            for (FunctionGraph function : graph.functions) {
                migrateShipSpeedPcts(function.nodes, function.edges);
            }
        }
        if (storedVersion < 7) {
            migrateDeadzonePorts(graph.nodes, graph.edges);
            for (FunctionGraph function : graph.functions) {
                migrateDeadzonePorts(function.nodes, function.edges);
            }
        }
        if (storedVersion < 8) {
            migrateCurveDefaults(graph.nodes);
            for (FunctionGraph function : graph.functions) {
                migrateCurveDefaults(function.nodes);
            }
        }
        if (storedVersion < 9) {
            migrateSwitchDataInputs(graph.nodes, graph.edges);
            for (FunctionGraph function : graph.functions) {
                migrateSwitchDataInputs(function.nodes, function.edges);
            }
        }
        // ------------------------------------PORT NORMALIZATION------------------------------------
        ensureAccDisplayDefaults(graph.nodes);
        removeInactiveCrnTextEdges(graph.nodes, graph.edges);
        syncAccDisplayWidgets(graph.nodes, graph.edges);
        for (FunctionGraph function : graph.functions) {
            ensureAccDisplayDefaults(function.nodes);
            removeInactiveCrnTextEdges(function.nodes, function.edges);
            syncAccDisplayWidgets(function.nodes, function.edges);
        }
        return graph;
    }

    // Ensure the ACC display defaults
    private static void ensureAccDisplayDefaults(List<Node> nodes) {
        // -----------------------------------------------------DISPLAY NODES-----------------------------------------------------
        for (Node node : nodes) {
            if (!(node.type().startsWith("acc_display_")
                    || "acc_hologram_widget".equals(node.type()))) {
                continue;
            }
            CompoundTag data = node.data();
            CompoundTag defaults = data.getCompound("Defaults");
            if (!defaults.contains("visible")) {
                defaults.put("visible", Value.bool(true).toTag());
            }
            // ------------------------------------WIDGET DEFAULTS------------------------------------
            if (isAccDisplayWidget(node.type())) {
                if (!defaults.contains("label")) {
                    defaults.put("label", Value.string("Widget").toTag());
                }
                if (!defaults.contains("value")) {
                    defaults.put("value", Value.string("").toTag());
                }
                putNumberDefault(defaults, "x", 4.0D);
                putNumberDefault(defaults, "y", 4.0D);
                putNumberDefault(defaults, "width", 132.0D);
                putNumberDefault(defaults, "height", 64.0D);
                putNumberDefault(defaults, "rotation", 0.0D);
                putNumberDefault(defaults, "scale", 1.0D);
                putNumberDefault(defaults, "font_size", AdvancedHudElementStyle.DEFAULT_FONT_SIZE);
                putNumberDefault(defaults, "color", AdvancedHudElementStyle.DEFAULT_TEXT_COLOR);
                putNumberDefault(defaults, "background_color",
                        AdvancedHudElementStyle.DEFAULT_WIDGET_BACKGROUND_COLOR);
                putNumberDefault(defaults, "accent_color",
                        AdvancedHudElementStyle.DEFAULT_WIDGET_ACCENT_COLOR);
                putNumberDefault(defaults, "track_color",
                        AdvancedHudElementStyle.DEFAULT_WIDGET_TRACK_COLOR);
                putNumberDefault(defaults, "border_color", 0xFF527185);
                putNumberDefault(defaults, "border_width", 1.0D);
                putNumberDefault(defaults, "border_radius", 4.0D);
                putNumberDefault(defaults, "minimum", 0.0D);
                putNumberDefault(defaults, "maximum", 1.0D);
                CompoundTag inputs = data.getCompound("DynamicInputs");
                if (!inputs.contains("value")) {
                    inputs.putString("value", "any");
                }
                data.put("DynamicInputs", inputs);
                if (data.getInt("WidgetWidth") <= 0) {
                    data.putInt("WidgetWidth", 140);
                }
                if (data.getInt("WidgetHeight") <= 0) {
                    data.putInt("WidgetHeight", 72);
                }
                // ------------------------------------MANAGED ELEMENTS------------------------------------
                String widgetType = data.getString("WidgetType");
                if (widgetType.isBlank()) {
                    widgetType = "text";
                    data.putString("WidgetType", widgetType);
                }
                ListTag elements = data.getList(AdvancedHudInteractions.ELEMENTS, Tag.TAG_COMPOUND);
                if (elements.isEmpty()) {
                    CompoundTag elm = new CompoundTag();
                    elm.putBoolean("ManagedWidget", true);
                    elm.putString("Type", widgetType);
                    elm.putString("Text", "Widget");
                    elm.putInt("X", 4);
                    elm.putInt("Y", 4);
                    elm.putInt("W", 132);
                    elm.putInt("H", 64);
                    if ("value".equals(widgetType) || "progress".equals(widgetType)) {
                        elm.putString("Port", "value");
                    }
                    if ("slider".equals(widgetType) || "progress".equals(widgetType)) {
                        elm.putDouble("Min", 0.0D);
                        elm.putDouble("Max", 1.0D);
                        if ("slider".equals(widgetType)) {
                            elm.putDouble("Step", 0.05D);
                        }
                    }
                    AdvancedHudElementStyle.applyDefaults(elm);
                    elements.add(elm);
                    data.put(AdvancedHudInteractions.ELEMENTS, elements);
                }
                for (int idx = 0; idx < elements.size(); idx++) {
                    CompoundTag elm = elements.getCompound(idx);
                    if (elm.getBoolean("ManagedWidget")) {
                        bindManagedAccWidget(elm);
                        AdvancedHudElementStyle.applyDefaults(elm);
                    }
                }
                data.put(AdvancedHudInteractions.ELEMENTS, elements);
            // ------------------------------------DISPLAY SOURCE DEFAULTS------------------------------------
            } else {
                if ("acc_display_plotter".equals(node.type()) && !defaults.contains("value")) {
                    defaults.put("value", Value.number(0.0D).toTag());
                }
                if ("acc_display_graph".equals(node.type())
                        || "acc_display_plotter".equals(node.type())
                        || "acc_display_external".equals(node.type())
                        || "acc_display_crn".equals(node.type())) {
                    putNumberDefault(defaults, "x", 0.0D);
                    putNumberDefault(defaults, "y", 0.0D);
                    putNumberDefault(defaults, "width", 320.0D);
                    putNumberDefault(defaults, "height", 180.0D);
                    putNumberDefault(defaults, "scale", 1.0D);
                    putNumberDefault(defaults, "rotation", 0.0D);
                }
                if ("acc_display_crn".equals(node.type())) {
                    if (!defaults.contains("text")) {
                        defaults.put("text", Value.string("").toTag());
                    }
                    if (data.getString("DisplayMode").isBlank()) {
                        data.putString("DisplayMode",
                                "passenger_information/detailed_with_schedule");
                    }
                }
            }
            data.put("Defaults", defaults);
        }
    }

    // Put the number default
    private static void putNumberDefault(CompoundTag defaults, String port, double val) {
        if (!defaults.contains(port)) {
            defaults.put(port, Value.number(val).toTag());
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bind the managed ACC widget
    private static void bindManagedAccWidget(CompoundTag elm) {
        Map<String, String> bindings = Map.ofEntries(
                Map.entry("Text", "label"), Map.entry("X", "x"), Map.entry("Y", "y"),
                Map.entry("W", "width"), Map.entry("H", "height"),
                Map.entry("Rotation", "rotation"), Map.entry("Scale", "scale"),
                Map.entry("FontSize", "font_size"), Map.entry("Color", "color"),
                Map.entry("BackgroundColor", "background_color"),
                Map.entry("AccentColor", "accent_color"), Map.entry("TrackColor", "track_color"),
                Map.entry("BorderColor", "border_color"),
                Map.entry("BorderWidth", "border_width"),
                Map.entry("BorderRadius", "border_radius"),
                Map.entry("Min", "minimum"), Map.entry("Max", "maximum"));
        bindings.forEach((property, port) -> AdvancedHudElementBinding.bind(elm, property, port));
    }

    // Sync the ACC display widgets
    private static void syncAccDisplayWidgets(List<Node> nodes, List<Edge> edges) {
        for (Node node : nodes) {
            if (isAccDisplayWidget(node.type())) {
                AdvancedHudInteractions.synchronize(node, edges);
            }
        }
    }

    // Check if this is an ACC display widget
    private static boolean isAccDisplayWidget(String type) {
        return "acc_display_widget".equals(type) || "acc_hologram_widget".equals(type);
    }

    // Remove the inactive CRN text edges
    private static void removeInactiveCrnTextEdges(List<Node> nodes, List<Edge> edges) {
        Set<String> dataModeNodes = new LinkedHashSet<>();
        for (Node node : nodes) {
            if ("acc_display_crn".equals(node.type())
                    && !AdvancedGraphCatalog.isCrnStaticTextMode(
                    node.data().getString("DisplayMode"))) {
                dataModeNodes.add(node.id());
            }
        }
        edges.removeIf(edge -> dataModeNodes.contains(edge.toNode())
                && "text".equals(edge.toPort()));
    }

    // Migrate the switch data inputs
    private static void migrateSwitchDataInputs(List<Node> nodes, List<Edge> edges) {
        Set<String> dataSwitches = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (!"switch".equals(node.type())
                    || !AdvancedGraphCatalog.switchDataMode(node)) {
                continue;
            }
            dataSwitches.add(node.id());
            CompoundTag data = node.data();
            CompoundTag dynamicOutputs = data.getCompound("DynamicOutputs");
            CompoundTag dynamicInputs = data.getCompound("DynamicInputs");
            CompoundTag legacyTypes = data.getCompound(
                    AdvancedGraphPortState.SWITCH_OUTPUT_TYPES_TAG);
            CompoundTag caseTypes = data.getCompound(
                    AdvancedGraphPortState.SWITCH_CASE_TYPES_TAG);
            CompoundTag outputDefaults = data.getCompound(
                    AdvancedGraphPortState.OUTPUT_DEFAULTS_TAG);
            CompoundTag defaults = data.getCompound("Defaults");
            Set<String> ports = new LinkedHashSet<>();
            ports.add("default");
            for (String port : dynamicOutputs.getAllKeys()) {
                if (port.startsWith("case_")) {
                    ports.add(port);
                }
            }
            for (String port : dynamicInputs.getAllKeys()) {
                if (port.startsWith("case_")) {
                    ports.add(port);
                }
            }

            for (String port : ports) {
                String configured = caseTypes.getString(port);
                if (configured.isBlank()) {
                    configured = legacyTypes.getString(port);
                }
                if (configured.isBlank() && dynamicOutputs.contains(port)) {
                    configured = dynamicOutputs.getString(port);
                }
                String type = AdvancedGraphPortState.normalizeSwitchDataType(configured);
                caseTypes.putString(port, type);
                if (port.startsWith("case_")) {
                    dynamicInputs.putString(port, type);
                }
                if (!defaults.contains(port) && outputDefaults.contains(port)) {
                    defaults.put(port, outputDefaults.get(port).copy());
                }
                if (!defaults.contains(port)) {
                    defaults.put(port,
                            AdvancedGraphPortState.defaultValue(type).toTag());
                }
            }

            migrateSwitchPersistentPorts(data, defaults, ports);
            dynamicInputs.remove("exec");
            dynamicInputs.remove("value");
            data.put("DynamicInputs", dynamicInputs);
            data.put("Defaults", defaults);
            data.put(AdvancedGraphPortState.SWITCH_CASE_TYPES_TAG, caseTypes);
            data.remove("DynamicOutputs");
            data.remove(AdvancedGraphPortState.SWITCH_OUTPUT_TYPES_TAG);
            data.remove(AdvancedGraphPortState.OUTPUT_DEFAULTS_TAG);
        }
        for (int idx = 0; idx < edges.size(); idx++) {
            Edge edge = edges.get(idx);
            if (dataSwitches.contains(edge.fromNode())
                    && ("default".equals(edge.fromPort())
                    || edge.fromPort().startsWith("case_"))) {
                edges.set(idx, new Edge(
                        edge.id(), edge.fromNode(), "value",
                        edge.toNode(), edge.toPort()));
            }
        }
    }

    // Migrate the switch persistent ports
    private static void migrateSwitchPersistentPorts(
            CompoundTag data,
            CompoundTag defaults,
            Set<String> ports
    ) {
        CompoundTag persistentPorts = data.getCompound(
                AdvancedGraphPortState.PERSISTENT_PORTS_TAG);
        CompoundTag persistentValues = data.getCompound(
                AdvancedGraphPortState.PERSISTENT_VALUES_TAG);
        for (String port : ports) {
            String oldKey = "output:" + port;
            String newKey = "input:" + port;
            if (persistentPorts.getBoolean(oldKey)) {
                persistentPorts.putBoolean(newKey, true);
            }
            persistentPorts.remove(oldKey);
            if (persistentValues.contains(oldKey)) {
                Tag val = persistentValues.get(oldKey).copy();
                persistentValues.put(newKey, val);
                defaults.put(port, val.copy());
            }
            persistentValues.remove(oldKey);
        }
        if (persistentPorts.isEmpty()) {
            data.remove(AdvancedGraphPortState.PERSISTENT_PORTS_TAG);
        } else {
            data.put(AdvancedGraphPortState.PERSISTENT_PORTS_TAG, persistentPorts);
        }
        if (persistentValues.isEmpty()) {
            data.remove(AdvancedGraphPortState.PERSISTENT_VALUES_TAG);
        } else {
            data.put(AdvancedGraphPortState.PERSISTENT_VALUES_TAG, persistentValues);
        }
    }

    // Migrate the curve defaults
    private static void migrateCurveDefaults(List<Node> nodes) {
        for (Node node : nodes) {
            if (!"curve".equals(node.type())) {
                continue;
            }
            CompoundTag defaults = node.data().getCompound("Defaults");
            if (!defaults.contains("min")) {
                defaults.put("min", Value.number(AdvancedGraphCurve.DEFAULT_MINIMUM).toTag());
            }
            if (!defaults.contains("max")) {
                defaults.put("max", Value.number(AdvancedGraphCurve.DEFAULT_MAXIMUM).toTag());
            }
            if (!defaults.contains("speed")) {
                defaults.put("speed", Value.number(AdvancedGraphCurve.DEFAULT_SPEED).toTag());
            }
            if (!defaults.contains("value")) {
                defaults.put("value", Value.number(AdvancedGraphCurve.DEFAULT_MAXIMUM).toTag());
            }
            node.data().put("Defaults", defaults);
        }
    }

    // Migrate the deadzone ports
    private static void migrateDeadzonePorts(List<Node> nodes, List<Edge> edges) {
        Set<String> deadzoneNodes = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (!"deadzone".equals(node.type())) {
                continue;
            }
            deadzoneNodes.add(node.id());
            CompoundTag defaults = node.data().getCompound("Defaults");
            if (defaults.contains("amount") && !defaults.contains("resist")) {
                defaults.put("resist", defaults.get("amount").copy());
            }
            defaults.remove("amount");
            if (!defaults.contains("start")) {
                defaults.put("start", Value.number(0.0D).toTag());
            }
            node.data().put("Defaults", defaults);
        }
        for (int idx = 0; idx < edges.size(); idx++) {
            Edge edge = edges.get(idx);
            if (deadzoneNodes.contains(edge.toNode()) && "amount".equals(edge.toPort())) {
                edges.set(idx, new Edge(edge.id(), edge.fromNode(), edge.fromPort(),
                        edge.toNode(), "resist"));
            }
        }
    }

    // Migrate the ship speed pcts
    private static void migrateShipSpeedPcts(
            List<Node> nodes,
            List<Edge> edges
    ) {
        Set<String> connectedSpeedNodes = new LinkedHashSet<>();
        for (Edge edge : edges) {
            if ("speed".equals(edge.toPort())) {
                connectedSpeedNodes.add(edge.toNode());
            }
        }
        for (Node node : nodes) {
            if (!AdvancedGraphCatalog.isShipSpeedNode(node.type())) {
                continue;
            }
            if (connectedSpeedNodes.contains(node.id())) {
                node.data().remove(AdvancedGraphCatalog.SHIP_SPEED_PERCENT_TAG);
                continue;
            }
            CompoundTag defaults = node.data().getCompound("Defaults");
            double percent = AdvancedGraphCatalog.defaultShipSpeedPercent(node.type());
            if (defaults.contains("speed", Tag.TAG_COMPOUND)) {
                Value legacy = Value.fromTag(defaults.getCompound("speed"));
                if ("number".equals(legacy.type()) && Double.isFinite(legacy.asNumber())) {
                    percent = Math.max(0.0D, Math.min(100.0D, legacy.asNumber() * 100.0D));
                }
            }
            defaults.put("speed", Value.number(percent).toTag());
            node.data().put("Defaults", defaults);
            node.data().putBoolean(AdvancedGraphCatalog.SHIP_SPEED_PERCENT_TAG, true);
        }
    }

    // Read the copied tags
    private static void readCopiedTags(CompoundTag src, String key, List<CompoundTag> output) {
        ListTag tags = src.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            output.add(tags.getCompound(i).copy());
        }
    }

    // Store the node
    public record Node(String id, String type, String label, double x, double y, CompoundTag data)
            implements GraphModel.Node {
        // Initialize the node
        public Node {
            id = normalize(id);
            type = normalize(type);
            label = label == null ? "" : label;
            data = data == null ? new CompoundTag() : data.copy();
        }

        // Write the node data
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Type", type);
            tag.putString("Label", label);
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.put("Data", data.copy());
            return tag;
        }

        // Read the node data
        static Node fromTag(CompoundTag tag) {
            return new Node(tag.getString("Id"), tag.getString("Type"), tag.getString("Label"),
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getCompound("Data"));
        }
    }

    // Store the edge
    public record Edge(String id, String fromNode, String fromPort, String toNode, String toPort)
            implements GraphModel.Edge {
        // Initialize the edge
        public Edge {
            id = normalize(id);
            fromNode = normalize(fromNode);
            fromPort = normalize(fromPort);
            toNode = normalize(toNode);
            toPort = normalize(toPort);
        }

        // Write the edge data
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("FromNode", fromNode);
            tag.putString("FromPort", fromPort);
            tag.putString("ToNode", toNode);
            tag.putString("ToPort", toPort);
            return tag;
        }

        // Read the edge data
        static Edge fromTag(CompoundTag tag) {
            return new Edge(tag.getString("Id"), tag.getString("FromNode"), tag.getString("FromPort"),
                    tag.getString("ToNode"), tag.getString("ToPort"));
        }
    }

    // Store the value
    public record Value(String type, CompoundTag payload) {
        // Initialize the value
        public Value {
            type = normalize(type);
            payload = payload == null ? new CompoundTag() : payload.copy();
        }

        // Read the numeric value
        public static Value number(double val) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Value", val);
            return new Value("number", tag);
        }

        // Get the bool
        public static Value bool(boolean val) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Value", val);
            return new Value("boolean", tag);
        }

        // Get the string
        public static Value string(String val) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Value", val == null ? "" : val);
            return new Value("string", tag);
        }

        // Get the direction
        public static Value direction(String dir) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Value", dir == null ? "" : dir);
            return new Value("direction", tag);
        }

        // Get the frequency
        public static Value frequency(CompoundTag frequencyPair) {
            return new Value("frequency", frequencyPair);
        }

        // Get the target
        public static Value target(CompoundTag target) {
            return new Value("target", target);
        }

        // Get the list
        public static Value list(CompoundTag values) {
            return new Value("list", values);
        }

        // Map the value
        public static Value map(CompoundTag values) {
            return new Value("map", values);
        }

        // Get the value as number
        public double asNumber() {
            return payload.getDouble("Value");
        }

        // Get the value as boolean
        public boolean asBoolean() {
            return payload.getBoolean("Value");
        }

        // Get the value as string
        public String asString() {
            return payload.getString("Value");
        }

        // Write the value data
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", type);
            tag.put("Payload", payload.copy());
            return tag;
        }

        // Read the value data
        static Value fromTag(CompoundTag tag) {
            return new Value(tag.getString("Type"), tag.getCompound("Payload"));
        }
    }

    // Handle the function graph
    public static final class FunctionGraph
            implements GraphModel<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> {
        // Function graph id
        private final String id;
        // Current function graph name
        private String name;
        // Current viewport x
        private float viewportX = 150.0f;
        // Current viewport y
        private float viewportY = 80.0f;
        // Current viewport zoom
        private float viewportZoom = 1.0f;
        // Tracked nodes
        private final List<AdvancedGraphDocument.Node> nodes = new ArrayList<>();
        // Tracked edges
        private final List<AdvancedGraphDocument.Edge> edges = new ArrayList<>();
        // Tracked groups
        private final List<CompoundTag> groups = new ArrayList<>();

        // Initialize the function graph
        public FunctionGraph(String id, String name) {
            this.id = normalize(id);
            setName(name);
        }

        // Get the id
        public String id() {
            return id;
        }

        // Get the name
        public String name() {
            return name;
        }

        // Set the name
        public void setName(String name) {
            String normalized = name == null ? "" : name.strip();
            this.name = normalized.isBlank() ? "Function" : normalized.substring(0, Math.min(64, normalized.length()));
        }

        // Get the nodes
        public List<AdvancedGraphDocument.Node> nodes() {
            return nodes;
        }

        // Get the edges
        public List<AdvancedGraphDocument.Edge> edges() {
            return edges;
        }

        // Get the groups
        public List<CompoundTag> groups() {
            return groups;
        }

        // Get the viewport x
        public float viewportX() {
            return viewportX;
        }

        // Get the viewport y
        public float viewportY() {
            return viewportY;
        }

        // Get the viewport zoom
        public float viewportZoom() {
            return viewportZoom;
        }

        // Set the viewport
        public void setViewport(double x, double y, double zoom) {
            viewportX = (float) x;
            viewportY = (float) y;
            viewportZoom = (float) Math.max(0.05D, Math.min(1.75D, zoom));
        }

        // Write the function graph data
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Name", name);
            tag.putFloat("ViewportX", viewportX);
            tag.putFloat("ViewportY", viewportY);
            tag.putFloat("ViewportZoom", viewportZoom);
            ListTag nodeTags = new ListTag();
            nodes.forEach(node -> nodeTags.add(node.toTag()));
            tag.put("Nodes", nodeTags);
            ListTag edgeTags = new ListTag();
            edges.forEach(edge -> edgeTags.add(edge.toTag()));
            tag.put("Edges", edgeTags);
            ListTag groupTags = new ListTag();
            groups.forEach(group -> groupTags.add(group.copy()));
            tag.put("Groups", groupTags);
            return tag;
        }

        // Read the function graph data
        public static FunctionGraph fromTag(CompoundTag tag) {
            FunctionGraph function = new FunctionGraph(tag.getString("Id"), tag.getString("Name"));
            function.viewportX = tag.contains("ViewportX") ? tag.getFloat("ViewportX") : 150.0f;
            function.viewportY = tag.contains("ViewportY") ? tag.getFloat("ViewportY") : 80.0f;
            function.viewportZoom = tag.contains("ViewportZoom") ? tag.getFloat("ViewportZoom") : 1.0f;
            ListTag nodes = tag.getList("Nodes", Tag.TAG_COMPOUND);
            for (int i = 0; i < nodes.size(); i++) {
                function.nodes.add(AdvancedGraphDocument.Node.fromTag(nodes.getCompound(i)));
            }
            ListTag edges = tag.getList("Edges", Tag.TAG_COMPOUND);
            for (int i = 0; i < edges.size(); i++) {
                function.edges.add(AdvancedGraphDocument.Edge.fromTag(edges.getCompound(i)));
            }
            readCopiedTags(tag, "Groups", function.groups);
            return function;
        }
    }

    // Normalize the advanced graph document
    private static String normalize(String val) {
        return val == null ? "" : val.trim();
    }
}
