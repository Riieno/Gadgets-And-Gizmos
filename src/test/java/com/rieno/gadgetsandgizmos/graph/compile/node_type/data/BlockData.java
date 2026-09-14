package com.rieno.gadgetsandgizmos.graph.compile.node_type.data;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

public class BlockData {
    public static final Object2ObjectMap<String, CompoundTag> WORLD = new Object2ObjectOpenHashMap<>();
    public static final String blockPosKey = "BLOCK_POS";

    public static void setValues(String blockPos, Object[] name2PortValueMap){
        CompoundTag block = WORLD.get(blockPos);
        CompoundTag values = block.getCompound("values");
        for(int i = 0; i < name2PortValueMap.length; i+=2) {
            String name = (String) name2PortValueMap[i];
            AdvancedGraphDocument.Value value = (AdvancedGraphDocument.Value) name2PortValueMap[i+1];
            values.put(name,value.toTag());
        }
        block.put("values",values);
    }

    public static void getValues(String blockPos, Map<String, AdvancedGraphDocument.Value> dest){
        CompoundTag block = WORLD.get(blockPos);
        CompoundTag values = block.getCompound("values");
        for(String key : values.getAllKeys()) {
            dest.put(key,AdvancedGraphDocument.Value.fromTag(values.getCompound(key)));
        }
    }

    public static ListTag getPorts(CompoundTag tag) {
        if(tag == null) return null;
        Tag ports0 = tag.get("ports");
        if(!(ports0 instanceof ListTag ports) || ports.getElementType() != Tag.TAG_STRING)
            return null;
        return ports;
    }

    static Object2ObjectMap<String, ValueType<?>> mergePorts(Object2ObjectMap<String, ValueType<?>> declared, CompoundTag data) {
        String string = data.getString(blockPosKey);
        CompoundTag tag = WORLD.get(string);
        ListTag ports = getPorts(tag);
        if(ports == null) return declared;
        Object2ObjectArrayMap<String, ValueType<?>> outPorts = new Object2ObjectArrayMap<>();
        outPorts.putAll(declared);
        for(Tag portEntry0 : ports) {
            outPorts.put(portEntry0.getAsString(), ValueTypes.VALUE);
        }

        return outPorts;
    }
}
