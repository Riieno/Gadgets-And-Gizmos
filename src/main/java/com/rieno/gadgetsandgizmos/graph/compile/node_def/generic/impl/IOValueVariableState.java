package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

@AllArgsConstructor
@Getter
@Accessors(fluent = true)
public enum IOValueVariableState {
    KEEP(true,true,false, true),

    FLAT_RECORD_KEEP_VARIABLE(true,false,true, true),
    FLAT_RECORD_NO_VARIABLE(false,false,true, false),

    FLAT_BOX_KEEP_VARIABLE(true,false,false, true),
    FLAT_BOX_NO_VARIABLE(false,true,false, true),
    ;

    private final boolean keep,loadInputFromInputPort,flatRecordFields, loadStoreNormalVariable;
    public static IOValueVariableState forRecord(boolean couldRemove, boolean needFlat) {
        if(!needFlat) return KEEP;
        return couldRemove ? FLAT_RECORD_NO_VARIABLE : FLAT_RECORD_KEEP_VARIABLE;
    }
    public static IOValueVariableState forBox(boolean couldRemove, boolean needFlat) {
        if(!needFlat) return KEEP;
        return couldRemove ? FLAT_BOX_NO_VARIABLE : FLAT_BOX_KEEP_VARIABLE;
    }
}
