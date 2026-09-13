package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphCompiler;
import com.rieno.gadgetsandgizmos.graph.compile.LazyNodeProperties;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapEdge;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.UnboundStateField;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;


public abstract class JVMNodeType {
    @Getter
    public final Object2ObjectLinkedOpenHashMap<String, ValueType> declaredInputPort;
    @Getter
    public final Object2ObjectLinkedOpenHashMap<String, ValueType> declaredOutputPort;

    public final LazyNodeProperties lazyProps=new LazyNodeProperties(getClass());

    public JVMNodeType(Map<String, ValueType> input, Map<String, ValueType> outputs) {
        this.declaredInputPort = new Object2ObjectLinkedOpenHashMap<>(input);
        this.declaredOutputPort = new Object2ObjectLinkedOpenHashMap<>(outputs);
    }

    public JVMNodeType() {
        this(Map.of(), Map.of());
    }

    public JVMNodeType input(String port, ValueType type) {
        declaredInputPort.put(port, type);
        return this;
    }

    public JVMNodeType output(String port, ValueType type) {
        declaredOutputPort.put(port, type);
        return this;
    }

    public Object2ObjectMap<String, ValueType> inputPorts(CompoundTag data, int nodeI) {
        return declaredInputPort;
    }

    public Object2ObjectMap<String, ValueType> outputPorts(CompoundTag data, int nodeI) {
        return declaredOutputPort;
    }

    @Nullable
    public Iterable<UnboundStateField> stateFields(SnapNode self, JVMGraphCompiler.Cache cache) {
        var myFields = lazyProps.stateFields.get();
        if(myFields.isEmpty()) return null;
        //TODO add annotation to mark fields?
        var fields = new ObjectArrayList<UnboundStateField>(myFields.size());
        for(Field myField : myFields) {
            myField.setAccessible(true);
            try {
                if(!(myField.get(this) instanceof UnboundStateField stateField)) continue;
                fields.add(stateField);
            } catch(IllegalAccessException e) {

            }

        }
        if(fields.isEmpty()) return null;
        return fields;
    }

    /**
     * Called when output ports of this node required
     */
    public abstract void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context);

    public boolean hasSideEffect(SnapNode self) {return false;}

    public void typeProperties(CompilationContext.TypeProperties properties, SnapNode self) {
        properties.customControlFlow = lazyProps.hasCustomControlFlow.get();
        properties.modeSensitivePortCalculator = lazyProps.isModeSensitive.get();
    }

    public void forEachFollowExec(SnapNode self, CompilationContext context, Consumer<SnapNode> consumer) {
        //nextAfterPort(self, consumer, "exec");
        nextExec(self, consumer);
    }

    public static void nextAfterPort(SnapNode self, String exec, Consumer<SnapNode> consumer) {
        int port = self.outputPort(exec);
        if(port == -1) return;
        var output = self.outputs[port];
        if(output == null) return;
        SnapEdge[] elements = output.elements();
        for(int i = 0; i < output.size(); i++) {
            consumer.accept(elements[i].nodeB);
        }
    }

    public static void nextExec(SnapNode self, Consumer<SnapNode> consumer) {
        int outputOffset = self.inputs.length;
        for(int outputPortI = 0; outputPortI < self.outputs.length; outputPortI++) {
            var output = self.outputs[outputPortI];
            if(output == null || self.portTypes[outputPortI + outputOffset] != ValueTypes.EXEC) return;
            for(int i = 0; i < output.size(); i++) {
                consumer.accept(output.get(i).nodeB);
            }
        }
    }

    public Iterable<String> supportedEvents(String type, CompoundTag data) {
        return null;
    }

    public void compileCustomControlFlow(GeneratorHelper mv, SnapNode sourceNode, CompilationContext context) {

    }
}
