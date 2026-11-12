package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

public class JVMRegistry implements DefHelper {

    static {
        ValueTypes.afterAll()
        ;
    }

    public final Map<String, JVMNodeType> entries = new Object2ObjectOpenHashMap<>();
    private static final JVMRegistry ROOT = new JVMRegistry(null);

    private static JVMRegistry instance = ROOT;
    public final JVMRegistry parent;

    public static JVMRegistry getRoot() {
        return ROOT;
    }

    public JVMRegistry(JVMRegistry parent) {this.parent = parent;}

    public static void register(String type, JVMNodeType value) {
        instance.entries.put(type, value);
    }

    public static JVMRegistry instance() {
        return instance;
    }

    public static void setInstance(JVMRegistry instance) {
        JVMRegistry.instance = instance;
    }

    public JVMNodeType get(String type) {
        JVMNodeType nodeType = entries.get(type);
        if(nodeType == null) {
            if(parent != null) return parent.get(type);
            throw new IllegalArgumentException("No such node type '"+type+"'");
        }
        return nodeType;
    }
}
