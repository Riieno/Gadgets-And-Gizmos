package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.struct.Calculator;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractJVMGraph {
    private final Calculator[] nodeCalculators;
    public final AdvancedGraphDocument graph;
    public final int graphRevision;
    public final JVMGraphLoader loader;
    public final JVMGraphCompiler.Cache cache;

    public AbstractJVMGraph(
        Calculator[] nodeCalculators,
        AdvancedGraphDocument graph,
        JVMGraphCompiler.Cache cache
    ) {
        this.nodeCalculators = nodeCalculators;
        this.graph = graph;
        this.graphRevision = graph.revision();
        this.loader = (JVMGraphLoader) getClass().getClassLoader();
        this.cache = cache;
        portToIndex =cache.portToIndex();
        nodeToIndex = cache.nodeToIndex();
    }
    public abstract void passive();
    public abstract void tick();
    public abstract void onEvent(String event);

    public final Object2IntOpenHashMap<String> nodeToIndex;
    public final Object2ObjectOpenHashMap<PortKey, PortIndex> portToIndex;

    public AdvancedGraphDocument.Value outputOf(AdvancedGraphDocument.Node node, String port) {
        return calculatePort(new PortKey(node.id(), port, true));
    }

    public AdvancedGraphDocument.@Nullable Value calculatePort(PortKey k) {
        PortIndex index = portToIndex.get(k);
        if(index == null) return null;
        int fieldIndex = index.globalOffset + index.localIndex;
        Calculator calculator = nodeCalculators[fieldIndex];
        if(calculator == null) {
            synchronized(cache) {
                calculator = nodeCalculators[fieldIndex];
                if(calculator == null) {
                    calculator = nodeCalculators[fieldIndex] = cache.defineCalculator(loader, index);
                }
            }
        }
        return calculator.calculate();
    }

    public AdvancedGraphDocument.Value inputOf(AdvancedGraphDocument.Node node, String port) {
        //Or calculatePort(redirectInput.get(new PortKey(node,port)))
        return calculatePort(new PortKey(node.id(), port, false));
    }

    public record PortKey(String nodeId, String portName, boolean isOutput) {}

    public record PortIndex(int nodeIndex, int globalOffset, int localIndex) {}

}
