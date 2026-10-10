package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

public class NonPublicReference extends RuntimeException {
    public NonPublicReference(Class<?> type) {
        super("'%s' remove usage(except field getting) of object with that type, or make is public.");
    }
}
