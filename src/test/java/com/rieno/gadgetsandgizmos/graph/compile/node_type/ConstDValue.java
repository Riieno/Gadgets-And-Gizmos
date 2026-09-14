package com.rieno.gadgetsandgizmos.graph.compile.node_type;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.*;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import net.minecraft.nbt.*;

import java.util.Map;

public class ConstDValue extends JVMNodeType {

    public ConstDValue() {super(Map.of(), Map.of("value", ValueTypes.NUMBER));}

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        Tag value = data.get("value");
        if(value instanceof NumericTag number) {
            mv.push(number.getAsNumber().doubleValue());
        }else {
            mv.push(0.0);
        }
        outputs.store(mv,"value");
    }

}
