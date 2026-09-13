package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.machinezoo.noexception.throwing.ThrowingSupplier;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import lombok.SneakyThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public sealed interface FieldInitExpr permits AsmExpression, FieldInitExpr.Value {
    static AsmExpression.ReflectionConstructor constructor(ThrowingSupplier<Constructor<?>> constructor) {return constructor(Handle.constructor(constructor));}

    static AsmExpression.ReflectionConstructor constructor(Constructor<?> constructor) {return new AsmExpression.ReflectionConstructor(constructor);}

    static AsmExpression.ReflectionMethod method(ThrowingSupplier<Method> method) {return method(Handle.method(method));}

    static AsmExpression.ReflectionMethod method(Method method) {return new AsmExpression.ReflectionMethod(method);}

    sealed interface Value extends FieldInitExpr {
        record Int(int value) implements Value {}

        record Float(float value) implements Value {}

        record Long(long value) implements Value {}

        record Double(double value) implements Value {}

        record String(String value) implements Value {}

        @SneakyThrows
        default Object getValue() {
            Method accessor = getClass().getRecordComponents()[0].getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(this);
        }
    }
}
