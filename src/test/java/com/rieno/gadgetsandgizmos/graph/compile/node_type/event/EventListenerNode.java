package com.rieno.gadgetsandgizmos.graph.compile.node_type.event;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;

public class EventListenerNode extends JVMNodeType {
    public EventListenerNode() {
        super(Map.of(), Map.of());
        //input("exec", ValueTypes.EXEC);
        output("exec", ValueTypes.EXEC);
    }

    @Override
    public Iterable<String> supportedEvents(String type, CompoundTag data) {
        var eventTag = data.get("Event");
        if(eventTag == null) return List.of();
        return List.of(eventTag.getAsString());
    }

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {

    }
}
