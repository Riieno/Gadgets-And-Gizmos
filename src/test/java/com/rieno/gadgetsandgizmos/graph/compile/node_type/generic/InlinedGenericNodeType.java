package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic;

import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.llamalad7.mixinextras.expression.impl.flow.postprocessing.InstantiationInfo;
import com.llamalad7.mixinextras.expression.impl.utils.FlowDecorations;
import com.llamalad7.mixinextras.lib.apache.commons.tuple.Pair;
import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.util.RecordInfo;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.*;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InlinedGenericNodeType extends GenericNodeType {

    private static final InsnList DUMMY = new InsnList();
    protected final CodeStats codeStats;

    /**
     * Caller sensitive, DO NOT WRAP
     */
    public <In extends Record, Out extends Record> InlinedGenericNodeType(Class<In> recordIn, Class<Out> recordOut, MyBody<In, Out> inlinedBody) {
        this(inlinedBody, recordIn, recordOut, collectStats(PtrExtractor.tryExtractMethod(1)));

        //handleBody = execBody(handle);

    }

    @SneakyThrows
    protected <In extends Record, Out extends Record> InlinedGenericNodeType(MyBody<In, Out> inlinedBody, Class<In> recordIn, Class<Out> recordOut, CodeStats codeStats) {
        super(inlinedBody, recordIn, recordOut);
        this.codeStats = codeStats;
        //handleBody = execBody(handle);

    }

    @Override
    protected boolean needBodyField() {
        return false;
    }

    /*
        private AbstractInsnNode @Nullable [] execBody(Handle handle) {
            if(handle == null) return null;
            try {
                var classNode = ClassNodeUtil.getClassNode(handle.getOwner().replace('/', '.'));
                for(MethodNode methodNode : classNode.methods) {
                    if(methodNode.name.equals(handle.getName()) && methodNode.desc.equals(handle.getDesc())) {
                        AbstractInsnNode[] arrs = methodNode.instructions.toArray();
                        AbstractInsnNode[] newNodes = new AbstractInsnNode[arrs.length];
                        InsnAdapter.LabelCloner labels = InsnAdapter.labelCloner();
                        int returns = 0;
                        for(int i = 0; i < arrs.length; i++) {
                            AbstractInsnNode node = arrs[i].clone(labels);
                            newNodes[i] = node;
                            if(isReturn(node)) {
                                returns++;
                            }
                        }
                        multipleReturn = returns > 1;
                        varOffset = (methodNode.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
                        return newNodes;
                    }
                }

            } catch(ClassNotFoundException | IOException e) {
            }
            return null;
        }
        */
    private boolean isReturn(AbstractInsnNode node) {
        int opcode = node.getOpcode();
        return Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN;
    }


    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        if(codeStats == null) {
            super.compileOutputPortCalculations(mv, node, inputs, outputs, data, context);
            return;
        }
        InsnAdapter.LabelCloner cloner = new InsnAdapter.LabelCloner();
        for(AbstractInsnNode insnNode : codeStats.nodes) {
            if(insnNode instanceof Compiler compiler) {
                compiler.compileOutputPortCalculations(mv, node, inputs, outputs, data, context);
            } else {
                insnNode.clone(cloner).accept(mv);
            }
        }
    }

    @Nullable
    static CodeStats collectStats(Method method) {
        return collectStats(Type.getInternalName(method.getDeclaringClass()), method.getName(), Type.getType(method).getDescriptor());
    }

    @Nullable
    static CodeStats collectStats(Handle handle) {
        if(handle == null) return null;
        return collectStats(handle.getOwner(), handle.getName(), handle.getDesc());
    }

    public static void main(String[] args) {
        record In(double x, double y) {}
        record Out(double length2) {}
        collectStats(HandleExtractor.getMethod((In it0) -> {
            var it = it0;
            //System.out.println(it);
            Out out1 = new Out(it.x * it.x() + it.y * it.y());
            Out out = new Out(it.x * it.x() + it.y * it.y());
            System.out.println(out);

            return out;
        }));
    }

    @Nullable
    static CodeStats collectStats(String owner, String name, String desc) {
        ClassNode classNode;
        try {
            classNode = ClassNodeUtil.getClassNode(owner);
        } catch(ClassNotFoundException | IOException e) {
            return null;
        }
        MethodNode lambdaBody = findMethod(classNode, name, desc);
        if(lambdaBody == null) return null;
        var originalInsnArray = lambdaBody.instructions.toArray();

        var inputRecord = RecordInfo.make(Type.getArgumentTypes(lambdaBody.desc)[0]);
        var outputRecord = RecordInfo.make(Type.getReturnType(lambdaBody.desc));
        var recordFields = recordFields(inputRecord, outputRecord);
        var valuesAndFrames = new UsageInterpreter(classNode, lambdaBody, recordFields).findValuesFramesUsages();
        ObjectArrayList<AbstractInsnNode> transformedNodes = new ObjectArrayList<>();
        var frames = valuesAndFrames.frames();
        FlowValue local = frames[0].getLocal(0);
        UsageStatistics usageStatistics = UsageInterpreter.usageStat(local);
        boolean needField = !usageStatistics.onlyFields();

        transformedNodes.add(compilable((mv, snapNode, inputs, outputs, data, context) -> {
            context.setProp("varMap", new Int2IntOpenHashMap());
        }));
        if(needField) {
            transformedNodes.add(compilable((mv, snapNode, inputs, outputs, data, context) -> {
                String localInputRecordVar = "var_n" + snapNode.id + "__input_record";
                int i = mv.newLocal(localInputRecordVar, inputRecord.type);
                context.<Int2IntOpenHashMap>getProp("varMap", null).put(0, i);

                mv.newInstance(inputRecord.type);
                mv.dup();
                inputRecord.fieldMap.forEach((port, type) -> {
                    inputs.load(mv,port);
                });
                mv.invoke(inputRecord.canonicalCtor);
                mv.storeLocal(localInputRecordVar);
            }));
        }
        transformedNodes.add(compilable((mv, snapNode, inputs, outputs, data, context) -> {
            int offset = mv.nextLocal() + (mv.nextLocal() & 1) - 1;
            mv.nextLocal(offset + 1 + lambdaBody.maxLocals);
            context.setProp("offset", offset);
        }));
        boolean reduceOutputVar = false;
        InsnAdapter.LabelCloner cloner = InsnAdapter.labelCloner();
        var oldInsnToNew = new Int2IntOpenHashMap();
        var initToInitProps = new Int2ObjectOpenHashMap<List<String>>();
        var ignoreInsn=new IntOpenHashSet();
        for(int i = 0; i < originalInsnArray.length; i++) {
            AbstractInsnNode node = originalInsnArray[i];
            if(ignoreInsn.contains(i))continue;
            {
                List<String> props = initToInitProps.get(i);
                if(props != null) {
                    transformedNodes.add(compilable((mv, snapNode, inputs, outputs, data, context) -> {
                        for(int outputPortI = props.size() - 1; outputPortI >= 0; outputPortI--) {
                            String outputPort = props.get(outputPortI);
                            outputs.store(mv, outputPort);
                        }
                    }));
                    continue;
                }
            }
            shortcut:
            {
                if(node instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.NEW) {
                    if(typeInsn.desc.equals(outputRecord.type.getInternalName())) {
                        Frame<FlowValue> frame = frames[i + 1];
                        FlowValue stack = topStack(frame, 1);
                        UsageStatistics usage = UsageInterpreter.usageStat(stack);
                        if(usage.isEmpty()) break shortcut;
                        if(!usage.onlyReturn()) break shortcut;
                        InstantiationInfo instantiationInfo = stack.getDecoration(FlowDecorations.INSTANTIATION_INFO);
                        List<String> props = propsInCanonicalCtor(instantiationInfo.initCall.getInsn(), outputRecord);
                        if(props == null) break shortcut;
                        reduceOutputVar = true;
                        initToInitProps.put(indexOfNode(instantiationInfo.initCall.getInsn()), props);
                        if(i+1<originalInsnArray.length && originalInsnArray[i+1].getOpcode()==Opcodes.DUP){
                            ignoreInsn.add(i+1);
                        }else{
                            for(Pair<FlowValue, Integer> pair : stack.getNext()) {
                                FlowValue left = pair.getLeft();
                                if(left.getInsn().getOpcode() == Opcodes.DUP) {
                                    ignoreInsn.add(indexOfNode(left.getInsn()));
                                }
                            }
                        }
                        continue;
                    }
                } else if(node instanceof VarInsnNode varNode) {
                    boolean isLoad = varNode.getOpcode() <= Opcodes.ALOAD;
                    Frame<FlowValue> frame = isLoad ? frames[i + 1] : frames[i];
                    FlowValue local1 = topStack(frame, 1);
                    if(!needField && UsageInterpreter.isInput(local1) || UsageInterpreter.isOutput(local1) && reduceOutputVar) {
                        continue;
                    }

                    transformedNodes.add(compilable((mv, snapNode, inputs, outputs, data, context) -> {
                        Int2IntOpenHashMap varMap = context.getProp("varMap", null);
                        int offset = context.<Number>getProp("offset", null).intValue();
                        mv.mv().visitVarInsn(varNode.getOpcode(), varMap.computeIfAbsent(varNode.var, _z -> _z + offset));
                    }));
                    continue;
                } else if(node instanceof MethodInsnNode || node instanceof FieldInsnNode) {
                    String prop = recordProp(node, inputRecord);
                    if(prop == null) break shortcut;
                    shortcut2: {
                        if(!UsageInterpreter.isInput(topStack(frames[i], 1))) break shortcut2;
                        if(needField) break shortcut2;
                        transformedNodes.add(compilable((mv, snapNode, inputs, outputs, data, context) -> {
                            inputs.load(mv, prop);
                        }));
                        continue;
                    }
                    if(!(node instanceof FieldInsnNode fieldInsn))break shortcut;
                    transformedNodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,fieldInsn.owner,fieldInsn.name,"()"+fieldInsn.desc,false));
                    continue;
                }else if(node.getOpcode()==Opcodes.ARETURN){
                    if(reduceOutputVar) continue;
                    for(Map.Entry<String, Type> entry : outputRecord.fieldMap.entrySet()) {
                        transformedNodes.add(new InsnNode(Opcodes.DUP));
                        String port = entry.getKey();
                        Type type = entry.getValue();
                        transformedNodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,outputRecord.type.getInternalName(),port,"()"+type.getDescriptor(),false));
                        transformedNodes.add(compilable((mv, snapNode, inputs, outputs, data, context) -> {
                            outputs.store(mv,port);
                        }));
                    }

                    continue;
                }
            }


            oldInsnToNew.put(i, transformedNodes.size());
            transformedNodes.add(node.clone(cloner));
        }


        return CodeStats.of(inputRecord, outputRecord, transformedNodes.toArray(AbstractInsnNode[]::new));
    }

    private static int indexOfNode(AbstractInsnNode insn) {
        return DUMMY.indexOf(insn);
    }

    private static @Nullable MethodNode findMethod(ClassNode classNode, String name, String desc) {
        MethodNode found = null;
        for(MethodNode method : classNode.methods) {
            if(method.name.equals(name) && method.desc.equals(desc)) {
                found = method;
                break;
            }
        }
        if(found == null) return null;
        return found;
    }

    @Nullable
    private static String recordProp(AbstractInsnNode insn, RecordInfo recordInfo) {
        String name;
        if(insn instanceof MethodInsnNode method) {
            if(!method.desc.startsWith("()")) return null;
            name = method.name;
        } else if(insn instanceof FieldInsnNode method) {
            name = method.name;
        } else return null;
        return recordInfo.fieldMap.containsKey(name) ? name : null;
    }


    @Nullable
    private static List<String> propsInCanonicalCtor(AbstractInsnNode abstractInsnNode, RecordInfo recordInfo) {
        if(!(abstractInsnNode instanceof MethodInsnNode methodInsnNode)) return null;
        if(!methodInsnNode.owner.equals(recordInfo.type.getInternalName())) {
            return null;
        }
        String descriptor = Type.getConstructorDescriptor(recordInfo.canonicalCtor);
        if(!methodInsnNode.desc.equals(descriptor)) return null;
        return new ObjectArrayList<>(recordInfo.fieldMap.keySet());
    }

    private static FlowValue topStack(Frame<FlowValue> frame, int i1) {
        return frame.getStack(frame.getStackSize() - i1);
    }

    private static @NotNull Object2ObjectOpenHashMap<Type, Set<String>> recordFields(RecordInfo... records) {
        var recordFields = new Object2ObjectOpenHashMap<Type, Set<String>>();
        for(RecordInfo record : records) {
            recordFields.put(record.type, new ObjectOpenHashSet<>(record.fieldNamesAsSet()));
        }
        return recordFields;
    }

    static AbstractInsnNode compilable(Compiler compiler) {
        class Node extends LineNumberNode implements Compiler {

            public Node() {
                super(0, new LabelNode());
            }

            @Override
            public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
                compiler.compileOutputPortCalculations(mv, node, inputs, outputs, data, context);
            }
        }
        return new Node();
    }

    @AllArgsConstructor
    public static class CodeStats {
        //boolean needRecordInstance;
        //final InsnAdapter.LabelCloner labelCloner = new InsnAdapter.LabelCloner();
        RecordInfo input, output;
        AbstractInsnNode[] nodes;

        public static CodeStats of(RecordInfo input, RecordInfo output, AbstractInsnNode[] nodes) {
            return new CodeStats(input, output, nodes);
        }
    }

    interface Compiler {
        void compileOutputPortCalculations(GeneratorHelper mv, SnapNode snapNode, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context);
    }
}
