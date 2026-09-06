package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.asm.NodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.struct.Calculator;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Lombok;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper.PortVarEntry;

public class JVMGraphCompiler {

    static final int classNodeVersion;
    private static final Type abstractGraph = Type.getType(AbstractJVMGraph.class);
    private static final Type calculatorType = Type.getType(Calculator.class);
    private static final Type documentType = Type.getType(AdvancedGraphDocument.class);

    static {
        try {
            ClassReader reader = new ClassReader(Type.getType(JVMGraphCompiler.class).getInternalName());
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            classNodeVersion = node.version;
        } catch(IOException e) {
            throw Lombok.sneakyThrow(e);
        }
    }

    @SneakyThrows
    public static AbstractJVMGraph compile(AdvancedGraphDocument document, File debugDir) {
        var nodesGroupedByEvent = new Object2ObjectOpenHashMap<String, ObjectArrayList<AdvancedGraphDocument.Node>>();
        var passive = new ObjectArrayList<AdvancedGraphDocument.Node>();
        JVMRegistry instance = JVMRegistry.instance;
        List<AdvancedGraphDocument.Node> nodes = document.nodes();
        Object2ObjectOpenHashMap<AbstractJVMGraph.PortKey, AbstractJVMGraph.PortIndex> portToIndex = new Object2ObjectOpenHashMap<>();

        Object2IntOpenHashMap<String> nodeToIndex = new Object2IntOpenHashMap<>();
        SnapNode[] snapNodes = new SnapNode[nodes.size()];
        int calculatorTracker = 0;
        for(int nodeI = 0; nodeI < nodes.size(); nodeI++) {
            AdvancedGraphDocument.Node node = nodes.get(nodeI);
            NodeType nodeType = instance.entries.get(node.type());

            nodeToIndex.put(node.id(), nodeI);
            var input = nodeType.getInput();
            var outputs = nodeType.getOutputs();

            SnapNode snapNode = new SnapNode(
                nodeI,
                nodeType,
                node.data(),
                input, outputs
            );

            snapNodes[nodeI] = snapNode;

            Iterable<String> supportedEvents = nodeType.supportedEvents(node.type(), node.data());
            if(supportedEvents != null) {
                for(String event : supportedEvents) {
                    nodesGroupedByEvent.computeIfAbsent(event, it -> new ObjectArrayList<>()).add(node);
                }
            }

            if(nodeType.isPassive(node.type(), node.data())) {
                passive.add(node);
            }

            for(var portEntry : input.entrySet()) {
                String portName = portEntry.getKey();
                portToIndex.put(new AbstractJVMGraph.PortKey(
                    node.id(), portName, false
                ), new AbstractJVMGraph.PortIndex(nodeI, calculatorTracker, snapNode.inputPort(portName)));
            }
            //calculatorTracker += portI;
            for(var portEntry : outputs.entrySet()) {
                String portName = portEntry.getKey();
                portToIndex.put(new AbstractJVMGraph.PortKey(
                    node.id(), portName, true
                ), new AbstractJVMGraph.PortIndex(nodeI, calculatorTracker, snapNode.outputPort(portName)));
            }
            calculatorTracker += snapNode.portIndexer.size();
        }

        var edges = document.edges();
        SnapEdge[] snapEdges = new SnapEdge[edges.size()];
        for(int i = 0; i < edges.size(); i++) {
            AdvancedGraphDocument.Edge edge = edges.get(i);
            SnapNode nodeA = snapNodes[nodeToIndex.getInt(edge.fromNode())];
            SnapNode nodeB = snapNodes[nodeToIndex.getInt(edge.toNode())];
            SnapEdge snapEdge = new SnapEdge(
                nodeA,
                nodeA.outputPort(edge.fromPort()),
                nodeB,
                nodeB.inputPort(edge.toPort())
            );
            nodeA.outputEdge(snapEdge.portA, snapEdge);
            nodeB.inputs[snapEdge.portB] = snapEdge;
            snapEdges[i] = snapEdge;
        }

        Cache cache = new Cache(snapNodes, snapEdges, portToIndex, nodeToIndex);

        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.superName = abstractGraph.getInternalName();
        node.name = "Impl$" + hexHash(node);
        defineCtor(calculatorTracker, node);
        {
            processTickEvent(nodesGroupedByEvent, node, cache);
        }

        try {
            return (AbstractJVMGraph) defineClass(new JVMGraphLoader(debugDir), node).getDeclaredConstructor(
                AdvancedGraphDocument.class,
                Cache.class
            ).newInstance(document, cache);
        } catch(ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException |
                NoSuchMethodException e) {
            throw Lombok.sneakyThrow(e);
        }
    }

    private static @NotNull String hexHash(ClassNode node) {
        return Integer.toHexString(System.identityHashCode(node));
    }

    private static void defineCtor(int calculatorTracker, ClassNode node) {
        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(
            Type.VOID_TYPE,
            documentType,
            Type.getType(Cache.class)
        ), null, null);
        var adapter = adapter(ctor);
        adapter.loadThis();
        adapter.push(calculatorTracker);
        adapter.newArray(calculatorType);
        adapter.loadArg(0);
        adapter.loadArg(1);
        adapter.invokeConstructor(abstractGraph, Method.getMethod(AbstractJVMGraph.class.getDeclaredConstructors()[0]));
        adapter.returnValue();
        adapter.visitEnd();
        node.methods.add(ctor);
    }

    private static void processTickEvent(
        Object2ObjectOpenHashMap<String, ObjectArrayList<AdvancedGraphDocument.Node>> nodesGroupedByEvent,
        ClassNode node,
        Cache cache
    ) throws NoSuchMethodException {
        ObjectArrayList<AdvancedGraphDocument.Node> tickers = nodesGroupedByEvent.get("tick");

        Method tick1 = Method.getMethod(AbstractJVMGraph.class.getDeclaredMethod("tick"));
        MethodNode tickEventMethod = new MethodNode(Opcodes.ACC_PUBLIC, tick1.getName(), tick1.getDescriptor(), null, null);
        node.methods.add(tickEventMethod);
        if(tickers == null) {
            tickEventMethod.visitInsn(Opcodes.RETURN);
        } else {
            var adapter = adapter(tickEventMethod);
            for(AdvancedGraphDocument.Node ticker : tickers) {
                cache.buildNodeCallTree(adapter, cache.nodeToIndex.getInt(ticker.id()));
            }
            adapter.visitEnd();
        }
    }

    private static Class<?> defineClass(JVMGraphLoader loader, ClassNode node) throws ClassNotFoundException {
        node.version = classNodeVersion;
        node.access|=Opcodes.ACC_PUBLIC;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        loader.defineClass(node.name, writer.toByteArray());
        Class<?> aClass = loader.loadClass(node.name);
        return aClass;
    }

    private static @NotNull GeneratorHelper adapter(MethodNode method) {
        return new GeneratorHelper(method);
    }

    public record Cache(
        SnapNode[] nodes,
        SnapEdge[] edges,
        Object2ObjectOpenHashMap<AbstractJVMGraph.PortKey, AbstractJVMGraph.PortIndex> portToIndex,
        Object2IntOpenHashMap<String> nodeToIndex
    ) {

        private static final Type calculator = Type.getType(Calculator.class);
        private static final Method method;

        static {
            try {
                method = Method.getMethod(Calculator.class.getDeclaredMethod("calculate"));
            } catch(NoSuchMethodException e) {
                throw Lombok.sneakyThrow(e);
            }
        }

        public Calculator defineCalculator(JVMGraphLoader loader, AbstractJVMGraph.PortIndex index) {
            ClassNode classNode = new ClassNode();
            classNode.name = Calculator.class.getSimpleName() + "$" + hexHash(classNode);
            classNode.superName = calculator.getInternalName();

            MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            classNode.methods.add(ctor);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, calculatorType.getInternalName(), "<init>", "()V", false);
            ctor.visitInsn(Opcodes.RETURN);

            MethodNode calculateMethod = new MethodNode(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, null);
            GeneratorHelper adapter = adapter(calculateMethod);
            SnapNode snapNode = nodes[index.nodeIndex()];
            buildNodeCallTree(
                adapter,
                index.nodeIndex(),
                (mv, portName) -> {
                    int outputPort = snapNode.outputPort(portName);
                    var portType = snapNode.portTypes[outputPort];
                    if(outputPort == index.localIndex()) {
                        portType.convertTo(mv, ValueType.VALUE);
                        mv.returnValue();
                        return;
                    }
                    mv.visitInsn(Opcodes.POP + portType.getSize() - 1);
                }
            );
            adapter.visitEnd();

            classNode.methods.add(calculateMethod);


            try {
                Class<?> aClass = defineClass(loader, classNode);
                return (Calculator) aClass.getConstructor().newInstance();
            } catch(ClassNotFoundException | InvocationTargetException | InstantiationException |
                    IllegalAccessException | NoSuchMethodException e) {
                throw Lombok.sneakyThrow(e);
            }


        }

        public void buildNodeCallTree(GeneratorHelper adapter, int rootNodeID, Outputs outputs) {

            var nodesWithDepth = collectNodesWithDepth(rootNodeID);
            for(IntArrayList list : nodesWithDepth) {
                for(int i = 0; i < list.size(); i++) {
                    int nodeI = list.getInt(i);
                    SnapNode node = nodes[nodeI];
                    compileNode(adapter, defaultOutputs(node), node);
                }
            }
            SnapNode node = nodes[rootNodeID];

            compileNode(adapter, outputs, node);
        }


        private @NotNull IntArrayList[] collectNodesWithDepth(int nodeID) {
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

        private void compileNode(GeneratorHelper adapter, Outputs outputs, SnapNode node) {
            node.type.compile(adapter,
                (mv, inputPortName) -> {
                    int portIndex = node.inputPort(inputPortName);
                    PortVarEntry existedEntry = mv.findEntry(node, portIndex);
                    if(existedEntry != null) {
                        mv.loadLocal(existedEntry.index());
                        return;
                    }

                    SnapEdge edge = node.inputs[portIndex];
                    PortVarEntry entry = mv.findEntry(edge.nodeA, edge.portA);
                    Objects.requireNonNull(entry, "variable is not allocated");

                    mv.loadLocal(entry.index());
                    if(entry.type().equals(node.portTypes[portIndex])) {
                        return;
                    }

                    existedEntry = mv.localOrNew(node, portIndex);
                    entry.type().convertTo(mv,existedEntry.type());
                    if(existedEntry.type().getSize() == 2) {
                        mv.dup2();
                    } else {
                        mv.dup();
                    }
                    mv.storeLocal(existedEntry.index());
                },
                outputs,
                node.data
            );
        }

        private @NotNull Outputs defaultOutputs(SnapNode node) {
            return (mv, port) -> {
                PortVarEntry entry = mv.localOrNew(node, node.outputPort(port));
                mv.storeLocal(entry.index());

            };
        }

        private void typeConversion(ValueType from, ValueType to) {
            if(from.equals(to)) return;

        }

        public void buildNodeCallTree(GeneratorHelper adapter, int nodeID) {
            buildNodeCallTree(adapter, nodeID, defaultOutputs(nodes[nodeID]));

        }

    }

    public record SnapEdge(SnapNode nodeA, int portA, SnapNode nodeB, int portB) {}
}
