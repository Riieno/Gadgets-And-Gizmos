package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Expose Advanced Contraption Controller controls and telemetry to ComputerCraft
public class AdvancedContraptionControllerPeripheral extends AnalogueContraptionControllerPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Advanced
    private final AdvancedContraptionControllerBlockEntity advanced;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller peripheral
    public AdvancedContraptionControllerPeripheral(AdvancedContraptionControllerBlockEntity blockEntity) {
        super(blockEntity);
        this.advanced = blockEntity;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return "advanced_contraption_controller";
    }

    // Get the graph status
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getGraphStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("draftRevision", advanced.getDraftGraph().revision());
        status.put("activeRevision", advanced.getActiveGraph().revision());
        status.put("template", advanced.getDraftGraph().templateId());
        status.put("valid", advanced.validateDraft().valid());
        return status;
    }

    // Get the list graph variables
    @LuaFunction(mainThread = true)
    public final List<String> listGraphVariables() {
        return List.copyOf(advanced.getActiveGraph().variables().keySet());
    }

    // Get the graph variable
    @LuaFunction(mainThread = true)
    public final Object getGraphVariable(String name) throws LuaException {
        AdvancedGraphDocument.Value val = advanced.getActiveGraph().variables().get(name);
        if (val == null) throw new LuaException("unknown graph variable '" + name + "'");
        return switch (val.type()) {
            case "boolean" -> val.asBoolean();
            case "string" -> val.asString();
            default -> val.asNumber();
        };
    }

    // Set the graph variable
    @LuaFunction(mainThread = true)
    public final void setGraphVariable(String name, Object value) throws LuaException {
        AdvancedGraphDocument.Value graphValue;
        if (value instanceof Boolean bool) {
            graphValue = AdvancedGraphDocument.Value.bool(bool);
        } else if (value instanceof Number num) {
            graphValue = AdvancedGraphDocument.Value.number(num.doubleValue());
        } else if (value instanceof String string) {
            graphValue = AdvancedGraphDocument.Value.string(string);
        } else {
            throw new LuaException("graph variables support numbers, booleans, and strings");
        }
        if (!advanced.setGraphVariable(name, graphValue)) {
            throw new LuaException("invalid graph variable name '" + name + "'");
        }
    }

    // Trigger the graph event
    @LuaFunction(mainThread = true)
    public final void triggerGraphEvent(String eventId) {
        advanced.triggerGraphEvent(eventId);
    }

    // Get the graph diagnostics
    @LuaFunction(mainThread = true)
    public final List<Map<String, String>> getGraphDiagnostics() {
        return advanced.getGraphDiagnostics().stream().map(diagnostic -> Map.of(
                "severity", diagnostic.severity(),
                "code", diagnostic.code(),
                "message", diagnostic.message(),
                "nodeId", diagnostic.nodeId())).toList();
    }

    // Validate the draft
    @LuaFunction(mainThread = true)
    public final boolean validateDraft() {
        return advanced.validateDraft().valid();
    }

    // Apply the draft
    @LuaFunction(mainThread = true)
    public final boolean applyDraft() {
        return advanced.applyDraft();
    }
}
