package com.rieno.gadgetsandgizmos.graph.compile.subsystem;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphCompiler;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapEdge;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class NodeFlowGenerator {
    public static void buildNodeCallTree(SnapNode[] nodes, GeneratorHelper adapter, int rootNodeID, Outputs outputs, CompilationContext context) {
        var nodesWithDepth = collectRequiredNodesWithDepth(nodes, rootNodeID, new boolean[0]);
        for(IntArrayList list : nodesWithDepth) {
            for(int i = 0; i < list.size(); i++) {
                int nodeI = list.getInt(i);
                SnapNode node = nodes[nodeI];
                compileNode(adapter, defaultOutputs(node), node, context);
            }
        }
        SnapNode node = nodes[rootNodeID];

        compileNode(adapter, outputs, node, context);
    }

    public static @NotNull IntArrayList[] collectRequiredNodesWithDepth(SnapNode[] nodes, int nodeID, boolean[] needExecution) {
        var nodesWithDepth = new Int2IntOpenHashMap();
        IntArrayList a = new IntArrayList();
        IntArrayList b = new IntArrayList();
        ObjectArrayList<IntArrayList> perDepth = new ObjectArrayList<>();
        a.add(nodeID);
        int depth = 0;

        while(!a.isEmpty()) {
            IntArrayList currentDepth = new IntArrayList();
            perDepth.add(currentDepth);
            int[] elements = a.elements();
            for(int i = 0; i < a.size(); i++) {
                SnapNode node = nodes[elements[i]];
                if(node.type.hasSideEffect(node)) {
                    needExecution[0] = true;
                }
                int curDepth = nodesWithDepth.getOrDefault(node.id, -1);
                if(curDepth < depth) {
                    currentDepth.add(node.id);
                    if(curDepth >= 0) {
                        perDepth.get(curDepth).removeInt(node.id);
                    }
                    nodesWithDepth.put(node.id, depth);
                }
                for(SnapEdge edge : node.inputs) b.add(edge.nodeA.id);
            }


            var a1 = a;
            a = b;
            b = a1;
            b.clear();
            depth++;
        }

        IntArrayList[] out = new IntArrayList[perDepth.size()];
        int outI = 0;
        for(int i = perDepth.size() - 1; i >= 1; i--) {
            IntArrayList list = perDepth.get(i);
            if(list.isEmpty()) continue;
            out[outI++] = list;
        }
        return outI == out.length ? out : Arrays.copyOf(out, outI);
    }

    public static void compileNode(GeneratorHelper adapter, Outputs outputs, SnapNode node, CompilationContext context) {
        node.type.compileOutputPortCalculations(adapter,
            node,
            defaultInputs(node),
            outputs,
            node.data,
            context);
    }

    public static @NotNull Inputs defaultInputs(SnapNode node) {
        return (mv, inputPortName) -> {
            int portIndex = node.inputPort(inputPortName);
            GeneratorHelper.PortVarEntry existedEntry = mv.findEntry(node, portIndex);
            if(existedEntry != null) {
                mv.loadLocal(existedEntry);
                return;
            }

            SnapEdge edge = node.inputs[portIndex];
            GeneratorHelper.PortVarEntry entry = mv.findEntry(edge.nodeA, edge.portA);
            Objects.requireNonNull(entry, "variable is not allocated");

            mv.loadLocal(entry);
            if(entry.type().equals(node.portTypes[portIndex])) {
                return;
            }

            existedEntry = mv.localOrNew(node, portIndex);
            entry.type().convertTo(mv, existedEntry.type());
            if(existedEntry.type().getSize() == 2) {
                mv.dup2();
            } else {
                mv.dup();
            }
            mv.storeLocal(existedEntry);
        };
    }

    public static @NotNull Outputs defaultOutputs(SnapNode node) {
        return (mv, port) -> {
            GeneratorHelper.PortVarEntry entry = mv.localOrNew(node, node.outputPort(port));
            mv.storeLocal(entry);

        };
    }

    public static void buildNodeCallTree(CompilationContext context, GeneratorHelper adapter, int nodeID) {
        JVMGraphCompiler.Cache cache = context.compilationCache;
        buildNodeCallTree(cache.nodes, adapter, nodeID, defaultOutputs(cache.nodes[nodeID]), context);
    }

    public static void buildNodeExecSignalPath(CompilationContext context, GeneratorHelper adapter, int sourceNodeID, boolean generateSourceExec) {
        JVMGraphCompiler.Cache cache = context.compilationCache;
        SnapNode sourceNode = cache.nodes[sourceNodeID];

        sourceNode.type.typeProperties(context.typeProperties, sourceNode);
        if(context.typeProperties.customControlFlow) {
            buildCustomControlFlow(context, adapter, sourceNode);
            return;
        }else if(generateSourceExec){
            buildNodeCallTree(context, adapter,sourceNodeID);
        }
        int prevSize = 0;
        IntArrayList curQueue = new IntArrayList();
        IntArrayList nextQueue = new IntArrayList();
        curQueue.add(sourceNodeID);
        while(!curQueue.isEmpty()) {
            IntArrayList nextQueue0 = nextQueue;
            for(int i = 0; i < curQueue.size(); i++) {
                int nodeID = curQueue.getInt(i);
                SnapNode node = cache.nodes[nodeID];
                node.type.forEachFollowExec(node, context, nextNode -> {
                    nextNode.type.typeProperties(context.typeProperties,nextNode);
                    if(context.typeProperties.customControlFlow) {
                        buildCustomControlFlow(context, adapter, nextNode);
                    } else {
                        buildNodeCallTree(context, adapter,nextNode.id);
                        nextQueue0.add(nextNode.id);
                    }
                });
            }
            IntArrayList curQueue1 = curQueue;
            curQueue=nextQueue;
            nextQueue=curQueue1;
            nextQueue.clear();
        }
    }

    public static void buildCustomControlFlow(CompilationContext context, GeneratorHelper adapter, SnapNode sourceNode) {
        sourceNode.type.compileCustomControlFlow(adapter,sourceNode, context);
    }
}
