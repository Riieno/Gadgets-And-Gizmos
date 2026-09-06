package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

public class JVMRegistry {
    public  final Map<String, JVMNodeType> entries = new Object2ObjectOpenHashMap<>();
    public static JVMRegistry instance=new JVMRegistry();

    public static void register(String type, JVMNodeType value) {
        instance.entries.put(type, value);
    }

}
