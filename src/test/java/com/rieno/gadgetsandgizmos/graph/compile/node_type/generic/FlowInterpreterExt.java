package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic;

import com.llamalad7.mixinextras.expression.impl.flow.FlowInterpreter;
import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.llamalad7.mixinextras.expression.impl.flow.postprocessing.FlowPostProcessor;
import com.machinezoo.noexception.throwing.ThrowingFunction;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.*;

class FlowInterpreterExt extends FlowInterpreter {
    public final Map<VarInsnNode, Type> localTypes = field(this, type -> type.getDeclaredField("localTypes"));
    public final Map<AbstractInsnNode, FlowValue> cache = field(this, type -> type.getDeclaredField("cache"));
    public final List<FlowPostProcessor> postProcessors = new ObjectArrayList<>(
        FlowInterpreterExt.<Collection<FlowPostProcessor>>field(this, type -> type.getDeclaredField("postProcessors"))
    );
    public final ClassNode classNode;
    public final MethodNode methodNode;

    public FlowInterpreterExt(ClassNode classNode, MethodNode methodNode) {
        super(classNode, methodNode, null);
        this.classNode = classNode;
        this.methodNode = methodNode;
    }

    @Override
    public Collection<FlowValue> finish() {
        Set<FlowValue> flows = Collections.newSetFromMap(new IdentityHashMap<>());
        flows.addAll(this.cache.values());
        for(FlowValue value : flows) {
            value.finish();
        }
        for(FlowValue value : flows) {
            value.onFinished();
        }
        for(FlowPostProcessor postProcessor : this.postProcessors) {
            Set<FlowValue> synthetic = Collections.newSetFromMap(new IdentityHashMap<>());
            List<FlowValue> newFlows = new ArrayList<>();
            FlowPostProcessor.OutputSink sink = new FlowPostProcessor.OutputSink() {
                @Override
                public void markAsSynthetic(FlowValue node) {
                    if(!node.isComplex()) {
                        synthetic.add(node);
                    }
                }

                @Override
                public void registerFlow(FlowValue... nodes) {
                    for(FlowValue node : nodes) {
                        visitNew(node, List.of());
                        if(!node.isComplex()) {
                            newFlows.add(node);
                        }
                    }
                }
            };
            for(FlowValue value : flows) {
                postProcessor.process(value, sink);
            }
            flows.removeAll(synthetic);
            for(FlowValue syntheticValue : synthetic) {
                syntheticValue.setParents(); // Unlink from the graph
            }
            flows.addAll(newFlows);
            for(FlowValue value : flows) {
                value.finish();
            }
            for(FlowValue value : flows) {
                value.onFinished();
            }
        }
        return flows;
    }

    static final MethodHandle recordFlow_handle = Handle.method(MethodHandles.lookup(), () -> FlowInterpreter.class.getDeclaredMethod("recordFlow", Type.class, AbstractInsnNode.class, FlowValue[].class));
    static final InsnList DUMMY = new InsnList();

    public @NotNull Result findValuesFramesUsages() {
        Frame<FlowValue>[] analyze;
        try {
            analyze = new Analyzer<>(this).analyze(this.classNode.name, this.methodNode);
        } catch(AnalyzerException e) {
            throw new RuntimeException("Failed to analyze value flow: ", e);
        }
        List<FlowValue> values = finish()
            .stream()
            .sorted(Comparator.comparingInt(it -> DUMMY.indexOf(it.getInsn())))
            .toList();

        return new Result(analyze, values, cache);
    }

    @SneakyThrows
    protected FlowValue recordFlow(Type type, AbstractInsnNode insn, FlowValue... inputs) {
        return (FlowValue) recordFlow_handle.invoke(this, type, insn, inputs);
    }


    @Override
    public FlowValue copyOperation(AbstractInsnNode insn, FlowValue value) {
        if(insn instanceof VarInsnNode varInsn) {

            Type type = localTypes.get(varInsn);
            return recordFlow(type == null ? value.getType() : type, insn, value);
        }
        return visitNew(super.copyOperation(insn, value), List.of(value));
    }

    @Override
    public FlowValue newOperation(AbstractInsnNode insn) {
        return visitNew(super.newOperation(insn), List.of());
    }

    @Override
    public FlowValue unaryOperation(AbstractInsnNode insn, FlowValue value) {
        return visitNew(super.unaryOperation(insn, value), List.of(value));
    }

    @Override
    public FlowValue binaryOperation(AbstractInsnNode insn, FlowValue value1, FlowValue value2) {
        return visitNew(super.binaryOperation(insn, value1, value2), List.of(value1, value2));
    }

    @Override
    public FlowValue ternaryOperation(AbstractInsnNode insn, FlowValue value1, FlowValue value2, FlowValue value3) {
        return visitNew(super.ternaryOperation(insn, value1, value2, value3), List.of(value1, value2, value3));
    }

    @Override
    public FlowValue naryOperation(AbstractInsnNode insn, List<? extends FlowValue> values) {
        return visitNew(super.naryOperation(insn, values), values);
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, FlowValue value, FlowValue expected) {
        super.returnOperation(insn, value, expected);
    }

    protected FlowValue visitNew(FlowValue flowValue, List<? extends FlowValue> value) {
        return flowValue;
    }


    @SneakyThrows
    @SuppressWarnings("unchecked")
    private static <T> T field(FlowInterpreterExt obj, ThrowingFunction<Class<FlowInterpreter>, Field> fieldMaker) {
        Class<FlowInterpreter> flowInterpreterClass = FlowInterpreter.class;
        Field field = fieldMaker.apply(flowInterpreterClass);
        field.setAccessible(true);
        return (T) field.get(obj);
    }

    public record Result(
        Frame<FlowValue>[] frames,
        List<FlowValue> values,
        Map<AbstractInsnNode, FlowValue> cache
    ) {

    }
}
