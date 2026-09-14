package com.rieno.gadgetsandgizmos.graph.compile.node_type;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import net.minecraft.nbt.CompoundTag;

public class BinOpNode extends BinaryNode {
    private final int opcodeOr;
    public BinOpNode(int opcode) {
        super();
        opcodeOr = opcode;
    }

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        inputs.load(mv, "a");
        inputs.load(mv, "b");
        mv.visitInsn(opcodeOr);
        outputs.store(mv, "c");
    }

    @Override
    public ValueType<?> type() {
        return ValueTypes.NUMBER;
    }
}
