package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Keep graph port defaults, links and live values in one stable serialized shape
public final class AdvancedGraphPortState {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String PERSISTENT_PORTS_TAG = "PersistentPorts";
    public static final String PERSISTENT_VALUES_TAG = "PersistentPortValues";
    public static final String OUTPUT_DEFAULTS_TAG = "OutputDefaults";
    public static final String SWITCH_OUTPUT_TYPES_TAG = "SwitchOutputTypes";
    public static final String SWITCH_CASE_TYPES_TAG = "SwitchCaseTypes";
    public static final List<String> SWITCH_DATA_TYPES = List.of(
            "any", "boolean", "string", "number", "direction", "frequency", "target");

    private static final String INPUT_PREFIX = "input:";
    private static final String OUTPUT_PREFIX = "output:";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph port state
    private AdvancedGraphPortState() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is persistent
    public static boolean isPersistent(
            AdvancedGraphDocument.Node node, String port, boolean output
    ) {
        return node != null && isPersistent(node.data(), port, output);
    }

    // Check if this is persistent
    public static boolean isPersistent(CompoundTag data, String port, boolean output) {
        return data != null && port != null
                && data.getCompound(PERSISTENT_PORTS_TAG).getBoolean(key(port, output));
    }

    // Set the persistent
    public static void setPersistent(
            AdvancedGraphDocument.Node node,
            String port,
            boolean output,
            boolean persistent,
            AdvancedGraphDocument.Value currentValue
    ) {
        if (node == null || port == null || port.isBlank()) {
            return;
        }
        CompoundTag ports = node.data().getCompound(PERSISTENT_PORTS_TAG);
        String key = key(port, output);
        if (persistent) {
            ports.putBoolean(key, true);
            node.data().put(PERSISTENT_PORTS_TAG, ports);
            if (currentValue != null) {
                updatePersistentValue(node, port, output, currentValue);
            }
        } else {
            ports.remove(key);
            if (ports.isEmpty()) {
                node.data().remove(PERSISTENT_PORTS_TAG);
            } else {
                node.data().put(PERSISTENT_PORTS_TAG, ports);
            }
            CompoundTag values = node.data().getCompound(PERSISTENT_VALUES_TAG);
            values.remove(key);
            if (values.isEmpty()) {
                node.data().remove(PERSISTENT_VALUES_TAG);
            } else {
                node.data().put(PERSISTENT_VALUES_TAG, values);
            }
        }
    }

    // Update the persistent value
    public static boolean updatePersistentValue(
            AdvancedGraphDocument.Node node,
            String port,
            boolean output,
            AdvancedGraphDocument.Value val
    ) {
        return val != null && updatePersistentValueTag(
                node, port, output, val.toTag());
    }

    // Update the persistent value tag
    public static boolean updatePersistentValueTag(
            AdvancedGraphDocument.Node node,
            String port,
            boolean output,
            CompoundTag encoded
    ) {
        if (node == null || encoded == null || !isPersistent(node, port, output)) {
            return false;
        }
        String key = key(port, output);
        CompoundTag values = node.data().getCompound(PERSISTENT_VALUES_TAG);
        boolean changed = !values.contains(key)
                || !values.getCompound(key).equals(encoded);
        if (!changed) {
            return false;
        }
        values.put(key, encoded.copy());
        node.data().put(PERSISTENT_VALUES_TAG, values);
        if (!output) {
            CompoundTag defaults = node.data().getCompound("Defaults");
            defaults.put(port, encoded.copy());
            node.data().put("Defaults", defaults);
        }
        return true;
    }

    // Get the persistent value
    public static AdvancedGraphDocument.Value persistentValue(
            AdvancedGraphDocument.Node node, String port, boolean output
    ) {
        if (node == null || port == null) {
            return null;
        }
        CompoundTag values = node.data().getCompound(PERSISTENT_VALUES_TAG);
        String key = key(port, output);
        return values.contains(key)
                ? AdvancedGraphDocument.Value.fromTag(values.getCompound(key)) : null;
    }

    // Switch the output type
    public static String switchOutputType(AdvancedGraphDocument.Node node, String port) {
        if (node == null || port == null || !AdvancedGraphCatalog.switchDataMode(node)) {
            return "exec";
        }
        return switchOutputType(node.data(), port, true);
    }

    // Switch the case type
    public static String switchCaseType(AdvancedGraphDocument.Node node, String port) {
        if (node == null || port == null || !AdvancedGraphCatalog.switchDataMode(node)) {
            return "any";
        }
        return switchCaseType(node.data(), port);
    }

    // Switch the case type
    public static String switchCaseType(CompoundTag data, String port) {
        if (data == null || port == null) {
            return "any";
        }
        String configured = data.getCompound(SWITCH_CASE_TYPES_TAG).getString(port);
        if (configured.isBlank()) {
            configured = data.getCompound(SWITCH_OUTPUT_TYPES_TAG).getString(port);
        }
        return normalizeSwitchDataType(configured);
    }

    // Set the switch case type
    public static void setSwitchCaseType(
            AdvancedGraphDocument.Node node, String port, String requestedType
    ) {
        if (node == null || port == null || !"switch".equals(node.type())
                || !("default".equals(port) || port.startsWith("case_"))) {
            return;
        }
        String type = normalizeSwitchDataType(requestedType);
        CompoundTag types = node.data().getCompound(SWITCH_CASE_TYPES_TAG);
        types.putString(port, type);
        node.data().put(SWITCH_CASE_TYPES_TAG, types);

        if (port.startsWith("case_")) {
            CompoundTag dynamicInputs = node.data().getCompound("DynamicInputs");
            dynamicInputs.putString(port, type);
            node.data().put("DynamicInputs", dynamicInputs);
        }
        AdvancedGraphDocument.Value current = inputDefault(node, port, type);
        setInputDefault(node, port, type, current);
    }

    // Get the input default
    public static AdvancedGraphDocument.Value inputDefault(
            AdvancedGraphDocument.Node node, String port, String type
    ) {
        String normalizedType = normalizeSwitchDataType(type);
        if (node != null && port != null) {
            CompoundTag defaults = node.data().getCompound("Defaults");
            if (defaults.contains(port)) {
                AdvancedGraphDocument.Value stored =
                        AdvancedGraphDocument.Value.fromTag(defaults.getCompound(port));
                return "any".equals(normalizedType)
                        ? stored : AdvancedGraphRuntime.convertValue(stored, normalizedType);
            }
        }
        return defaultValue(normalizedType);
    }

    // Set the input default
    public static void setInputDefault(
            AdvancedGraphDocument.Node node,
            String port,
            String type,
            AdvancedGraphDocument.Value val
    ) {
        if (node == null || port == null || port.isBlank()) {
            return;
        }
        String normalizedType = normalizeSwitchDataType(type);
        AdvancedGraphDocument.Value converted = val == null
                ? defaultValue(normalizedType)
                : "any".equals(normalizedType)
                ? val : AdvancedGraphRuntime.convertValue(val, normalizedType);
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.put(port, converted.toTag());
        node.data().put("Defaults", defaults);
        if (isPersistent(node, port, false)) {
            updatePersistentValue(node, port, false, converted);
        }
    }

    // Switch the output type
    public static String switchOutputType(CompoundTag data, String port, boolean dataMode) {
        if (data == null || port == null || !dataMode) {
            return "exec";
        }
        String configured = data.getCompound(SWITCH_OUTPUT_TYPES_TAG).getString(port);
        return normalizeSwitchDataType(configured);
    }

    // Set the switch output type
    public static void setSwitchOutputType(
            AdvancedGraphDocument.Node node, String port, String requestedType
    ) {
        if (node == null || port == null || !"switch".equals(node.type())) {
            return;
        }
        String type = normalizeSwitchDataType(requestedType);
        CompoundTag types = node.data().getCompound(SWITCH_OUTPUT_TYPES_TAG);
        types.putString(port, type);
        node.data().put(SWITCH_OUTPUT_TYPES_TAG, types);

        CompoundTag dynamicOutputs = node.data().getCompound("DynamicOutputs");
        if (port.startsWith("case_")) {
            dynamicOutputs.putString(port,
                    AdvancedGraphCatalog.switchDataMode(node) ? type : "exec");
            node.data().put("DynamicOutputs", dynamicOutputs);
        }
        AdvancedGraphDocument.Value current = outputDefault(node, port, type);
        setOutputDefault(node, port, type, current);
    }

    // Get the output default
    public static AdvancedGraphDocument.Value outputDefault(
            AdvancedGraphDocument.Node node, String port, String type
    ) {
        String normalizedType = normalizeSwitchDataType(type);
        if (node != null && port != null) {
            CompoundTag defaults = node.data().getCompound(OUTPUT_DEFAULTS_TAG);
            if (defaults.contains(port)) {
                AdvancedGraphDocument.Value stored =
                        AdvancedGraphDocument.Value.fromTag(defaults.getCompound(port));
                return "any".equals(normalizedType)
                        ? stored : AdvancedGraphRuntime.convertValue(stored, normalizedType);
            }
        }
        return defaultValue(normalizedType);
    }

    // Set the output default
    public static void setOutputDefault(
            AdvancedGraphDocument.Node node,
            String port,
            String type,
            AdvancedGraphDocument.Value val
    ) {
        if (node == null || port == null || port.isBlank()) {
            return;
        }
        String normalizedType = normalizeSwitchDataType(type);
        AdvancedGraphDocument.Value converted = val == null
                ? defaultValue(normalizedType)
                : "any".equals(normalizedType)
                ? val : AdvancedGraphRuntime.convertValue(val, normalizedType);
        CompoundTag defaults = node.data().getCompound(OUTPUT_DEFAULTS_TAG);
        defaults.put(port, converted.toTag());
        node.data().put(OUTPUT_DEFAULTS_TAG, defaults);
        if (isPersistent(node, port, true)) {
            updatePersistentValue(node, port, true, converted);
        }
    }

    // Create the default value
    public static AdvancedGraphDocument.Value defaultValue(String type) {
        return switch (normalizeSwitchDataType(type)) {
            case "boolean" -> AdvancedGraphDocument.Value.bool(false);
            case "any", "string" -> AdvancedGraphDocument.Value.string("");
            case "direction" -> AdvancedGraphDocument.Value.direction("");
            case "frequency" -> AdvancedGraphDocument.Value.frequency(new CompoundTag());
            case "target" -> AdvancedGraphDocument.Value.target(new CompoundTag());
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Normalize the switch data type
    public static String normalizeSwitchDataType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if ("integer".equals(normalized) || "float".equals(normalized)) {
            normalized = "number";
        }
        return SWITCH_DATA_TYPES.contains(normalized) ? normalized : "any";
    }

    // Merge the persistent values
    public static void mergePersistentValues(
            AdvancedGraphDocument src, AdvancedGraphDocument destination
    ) {
        if (src == null || destination == null) {
            return;
        }
        mergeNodes(src.nodes(), destination.nodes());
        Map<String, AdvancedGraphDocument.FunctionGraph> sourceFunctions = new LinkedHashMap<>();
        for (AdvancedGraphDocument.FunctionGraph function : src.functions()) {
            sourceFunctions.put(function.id(), function);
        }
        for (AdvancedGraphDocument.FunctionGraph destinationFunction : destination.functions()) {
            AdvancedGraphDocument.FunctionGraph sourceFunction =
                    sourceFunctions.get(destinationFunction.id());
            if (sourceFunction != null) {
                mergeNodes(sourceFunction.nodes(), destinationFunction.nodes());
            }
        }
    }

    // Merge the nodes
    private static void mergeNodes(
            List<AdvancedGraphDocument.Node> src,
            List<AdvancedGraphDocument.Node> destination
    ) {
        Map<String, AdvancedGraphDocument.Node> sourceNodes = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : src) {
            sourceNodes.put(node.id(), node);
        }
        for (AdvancedGraphDocument.Node destinationNode : destination) {
            AdvancedGraphDocument.Node sourceNode = sourceNodes.get(destinationNode.id());
            if (sourceNode == null) {
                continue;
            }
            CompoundTag destinationPorts =
                    destinationNode.data().getCompound(PERSISTENT_PORTS_TAG);
            CompoundTag sourceValues = sourceNode.data().getCompound(PERSISTENT_VALUES_TAG);
            for (String key : destinationPorts.getAllKeys()) {
                if (!destinationPorts.getBoolean(key) || !sourceValues.contains(key)) {
                    continue;
                }
                CompoundTag val = sourceValues.getCompound(key).copy();
                CompoundTag destinationValues =
                        destinationNode.data().getCompound(PERSISTENT_VALUES_TAG);
                destinationValues.put(key, val);
                destinationNode.data().put(PERSISTENT_VALUES_TAG, destinationValues);
                if (key.startsWith(INPUT_PREFIX)) {
                    String port = key.substring(INPUT_PREFIX.length());
                    CompoundTag defaults = destinationNode.data().getCompound("Defaults");
                    defaults.put(port, val.copy());
                    destinationNode.data().put("Defaults", defaults);
                }
            }
        }
    }

    // Handle key
    private static String key(String port, boolean output) {
        return (output ? OUTPUT_PREFIX : INPUT_PREFIX) + port;
    }
}
