package com.rieno.gadgetsandgizmos.graph.compile.node_type;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.*;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapEdge;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class ConvertNode extends JVMNodeType {

    public static final String OUTPUT_PORT_NAME = "value";
    private final Object2ObjectArrayMap<String, ValueType<?>> VOID_OUTPUT = new Object2ObjectArrayMap<>(Map.of(
        OUTPUT_PORT_NAME, ValueTypes.VOID
    ));

    public ConvertNode() {
        input("value", ValueTypes.ANY);
        output(OUTPUT_PORT_NAME, ValueTypes.ANY);
        //input("signal", ValueTypes.ANY);

    }

    @Override
    public Object2ObjectMap<String, ValueType<?>> outputPorts(CompoundTag data, int nodeI) {
        String string = data.getString("OutputType");
        if(string.isEmpty())return VOID_OUTPUT;
        ValueType<?> outputType=null;
        try {
            ResourceLocation resource = CreateThrusters.resource(string);
            outputType = ValueType.byName(resource);
        } catch(Exception e) {
        }
        if(outputType==null){
            try {
                ResourceLocation resource = ResourceLocation.parse(string);
                outputType = ValueType.byName(resource);
            } catch(Exception ex) {
            }
        }
        if(outputType==null)return VOID_OUTPUT;

        return new Object2ObjectArrayMap<>(Map.of(
            OUTPUT_PORT_NAME, outputType
        ));
    }

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        SnapEdge input = node.inputs[0];
        ValueType<?> valueType = input.nodeA.outputType(input.portA);
        inputs.load(mv,"value");
        valueType.convertTo(mv,node.outputType(OUTPUT_PORT_NAME));
        outputs.store(mv,OUTPUT_PORT_NAME);
    }
}
