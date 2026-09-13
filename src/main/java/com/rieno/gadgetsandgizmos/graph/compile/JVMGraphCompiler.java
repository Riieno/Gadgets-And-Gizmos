package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.asm.AsmExpression;
import com.rieno.gadgetsandgizmos.graph.compile.asm.FieldInitExpr;
import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.debug.DebugProps;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapEdge;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.UnboundStateField;
import com.rieno.gadgetsandgizmos.graph.init.GNG_Events;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import lombok.Lombok;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * @see com.rieno.gadgetsandgizmos.graph.compile.subsystem.CalculatorGenerator
 * @see com.rieno.gadgetsandgizmos.graph.compile.subsystem.NodeFlowGenerator
 *
 */
public class JVMGraphCompiler {
    public static final Map<String, EventMethodCompiler> eventCompilers = new Object2ObjectOpenHashMap<>();
    static {
        GNG_Events.register();
    }
    public static class DuplicatedCompiler extends RuntimeException {
        public DuplicatedCompiler(String eventId) {
            super("Compiler for event '%s' already exists".formatted(eventId));
        }
    }

    public static void registerEventCompiler(@NonNull String eventId, @NonNull EventMethodCompiler compiler) throws DuplicatedCompiler {
        EventMethodCompiler exists = eventCompilers.get(eventId);
        if(exists != null) throw new DuplicatedCompiler(eventId);
        eventCompilers.put(eventId, compiler);
    }

    static final int classNodeVersion;
    private static final Type abstractGraph = Type.getType(AbstractJVMGraph.class);

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
    public static AbstractJVMGraph compile(AdvancedGraphDocument document, DebugProps debugProps) {
        var nodesGroupedByEvent = new Object2ObjectOpenHashMap<String, ObjectArrayList<EventNode>>();
        JVMRegistry instance = JVMRegistry.instance;
        List<AdvancedGraphDocument.Node> nodes = document.nodes();
        Object2ObjectOpenHashMap<AbstractJVMGraph.PortKey, AbstractJVMGraph.PortIndex> portToIndex = new Object2ObjectOpenHashMap<>();

        Object2IntOpenHashMap<String> nodeToIndex = new Object2IntOpenHashMap<>();
        SnapNode[] snapNodes = new SnapNode[nodes.size()];
        int calculatorTracker = 0;
        for(int nodeI = 0; nodeI < nodes.size(); nodeI++) {
            AdvancedGraphDocument.Node node = nodes.get(nodeI);
            JVMNodeType nodeType = instance.entries.get(node.type());

            nodeToIndex.put(node.id(), nodeI);
            var input = nodeType.inputPorts(node.data(), nodeI);
            var outputs = nodeType.outputPorts(node.data(), nodeI);

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
                    int i = event.indexOf(':');
                    nodesGroupedByEvent.computeIfAbsent(i == -1 ? event : event.substring(0, i), it -> new ObjectArrayList<>())
                                       .add(new EventNode(i == -1 ? null : event.substring(i + 1), node)
                                       );
                }
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


        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        classNode.superName = abstractGraph.getInternalName();
        classNode.name = "Impl$" + hexHash(classNode);

        Cache cache = new Cache(classNode.name, snapNodes, snapEdges, portToIndex, nodeToIndex);
        ObjectArrayList<Map.Entry<SnapNode,Iterable<UnboundStateField>>> fields=new ObjectArrayList<>();
        for(SnapNode snapNode : snapNodes) {
            var iterable = snapNode.type.stateFields(snapNode, cache);
            if(iterable==null)continue;
            fields.add(Map.entry(snapNode,iterable));
        }
        defineCtorAndStateFields(calculatorTracker, classNode,fields);
        for(Map.Entry<String, EventMethodCompiler> entry : eventCompilers.entrySet()) {
            var eventNodes = nodesGroupedByEvent.remove(entry.getKey());
            entry.getValue().compile(
                classNode, cache, eventNodes, nodesGroupedByEvent
            );
        }
        {
            processRestEvent(nodesGroupedByEvent, classNode, cache);
        }

        try {
            return (AbstractJVMGraph) defineClass(new JVMGraphLoader(debugProps), classNode).getDeclaredConstructor(
                AdvancedGraphDocument.class,
                Cache.class
            ).newInstance(document, cache);
        } catch(ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException |
                NoSuchMethodException e) {
            throw Lombok.sneakyThrow(e);
        }
    }

    private static void processRestEvent(Object2ObjectOpenHashMap<String, ObjectArrayList<EventNode>> nodesGroupedByEvent, ClassNode classNode, Cache cache) {
        //
    }

    public static @NotNull String hexHash(ClassNode node) {
        return Integer.toHexString(System.identityHashCode(node));
    }

    private static void defineCtorAndStateFields(int calculatorTracker, ClassNode node, ObjectArrayList<Map.Entry<SnapNode, Iterable<UnboundStateField>>> fields) {
        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(
            Type.VOID_TYPE,
            documentType,
            Type.getType(Cache.class)
        ), null, null);
        var adapter = adapter(ctor, node);
        adapter.loadThis();
        adapter.push(calculatorTracker);
        adapter.loadArg(0);
        adapter.loadArg(1);
        adapter.invokeConstructor(abstractGraph, Method.getMethod(AbstractJVMGraph.class.getDeclaredConstructors()[0]));

        for(Map.Entry<SnapNode, Iterable<UnboundStateField>> fieldDefs : fields) {
            SnapNode fieldOwner = fieldDefs.getKey();
            for(UnboundStateField stateField : fieldDefs.getValue()) {
                var bound = stateField.bind(fieldOwner);
                FieldNode fieldDef = new FieldNode(Opcodes.ACC_PUBLIC, bound.name(), bound.type().getDescriptor(), null, null);
                node.fields.add(fieldDef);
                FieldInitExpr initExpr = stateField.initExpression();
                if(initExpr==null)continue;
                switch(initExpr) {
                    case AsmExpression.ReflectionMethod(java.lang.reflect.Method method) -> {
                        adapter.loadThis();

                        adapter.invoke(method);

                        adapter.storeField(node.name,fieldDef);
                    }
                    case AsmExpression.InsnListAsm(AbstractInsnNode[] init)  -> {

                        adapter.loadThis();
                        for(AbstractInsnNode insnNode : init) insnNode.accept(adapter);
                        adapter.storeField(node.name,fieldDef);
                    }
                    case AsmExpression.ReflectionConstructor(Constructor<?> init)-> {
                        adapter.loadThis();

                        adapter.newInstance(Type.getType(init.getDeclaringClass()));
                        adapter.dup();
                        adapter.invoke(init);

                        adapter.storeField(node.name,fieldDef);
                    }
                    case FieldInitExpr.Value value->fieldDef.value=value.getValue();
                }
            }
        }

        adapter.returnValue();
        adapter.visitEnd();
        node.methods.add(ctor);
    }


    public static Class<?> defineClass(JVMGraphLoader loader, ClassNode node) throws ClassNotFoundException {
        node.version = classNodeVersion;
        node.access |= Opcodes.ACC_PUBLIC;
        if(loader.debugProps != null) {loader.debugProps.onClassDefine(loader, node);}
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        loader.defineClass(node.name, writer.toByteArray());
        Class<?> aClass = loader.loadClass(node.name);
        return aClass;
    }

    public static @NotNull GeneratorHelper adapter(MethodNode method, ClassNode declaredNode) {
        return new GeneratorHelper(declaredNode, method);
    }

    @AllArgsConstructor

    public static final class Cache {
        public final String className;
        public final SnapNode[] nodes;
        public final SnapEdge[] edges;
        public final Object2ObjectOpenHashMap<AbstractJVMGraph.PortKey, AbstractJVMGraph.PortIndex> portToIndex;
        public final Object2IntOpenHashMap<String> nodeToIndex;

        public Object2ObjectOpenHashMap<AbstractJVMGraph.PortKey, AbstractJVMGraph.PortIndex> portToIndex() {
            return portToIndex;
        }

        public Object2IntOpenHashMap<String> nodeToIndex() {
            return nodeToIndex;
        }


    }

    public interface EventMethodCompiler {
        MethodNode compile(ClassNode classNode, Cache cache, ObjectArrayList<EventNode> eventNodes, Object2ObjectOpenHashMap<String, ObjectArrayList<EventNode>> nodesGroupedByEvent) throws Exception;
    }
}
