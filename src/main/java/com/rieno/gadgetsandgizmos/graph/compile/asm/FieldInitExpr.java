package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.machinezoo.noexception.throwing.ThrowingSupplier;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handles;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public sealed interface FieldInitExpr permits AsmExpression, FieldInitExpr.AnyObject, FieldInitExpr.Value {
    static AsmExpression.ReflectionConstructor constructor(ThrowingSupplier<Constructor<?>> constructor) {return constructor(Handles.constructor(constructor));}

    static AsmExpression.ReflectionConstructor constructor(Constructor<?> constructor) {return new AsmExpression.ReflectionConstructor(constructor);}

    static AsmExpression.ReflectionMethod method(ThrowingSupplier<Method> method) {return method(Handles.method(method));}

    static AsmExpression.ReflectionMethod method(Method method) {return new AsmExpression.ReflectionMethod(method);}
    static  AsmExpression.InsnListAsm list(Consumer<MethodVisitor> maker) { return AsmExpression.list(maker); }
    static  AsmExpression.InsnListAsm list(AbstractInsnNode list) { return AsmExpression.list(list); }
    static AnyObject someObject(@NonNull Object value) {return new AnyObject(value.getClass(),value);}
    static <T> AnyObject someObject(Class<? super T> type,T value) {return new AnyObject(type,value);}

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


    record AnyObject(Class<?> type, Object value) implements FieldInitExpr {}
}
