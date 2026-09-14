package com.rieno.gadgetsandgizmos.graph.compile.subsystem;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraph;
import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphCompiler;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphLoader;
import com.rieno.gadgetsandgizmos.graph.compile.annotations.StateHolder;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.struct.NodeCalculator;
import lombok.Lombok;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.lang.reflect.InvocationTargetException;

public class CalculatorGenerator {
    private static final Type calculator = Type.getType(NodeCalculator.class);
    public static final Method method;
    public static final String EVENT_ID = "eventId";
    public static final String CALC_MODE_VARIABLE = "mode";

    static {
        try {
            method = Method.getMethod(NodeCalculator.class.getDeclaredMethod("calculate", NodeCalculator.Mode.class, String.class));
        } catch(NoSuchMethodException e) {
            throw Lombok.sneakyThrow(e);
        }
    }

    private static final Type calculatorType = Type.getType(NodeCalculator.class);

    public static NodeCalculator defineCalculator(JVMGraphCompiler.Cache cache, JVMGraphLoader loader, AbstractJVMGraph abstractJVMGraph, int targetNodeID) {
        SnapNode targetNode = cache.nodes[targetNodeID];

        ClassNode calculatorClassNode = new ClassNode();
        calculatorClassNode.name = NodeCalculator.class.getSimpleName() + "$" + cache.className + "$" + targetNodeID;
        calculatorClassNode.superName = calculator.getInternalName();

        defineCtorAndField(Type.getType(abstractJVMGraph.getClass()), calculatorClassNode, targetNode);

        MethodNode calculateMethod = defineCalculateMethod(cache, calculatorClassNode, targetNode);

        calculatorClassNode.methods.add(calculateMethod);


        try {
            Class<?> aClass = JVMGraphCompiler.defineClass(loader, calculatorClassNode);
            return (NodeCalculator) aClass.getConstructor(abstractJVMGraph.getClass()).newInstance(abstractJVMGraph);
        } catch(ClassNotFoundException | InvocationTargetException | InstantiationException |
                IllegalAccessException | NoSuchMethodException e) {
            throw Lombok.sneakyThrow(e);
        }


    }

    private static @NotNull MethodNode defineCalculateMethod(JVMGraphCompiler.Cache cache, ClassNode calculatorClassNode, SnapNode targetNode) {
        MethodNode calculateMethod = new MethodNode(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, null);
        GeneratorHelper adapter = JVMGraphCompiler.adapter(calculateMethod, calculatorClassNode);
        adapter.nameArg(0, CALC_MODE_VARIABLE);
        adapter.nameArg(1, EVENT_ID);

        final CompilationContext context = new CompilationContext(cache);
        targetNode.type.typeProperties(context.typeProperties, targetNode);
        boolean needSeparateCallTree = context.typeProperties.modeSensitivePortCalculator || context.typeProperties.customControlFlow;
        if(!needSeparateCallTree) {
            callTree(targetNode, adapter, context);
        }

        adapter.loadLocal("mode");
        adapter.invokeVirtual(Type.getType(Enum.class), "ordinal", Type.INT_TYPE);

        Label dflt = new Label();
        Label[] modeToLabel = new Label[NodeCalculator.Mode.all.length];
        for(int i = 0; i < modeToLabel.length; i++) {
            modeToLabel[i] = new Label();
        }

        adapter.visitTableSwitchInsn(
            0,
            modeToLabel.length - 1,
            dflt,
            modeToLabel
        );
        var tableSwitchInsnNode = ((TableSwitchInsnNode) adapter.node.instructions.getLast());
        for(NodeCalculator.Mode mode : NodeCalculator.Mode.all) {
            adapter.visitLabel(modeToLabel[mode.id]);
            context.reset();
            context.calculatorMode = mode;
            //For compile time checks
            Void i = switch(mode) {
                case CalculatePorts -> {
                    if(needSeparateCallTree) {
                        callTree(targetNode, adapter, context);
                    }
                    yield storePorts(adapter, targetNode);
                }
                case ExecFollow -> execFollow(context, adapter, targetNode, needSeparateCallTree);
                case Passive -> {
                    if(needSeparateCallTree)
                        callTree(targetNode, adapter, context);
                    yield null;
                }
            };
            adapter.goTo(dflt);
        }

        adapter.visitLabel(dflt);
        adapter.returnValue();
        adapter.visitEnd();
        return calculateMethod;
    }

    private static void callTree(SnapNode targetNode, GeneratorHelper adapter, CompilationContext context) {
        NodeFlowGenerator.buildNodeCallTree(
            context.compilationCache.nodes, adapter,
            targetNode.id,
            NodeFlowGenerator.defaultOutputs(targetNode), context
        );
    }

    private static @Nullable Void execFollow(CompilationContext context, GeneratorHelper adapter, SnapNode targetNode, boolean needCallTree) {
        NodeFlowGenerator.buildNodeExecSignalPath(context, adapter, targetNode.id, needCallTree);
        return null;
    }

    private static void defineCtorAndField(Type ownerGraphType, ClassNode calculatorClassNode, SnapNode targetNode) {
        FieldNode $$owner_field = new FieldNode(Opcodes.ACC_PUBLIC, "$$owner", ownerGraphType.getDescriptor(), null, null);
        calculatorClassNode.fields.add($$owner_field);
        $$owner_field.visitAnnotation(Type.getDescriptor(StateHolder.class), true);


        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, ownerGraphType), null, null);
        calculatorClassNode.methods.add(ctor);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        JVMGraphCompiler.adapter(ctor, calculatorClassNode).push(targetNode.portTypes.length);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, calculatorType.getInternalName(), "<init>", "(I)V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, calculatorClassNode.name, $$owner_field.name, $$owner_field.desc);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitEnd();
    }

    private static Void storePorts(GeneratorHelper adapter, SnapNode targetNode) {
        Inputs inputs = NodeFlowGenerator.defaultInputs(targetNode);
        String[] portNames = targetNode.portIndexerInverse;
        int inputsAmount = targetNode.inputs.length;

        adapter.loadThis();
        adapter.getField(calculatorType, "ports", Type.getType(AdvancedGraphDocument.Value[].class));

        for(int i = 0; i < inputsAmount; i++) {
            adapter.dup();
            adapter.push(i);

            ValueType<?> portType = targetNode.portTypes[i];
            if(portType.isCannotBeSaved()){
                adapter.pushNull();
            }else{
                inputs.load(adapter, portNames[i]);
                portType.wrapToValue(adapter);
            }

            adapter.visitInsn(Opcodes.AASTORE);
        }

        for(int i = inputsAmount; i < portNames.length; i++) {
            adapter.dup();
            adapter.push(i);


            ValueType<?> portType = targetNode.portTypes[i];
            if(portType.isCannotBeSaved()){
                adapter.pushNull();
            }else{
                GeneratorHelper.PortVarEntry entry = adapter.findEntry(targetNode, i);
                if(entry!=null){
                    adapter.loadLocal(entry);
                    portType.wrapToValue(adapter);
                }else{
                    adapter.pushNull();
                }
            }

            adapter.visitInsn(Opcodes.AASTORE);
        }

        adapter.pop();
        return null;
    }
}
