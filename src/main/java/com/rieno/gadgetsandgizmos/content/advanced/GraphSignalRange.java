package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.control.SignalValueType;

// Convert graph signals using the node's explicitly selected value type
public final class GraphSignalRange {
    public static final String REDSTONE_SIGNAL_PORT = "redstone_signal_strength";
    public static final String VALUE_TYPE_TAG = "SignalValueType";
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph signal range
    private GraphSignalRange() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Identify output nodes that consume scalar or redstone signals
    public static boolean usesValueType(String type){
        if(type == null) return false;
        return switch(type){
            case "controller_channel_output", "direct_target_output", "linker_face_output",
                    "local_redstone_output", "wireless_frequency_output", "set_block_data" -> true;
            default -> false;
        };
    }

    // Resolve the saved output type without guessing from the input value
    public static SignalValueType valueType(AdvancedGraphDocument.Node node){
        return SignalValueType.fromId(node == null ? "" : node.data().getString(VALUE_TYPE_TAG));
    }

    // Read normalized redstone inputs as signal strengths
    static double fromNormalizedRedstone(double val){
        return SignalValueType.SCALAR.toRedstoneStrength(val);
    }

    // Convert an untyped strength using the explicit redstone default
    public static int outputStrength(double val){
        return SignalValueType.REDSTONE.toRedstoneStrength(val);
    }

    // Convert output strengths according to the node's saved selection
    public static int outputStrength(AdvancedGraphDocument.Node node, double val){
        return valueType(node).toRedstoneStrength(val);
    }

    // Normalize quantized signal outputs for transport
    static double toNormalizedRedstone(AdvancedGraphDocument.Node node, double val){
        return valueType(node).toNormalizedRedstone(val);
    }

    // Preserve continuous scalar values when driving analogue receivers
    public static double toDirectControl(AdvancedGraphDocument.Node node, double val){
        return valueType(node).toScalar(val);
    }

    // Identify both legacy and generated per-target redstone inputs
    public static boolean isRedstonePort(AdvancedGraphDocument.Node node, String port){
        return REDSTONE_SIGNAL_PORT.equals(port) || AdvancedGraphTargetSignals.isTargetSignal(node, port);
    }

    // Compare and cache the same signal value that the target receives
    public static AdvancedGraphDocument.Value targetValue(AdvancedGraphDocument.Node node, String port,
                                                   AdvancedGraphDocument.Value val){
        if(val == null || !"number".equals(val.type())) return val;
        if(isRedstonePort(node, port)){
            return AdvancedGraphDocument.Value.number(outputStrength(node, val.asNumber()));
        }
        if("set_block_data".equals(node.type()) && "direct_signal".equals(port)){
            return AdvancedGraphDocument.Value.number(toDirectControl(node, val.asNumber()));
        }
        return val;
    }
}
