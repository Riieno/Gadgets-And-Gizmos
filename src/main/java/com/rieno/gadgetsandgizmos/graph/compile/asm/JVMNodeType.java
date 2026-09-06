package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

@Getter
public abstract class JVMNodeType {
    public final Object2ObjectLinkedOpenHashMap<String, ValueType> input;
    public final Object2ObjectLinkedOpenHashMap<String, ValueType> outputs;

    public JVMNodeType(Map<String, ValueType> input, Map<String, ValueType> outputs) {
        this.input = new Object2ObjectLinkedOpenHashMap<>(input);
        this.outputs = new Object2ObjectLinkedOpenHashMap<>(outputs);
    }

    public Object2ObjectMap<String, ValueType> getInput() {
        return input;
    }

    public Object2ObjectMap<String, ValueType> getOutputs() {
        return outputs;
    }

    public abstract void compile(GeneratorHelper mv, Inputs inputs, Outputs outputs, CompoundTag data);

    public Iterable<String> supportedEvents(String type, CompoundTag data) {
        return null;
    }

    public boolean isPassive(String type, CompoundTag data) {
        return false;
    }

}
