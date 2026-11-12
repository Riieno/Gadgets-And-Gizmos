package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.*;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.BoxingTool;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class ProcessedLambda_Unboxing extends AbstractJVMGraphCompilerTest {
    private JVMGraphRuntime original = runtime;
    private JVMGraphRuntime testUnboxingDebug = runtime = new JVMGraphRuntime(new TestDebugProps() {
        @Override
        public void onClassDefine(JVMGraphLoader loader, ClassNode node) {
            if(!node.superName.endsWith(AbstractJVMGraph.class.getSimpleName())) {
                for(MethodNode method : node.methods) {
                    if(method.name.equals("<init>")) continue;
                    if(method.name.equals("delayed")) continue;
                    if(method.name.equals("<clinit>")) continue;
                    for(AbstractInsnNode insn : method.instructions) {
                        if(insn instanceof MethodInsnNode methodInsnNode) {
                            if(BoxingTool.isBoxingUnboxing(methodInsnNode)) {
                                printMethod(method);
                                throw new AssertionError("Unboxing found");
                            }
                        }
                    }
                }
            }
            super.onClassDefine(loader, node);
        }
    });

    @Test
    void alwaysPass() {
        JVMRegistry.register("unboxing", DefHelper.binary("c", (Double a, Double b) -> {
            return a + b;
        }));
        test(1, 2, "unboxing", 3);
        test(2, 2, "unboxing", 4);
    }

    @Test
    void testUsageOfBoxedAsNonBoxed() {
        JVMRegistry.register("unboxing", DefHelper.binary("c", (Double a, Double b) -> {
            return plus(a, b, 0);
        }));
        test(1, 2, "unboxing", 3);
        test(2, 2, "unboxing", 4);
    }


    public void test(double a, double b, String type, double c) {
        super.simpleTest(a, b, type, c, type);
    }

     static double plus(double a, double b, double zero) {
        return a + b;
    }
}
