package com.rieno.gadgetsandgizmos.compat.computercraft.client;

import com.rieno.gadgetsandgizmos.compat.computercraft.AdvancedContraptionControllerPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDocumentation;

import java.util.List;
import java.util.stream.Stream;

// Keep completion names aligned with the documented controller bridge
public final class LuaEditorCompletions{
    private LuaEditorCompletions(){}

    public static List<String> names(){
        return PeripheralDocumentation.catalog(AdvancedContraptionControllerPeripheral.class).entries().stream()
                .flatMap(entry -> Stream.of("gng." + entry.name() + "(", "acc." + entry.name() + "(")).toList();
    }
}
