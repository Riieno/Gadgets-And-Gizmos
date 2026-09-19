package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.subsystem.CalculatorGenerator;
import com.rieno.gadgetsandgizmos.graph.struct.NodeCalculator;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public abstract class AbstractJVMGraph {
    public static final Method eventMethodSample = new Method("<sample>",
        Type.VOID_TYPE,
        new Type[]{Type.DOUBLE_TYPE/*partialTicks*/, Type.getType(String.class)/*eventId*/}
    );
    private final NodeCalculator[] nodeCalculators;
    public final AdvancedGraphDocument graph;
    public final int graphRevision;
    public final JVMGraphLoader loader;
    public final JVMGraphCompiler.Cache cache;

    public AbstractJVMGraph(
        int amountOfCalculators,
        AdvancedGraphDocument graph,
        JVMGraphCompiler.Cache cache
    ) {
        this.nodeCalculators = new NodeCalculator[amountOfCalculators];
        this.graph = graph;
        this.graphRevision = graph.revision();
        this.loader = (JVMGraphLoader) getClass().getClassLoader();
        this.cache = cache;
        portToIndex = cache.portToIndex();
        nodeToIndex = cache.nodeToIndex();
    }

    public abstract void passive(double deltaTime, String eventId);

    public abstract void tick(double deltaTime, String eventId);

    public abstract void delayed(double deltaTime, String eventId);

    public abstract void onEvent(double deltaTime, String eventId);

    public final Object2IntOpenHashMap<String> nodeToIndex;
    public final Object2ObjectOpenHashMap<PortKey, PortIndex> portToIndex;

    public AdvancedGraphDocument.Value outputOf(AdvancedGraphDocument.Node node, String port) {
        return calculatePort(new PortKey(node.id(), port, true), false);
    }

    public AdvancedGraphDocument.Value outputOf(AdvancedGraphDocument.Node node, String port, boolean throwError) {
        return calculatePort(new PortKey(node.id(), port, true), throwError);
    }

    public AdvancedGraphDocument.@Nullable Value calculatePort(PortKey k, boolean throwError) {
        PortIndex index = portToIndex.get(k);
        if(index == null) {
            if(throwError) {
                if(!nodeToIndex.containsKey(k.nodeId)){
                    throw new IllegalArgumentException("No such node: " + k.nodeId);
                }
                throw new IllegalArgumentException("No such port: " + k);
            }
            return null;
        }
        int nodeIndex = index.nodeIndex();
        NodeCalculator calculator = getOrCreateNodeCalculator(nodeIndex);
        ;
        calculator.calculate(NodeCalculator.Mode.CalculatePorts, null);
        return calculator.ports[index.localIndex];
    }

    public @NotNull NodeCalculator getOrCreateNodeCalculator(AdvancedGraphDocument.Node node) {

        int nodeIndex = nodeToIndex.getOrDefault(node.id(), -1);
        if(nodeIndex < 0) throw new IllegalArgumentException("Node with unknown id");
        return getOrCreateNodeCalculator(nodeIndex);
    }

    public @NotNull NodeCalculator getOrCreateNodeCalculator(int nodeIndex) {
        NodeCalculator calculator = nodeCalculators[nodeIndex];
        if(calculator == null) {
            synchronized(cache) {
                calculator = nodeCalculators[nodeIndex];
                if(calculator == null) {
                    calculator = nodeCalculators[nodeIndex] = CalculatorGenerator.defineCalculator(cache, loader, this, nodeIndex);
                }
            }
        }
        return calculator;
    }

    public AdvancedGraphDocument.Value inputOf(AdvancedGraphDocument.Node node, String port) {
        //Or calculatePort(redirectInput.get(new PortKey(node,port)))
        return calculatePort(new PortKey(node.id(), port, false), false);
    }

    public AdvancedGraphDocument.Value inputOf(AdvancedGraphDocument.Node node, String port, boolean throwError) {
        //Or calculatePort(redirectInput.get(new PortKey(node,port)))
        return calculatePort(new PortKey(node.id(), port, false), throwError);
    }

    public record PortKey(String nodeId, String portName, boolean isOutput) {}

    public record PortIndex(int nodeIndex, int globalOffset, int localIndex) {}

}
