package com.rieno.gadgetsandgizmos.graph.compile;

import com.lowdragmc.lowdraglib2.utils.consumer.Consumer4;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.NodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

public abstract class BinaryNode extends NodeType {
    public BinaryNode() {
        super(Map.of(), Map.of());
        input.put("a",type());
        input.put("b",type());
        outputs.put("c",type());
    }

    public static NodeType impl(ValueType valueType,Compiler compiler) {
        return new BinaryNode() {
            @Override
            public ValueType type() {
                return valueType;
            }

            @Override
            public void compile(GeneratorHelper mv, Inputs inputs, Outputs outputs, CompoundTag data) {
                compiler.compile(mv, inputs, outputs, data);
            }
        };
    }

    public abstract ValueType type();
    public interface Compiler{
         void compile(GeneratorHelper mv, Inputs inputs, Outputs outputs, CompoundTag data) ;
    }
}
