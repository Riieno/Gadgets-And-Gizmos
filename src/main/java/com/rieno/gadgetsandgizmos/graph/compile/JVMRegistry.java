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
    public  final Map<String, JVMNodeType> entries = new Object2ObjectOpenHashMap<>();
    public static JVMRegistry instance=new JVMRegistry();

    public static void register(String type, JVMNodeType value) {
        instance.entries.put(type, value);
    }

}
