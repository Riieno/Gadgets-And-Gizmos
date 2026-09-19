package com.rieno.gadgetsandgizmos.graph.compile.node_type;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.*;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.SneakyThrows;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;

public class NumberBinaryNode extends BinaryNode {
    private final InlinedBody inlinedBody;
    private static final ObjectArrayList<InlinedBody> inlinedBodies = new ObjectArrayList<>();
    private final int inlinedBodyIndex = inlinedBodies.size();

    private final AbstractInsnNode[] handleBody;
    private boolean multipleReturn;
    private int varOffset;
    private final Handle getBodyHandle = HandleExtractor.getMethod(NumberBinaryNode::body);
    private final Handle invokeBodyHandle = HandleExtractor.getMethod(InlinedBody::calculate);

    @SneakyThrows
    /**
     * Caller sensitive, DO NOT WRAP
     */
    public NumberBinaryNode(InlinedBody inlinedBody) {
        super();
        var handle = PtrExtractor.tryExtractMethod(1 + extraDepth());
        handleBody = execBody(handle);
        inlinedBodies.add(inlinedBody);
        this.inlinedBody = inlinedBody;

    }

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

    private boolean isReturn(AbstractInsnNode node) {
        int opcode = node.getOpcode();
        return Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN;
    }

    protected int extraDepth() {
        return 0;
    }

    public static InlinedBody body(int i) {return inlinedBodies.get(i);}

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        var handleBody = this.handleBody;
        if(handleBody != null) {
            var cloner = InsnAdapter.labelCloner();
            int var_a = varOffset;
            int var_b = varOffset + 2;

            var finish = new Label();
            var varMap = new Object2IntOpenHashMap<String>();
            for(AbstractInsnNode insnNode : handleBody) {
                if(insnNode instanceof VarInsnNode var) {
                    if(var.var == var_a) {
                        inputs.load(mv, "a");
                        continue;
                    } else if(var.var==var_b) {
                        inputs.load(mv, "b");
                        continue;
                    }else{
                        var mockType= switch(var.getOpcode()) {
                            case Opcodes.DLOAD,Opcodes.LLOAD,Opcodes.DSTORE,Opcodes.LSTORE -> Type.LONG_TYPE;
                            default -> Type.INT_TYPE;
                        };
                        insnNode=new VarInsnNode(
                            var.getOpcode(),
                            varMap.computeIfAbsent(var.var+"_"+mockType.getSize(),i->mv.newLocalMapping(mockType))
                        );
                    }
                } else if(isReturn(insnNode)) {
                    outputs.store(mv, "c");
                    mv.goTo(finish);
                    continue;
                }else{
                    insnNode=insnNode.clone(cloner);
                }
                insnNode.accept(mv);
            }

            mv.visitLabel(finish);

        } else {
            mv.push(inlinedBodyIndex);
            InsnAdapter.invoke(mv, getBodyHandle);
            inputs.load(mv, "a");
            inputs.load(mv, "b");
            InsnAdapter.invoke(mv, invokeBodyHandle);
            outputs.store(mv, "c");
        }
        ;
    }

    @Override
    public ValueType<?> type() {
        return ValueTypes.NUMBER;
    }

    public interface InlinedBody {
        double calculate(double x, double y);
    }
}
