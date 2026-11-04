package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphPortState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

// Hold execution port schema helpers for the advanced graph editor
final class AdvancedGraphExecutionPortSupport {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final int MIN_EXECUTION_OUTPUTS = 2;
    static final int MAX_EXECUTION_OUTPUTS = 16;
    static final int MIN_EXECUTION_INPUTS = 2;
    static final int MAX_EXECUTION_INPUTS = 16;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the execution port support
    private AdvancedGraphExecutionPortSupport() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is an execution splitter type
    static boolean isExecutionSplitterType(String type) {
        return "parallel_execution".equals(type) || "sequenced_execution".equals(type)
                || "switch".equals(type);
    }

    // Check if this is an execution splitter node
    static boolean isExecutionSplitterNode(AdvancedGraphDocument.Node node) {
        return node != null && isExecutionSplitterType(node.type());
    }

    // Get the execution output count
    static int executionOutputCount(AdvancedGraphDocument.Node node) {
        if (node == null) return MIN_EXECUTION_OUTPUTS;
        return clampExecOutputs(node.data().contains("OutputCount", Tag.TAG_INT)
                ? node.data().getInt("OutputCount")
                : MIN_EXECUTION_OUTPUTS);
    }

    // Clamp the execution output count
    static int clampExecOutputs(int count) {
        return Mth.clamp(count, MIN_EXECUTION_OUTPUTS, MAX_EXECUTION_OUTPUTS);
    }

    // Put the execution outputs
    static void putExecutionOutputs(String type, CompoundTag data, int count) {
        int clamped = clampExecOutputs(count);
        boolean dataSwitch = "switch".equals(type)
                && AdvancedGraphCatalog.SWITCH_DATA_TYPE.equalsIgnoreCase(
                data.getString(AdvancedGraphCatalog.SWITCH_TYPE_TAG));
        if (dataSwitch) {
            CompoundTag inputs = data.getCompound("DynamicInputs");
            inputs.getAllKeys().stream()
                    .filter(port -> port.startsWith("case_"))
                    .toList().forEach(inputs::remove);
            CompoundTag types = data.getCompound(
                    AdvancedGraphPortState.SWITCH_CASE_TYPES_TAG);
            CompoundTag defaults = data.getCompound("Defaults");
            for (int idx = 0; idx < clamped; idx++) {
                String port = executionOutputPort(type, idx);
                String caseType = AdvancedGraphPortState.switchCaseType(data, port);
                types.putString(port, caseType);
                inputs.putString(port, caseType);
                if (!defaults.contains(port)) {
                    defaults.put(port, graphDefault(caseType,
                            AdvancedGraphPortState.defaultValue(caseType)));
                }
            }
            String defaultType = AdvancedGraphPortState.switchCaseType(data, "default");
            types.putString("default", defaultType);
            if (!defaults.contains("default")) {
                defaults.put("default", graphDefault(defaultType,
                        AdvancedGraphPortState.defaultValue(defaultType)));
            }
            data.put("DynamicInputs", inputs);
            data.put("Defaults", defaults);
            data.put(AdvancedGraphPortState.SWITCH_CASE_TYPES_TAG, types);
            data.remove("DynamicOutputs");
            return;
        }
        CompoundTag inputs = data.getCompound("DynamicInputs");
        inputs.getAllKeys().stream()
                .filter(port -> port.startsWith("case_"))
                .toList().forEach(inputs::remove);
        if (inputs.isEmpty()) data.remove("DynamicInputs");
        else data.put("DynamicInputs", inputs);
        CompoundTag outputs = new CompoundTag();
        for (int idx = 0; idx < clamped; idx++) {
            outputs.putString(executionOutputPort(type, idx), "exec");
        }
        data.put("DynamicOutputs", outputs);
    }

    // Check if the execution ports match
    static boolean executionPortsMatch(AdvancedGraphDocument.Node node, CompoundTag ports, int count) {
        long casePortCount = ports.getAllKeys().stream()
                .filter(port -> port.startsWith(
                        "switch".equals(node.type()) ? "case_" : "exec_"))
                .count();
        if (casePortCount != count) return false;
        for (int idx = 0; idx < count; idx++) {
            String port = executionOutputPort(node.type(), idx);
            String expected = AdvancedGraphCatalog.switchDataMode(node)
                    ? AdvancedGraphPortState.switchCaseType(node, port)
                    : AdvancedGraphCatalog.switchOutputType(node);
            if (!expected.equals(ports.getString(port))) return false;
        }
        return true;
    }

    // Get the execution output port
    static String executionOutputPort(String type, int zeroBasedIndex) {
        return "switch".equals(type) ? "case_" + zeroBasedIndex : "exec_" + (zeroBasedIndex + 1);
    }

    // Check if the execution output was removed
    static boolean removedExecutionOutput(String type, String port, int count) {
        String prefix = "switch".equals(type) ? "case_" : "exec_";
        if (port == null || !port.startsWith(prefix)) return false;
        try {
            int idx = Integer.parseInt(port.substring(prefix.length()));
            return "switch".equals(type) ? idx >= count : idx > count;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // Check if this is an execution combiner type
    static boolean isExecutionCombinerType(String type) {
        return "exec_combine".equals(type);
    }

    // Check if this is an execution combiner node
    static boolean isExecutionCombinerNode(AdvancedGraphDocument.Node node) {
        return node != null && isExecutionCombinerType(node.type());
    }

    // Get the execution input count
    static int executionInputCount(AdvancedGraphDocument.Node node) {
        if (node == null) return MIN_EXECUTION_INPUTS;
        int count = node.data().contains("InputCount", Tag.TAG_INT)
                ? node.data().getInt("InputCount")
                : MIN_EXECUTION_INPUTS;
        return Mth.clamp(count, MIN_EXECUTION_INPUTS, MAX_EXECUTION_INPUTS);
    }

    // Put the execution inputs
    static void putExecutionInputs(CompoundTag data, int count) {
        CompoundTag inputs = new CompoundTag();
        int clamped = Mth.clamp(count, MIN_EXECUTION_INPUTS, MAX_EXECUTION_INPUTS);
        for (int idx = 1; idx <= clamped; idx++) {
            inputs.putString("exec_" + idx, "exec");
        }
        data.put("DynamicInputs", inputs);
    }

    // Check if the execution inputs match
    static boolean executionInputsMatch(CompoundTag inputs, int count) {
        if (inputs == null || inputs.getAllKeys().size() != count) return false;
        for (int idx = 1; idx <= count; idx++) {
            if (!"exec".equals(inputs.getString("exec_" + idx))) return false;
        }
        return true;
    }

    // Check if the execution input was removed
    static boolean removedExecutionInput(String port, int count) {
        if (port == null || !port.startsWith("exec_")) return false;
        try {
            return Integer.parseInt(port.substring("exec_".length())) > count;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // Get the graph default
    static CompoundTag graphDefault(String type, Object val) {
        if (val instanceof AdvancedGraphDocument.Value graphValue) {
            return graphDefault(type, graphValue);
        }
        CompoundTag entry = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        entry.putString("Type", type);
        if (val instanceof Boolean bool) payload.putBoolean("Value", bool);
        else if (val instanceof Number num) payload.putDouble("Value", num.doubleValue());
        else if (val instanceof CompoundTag compound) payload.merge(compound.copy());
        else payload.putString("Value", String.valueOf(val));
        entry.put("Payload", payload);
        return entry;
    }

    // Get the graph default
    static CompoundTag graphDefault(String type, AdvancedGraphDocument.Value val) {
        if (val == null) return graphDefault(type, "");
        return switch (type) {
            case "boolean" -> graphDefault(type, val.asBoolean());
            case "number" -> graphDefault(type, val.asNumber());
            case "string", "direction" -> graphDefault(type, val.asString());
            default -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("Type", type);
                entry.put("Payload", val.payload().copy());
                yield entry;
            }
        };
    }
}
