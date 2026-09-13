package com.rieno.gadgetsandgizmos.graph.compile.node_type;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

public abstract class BinaryNode extends JVMNodeType {
    public BinaryNode() {
        super(Map.of(), Map.of());
        declaredInputPort.put("a",type());
        declaredInputPort.put("b",type());
        declaredOutputPort.put("c",type());
    }

    public static JVMNodeType impl(ValueType valueType, Compiler compiler) {
        return new BinaryNode() {
            @Override
            public ValueType type() {
                return valueType;
            }

            @Override
            public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
                compiler.compile(mv, inputs, outputs, data);
            }
        };
    }

    public abstract ValueType type();
    public interface Compiler{
         void compile(GeneratorHelper mv, Inputs inputs, Outputs outputs, CompoundTag data) ;
    }
}
