package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;

import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

// Track graph outputs that changed so only new values are synchronized
final class AdvancedGraphOutputDelta {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double NUMBER_EPSILON = 1.0E-6D;
    private static final Map<AdvancedContraptionControllerBlockEntity,
            ControllerOutputState> OUTPUT_STATES = new WeakHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph output delta
    private AdvancedGraphOutputDelta() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the changed ports
    static synchronized Set<String> changedPorts(
            AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node,
            Map<String, AdvancedGraphDocument.Value> desiredValues
    ) {
        Set<String> changed = new LinkedHashSet<>();
        if (controller == null || node == null) {
            changed.addAll(desiredValues.keySet());
            return changed;
        }
        NodeOutputState outputState = outputState(controllerState(controller), node);
        long gameTime = controller.getLevel() == null
                ? Long.MIN_VALUE : controller.getLevel().getGameTime();
        if (outputState.sampledGameTime != gameTime) {
            outputState.sampledGameTime = gameTime;
            outputState.currentValues.clear();
        }
        desiredValues.forEach((port, desired) -> {
            if (AdvancedContraptionControllerBlockEntity.GRAPH_RAW_DIRECT_SIGNAL_PORT.equals(port)) {
                return;
            }
            AdvancedGraphDocument.Value current = outputState.currentValues.get(port);
            if (current == null || gameTime == Long.MIN_VALUE) {
                current = controller.getGraphTargetData(node, port);
                if (gameTime != Long.MIN_VALUE && current != null) {
                    outputState.currentValues.put(port, current);
                }
            }
            AdvancedGraphDocument.Value lastApplied = outputState.lastAppliedValues.get(port);
            if (!sameValue(current, desired)
                    || lastApplied != null && !sameValue(lastApplied, desired)) {
                changed.add(port);
            }
        });
        return changed;
    }

    // Record the applied
    static synchronized void recordApplied(AdvancedContraptionControllerBlockEntity controller,
                                           AdvancedGraphDocument.Node node,
                                           Set<String> ports,
                                           Map<String, AdvancedGraphDocument.Value> desiredValues) {
        if (controller == null || node == null || ports == null || desiredValues == null) {
            return;
        }
        ControllerOutputState controllerState = controllerState(controller);
        for (NodeOutputState state : controllerState.nodes.values()) {
            state.currentValues.clear();
        }
        NodeOutputState outputState = outputState(controllerState, node);
        for (String port : ports) {
            AdvancedGraphDocument.Value val = desiredValues.get(port);
            if (val != null) {
                outputState.lastAppliedValues.put(port, val);
                outputState.currentValues.put(port, val);
            }
        }
    }

    // Invalidate the samples
    static synchronized void invalidateSamples(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        ControllerOutputState controllerState = OUTPUT_STATES.get(controller);
        if (controllerState == null) {
            return;
        }
        for (NodeOutputState state : controllerState.nodes.values()) {
            state.sampledGameTime = Long.MIN_VALUE;
            state.currentValues.clear();
        }
    }

    // Get the controller state
    private static ControllerOutputState controllerState(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        return OUTPUT_STATES.computeIfAbsent(controller, ignored -> new ControllerOutputState());
    }

    // Get the output state
    private static NodeOutputState outputState(
            ControllerOutputState controllerState, AdvancedGraphDocument.Node node
    ) {
        NodeOutputState outputState = controllerState.nodes.computeIfAbsent(
                node.id(), ignored -> new NodeOutputState(node));
        if (outputState.node != node) {
            outputState.node = node;
            int targetSignature = writeTargetSignature(node);
            if (outputState.targetSignature != targetSignature) {
                outputState.targetSignature = targetSignature;
                outputState.lastAppliedValues.clear();
            }
            outputState.sampledGameTime = Long.MIN_VALUE;
            outputState.currentValues.clear();
        }
        return outputState;
    }

    // Write the target signature
    private static int writeTargetSignature(AdvancedGraphDocument.Node node) {
        return Objects.hash(node.data().getCompound("TargetData"),
                node.data().getCompound("Defaults").getCompound("face"));
    }

    // Store controller output state
    private static final class ControllerOutputState {
        // Tracked nodes
        private final Map<String, NodeOutputState> nodes = new HashMap<>();
    }

    // Store node output state
    private static final class NodeOutputState {
        // Current node
        private AdvancedGraphDocument.Node node;
        // Target signature
        private int targetSignature;
        // Current sampled game time
        private long sampledGameTime = Long.MIN_VALUE;
        // Current values
        private final Map<String, AdvancedGraphDocument.Value> currentValues = new HashMap<>();
        // Last applied values
        private final Map<String, AdvancedGraphDocument.Value> lastAppliedValues = new HashMap<>();

        // Initialize the node output state
        private NodeOutputState(AdvancedGraphDocument.Node node) {
            this.node = node;
            this.targetSignature = writeTargetSignature(node);
        }
    }

    // Check if this uses the same value
    static boolean sameValue(AdvancedGraphDocument.Value current, AdvancedGraphDocument.Value desired) {
        if (current == desired) {
            return true;
        }
        if (current == null || desired == null || !current.type().equals(desired.type())) {
            return false;
        }
        if ("number".equals(current.type())) {
            return Math.abs(current.asNumber() - desired.asNumber()) <= NUMBER_EPSILON;
        }
        return current.payload().equals(desired.payload());
    }
}
