package com.rieno.gadgetsandgizmos.graph.type;

import com.machinezoo.noexception.throwing.ThrowingSupplier;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import com.rieno.gadgetsandgizmos.graph.compile.util.InsnAdapter;
import com.rieno.gadgetsandgizmos.graph.compile.util.PtrExtractor;
import lombok.SneakyThrows;
import org.intellij.lang.annotations.MagicConstant;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.util.function.Function;

public interface ConvertHelpers<T> {
    void setConvertExpression(ValueType<?> other, AbstractInsnNode... nodes);

    default ValueType<T> self() {
        return ((ValueType<T>) this);
    }

    default void convertViaOpcode(ValueType<?> other, @MagicConstant(valuesFromClass = Opcodes.class) int opcode) {
        setConvertExpression(other, new InsnNode(opcode));
    }

    @SneakyThrows
    default void convertViaMethodUnsafe(ValueType<?> other, ThrowingSupplier<Method> methodMaker) {
        Method method = Handle.method(methodMaker);
        MethodNode tmp = new MethodNode();
        InsnAdapter.invoke(tmp, method);
        setConvertExpression(other, tmp.instructions.getFirst());
    }

    default void convertViaMethod(ValueType<?> other, ThrowingSupplier<Method> methodMaker) throws NoSuchMethodException {
        convertViaMethodUnsafe(other, methodMaker);
    }

    default <O> void convertViaLambda(ValueType<O> other, Function<T, O> mapper) throws NoSuchMethodException {
        org.objectweb.asm.Handle handle = PtrExtractor.tryExtractMethod(1);
        MethodNode mv = new MethodNode();
        ValueType<T> self = self();
        if(handle != null) {
            Type outType = Type.getReturnType(handle.getDesc());
            Type[] argumentTypes = Type.getArgumentTypes(handle.getDesc());
            Type inType = argumentTypes.length == 0 ? null : argumentTypes[0];

            if(needBoxing(inType, self.innerType)) InsnAdapter.box(mv, self.innerType);
            InsnAdapter.invoke(mv, handle);
            if(needBoxing(outType, other.innerType)) InsnAdapter.unbox(mv, other.innerType);
        } else {
            InternalLambdaStorage.invokeLambda(mapper, self.innerType, other.innerType, mv);
        }
        setConvertExpression(other,
            mv.instructions.toArray()
        );
    }

    private static boolean needBoxing(Type inType, Type innerType) {
        return !innerType.equals(inType) && innerType.getSort() <= Type.DOUBLE;
    }

    default void convertViaStaticMethod(ValueType<?> other, Class<?> methodOwner, String methodName) {
        setConvertExpression(other, new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(methodOwner),
            methodName, Type.getMethodDescriptor(other.innerType, self().innerType),
            methodOwner.isInterface()
        ));
    }

    default void convertViaInstanceMethod(ValueType<?> other, String methodName) {
        boolean anInterface = false;
        try {
            anInterface = Class.forName(self().innerType.getClassName()).isInterface();
        } catch(ClassNotFoundException e) {
        }
        setConvertExpression(other, new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            self().innerType.getInternalName(),
            methodName, Type.getMethodDescriptor(other.innerType),
            //innerType.isInterface()
            anInterface
        ));
    }

}
