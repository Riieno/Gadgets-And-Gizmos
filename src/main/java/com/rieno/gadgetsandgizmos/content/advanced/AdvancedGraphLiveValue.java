package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

// Store one live graph value
public record AdvancedGraphLiveValue(String type, double numberValue,
                                     boolean booleanValue, String textValue, int entryCount,
                                     Map<String, String> entryTypes) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_TEXT_LENGTH = 512;
    private static final int MAX_ENTRY_TYPES = 64;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph live value
    public AdvancedGraphLiveValue {
        type = type == null ? "" : type;
        textValue = truncate(textValue == null ? "" : textValue);
        entryCount = Math.max(0, entryCount);
        entryTypes = entryTypes == null ? Map.of() : Map.copyOf(entryTypes);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the advanced graph live value
    public static AdvancedGraphLiveValue from(AdvancedGraphDocument.Value val) {
        if (val == null) {
            return new AdvancedGraphLiveValue("", 0.0D, false, "", 0, Map.of());
        }
        return switch (val.type()) {
            case "number" -> new AdvancedGraphLiveValue(
                    val.type(), val.asNumber(), false, "", 0, Map.of());
            case "boolean" -> new AdvancedGraphLiveValue(
                    val.type(), 0.0D, val.asBoolean(), "", 0, Map.of());
            case "string", "direction" -> new AdvancedGraphLiveValue(
                    val.type(), 0.0D, false, val.asString(), 0, Map.of());
            case "target" -> new AdvancedGraphLiveValue(
                    val.type(), 0.0D, false, val.payload().getString("Label"), 0, Map.of());
            default -> new AdvancedGraphLiveValue(
                    val.type(), 0.0D, false,
                    val.payload().isEmpty() ? val.type() : val.payload().toString(),
                    "list".equals(val.type()) ? GraphRuntime.listSize(val) : val.payload().size(),
                    entryTypes(val));
        };
    }

    // Get the entry types
    private static Map<String, String> entryTypes(AdvancedGraphDocument.Value val) {
        CompoundTag outputs = AdvancedGraphRuntime.splitListOutputsFor(val);
        Map<String, String> res = new LinkedHashMap<>();
        for (String key : outputs.getAllKeys()) {
            if (res.size() >= MAX_ENTRY_TYPES) {
                break;
            }
            res.put(truncate(key), truncate(outputs.getString(key)));
        }
        return res;
    }

    // Truncate the live graph value
    private static String truncate(String val) {
        return val.length() <= MAX_TEXT_LENGTH ? val : val.substring(0, MAX_TEXT_LENGTH);
    }
}
