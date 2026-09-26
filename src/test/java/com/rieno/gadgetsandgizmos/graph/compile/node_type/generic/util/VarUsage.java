package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.util;

public enum VarUsage {
    NONE,
    READ,
    WRITE,
    BOTH;
    public final int id = ordinal();
    public final static VarUsage[] all = values();

    public VarUsage or(VarUsage other) {
        return all[id | other.id];
    }

}
