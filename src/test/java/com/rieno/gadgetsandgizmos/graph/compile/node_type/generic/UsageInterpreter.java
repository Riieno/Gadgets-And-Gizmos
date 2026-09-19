package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic;

import com.llamalad7.mixinextras.expression.impl.flow.DummyFlowValue;
import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UsageInterpreter extends FlowInterpreterExt implements Opcodes {
    public static final String INPUT_MARKER = "inputRecord";
    public static final String OUTPUT_MARKER = "outputRecord";
    public static final String COPY_MARKER = "copy";
    //public static final String RETURN_MARKER = "copy";
    public static final String NEW_MARKER = "new";
    public static final String USAGE_STAT = "usage_stat";
    public boolean needOutput, needInput;
    public Map<Type, Set<String>> recordFields;

    public UsageInterpreter(ClassNode classNode, MethodNode found, Map<Type, Set<String>> recordFields) {
        super(classNode, found);
        this.recordFields=recordFields;
        postProcessors.add((node, sink) -> {
            if(node.hasDecoration(COPY_MARKER)) return;
            AbstractInsnNode insn = node.getInsn();
            int opcode = insn.getOpcode();
            if(IRETURN <= opcode && opcode <= RETURN) return;
            if(insn instanceof MethodInsnNode methodInsnNode && methodInsnNode.name.equals("<init>")) return;
            if(insn instanceof VarInsnNode var) {
                return;
            }
            int inputCount = node.inputCount();
            BiConsumer<FlowValue, UsageStatistics> defUsed = (flowValue1, usageStatistics) -> {
                usageStatistics.other = true;
            };


            for(int i = 0; i < inputCount; i++) {
                var curUsed = defUsed;
                if(insn instanceof MethodInsnNode insnNode) {
                    if(i == 0 && insnNode.getOpcode() != Opcodes.INVOKESTATIC) {
                        Type recordType = Type.getObjectType(insnNode.owner);
                        Set<String> fields = this.recordFields.get(recordType);
                        if(fields!=null && Type.getArgumentCount(insnNode.desc)==0 && fields.contains(insnNode.name)){
                            curUsed = (flowValue, usageStatistics) -> {
                                usageStatistics.recordField = true;
                            };
                        }else {
                            curUsed = (flowValue, usageStatistics) -> {
                                usageStatistics.instanceMethod = true;
                                //TODO usageStatistics.other = true;
                            };
                        }
                    } else {
                        curUsed = (flowValue, usageStatistics) -> {
                            usageStatistics.otherMethod = true;
                            usageStatistics.other = true;
                        };
                    }
                } else if(insn instanceof FieldInsnNode fieldInsnNode) {
                    if(i == 0 && fieldInsnNode.getOpcode() > PUTSTATIC) {
                        curUsed = (flowValue, usageStatistics) -> {
                            usageStatistics.instanceField = true;
                            usageStatistics.other |= opcode==PUTFIELD;
                        };
                    } else {
                        curUsed = (flowValue, usageStatistics) -> {
                            usageStatistics.fieldValue = true;
                            usageStatistics.other = true;
                        };
                    }
                }
                FlowValue flowValue = node.getInput(i);
                UsageStatistics decoration = flowValue.getDecoration(USAGE_STAT);
                if(decoration != null) curUsed.accept(flowValue, decoration);
                //markBackwardChain(node.getInput(i), processUsed(curUsed));
            }
        });
    }

    public static boolean isInput(FlowValue local) {
        return local.hasDecoration(INPUT_MARKER);
    }
    public static boolean isOutput(FlowValue local) {
        return usageStat(local).returnValue;
    }
    public static UsageStatistics usageStat(FlowValue local) {
        return local.getDecoration(USAGE_STAT);
    }

    private @NotNull Consumer<FlowValue> processUsed(BiConsumer<FlowValue, UsageStatistics> flowValueConsumer) {
        return flowValue -> {
            UsageStatistics decoration = flowValue.getDecoration(USAGE_STAT);
            if(decoration != null) flowValueConsumer.accept(flowValue, decoration);
        };
    }

    @Override
    public FlowValue newValue(Type type) {
        FlowValue flowValue = super.newValue(type);
        flowValue.decorate(USAGE_STAT, new UsageStatistics());
        return flowValue;
    }

    @Override
    protected FlowValue visitNew(FlowValue flowValue, List<? extends FlowValue> value) {
        if(!flowValue.hasDecoration(USAGE_STAT)) {
            flowValue.decorate(USAGE_STAT, new UsageStatistics());
        }
        return super.visitNew(flowValue, value);
    }

    @Override
    public FlowValue newParameterValue(boolean isInstanceMethod, int local, Type type) {

        FlowValue flowValue = super.newParameterValue(isInstanceMethod, local, type);
        if(isInstanceMethod && local == 1 || !isInstanceMethod && local == 0) {
            flowValue.decorate(INPUT_MARKER, Boolean.TRUE);

        }
        return flowValue;
    }

    @Override
    public FlowValue copyOperation(AbstractInsnNode insn, FlowValue value) {
        FlowValue copied = super.copyOperation(insn, value);
        if(value != null && copied != null) {
            copyDeco(value, copied, INPUT_MARKER);
            copied.decorate(COPY_MARKER, true);
            copied.decorate(USAGE_STAT, value.getDecoration(USAGE_STAT));
            //copyDeco(value, copied, OUTPUT_RECORD_KIND);
        }
        return copied;
    }

    @Override
    public FlowValue newOperation(AbstractInsnNode insn) {
        FlowValue flowValue = super.newOperation(insn);
        flowValue.decorate(NEW_MARKER, true);
        return flowValue;
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, FlowValue value, FlowValue expected) {
        //markBackwardChain(value, flowValue1 -> flowValue1.decorate(OUTPUT_MARKER, true));
        usageStat(value).returnValue=true;
        super.returnOperation(insn, value, expected);
    }

    private boolean markBackwardChain(FlowValue value, Consumer<FlowValue> visitor) {
        FlowValue v1 = value;
        boolean singleLine = true;
        boolean needDeco = true;
        Type type = v1.getType();
        var values = new ObjectArrayList<FlowValue>();
        values.add(v1);
        while(true) {
            if(v1.getDecoration(NEW_MARKER) != null || v1 instanceof DummyFlowValue) break;
            if(v1.isComplex()) {
                singleLine = false;
                break;
            } else if(v1.inputCount() != 1 ||
                      !v1.getInput(0).typeMatches(type) ||
                      v1.getDecoration(COPY_MARKER) == null) {
                singleLine = false;
                needDeco = false;
                break;
            }

            v1 = v1.getInput(0);
            values.add(v1);
        }
        if(needDeco) {
            for(FlowValue flowValue : values) {
                visitor.accept(flowValue);
            }
        }
        return singleLine;
    }

    @Override
    public FlowValue merge(FlowValue value1, FlowValue value2) {
        FlowValue merge = super.merge(value1, value2);
        mergeMarker(value1, value2, merge, INPUT_MARKER);
        return merge;
    }

    private void mergeMarker(FlowValue value1, FlowValue value2, FlowValue merge, String marker) {
        Object decoration1 = value1.getDecoration(marker);
        if(decoration1 == null || value2 == merge || value1 == merge) return;
        if(decoration1 != value2.getDecoration(marker)) return;
        merge.decorate(marker, decoration1);
    }

    private void copyDeco(FlowValue src, FlowValue dest, String key) {
        Object decoration = src.getDecoration(key);
        if(decoration != null) dest.decorate(key, decoration);
    }
}
