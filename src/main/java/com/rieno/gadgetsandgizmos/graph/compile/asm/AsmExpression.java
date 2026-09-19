package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.rieno.gadgetsandgizmos.graph.compile.util.InsnAdapter;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public sealed interface AsmExpression extends FieldInitExpr {
    static  FieldInitExpr list(AbstractInsnNode... list) {
        return new InsnListAsm(list);
    }
    static  FieldInitExpr list(Consumer<MethodVisitor> maker) {
        MethodNode node = new MethodNode();
        maker.accept(node);
        return new InsnListAsm(node.instructions.toArray());
    }

    record ReflectionConstructor(Constructor<?> init) implements AsmExpression {}

    record ReflectionMethod(Method init) implements AsmExpression {
        public InsnListAsm bindToThis() {
            MethodNode node = new MethodNode();
            node.visitVarInsn(Opcodes.ALOAD, 0);
            InsnAdapter.invoke(node, init);
            return new InsnListAsm(node.instructions.toArray());
        }
    }

    record InsnListAsm(AbstractInsnNode... init) implements AsmExpression {}
}
