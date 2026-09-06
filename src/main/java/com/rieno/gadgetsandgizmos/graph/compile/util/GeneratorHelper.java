package com.rieno.gadgetsandgizmos.graph.compile.util;

import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.tree.MethodNode;

public class GeneratorHelper extends AdviceAdapter {


    public final MethodNode node;
    public final Object2ObjectMap<String, PortVarEntry> allocatedVariables = new Object2ObjectOpenHashMap<>();
    private final Label startLabel = new Label();
    private final Label endLabel = new Label();

    public GeneratorHelper(MethodNode node) {
        super(Opcodes.ASM9, node, node.access, node.name, node.desc);
        this.node = node;
        visitCode();
        visitLabel(startLabel);
    }

    @Override
    public void visitEnd() {
        node.visitLabel(endLabel);
        super.visitEnd();
    }

    public PortVarEntry localOrNew(String name, ValueType type) {
        PortVarEntry entry = findEntry(name, type);
        if(entry != null) return entry;
        int i = newLocal(type.innerType);
        PortVarEntry value = new PortVarEntry(i,type, type.innerType);
        String key =entryInsertPos[0]<0?name: name + "$" + entryInsertPos[0];
        allocatedVariables.put(key, value);
        mv.visitLocalVariable(
            name,type.getDescriptor(),null,
            startLabel,endLabel,value.index()
        );
        return value;
    }

    public final int[] entryInsertPos = {-1};

    public PortVarEntry findEntry(String name, ValueType type) {
        entryInsertPos[0] = -1;
        return findEntry(name, type, entryInsertPos);
    }

    public PortVarEntry findEntry(String name, ValueType type, int @Nullable [] insertPos) {
        PortVarEntry entry = allocatedVariables.get(name);
        if(entry == null) return null;
        if(entry.type.equals(type)) return entry;

        for(int i = 0; ; i++) {
            entry = allocatedVariables.get(name + "__" + i);
            if(insertPos != null) insertPos[0] = i;
            if(entry == null) return null;
            if(entry.type.equals(type)) {
                return entry;
            }

        }
    }

    public PortVarEntry localOrNew(SnapNode node, int portIndex) {
        return localOrNew("var_"+ node.id + "_" + portIndex, node.portTypes[portIndex]);
    }

    public PortVarEntry findEntry(SnapNode node, int portIndex) {
        return findEntry("var_"+ node.id + "_" + portIndex, node.portTypes[portIndex]);
    }

    public record PortVarEntry(int index,ValueType type, Type realType) {}


}
