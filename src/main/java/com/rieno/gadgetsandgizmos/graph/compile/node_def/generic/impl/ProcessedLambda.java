package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.llamalad7.mixinextras.expression.impl.flow.postprocessing.InstantiationInfo;
import com.llamalad7.mixinextras.expression.impl.utils.FlowDecorations;
import com.llamalad7.mixinextras.lib.apache.commons.tuple.Pair;
import com.rieno.gadgetsandgizmos.graph.compile.asm.FieldInitExpr;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.metafactory.OwnLambdaMetafactory;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.metafactory.PrivateAccMetafactory;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.util.RecordInfo;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.util.UsageInterpreter;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.util.UsageStatistics;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.*;
import com.rieno.gadgetsandgizmos.graph.compile.util.helper.InsnAdapter;
import com.rieno.gadgetsandgizmos.graph.compile.util.helper.InvokePrivateHelper;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import lombok.*;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.io.IOException;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessedLambda {
    public static final ClassLoader MY_LOADER = ProcessedLambda.class.getClassLoader();
    public final AbstractInsnNode[] insnArray;
    public final OutputPortRef outputPortRef;
    public final Object2ObjectOpenCustomHashMap<AbstractInsnNode, IOValue> outputValues;
    public final IOValue rawOutputValue;
    public final IOValue[] inputValues;
    public final Object2ObjectArrayMap<String, ValueType<?>> inputPorts;
    public final Object2ObjectArrayMap<String, ValueType<?>> outputPorts;
    public final UnboundStateField[] stateFields;

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
        var classAndMethod = findMethod(1 + searchLevelOffset);
        var resolvedInterface = getTargetInterface(lambda, targetInterace, classAndMethod);
        MethodNode methodNode = classAndMethod.methodNode;

        String[] raw = bakeArgNames(methodNode);
        var params = Arrays.copyOfRange(raw, raw.length - resolvedInterface.argumentCount(), raw.length);

        return make(
            options, methodNode,
            classAndMethod.classNode,
            resolvedInterface,
            lambda,
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
        ResolvedInterfaced resolvedInterface,
        Object lambdaObject, final Int2ObjectFunction<String> argumentDefNames
    ) {
        final int startOfRealInput = Type.getArgumentCount(lambdaBody.desc) - resolvedInterface.argumentCount();
        AbstractInsnNode[] originalInsnArray = lambdaBody.instructions.toArray();
        var fields = new Object2ObjectOpenHashMap<Type, Set<String>>();
        for(Type type : resolvedInterface.getArgumentTypes()) {
            addRecordFields(fields, type);
        }
        addRecordFields(fields, Type.getReturnType(lambdaBody.desc));
        var usageAnalyzerResult = new UsageInterpreter(lambdaOwner, lambdaBody, fields, resolvedInterface.method.getParameterCount()).findValuesFramesUsages();

        ObjectArrayList<AbstractInsnNode> transformedNodes = new ObjectArrayList<>();
        var frames = usageAnalyzerResult.frames();
        final InsnAdapter.LabelCloner cloner = InsnAdapter.labelCloner();
        Type[] argumentTypes = resolvedInterface.realArgs(Type.getArgumentTypes(lambdaBody.desc));
        IOValue[] inputValues = new IOValue[argumentTypes.length];
        Type returnType = Type.getReturnType(lambdaBody.desc);

        for(int i = 0; i < argumentTypes.length; i++) {
            Type argumentType = argumentTypes[i];
            var val = inputValues[i] = IOValue.make(
                frames[0].getLocal(i + startOfRealInput),
                argumentType,
                options.flatInputPredicate().test(CompileUtil.type2class(argumentType), argumentTypes, i)
            );
            val.couldRemoveRecordVariables(val.usage().onlyFields() && val.isRecord() && val.needToBeFlat());
            Type unboxedType = BoxingTool.getUnboxedType(val.type());
            if(unboxedType != null) {
                val.couldRemoveBoxing(val.needToBeFlat() && val.usage().onlyUnwrapperAndReturn());

                val.variableState(IOValueVariableState.forBox(val.couldRemoveBoxing(), val.needToBeFlat()));
                //outputValue.couldReduceVariable(true);
            } else {

                val.variableState(IOValueVariableState.forRecord(val.couldRemoveRecordVariables(), val.needToBeFlat()));

            }
        }

        var handledInputVars = new IntOpenHashSet();
        {
            int offset = 0;
            for(int i = 0; i < inputValues.length; i++) {
                IOValue value = inputValues[i];
                boolean needToFlat = options.flatInputPredicate().test(value.clazz(), argumentTypes, i);
                int myOffset = offset, myI = i;
                if((needToFlat && value.isRecord())) {
                    if(!value.couldRemoveRecordVariables()) {
                        transformedNodes.add(makeInputRecordVar(value, i, offset, cloner));
                    }
                } else if(needToFlat && value.isBox() && value.variableState().keep()) {
                    String portName = argumentDefNames.get(i);
                    BoxingTool.Entry boxingEntry = BoxingTool.boxingEntry(value.type());
                    Type boxed = boxingEntry.boxed();
                    Handle boxingMethod = boxingEntry.boxingMethod();
                    handledInputVars.add(i);
                    transformedNodes.add(compilable("boxing for " + i, (mv, snapNode, inputs, outputs, data, context) -> {
                        int newLocal = mv.newLocal(boxed);
                        inputs.load(mv, portName);
                        mv.invoke(boxingMethod);
                        mv.storeLocal(newLocal);
                        context.varMap.put(myOffset, newLocal);
                    }));
                } else {

                    String portName = argumentDefNames.get(i);
                    if(usageAnalyzerResult.modifiedArguments().get(offset)) {
                        Type type = value.type();
                        transformedNodes.add(compilable("init varMap MUT input_" + i, (mv, snapNode, inputs, outputs, data, context) -> {
                            int tmpVar = mv.newLocal(type);
                            context.varMap.put(myOffset, tmpVar);
                            inputs.load(mv, portName);
                            mv.storeLocal(tmpVar);
                        }));
                    } else {
                        transformedNodes.add(compilable("init varMap input_" + i, (mv, snapNode, inputs, outputs, data, context) -> {
                            context.varMap.put(myOffset, -myI);
                            context.varToPortMap.put(myI, portName);
                        }));
                    }
                }
                offset += value.type().getSize();
            }
        }

        var stateFields = new ObjectArrayList<UnboundStateField>();

        if(resolvedInterface.argumentCount() < Type.getArgumentCount(lambdaBody.desc)) {
            int varOffset = 0;
            UnboundStateField stateField = UnboundStateField.make("lambda_body", Object.class, FieldInitExpr.someObject(Object.class, lambdaObject));
            stateFields.add(stateField);
            Class<?> ownerClass = lambdaObject.getClass();
            PrivateAccMetafactory.hiddenClass(ownerClass);
            String ownerClassName = ownerClass.getName();
            for(int i = 0; i < startOfRealInput; i++) {
                handledInputVars.add(i);
                int localArgIndex = i + 1;
                try {
                    String fieldName = "arg$" + localArgIndex;
                    var fieldType = Type.getType(ownerClass.getDeclaredField(fieldName).getType());
                    int myVarOffset = varOffset;
                    transformedNodes.add(compilable("load extra arg#" + localArgIndex + "#" + i, (mv, snapNode, inputs, outputs, data, context) -> {
                        mv.loadStateField(stateField, snapNode.id);
                        mv.privateField(Opcodes.GETFIELD, ownerClassName, fieldName, fieldType.getDescriptor());
                        int local = mv.newLocal(fieldType);
                        mv.storeLocal(local);
                        context.varMap.put(myVarOffset, local);
                    }));
                    varOffset += fieldType.getSize();
                } catch(NoSuchFieldException e) {
                    throw Lombok.sneakyThrow(e);
                }
            }
        }
        transformedNodes.add(compilable("init offset and shift locals", (mv, snapNode, inputs, outputs, data, context) -> {
            int offset = mv.nextLocal() + (mv.nextLocal() & 1) - 1;
            mv.nextLocal(offset + 1 + lambdaBody.maxLocals);
            context.setOffset(offset);
        }));

        var oldInsnToNew = new Int2IntOpenHashMap();
        var replacedNodes = new Int2ObjectOpenHashMap<List<AbstractInsnNode>>();
        var ignoreInsn = new IntOpenHashSet();

        var outputPortRef = new OutputPortRef();

        var rawOutputValue = IOValue.make(
            null,
            returnType,
            options.flatOutputPredicate().test(CompileUtil.type2class(returnType))
        ).withUsage(new UsageStatistics(null));

        var outputValues = new Object2ObjectOpenCustomHashMap<AbstractInsnNode, IOValue>(IndexInsnHashStrategy.INSTANCE);
        BoxingTool.Entry boxingEntry = BoxingTool.boxingEntry(rawOutputValue.type());
        boolean doUnwrapOutput = rawOutputValue.needToBeFlat();
        if(boxingEntry != null && rawOutputValue.needToBeFlat()) {
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
                    !topStack(frames[insnIdx + 1], 1).getType().equals(boxingEntry.unboxed())
                ) {
                    doUnwrapOutput = false;
                    break;
                }
                output.add(insnIdx);

            }
            if(doUnwrapOutput) {
                outputPortRef.used = true;
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
                        if(max >= 0) {
                            replacedNodes.put(max, replacement);

                        }
                    }
                }
            }
            rawOutputValue.variableState(IOValueVariableState.forBox(doUnwrapOutput, rawOutputValue.needToBeFlat()));
        }

        rawOutputValue.couldRemoveBoxing(doUnwrapOutput);
        for(int i = 0; i < originalInsnArray.length; i++) {
            AbstractInsnNode node = originalInsnArray[i];
            handleReplaced:
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
                if(node instanceof InvokeDynamicInsnNode invokeDynamicNode) {
                    Handle bsm = invokeDynamicNode.bsm;
                    if(bsm.equals(OwnLambdaMetafactory.originalMetaFactory)) {
                        Type interfaceMethodType = (Type) invokeDynamicNode.bsmArgs[0];
                        Handle implementation = (Handle) invokeDynamicNode.bsmArgs[1];
                        Type dynMethodType = (Type) invokeDynamicNode.bsmArgs[2];

                        //bootstrapMethodArguments[bootstrapMethodArguments.length-1]=Type.getObjectType(lambdaOwner.name);
                        transformedNodes.add(new InvokeDynamicInsnNode(
                            invokeDynamicNode.name,
                            invokeDynamicNode.desc,
                            OwnLambdaMetafactory.myMetaFactory,
                            interfaceMethodType,
                            dynMethodType,
                            implementation.getTag(),
                            implementation.getOwner().replace('/', '.'),
                            implementation.getName(),
                            Type.getType(implementation.getDesc())
                        ));
                        continue;
                    }
                }
                UsageStatistics statistics = usageAnalyzerResult.usageStatistics().get(i);
                if(statistics != null && statistics.returnValue) {
                    IOValue outputValue = cachedOutputValue(outputValues, topStack(frames[i + 1], 1), rawOutputValue);
                    if(outputValue.recordInfo() != null && outputValue.usage().onlyReturn() && outputValue.flowValue().getInsn() == node && outputValue.needToBeFlat()) {
                        if(node instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.NEW) {
                            if(typeInsn.desc.equals(outputValue.type().getInternalName())) {

                                InstantiationInfo instantiationInfo = outputValue.flowValue().getDecoration(FlowDecorations.INSTANTIATION_INFO);
                                List<String> props = propsInCanonicalCtor(instantiationInfo.initCall.getInsn(), outputValue.recordInfo());
                                if(props == null) break shortcut;
                                outputValue.variableState(IOValueVariableState.FLAT_RECORD_NO_VARIABLE);
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
                if(node.getOpcode() == Opcodes.NEW) {

                    InstantiationInfo instantiationInfo = topStack(frames[i + 1], 1).getDecoration(FlowDecorations.INSTANTIATION_INFO);
                    try {
                        Class<?> type = Class.forName(instantiationInfo.type.getClassName(), false, MY_LOADER);
                        InvokePrivateHelper.CallerCtx callerCtx = cloner.callerCtx();
                        if(InvokePrivateHelper.isPrivateOrHidden(type, callerCtx)) {
                            ignoreInsn.add(i + 1);
                            continue;
                        }
                    } catch(ClassNotFoundException e) {

                    }
                }
                if(node instanceof VarInsnNode varNode) {
                    int varOpcode = varNode.getOpcode();
                    boolean isLoad = varOpcode <= Opcodes.ALOAD;
                    Frame<FlowValue> frame = isLoad ? frames[i + 1] : frames[i];
                    FlowValue local1 = topStack(frame, 1);
                    int argI = UsageInterpreter.getArgumentIndex(local1);
                    if(argI >= 0 && !handledInputVars.contains(varNode.var)) {
                        IOValueVariableState var = inputValues[argI].variableState();
                        if(var.loadInputFromInputPort()) {
                            String name = argumentDefNames.get(argI);
                            transformedNodes.add(compilable("transformed VarInsn loadInput#" + argI, (mv, snapNode, inputs, outputs, data, context) -> {
                                inputs.load(mv, name);
                            }));
                            continue;
                        } else if(!var.loadStoreNormalVariable()) {
                            continue;
                        }
                    }
                    if(UsageInterpreter.isOutput(local1)) {
                        IOValueVariableState val = cachedOutputValue(outputValues, local1, rawOutputValue).variableState();
                        if(!val.keep() || !val.loadStoreNormalVariable()) {
                            continue;
                        }
                    }


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
                if(node instanceof MethodInsnNode && node.getOpcode() != Opcodes.INVOKESTATIC || node instanceof FieldInsnNode && node.getOpcode() > Opcodes.PUTSTATIC) {

                    shortcut2:
                    {
                        int argIndex = UsageInterpreter.getArgumentIndex(topStack(frames[i], 1));
                        if(argIndex < 0) break shortcut2;
                        IOValue inputValue = inputValues[argIndex];
                        if(inputValue.recordInfo() != null) {
                            IOValueVariableState var = inputValue.variableState();
                            if(!var.keep()) {
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
                    node = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fieldInsn.owner, fieldInsn.name, "()" + fieldInsn.desc, false);
                    break shortcut;
                }
                if(node.getOpcode() == Opcodes.ARETURN) {
                    IOValue outputValue = cachedOutputValue(outputValues, topStack(frames[i], 1), rawOutputValue);
                    if(!outputValue.variableState().keep()) continue;
                    if(outputValue.isRecord()) {
                        recordDestructor(transformedNodes, outputValue);
                    } else if(outputValue.needToBeFlat() && outputValue.usage().onlyUnwrapperAndReturn()) {
                        boxDestructor(transformedNodes, outputValue, outputPortRef);
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
                    ValueType<?> returnedValueType = ValueType.byClass(CompileUtil.type2class(type), true);

                    outputPortRef.used = true;
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
            transformedNodes.add(cloneOrHandlePrivateCall(node, cloner));
        }


        return ProcessedLambda.of(
            transformedNodes.toArray(AbstractInsnNode[]::new),
            outputPortRef,
            outputValues,
            rawOutputValue,
            inputValues,
            argumentDefNames,
            options, stateFields
        );
    }

    @SneakyThrows
    private static AbstractInsnNode cloneOrHandlePrivateCall(AbstractInsnNode node, InsnAdapter.LabelCloner cloner) {
        return switch(node) {
            case MethodInsnNode methodInsnNode -> {
                {
                    var owner = findClassOr(methodInsnNode.owner, null);
                    if(owner != null) {
                        Class[] parameterTypes = ArrayTransformUtil.map(Type.getArgumentTypes(methodInsnNode.desc), Class.class, CompileUtil::type2class);
                        if(methodInsnNode.name.equals("<init>")) {
                            var ctor = owner.getDeclaredConstructor(parameterTypes);

                            if(InvokePrivateHelper.isPrivateOrHidden(cloner.callerCtx(), ctor)) {
                                yield cloner.getNode(mv -> InvokePrivateHelper.privateConstructor(mv, cloner.callerCtx(), ctor));
                            }
                        } else {
                            Method method = findMethod(owner, methodInsnNode.itf, methodInsnNode.name, parameterTypes);
                            if(InvokePrivateHelper.isPrivateOrHidden(cloner.callerCtx(), method)) {
                                yield cloner.getNode(mv -> InvokePrivateHelper.privateMethod(mv, cloner.callerCtx(), method));
                            }
                        }
                    }
                    yield node.clone(cloner);
                }
            }
            case FieldInsnNode fieldInsnNode -> {

                isPublic:
                {
                    var owner = findClassOr(fieldInsnNode.owner, null);
                    if(owner != null) {
                        if(owner.isHidden() || !Modifier.isPublic(owner.getModifiers())) break isPublic;

                        java.lang.reflect.Field field = owner.getDeclaredField(fieldInsnNode.name);
                        if(!Modifier.isPublic(field.getModifiers())) break isPublic;
                    }
                    yield node.clone(cloner);
                }
                yield cloner.getNode(mv -> InvokePrivateHelper.privateField(mv, fieldInsnNode));
            }


            default -> node.clone(cloner);
        };

    }

    private static @NotNull Method findMethod(Class<?> owner, boolean isInterface, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        if(isInterface) {
            Method type = findInterfaceMethod(owner, name, parameterTypes);
            if(type != null) return type;
        }else{
            Class<?> type = owner;
            while(type!=null) {
                try {
                    return type.getDeclaredMethod(name, parameterTypes);
                } catch(NoSuchMethodException e) {
                    type=type.getSuperclass();
                }
            }
        }


        return owner.getDeclaredMethod(name,parameterTypes);
    }

    private static @Nullable Method findInterfaceMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        ObjectSet<Class<?>> visited = new ObjectOpenHashSet<>();
        ObjectSet<Class<?>> next = new ObjectOpenHashSet<>();
        ObjectSet<Class<?>> cur = new ObjectOpenHashSet<>();
        cur.add(owner);

        while(!cur.isEmpty()) {
            for(Class<?> type : cur) {
                try {
                    return type.getDeclaredMethod(name, parameterTypes);
                } catch(NoSuchMethodException ignore) {
                }
                for(Class<?> subInterface : type.getInterfaces()) {
                    if(visited.add(subInterface)) {
                        next.add(subInterface);
                    }
                }
            }
            ObjectSet<Class<?>> cur1 = cur;
            cur1.clear();
            cur = next;
            next = cur1;
        }
        return null;
    }


    private static Class<?> findClassOr(String owner, Class<?> objectClass) {
        try {
            return PrivateAccMetafactory.findClass(owner.replace('/', '.'));
        } catch(ClassNotFoundException e) {
            return objectClass;
        }
    }

    private static void boxDestructor(ObjectArrayList<AbstractInsnNode> transformedNodes, IOValue outputValue, OutputPortRef outputPortRef) {
        BoxingTool.Entry boxingEntry = BoxingTool.boxingEntry(outputValue.type());
        if(boxingEntry == null) return;
        outputPortRef.used = true;
        Handle unboxingMethod = boxingEntry.unboxingMethod();
        transformedNodes.add(compilable("output_destruct_unbox", (mv, snapNode, inputs, outputs, data, context) -> {
            mv.invoke(unboxingMethod);
            outputs.store(mv, outputPortRef.name);
        }));
    }

    private static void recordDestructor(ObjectArrayList<AbstractInsnNode> transformedNodes, IOValue outputValue) {
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
    }

    private static void addRecordFields(Object2ObjectOpenHashMap<Type, Set<String>> fields, Type type) {
        Class<?> aClass = CompileUtil.type2class(type);
        if(!aClass.isRecord()) return;
        fields.put(type, RecordInfo.make(type).fieldMap.keySet());
    }

    private static boolean areEqual(MethodInsnNode method, Handle boxingMethod) {
        return method.owner.equals(boxingMethod.getOwner()) && method.name.equals(boxingMethod.getName()) && method.desc.equals(boxingMethod.getDesc());
    }

    private static ProcessedLambda of(AbstractInsnNode[] array, OutputPortRef outputPortRef, Object2ObjectOpenCustomHashMap<AbstractInsnNode, IOValue> outputValues, IOValue rawOutputValue, IOValue[] inputValues, Int2ObjectFunction<String> argumentDefNames, Options options, ObjectArrayList<UnboundStateField> stateFields) {
        var inputPorts = new Object2ObjectArrayMap<String, ValueType<?>>();
        var outputPorts = new Object2ObjectArrayMap<String, ValueType<?>>();
        for(int i = 0; i < inputValues.length; i++) {
            IOValue value = inputValues[i];
            addPorts(inputPorts, value, argumentDefNames.get(i));
        }

        addPorts(outputPorts, rawOutputValue, options.outputPortDefName());
        if(outputPortRef.used) {
            Object2ObjectMap.Entry<String, ValueType<?>> next = Object2ObjectMaps.fastIterator(outputPorts).next();
            outputPortRef.name = next.getKey();
            outputPortRef.valueType = next.getValue();
        }
        return new ProcessedLambda(
            array, outputPortRef, outputValues, rawOutputValue, inputValues, inputPorts, outputPorts, stateFields.toArray(UnboundStateField[]::new)
        );
    }

    private static void addPorts(Object2ObjectArrayMap<String, ValueType<?>> ports, IOValue value, String def) {
        if(value.needToBeFlat() && value.isRecord()) {
            var iterator = Object2ObjectMaps.fastIterator(value.recordInfo().fieldMap);
            while(iterator.hasNext()) {
                final var e = iterator.next();
                ports.put(e.getKey(), valueType(e.getValue()));
            }
            return;
        }

        Class<?> clazz = value.clazz();
        Type unboxedType = BoxingTool.getUnboxedType(clazz);
        if(value.needToBeFlat() && value.isBox()) {
            ports.put(def, valueType(unboxedType));
        } else {
            ports.put(def, valueType(clazz));
        }
    }

    private static @Nullable ValueType<?> valueType(@NotNull Type type) {
        return valueType(CompileUtil.type2class(type));
    }

    private static @Nullable ValueType<?> valueType(@NotNull Class<?> clazz) {
        ValueType<?> valueType = ValueType.byClass(clazz, true);
        if(valueType == null) throw new RuntimeException("Cannot find value for type: " + clazz);
        return valueType;
    }

    private static @NotNull AbstractInsnNode makeInputRecordVar(IOValue value, int argIndex, int byteCodeArgIndex, InsnAdapter.@NonNull LabelCloner cloner) {

        InvokePrivateHelper.CallerCtx make = cloner.callerCtx();
        return compilable("input_record_var#" + argIndex, (mv, snapNode, inputs, outputs, data, context) -> {
            String localInputRecordVar = "var_" + snapNode.id + "_inrecord_" + argIndex;
            RecordInfo inputRecord = value.recordInfo();
            int i = mv.newLocal(localInputRecordVar, inputRecord.type);
            context.varMap.put(byteCodeArgIndex, i);

            Constructor<?> ctor = inputRecord.canonicalCtor;
            boolean isPrivate = isPrivate(ctor);
            if(!isPrivate) {
                mv.newInstance(inputRecord.type);
                mv.dup();
            }
            inputRecord.fieldMap.forEach((port, type) -> {
                inputs.load(mv, port);
            });
            if(isPrivate) {
                InvokePrivateHelper.privateConstructor(mv, make, ctor);
            } else {
                mv.invoke(ctor);
            }
            mv.storeLocal(localInputRecordVar);
        });
    }

    private static boolean isPrivate(Executable ctor) {
        return !Modifier.isPublic(ctor.getModifiers()) && !Modifier.isPublic(ctor.getDeclaringClass().getModifiers());
    }

    private static @NotNull AbstractInsnNode storePorts(List<String> ports) {
        return compilable("store_ports_multy", (mv, snapNode, inputs, outputs, data, context) -> {
            for(int outputPortI = ports.size() - 1; outputPortI >= 0; outputPortI--) {
                String outputPort = ports.get(outputPortI);
                outputs.store(mv, outputPort);
            }
        });
    }

    @Nullable
    public static List<String> propsInCanonicalCtor(AbstractInsnNode abstractInsnNode, RecordInfo recordInfo) {
        if(!(abstractInsnNode instanceof MethodInsnNode methodInsnNode)) return null;
        if(!methodInsnNode.owner.equals(recordInfo.type.getInternalName())) {
            return null;
        }
        String descriptor = Type.getConstructorDescriptor(recordInfo.canonicalCtor);
        if(!methodInsnNode.desc.equals(descriptor)) return null;
        return new ObjectArrayList<>(recordInfo.fieldMap.keySet());
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
        rawOutputValue.usage().setOr(outputValue.usage());
        return outputValue;
    }

    private static @NotNull ProcessedLambda.ResolvedInterfaced getTargetInterface(Object o, @Nullable Class<?> targetInterace, FoundMethod foundMethod) {
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


        try {
            Method method = targetInterace.getDeclaredMethod(
                foundMethod.insn.name,
                MethodType.fromMethodDescriptorString(((Type) foundMethod.insn.bsmArgs[0]).getDescriptor(), o.getClass().getClassLoader()).parameterArray()

            );
            return new ResolvedInterfaced(targetInterace, method);
        } catch(NoSuchMethodException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
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

    private static @NotNull ProcessedLambda.FoundMethod findMethod(int callerDepth) {
        var insn = PtrExtractor.tryExtractInvokeDynamic(1 + callerDepth);
        if(insn == null) throw new IllegalArgumentException("Cannot find lambda body");
        return toClassAndMethod(insn);
    }

    private static @NotNull FoundMethod toClassAndMethod(InvokeDynamicInsnNode insn) {
        Handle handle = PtrExtractor.findHandle(insn.bsmArgs);
        ClassNode node = toClassNode(handle.getOwner());
        int foundIdx = findMethod(node, handle);
        if(foundIdx < 0) {
            throw new IllegalArgumentException("Cannot find lambda body in bytecode");
        }
        return new FoundMethod(node, node.methods.get(foundIdx), foundIdx, insn);
    }

    record FoundMethod(ClassNode classNode, MethodNode methodNode, int index, InvokeDynamicInsnNode insn) {}

    record ResolvedInterfaced(Class<?> type, Method method) {
        public int argumentCount() {return method.getParameterCount();}

        public Type[] getArgumentTypes() {
            return Type.getArgumentTypes(method);
        }

        public Type[] realArgs(Type[] argumentTypes) {
            return Arrays.copyOfRange(argumentTypes, argumentTypes.length - argumentCount(), argumentTypes.length);
        }
    }

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
