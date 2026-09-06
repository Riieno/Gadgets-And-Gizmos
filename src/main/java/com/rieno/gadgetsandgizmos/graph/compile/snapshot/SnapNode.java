package com.rieno.gadgetsandgizmos.graph.compile.snapshot;

import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import it.unimi.dsi.fastutil.objects.*;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@RequiredArgsConstructor
public final class SnapNode {
    private static final String OUTPUT_POSTFIX = "\0000";
    private static final String INPUT_POSTFIX = "\0001";

    public final int id;
    public final JVMNodeType type;
    public final CompoundTag data;
    public final SnapEdge[] inputs;
    public final @Nullable ObjectArrayList<SnapEdge>[] outputs;
    public final ValueType[] portTypes;
    public Object2IntMap<String> portIndexer;
    public String[] portIndexerInverse;

    public SnapNode(
        int nodeI,
        JVMNodeType nodeType,
        CompoundTag data,
        Object2ObjectMap<String, ValueType> input,
        Object2ObjectMap<String, ValueType> outputs
    ) {
        this(
            nodeI,
            nodeType,
            data,
            new SnapEdge[input.size()],
            new ObjectArrayList[outputs.size()],
            new ValueType[input.size() + outputs.size()]
        );
        indexPorts(input, outputs);
        addTypes(input, false);
        addTypes(outputs, true);
    }

    private void addTypes(Object2ObjectMap<String, ValueType> ports, boolean isOutput) {
        for(var entry : ports.entrySet()) {
            int port = port(entry.getKey(), isOutput);
            portTypes[port] = entry.getValue();

        }
    }


    public void outputEdge(int port, SnapEdge snapEdge) {
        port -= inputs.length;
        var edges = outputs[port];
        if(edges == null) edges = outputs[port] = new ObjectArrayList<>();
        edges.add(snapEdge);
    }

    public void indexPorts(Map<String, ?> input, Map<String, ?> output) {
        var indexer = new Object2IntOpenHashMap<String>();
        int index = 0;
        portIndexerInverse = new String[input.size() + output.size()];
        index = indexPorts(input, indexer, INPUT_POSTFIX, index);
        index = indexPorts(output, indexer, OUTPUT_POSTFIX, index);
        portIndexer = Object2IntMaps.unmodifiable(indexer);
    }

    private int indexPorts(Map<String, ?> map, Object2IntOpenHashMap<String> indexer, String postfix, int index) {
        for(String port : map.keySet()) {
            portIndexerInverse[index] = port;
            indexer.put(port + postfix, index++);
        }
        return index;
    }

    public int outputPort(String port) {
        return portIndexer.getInt(port + OUTPUT_POSTFIX);
    }

    public int inputPort(String port) {
        return portIndexer.getInt(port + INPUT_POSTFIX);
    }

    public int port(String port, boolean output) {
        return portIndexer.getInt(port + (output ? OUTPUT_POSTFIX : INPUT_POSTFIX));
    }
}
