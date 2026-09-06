package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.asm.NodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import net.minecraft.nbt.*;

import java.util.Map;

class ConstDValue extends NodeType {

    public ConstDValue() {super(Map.of(), Map.of("value", ValueType.NUMBER));}

    @Override
    public void compile(GeneratorHelper mv, Inputs inputs, Outputs outputs, CompoundTag data) {
        Tag value = data.get("value");
        if(value instanceof NumericTag number) {
            mv.push(number.getAsNumber().doubleValue());
        }else {
            mv.push(0.0);
        }
        outputs.store(mv,"value");
    }

}
