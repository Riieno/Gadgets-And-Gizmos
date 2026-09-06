package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import net.minecraft.nbt.CompoundTag;

class BinOpNode extends BinaryNode {
    private final int opcodeOr;
    public BinOpNode(int opcode) {
        super();
        opcodeOr = opcode;
    }

    @Override
    public void compile(GeneratorHelper mv, Inputs inputs, Outputs outputs, CompoundTag data) {
        inputs.load(mv, "a");
        inputs.load(mv, "b");
        mv.visitInsn(opcodeOr);
        outputs.store(mv, "c");
    }

    @Override
    public ValueType type() {
        return ValueType.NUMBER;
    }
}
