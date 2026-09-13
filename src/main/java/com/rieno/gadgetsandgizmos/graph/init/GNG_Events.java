package com.rieno.gadgetsandgizmos.graph.init;

import com.machinezoo.noexception.throwing.ThrowingSupplier;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraph;
import com.rieno.gadgetsandgizmos.graph.compile.EventNode;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphCompiler;
import com.rieno.gadgetsandgizmos.graph.compile.subsystem.NodeFlowGenerator;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import com.rieno.gadgetsandgizmos.graph.struct.NodeCalculator;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class GNG_Events {

    public static final String PASSIVE_EVENT = "passive";

    static {
        JVMGraphCompiler.registerEventCompiler(PASSIVE_EVENT, GNG_Events::passiveEvent);
        JVMGraphCompiler.registerEventCompiler("tick", GNG_Events::processTickEvent);
        JVMGraphCompiler.registerEventCompiler("delayed", GNG_Events::processDelayEvent);
    }

    public static boolean isPassive(String passive) {
        return passive.equals("passive");
    }

    private static MethodNode passiveEvent(
        ClassNode node,
        JVMGraphCompiler.Cache cache, ObjectArrayList<EventNode> tickers,
        Object2ObjectOpenHashMap<String, ObjectArrayList<EventNode>> nodesGroupedByEvent) throws NoSuchMethodException {

        var adapter = getGeneratorHelper(node, () -> AbstractJVMGraph.class.getDeclaredMethod("passive", double.class, String.class));
        var tickEventMethod = node.methods.getLast();
        if(tickers == null) {
            adapter.returnValue();
        } else {
            execWithMode(cache, tickers, adapter, NodeCalculator.Mode.Passive);
            adapter.visitInsn(Opcodes.RETURN);
            adapter.visitEnd();
        }
        return tickEventMethod;
    }

    private static MethodNode processTickEvent(
        ClassNode node,
        JVMGraphCompiler.Cache cache, ObjectArrayList<EventNode> tickers,
        Object2ObjectOpenHashMap<String, ObjectArrayList<EventNode>> nodesGroupedByEvent) throws NoSuchMethodException {

        var adapter = getGeneratorHelper(node, () -> AbstractJVMGraph.class.getDeclaredMethod("tick", double.class, String.class));
        var tickEventMethod = node.methods.getLast();
        if(tickers == null) {
            tickEventMethod.visitInsn(Opcodes.RETURN);
        } else {
            execWithMode(cache,tickers,adapter, NodeCalculator.Mode.ExecFollow);
            tickEventMethod.visitInsn(Opcodes.RETURN);
            adapter.visitEnd();
        }
        return tickEventMethod;
    }

    private static MethodNode processDelayEvent(
        ClassNode node,
        JVMGraphCompiler.Cache cache, ObjectArrayList<EventNode> tickers,
        Object2ObjectOpenHashMap<String, ObjectArrayList<EventNode>> nodesGroupedByEvent) {

        var adapter = getGeneratorHelper(node, () -> AbstractJVMGraph.class.getDeclaredMethod("delayed", double.class, String.class));
        var eventMethod = node.methods.getLast();

        adapter.loadLocal("eventId");
        adapter.invoke(Handle.secondPartOfEvent); //[...,part]

        {
            Label doExec = adapter.newLabel();
            adapter.dup();//[...,part,part]
            adapter.visitJumpInsn(Opcodes.IFNONNULL, doExec);
            eventMethod.visitInsn(Opcodes.POP);
            eventMethod.visitInsn(Opcodes.RETURN);

            adapter.visitLabel(doExec);//[...,part]
        }


        adapter.loadThis();//[...,part,this]
        adapter.swap();//[...,this,part]
        adapter.invoke(Handle.jvmGraph_nodeId);//[...,nodeId]

        adapter.loadThis();//[...,nodeId,this]
        adapter.swap();//[...,this,nodeId]
        adapter.invoke(Handle.jvmGraph_calculator_fromInt);//[...,calculator]

        adapter.get(NodeCalculator.Mode.ExecFollow.myField);//[...,calculator,mode]
        //adapter.swap();//[...,mode,calc]
        adapter.loadLocal("eventId");//[...,calc,mode,event]
        adapter.invoke(Handle.calculator_calc);
        adapter.visitInsn(Opcodes.RETURN);
        adapter.visitEnd();
        /*if(tickers == null) {
            eventMethod.visitInsn(Opcodes.RETURN);
        } else {


            for(EventNode ticker : tickers) {
                NodeFlowGenerator.buildNodeExecCallTree(cache, adapter, cache.nodeToIndex.getInt(ticker.id()));
            }
            adapter.visitEnd();
        }*/
        return eventMethod;
    }

    private static void execWithMode(JVMGraphCompiler.Cache cache, ObjectArrayList<EventNode> tickers, GeneratorHelper adapter, NodeCalculator.Mode passive) {
        for(EventNode ticker : tickers) {
            int nodeId = cache.nodeToIndex.getInt(ticker.id());


            adapter.loadThis();
            adapter.push(nodeId);
            adapter.invoke(Handle.jvmGraph_calculator_fromInt);

            adapter.get(passive.myField);
            adapter.loadLocal("eventId");
            adapter.invoke(Handle.calculator_calc);

            //NodeFlowGenerator.buildNodeCallTree(cache, adapter, nodeId);
        }
    }

    @SneakyThrows
    private static @NotNull GeneratorHelper getGeneratorHelper(ClassNode node, ThrowingSupplier<java.lang.reflect.Method> supplier) {
        var adapter = createEventMethod(node, supplier.get());
        adapter.nameArg(0, "partialTicks");
        adapter.nameArg(1, "eventId");
        return adapter;
    }

    private static @NotNull GeneratorHelper createEventMethod(ClassNode node, java.lang.reflect.Method method) {
        Method tick1 = Method.getMethod(method);
        MethodNode tickEventMethod = new MethodNode(Opcodes.ACC_PUBLIC, tick1.getName(), tick1.getDescriptor(), null, null);
        node.methods.add(tickEventMethod);
        var adapter = new GeneratorHelper(node, tickEventMethod);
        return adapter;
    }

    public static void register() {

    }
}
