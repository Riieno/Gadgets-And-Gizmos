package com.rieno.gadgetsandgizmos.content.advanced.runtime;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.function.Function;
import java.util.function.Supplier;

public class NodeExtraRegistrar {
    public static final Object2ObjectOpenHashMap<String, NodeExtra.DefaultDataMaker> defaultDataMakers = new Object2ObjectOpenHashMap<>();
    public static final Object2ObjectOpenHashMap<String, Function<AdvancedGraphDocument.Node,String>> editableProperties = new Object2ObjectOpenHashMap<>();

    public static NodeExtra.DefaultDataMaker defaultDataMaker(String type) {return defaultDataMakers.get(type);}
    public static Function<AdvancedGraphDocument.Node,String> editableProperty(String type) {return editableProperties.get(type);}
}
