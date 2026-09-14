package com.rieno.gadgetsandgizmos.graph.compile.node_type.control_flow;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphCompiler;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.subsystem.NodeFlowGenerator;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.UnboundStateField;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

import java.util.function.Consumer;

public class Gate extends JVMNodeType {

    public static final UnboundStateField enabled = UnboundStateField.make("enabled", boolean.class,null);
    public static final String signal = "signal";

    public Gate() {
        input("exec", ValueTypes.EXEC);
        input(signal, ValueTypes.BOOL);

        output("exec", ValueTypes.EXEC);
    }
//TODO use defaults to set initExpression
    @Override
    public Iterable<UnboundStateField> stateFields(SnapNode self, JVMGraphCompiler.Cache cache) {
        //self.data.get("Defaults")
        return super.stateFields(self, cache);
    }

    @Override
    public void forEachFollowExec(SnapNode self, CompilationContext context, Consumer<SnapNode> consumer) {
    }

    @Override
    public void compileCustomControlFlow(GeneratorHelper mv, SnapNode sourceNode, CompilationContext context) {
        mv.loadStateField(enabled.bind(sourceNode));

        Label doNothing = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, doNothing);
        nextAfterPort(sourceNode, "exec", it -> NodeFlowGenerator.buildNodeCallTree(context, mv, it.id));
        mv.visitLabel(doNothing);
    }

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        //GeneratorHelper.UnboundStateField entry = stateField(mv, node);
        inputs.load(mv, signal);
        mv.storeField(enabled.bind(node));
    }
}
