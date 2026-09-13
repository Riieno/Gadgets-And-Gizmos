package com.rieno.gadgetsandgizmos.graph.compile.node_type.event;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueTypes;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;

public class TickerNode extends JVMNodeType {
    public TickerNode() {
        super(Map.of(), Map.of());
        //input("exec", ValueTypes.EXEC);
        output("exec", ValueTypes.EXEC);
    }

    @Override
    public Iterable<String> supportedEvents(String type, CompoundTag data) {
return List.of("tick");
    }

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {

    }
}
