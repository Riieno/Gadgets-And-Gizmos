package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.impl;

import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.llamalad7.mixinextras.expression.impl.flow.postprocessing.InstantiationInfo;
import com.llamalad7.mixinextras.expression.impl.utils.FlowDecorations;
import com.llamalad7.mixinextras.lib.apache.commons.tuple.Pair;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.util.RecordInfo;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.util.UsageInterpreter;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.util.UsageStatistics;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.*;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.record.InlinedGenericRecordFunctionNode.propsInCanonicalCtor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessedLambda {
    public final AbstractInsnNode[] insnArray;
    public final OutputPortRef outputPortRef;
    public final Object2ObjectOpenCustomHashMap<AbstractInsnNode, IOValue> outputValues;
    public final IOValue rawOutputValue;
    public final IOValue[] inputValues;
    public final Object2ObjectArrayMap<String, ValueType<?>> inputPorts;
    public final Object2ObjectArrayMap<String, ValueType<?>> outputPorts;

    /**
     * TODO: flat records
     *
     */
    public static ProcessedLambda make(
        Options options,
        @Nullable Class<?> targetInterace,
        int searchLevelOffset,
        Object lambda
    ) {
        targetInterace = getTargetInterace(lambda, targetInterace);
        var classAndMethod = findMethod(1 + searchLevelOffset);
        MethodNode methodNode = classAndMethod.methodNode;
        var params = bakeArgNames(methodNode);
        return make(
            options, methodNode,
            classAndMethod.classNode,
            key -> params[key]
        );

    }

    private static String @NotNull [] bakeArgNames(MethodNode methodNode) {
        Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        var params = new String[argumentTypes.length];
        if(methodNode.localVariables != null) {
            int offset = 0;
            for(int i = 0; i < argumentTypes.length; i++) {
                for(LocalVariableNode variable : methodNode.localVariables) {
                    if(variable.index == offset) {
                        params[i] = variable.name;
                        break;
                    }
                }
                offset += argumentTypes[i].getSize();
            }
        } else {
            throw new RuntimeException("Cannot find names for input ports");
        }
        return params;
    }

    private static ProcessedLambda make(
        Options options, final MethodNode lambdaBody,
        final ClassNode lambdaOwner,
        final Int2ObjectFunction<String> argumentDefNames
    ) {
        AbstractInsnNode[] originalInsnArray = lambdaBody.instructions.toArray();
        var fields = new Object2ObjectOpenHashMap<Type, Set<String>>();
        var usageAnalyzerResult = new UsageInterpreter(lambdaOwner, lambdaBody, fields).findValuesFramesUsages();

        ObjectArrayList<AbstractInsnNode> transformedNodes = new ObjectArrayList<>();
        var frames = usageAnalyzerResult.frames();
        Type[] argumentTypes = Type.getArgumentTypes(lambdaBody.desc);
        IOValue[] inputValues = new IOValue[argumentTypes.length];
        Type returnType = Type.getReturnType(lambdaBody.desc);

        for(int i = 0; i < argumentTypes.length; i++) {
            Type argumentType = argumentTypes[i];
            var val = inputValues[i] = IOValue.make(
                frames[0].getLocal(i),
                argumentType,
                options.flatInputPredicate().test(CompileUtil.type2class(argumentType), argumentTypes, i)
            );
            val.couldRemoveRecordVariables(val.usage().onlyFields() && val.isRecord() && val.needToBeFlat());
            val.doRemoveIntermediateVariable(val.doRemoveIntermediateVariable()||val.couldRemoveRecordVariables());

            Type unboxedType = BoxingTool.getUnboxedType(val.type());
            if(unboxedType != null) {
                val.couldRemoveBoxing(val.needToBeFlat() && val.usage().onlyUnwrapperAndReturn());
                //outputValue.couldReduceVariable(true);
            }
        }
        {
            int offset = 0;
            for(int i = 0; i < inputValues.length; i++) {
                IOValue value = inputValues[i];
                boolean needToFlat = options.flatInputPredicate().test(value.clazz(), argumentTypes, i);
                if(!value.couldRemoveRecordVariables() && needToFlat && value.isRecord()) {
                    transformedNodes.add(makeInputRecordVar(value, i, offset));
                } else {
                    int myOffset = offset, myI = i;
                    String portName = argumentDefNames.get(i);
                    if(usageAnalyzerResult.modifiedArguments().get(offset)) {
                        Type type = value.type();
                        transformedNodes.add(compilable("init varMap MUT input_" + i, (mv, snapNode, inputs, outputs, data, context) -> {
                            int tmpVar = mv.newLocal(type);
                            context.varMap.put(myOffset, tmpVar);
                            inputs.load(mv,portName);
                            mv.storeLocal(tmpVar);
                        }));
                    }else{
                        transformedNodes.add(compilable("init varMap input_" + i, (mv, snapNode, inputs, outputs, data, context) -> {
                            context.varMap.put(myOffset, -myI);
                            context.varToPortMap.put(myI, portName);
                        }));
                    }
                }
                offset += value.type().getSize();
            }
        }
        transformedNodes.add(compilable("init offset and shift locals", (mv, snapNode, inputs, outputs, data, context) -> {
            int offset = mv.nextLocal() + (mv.nextLocal() & 1) - 1;
            mv.nextLocal(offset + 1 + lambdaBody.maxLocals);
            context.setOffset(offset);
        }));
        InsnAdapter.LabelCloner cloner = InsnAdapter.labelCloner();
        var oldInsnToNew = new Int2IntOpenHashMap();
        var replacedNodes = new Int2ObjectOpenHashMap<List<AbstractInsnNode>>();
        var ignoreInsn = new IntOpenHashSet();
        var outputPortRef = new OutputPortRef();

        var rawOutputValue = IOValue.make(
            null,
            returnType,
            options.flatOutputRecord()
        );
        var outputValues = new Object2ObjectOpenCustomHashMap<AbstractInsnNode, IOValue>(IndexInsnHashStrategy.INSTANCE);
        BoxingTool.Entry boxingEntry = BoxingTool.boxingEntry(rawOutputValue.type());
        boolean doUnwrapOutput = options.flatOutputRecord();
        if(boxingEntry != null && options.flatOutputRecord()) {
            IntArrayList output = new IntArrayList();
            Handle boxingMethod = boxingEntry.boxingMethod();
            Int2ObjectMap<UsageStatistics> usageStatistics = usageAnalyzerResult.usageStatistics();
            for(var entry : usageStatistics.int2ObjectEntrySet()) {
                UsageStatistics usage = entry.getValue();
                if(!usage.returnValue) continue;
                if(!usage.onlyUnwrapperAndReturn()) {
                    doUnwrapOutput = false;
                    break;
                }
                int insnIdx = entry.getIntKey();
                if(
                    originalInsnArray[insnIdx] instanceof MethodInsnNode method && !areEqual(method, boxingMethod) ||
                    !topStack(frames[insnIdx + 1], 1).getType().equals(boxingEntry.unboxed())
                ) {
                    doUnwrapOutput = false;
                    break;
                }
                output.add(insnIdx);

            }
            if(doUnwrapOutput) {
                outputPortRef.used=true;
                var replacement = List.of(compilable("store_single_output", (mv, snapNode, inputs, outputs, data, context) -> {
                    outputs.store(mv, outputPortRef.name);
                }));
                for(int i = 0; i < output.size(); i++) {
                    int insnIdx = output.getInt(i);
                    FlowValue flowValue = topStack(frames[insnIdx + 1], 1);
                    ObjectArrayList<UsageInterpreter.BoxUnboxEntry> entries = UsageInterpreter.wrapUnwrapMap(flowValue);
                    if(entries != null && !entries.isEmpty()) {
                        int max = Integer.MIN_VALUE;
                        for(UsageInterpreter.BoxUnboxEntry entry : entries) {
                            int insn = entry.insn();
                            if(!entry.isBoxing()) continue;
                            if(max < 0) {
                                max = insn;
                            } else if(max < insn) {
                                ignoreInsn.add(max);
                                max = insn;
                            } else {
                                ignoreInsn.add(entry.insn());
                            }
                        }
                        if(max >= 0)
                            replacedNodes.put(max, replacement);
                    }
                }
            }
        }
        rawOutputValue.couldRemoveBoxing(doUnwrapOutput);
        for(int i = 0; i < originalInsnArray.length; i++) {
            AbstractInsnNode node = originalInsnArray[i];
            {
                List<AbstractInsnNode> nodes = replacedNodes.get(i);
                if(nodes != null) {
                    transformedNodes.addAll(nodes);
                    continue;
                }
            }
            if(ignoreInsn.contains(i)) continue;


            shortcut:
            {
                UsageStatistics statistics = usageAnalyzerResult.usageStatistics().get(i);
                if(statistics != null && statistics.returnValue) {
                    IOValue outputValue = cachedOutputValue(outputValues, topStack(frames[i + 1], 1), rawOutputValue);
                    if(outputValue.recordInfo() != null && outputValue.usage().onlyReturn() && outputValue.flowValue().getInsn() == node && outputValue.needToBeFlat()) {
                        if(node instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.NEW) {
                            if(typeInsn.desc.equals(rawOutputValue.type().getInternalName())) {

                                InstantiationInfo instantiationInfo = outputValue.flowValue().getDecoration(FlowDecorations.INSTANTIATION_INFO);
                                List<String> props = propsInCanonicalCtor(instantiationInfo.initCall.getInsn(), outputValue.recordInfo());
                                if(props == null) break shortcut;
                                outputValue.doRemoveIntermediateVariable(true);
                                replacedNodes.put(CompileUtil.indexOf(instantiationInfo.initCall.getInsn()), List.of(storePorts(props)));
                                if(i + 1 < originalInsnArray.length && originalInsnArray[i + 1].getOpcode() == Opcodes.DUP) {
                                    ignoreInsn.add(i + 1);
                                } else {
                                    for(Pair<FlowValue, Integer> pair : outputValue.flowValue().getNext()) {
                                        FlowValue left = pair.getLeft();
                                        if(left.getInsn().getOpcode() == Opcodes.DUP) {
                                            ignoreInsn.add(CompileUtil.indexOf(left.getInsn()));
                                        }
                                    }
                                }
                                continue;
                            }
                        }
                    }

                }
                if(node instanceof VarInsnNode varNode) {
                    int varOpcode = varNode.getOpcode();
                    boolean isLoad = varOpcode <= Opcodes.ALOAD;
                    Frame<FlowValue> frame = isLoad ? frames[i + 1] : frames[i];
                    FlowValue local1 = topStack(frame, 1);
                    int argI = UsageInterpreter.getArgumentIndex(local1);
                    if(argI >= 0) {
                        if(!inputValues[argI].doRemoveIntermediateVariable()) {
                            String name = argumentDefNames.get(argI);
                            transformedNodes.add(compilable("transformed VarInsn loadInput#" +argI, (mv, snapNode, inputs, outputs, data, context) -> {
                                inputs.load(mv,name);
                            }));
                        }
                        continue;
                    }
                    if(UsageInterpreter.isOutput(local1) && cachedOutputValue(outputValues, local1, rawOutputValue).doRemoveIntermediateVariable())
                        continue;


                    int varIdx = varNode.var;
                    transformedNodes.add(compilable("transformed VarInsn.var=" + varIdx, (mv, snapNode, inputs, outputs, data, context) -> {
                        int offset = context.getOffset();
                        int varIndex = context.varMap.computeIfAbsent(varIdx, _z -> _z + offset);
                        if(varIndex < 0) {
                            String portName = context.varToPortMap.get(-varIndex);
                            inputs.load(mv, portName);
                        } else {
                            mv.mv().visitVarInsn(varOpcode, varIndex);
                        }
                    }));
                    continue;
                }
                if(node instanceof MethodInsnNode || node instanceof FieldInsnNode) {

                    shortcut2:
                    {
                        int argIndex = UsageInterpreter.getArgumentIndex(topStack(frames[i], 1));
                        if(argIndex < 0) break shortcut2;
                        IOValue inputValue = inputValues[argIndex];
                        if(inputValue.recordInfo() != null) {
                            if(inputValue.doRemoveIntermediateVariable()) {
                                String prop = inputValue.recordInfo().toPropertyName(node);
                                if(prop == null) break shortcut;
                                transformedNodes.add(compilable("load input#" + prop, (mv, snapNode, inputs, outputs, data, context) -> {
                                    inputs.load(mv, prop);
                                }));
                                continue;
                            }
                            break shortcut2;
                        } else if(node instanceof MethodInsnNode method && BoxingTool.isBoxingUnboxing(method) && inputValue.couldRemoveBoxing()) {
                            //inputValue.doRemoveIntermediateVariable(true);
                            continue;
                        }

                    }
                    if(!(node instanceof FieldInsnNode fieldInsn)) break shortcut;
                    transformedNodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fieldInsn.owner, fieldInsn.name, "()" + fieldInsn.desc, false));
                    continue;
                }
                if(node.getOpcode() == Opcodes.ARETURN) {
                    IOValue outputValue = cachedOutputValue(outputValues, topStack(frames[i], 1), rawOutputValue);
                    if(!outputValue.couldRemoveRecordVariables()) continue;
                    RecordInfo outputRecord = outputValue.recordInfo();
                    for(Map.Entry<String, Type> entry : outputRecord.fieldMap.entrySet()) {
                        transformedNodes.add(new InsnNode(Opcodes.DUP));
                        String port = entry.getKey();
                        Type type = entry.getValue();
                        transformedNodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, outputRecord.type.getInternalName(), port, "()" + type.getDescriptor(), false));
                        transformedNodes.add(compilable("output_destruct#" + port, (mv, snapNode, inputs, outputs, data, context) -> {
                            outputs.store(mv, port);
                        }));
                    }

                    continue;
                }
                if(node.getOpcode() == Opcodes.RETURN) {
                    continue;
                }
                if(CompileUtil.isReturn(node.getOpcode())) {
                    FlowValue value = topStack(frames[i], 1);
                    Type type = value.getType();
                    int numeric2double = CompileUtil.numeric2double(type.getSort());
                    ValueType<?> returnedValueType = valueType(type);

                    outputPortRef.used=true;
                    transformedNodes.add(compilable(i + ": single return", (mv, snapNode, inputs, outputs, data, context) -> {
                        if(numeric2double >= 0 && returnedValueType == null) {
                            mv.visitInsn(numeric2double);
                        } else if(returnedValueType != null) {
                            returnedValueType.convertTo(mv, outputPortRef.valueType);
                        }
                        outputs.store(mv, outputPortRef.name);
                    }));
                    continue;
                }
            }


            oldInsnToNew.put(i, transformedNodes.size());
            transformedNodes.add(node.clone(cloner));
        }

        return ProcessedLambda.of(
            transformedNodes.toArray(AbstractInsnNode[]::new),
            outputPortRef,
            outputValues,
            rawOutputValue,
            inputValues,
            argumentDefNames,
            options
        );
    }

    private static boolean areEqual(MethodInsnNode method, Handle boxingMethod) {
        return method.owner.equals(boxingMethod.getOwner()) && method.name.equals(boxingMethod.getName()) && method.desc.equals(boxingMethod.getDesc());
    }

    private static ProcessedLambda of(AbstractInsnNode[] array, OutputPortRef outputPortRef, Object2ObjectOpenCustomHashMap<AbstractInsnNode, IOValue> outputValues, IOValue rawOutputValue, IOValue[] inputValues, Int2ObjectFunction<String> argumentDefNames, Options options) {
        var inputPorts=new Object2ObjectArrayMap<String, ValueType<?>>();
        var outputPorts=new Object2ObjectArrayMap<String, ValueType<?>>();
        for(int i = 0; i < inputValues.length; i++) {
            IOValue value = inputValues[i];
            addPorts(inputPorts, value, argumentDefNames.get(i));
        }

        addPorts(outputPorts, rawOutputValue, options.outputPortDefName());
        if(outputPortRef.used){
            Object2ObjectMap.Entry<String, ValueType<?>> next = Object2ObjectMaps.fastIterator(outputPorts).next();
            outputPortRef.name=next.getKey();
            outputPortRef.valueType=next.getValue();
        }
        return new ProcessedLambda(
            array, outputPortRef, outputValues, rawOutputValue, inputValues,inputPorts,outputPorts
        );
    }

    private static void addPorts(Object2ObjectArrayMap<String, ValueType<?>> ports, IOValue value, String def) {
        if(value.needToBeFlat() && value.isRecord()) {
            var iterator = Object2ObjectMaps.fastIterator(value.recordInfo().fieldMap);
            while (iterator.hasNext()) {
                final var e = iterator.next();
                ports.put(e.getKey(), valueType(e.getValue()));
            }
            return;
        }

        Class<?> clazz = value.clazz();
        Type unboxedType = BoxingTool.getUnboxedType(clazz);
        if(value.needToBeFlat() && value.couldRemoveBoxing() && unboxedType!=null){
            ports.put(def, valueType(unboxedType));
        }else{
            ports.put(def, valueType(clazz));
        }
    }

    private static @Nullable ValueType<?> valueType(@NotNull Type type) {
        return ValueType.byClass(CompileUtil.type2class(type), true);
    }

    private static @Nullable ValueType<?> valueType(@NotNull Class<?> clazz) {
        return ValueType.byClass(clazz, true);
    }

    private static @NotNull AbstractInsnNode makeInputRecordVar(IOValue value, int argIndex, int byteCodeArgIndex) {
        return compilable("input_record_var#" + argIndex, (mv, snapNode, inputs, outputs, data, context) -> {
            String localInputRecordVar = "var_" + snapNode.id + "input_record_" + argIndex;
            RecordInfo inputRecord = value.recordInfo();
            int i = mv.newLocal(localInputRecordVar, inputRecord.type);
            context.varMap.put(byteCodeArgIndex, i);

            mv.newInstance(inputRecord.type);
            mv.dup();
            inputRecord.fieldMap.forEach((port, type) -> {
                inputs.load(mv, port);
            });
            mv.invoke(inputRecord.canonicalCtor);
            mv.storeLocal(localInputRecordVar);
        });
    }

    private static @NotNull AbstractInsnNode storePorts(List<String> ports) {
        return compilable("store_ports_multy", (mv, snapNode, inputs, outputs, data, context) -> {
            for(int outputPortI = ports.size() - 1; outputPortI >= 0; outputPortI--) {
                String outputPort = ports.get(outputPortI);
                outputs.store(mv, outputPort);
            }
        });
    }

    public static class OutputPortRef {
        public boolean used;
        String name;
        ValueType<?> valueType;
    }

    private static IOValue cachedOutputValue(
        Object2ObjectMap<AbstractInsnNode, IOValue> outputValues,
        FlowValue flowValue,
        IOValue rawOutputValue
    ) {
        UsageStatistics usageStatistics = UsageInterpreter.usageStat(flowValue);
        IOValue outputValue = outputValues.computeIfAbsent(usageStatistics.source, it -> rawOutputValue.withValue(flowValue));
        return outputValue;
    }

    private static @NotNull Class<?> getTargetInterace(Object o, @Nullable Class<?> targetInterace) {
        Class<?> type = o.getClass();
        if(type.getSuperclass() != Object.class) throw new IllegalArgumentException("Only lambdas allowed");
        @NotNull Class<?>[] interfaces = type.getInterfaces();
        if(interfaces.length != 1 && targetInterace == null)
            throw new IllegalArgumentException("cannot determine target interface");
        if(targetInterace == null) {
            targetInterace = interfaces[0];
        } else {
            if(!targetInterace.isInstance(o))
                throw new IllegalArgumentException("Object is not implemented targetInterface");
        }
        return targetInterace;
    }

    private static FlowValue topStack(Frame<FlowValue> frame, int i1) {
        return frame.getStack(frame.getStackSize() - i1);
    }


    static AbstractInsnNode compilable(String title, Compilable compilable) {
        class Node extends LineNumberNode implements Compilable {

            public Node() {
                super(0, new LabelNode());
            }

            @Override
            public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompileCtx context) {
                compilable.compileOutputPortCalculations(mv, node, inputs, outputs, data, context);
            }

            @Override
            public String toString() {
                return "Compilible: " + title;
            }
        }
        return new Node();
    }

    public interface Compilable {
        void compileOutputPortCalculations(GeneratorHelper mv, SnapNode snapNode, Inputs inputs, Outputs outputs, CompoundTag data, CompileCtx context);
    }

    private static @NotNull ClassAndMethod findMethod(int callerDepth) {
        Handle handle = PtrExtractor.tryExtractMethod(1 + callerDepth);
        if(handle == null) {
            throw new IllegalArgumentException("Cannot find lambda body");
        }
        return toClassAndMethod(handle);
    }

    private static @NotNull ClassAndMethod toClassAndMethod(Handle handle) {
        ClassNode node = toClassNode(handle.getOwner());
        int foundIdx = findMethod(node, handle);
        if(foundIdx < 0) {
            throw new IllegalArgumentException("Cannot find lambda body in bytecode");
        }
        return new ClassAndMethod(node, node.methods.get(foundIdx), foundIdx);
    }

    record ClassAndMethod(ClassNode classNode, MethodNode methodNode, int index) {}

    private static int findMethod(ClassNode node, Handle handle) {

        List<MethodNode> methods = node.methods;
        for(int i = 0; i < methods.size(); i++) {
            MethodNode method = methods.get(i);
            if(!handle.getName().equals(method.name)) continue;
            if(!handle.getDesc().equals(method.desc)) continue;
            return i;
        }
        return -1;
    }

    private static ClassNode toClassNode(String type) {
        try {
            return ClassNodeUtil.getClassNode(type.replace('/', '.'));
        } catch(ClassNotFoundException | IOException e) {
            throw new IllegalArgumentException("Cannot find class bytes");
        }
    }
}
