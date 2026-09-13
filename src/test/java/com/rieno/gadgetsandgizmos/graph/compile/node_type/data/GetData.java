package com.rieno.gadgetsandgizmos.graph.compile.node_type.data;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.*;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.subsystem.CalculatorGenerator;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import com.rieno.gadgetsandgizmos.graph.compile.util.UnboundStateField;
import com.rieno.gadgetsandgizmos.graph.init.GNG_Events;
import com.rieno.gadgetsandgizmos.graph.struct.NodeCalculator;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Map;

public class GetData extends JVMNodeType {

    private final UnboundStateField dataValues = UnboundStateField.make(
        "data_values",
        Map.class,
        FieldInitExpr.constructor(Object2ObjectOpenHashMap.class::getConstructor)
    );

    public GetData() {
    }

    @Override
    public Iterable<String> supportedEvents(String type, CompoundTag data) {
        return List.of(GNG_Events.PASSIVE_EVENT);
        //return super.supportedEvents(type, data);
    }

    @Override
    public Object2ObjectMap<String, ValueType> outputPorts(CompoundTag data, int nodeI) {
        return BlockData.mergePorts(super.outputPorts(data, nodeI), data);
    }

    //todo maybe just add isRoot flag as argument
    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        if(node.inputs.length == 1) return;
        if(context.calculatorMode!=null){
            if(context.calculatorMode == NodeCalculator.Mode.Passive){
                addPassiveUpdate(mv,node,inputs,data);
            }else{
                loadFields(mv, node, outputs, data);
            }
            return;
        }
        Label isPassiveEvent = mv.newLabel();
        Label isNotPassiveEvent = mv.newLabel();
        Label finish = mv.newLabel();
        if(mv.hasLocal(CalculatorGenerator.CALC_MODE_VARIABLE)) {
            mv.loadLocal(CalculatorGenerator.CALC_MODE_VARIABLE);
            mv.get(NodeCalculator.Mode.Passive.myField);
            mv.ifPlainEquals(Type.getType(Object.class),isNotPassiveEvent);
        }else{
            mv.push(GNG_Events.PASSIVE_EVENT);
            mv.loadLocal("eventId");
            mv.objectEquals(false);
            mv.ifFalse(isNotPassiveEvent);
        }
        ;
        mv.visitLabel(isPassiveEvent);
        addPassiveUpdate(mv, node, inputs, data);

        mv.returnValue();
        mv.visitLabel(isNotPassiveEvent);
        loadFields(mv, node, outputs, data);
        mv.visitLabel(finish);
    }

    private void loadFields(GeneratorHelper mv, SnapNode node, Outputs outputs, CompoundTag data) {
        mv.loadStateField(dataValues.bind(node));
        for(int i = 0; i < node.outputs.length; i++) {
            String portName = node.portIndexerInverse[i];

            mv.dup();
            mv.push(portName);
            mv.invoke(Handle.method(() -> Map.class.getDeclaredMethod("get", Object.class)));
            mv.checkCast(Type.getType(AdvancedGraphDocument.Value.class));
            outputs.store(mv, portName);

        }
        mv.pop();
    }

    private void addPassiveUpdate(GeneratorHelper mv, SnapNode node, Inputs inputs, CompoundTag data) {
        String blockPos = data.getString(BlockData.blockPosKey);
        var entry = dataValues.bind(node);

        mv.push(blockPos);
        mv.loadStateField(entry);
        mv.invoke(Handle.method(() -> BlockData.class.getDeclaredMethod("getValues", String.class, Map.class)));


    }

}
