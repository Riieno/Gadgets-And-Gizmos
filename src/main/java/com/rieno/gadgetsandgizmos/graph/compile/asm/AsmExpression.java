package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public sealed interface AsmExpression extends FieldInitExpr {
    record ReflectionConstructor(Constructor<?> init) implements AsmExpression {}

    record ReflectionMethod(Method init) implements AsmExpression {
        public InsnListAsm bindToThis() {
            MethodNode node = new MethodNode();
            node.visitVarInsn(Opcodes.ALOAD, 0);
            GeneratorHelper.invoke(node, init);
            return new InsnListAsm(node.instructions.toArray());
        }
    }

    record InsnListAsm(AbstractInsnNode... init) implements AsmExpression {}
}
