package com.rieno.gadgetsandgizmos.graph.compile.util;

import com.machinezoo.noexception.throwing.ThrowingSupplier;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraph;
import com.rieno.gadgetsandgizmos.graph.struct.NodeCalculator;
import lombok.Lombok;
import lombok.SneakyThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

public class Handle {
    public static final Method firstPartOfEvent, secondPartOfEvent;
    public static final Method jvmGraph_nodeId;
    public static final Method jvmGraph_calculator_fromInt = method(() -> AbstractJVMGraph.class.getDeclaredMethod("getOrCreateNodeCalculator", int.class));
    public static final Method calculator_calc = method(() -> NodeCalculator.class.getDeclaredMethod("calculate", NodeCalculator.Mode.class, String.class));
    public static Method NULLABLE_OBJECT_EQUALS = method(() -> Objects.class.getDeclaredMethod("equals", Object.class, Object.class));
    public static Method NOT_NULL_OBJECT_EQUALS = method(() -> Object.class.getDeclaredMethod("equals", Object.class));

    @SneakyThrows
    public static Method method(ThrowingSupplier<Method> getter) {
        return getter.get();
    }
    @SneakyThrows
    public static Constructor<?> constructor(ThrowingSupplier<Constructor<?>> getter) {
        return getter.get();
    }
    @SneakyThrows
    public static Field field(ThrowingSupplier<Field> getter) {
        return getter.get();
    }
    //TODO compile time check

    static {
        try {
            firstPartOfEvent = Handle.class.getDeclaredMethod("firstPartOfEvent", String.class);
            secondPartOfEvent = Handle.class.getDeclaredMethod("secondPartOfEvent", String.class);
            jvmGraph_nodeId = Handle.class.getDeclaredMethod("jvmGraph_nodeId", AbstractJVMGraph.class, String.class);
        } catch(NoSuchMethodException e) {
            throw Lombok.sneakyThrow(e);
        }
    }

    public static String firstPartOfEvent(String eventId) {
        int i = eventId.indexOf(':');
        return i == -1 ? eventId : eventId.substring(0, i);
    }

    public static String secondPartOfEvent(String eventId) {
        int i = eventId.indexOf(':');
        return i == -1 ? null : eventId.substring(i + 1);
    }

    public static int jvmGraph_nodeId(AbstractJVMGraph graph, String nodeId) {
        return graph.nodeToIndex.getOrDefault(nodeId, -1);
    }

}
