package com.rieno.gadgetsandgizmos.graph.struct;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Might be compiled lazy
 *
 */
public abstract class NodeCalculator {
    public final AdvancedGraphDocument.Value[] ports;

    public NodeCalculator(int ports) {
        this.ports = new AdvancedGraphDocument.Value[ports];
    }

    public abstract void calculate(@NotNull Mode mode, @Nullable String event);

    public enum Mode {
        CalculatePorts,
        ExecFollow,
        Passive;
        public final int id = ordinal();
        public final Field myField = Handle.field(() -> Mode.class.getDeclaredField(name()));
        public final static Mode[] all = values();

    }
}
