package com.rieno.gadgetsandgizmos.content.advanced.runtime;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import net.minecraft.nbt.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@FunctionalInterface
public interface NodeExtra {

    String nodeId();

    default NodeExtra editablePort(Function<AdvancedGraphDocument.Node,String> port){
        NodeExtraRegistrar.editableProperties.put(nodeId(),port);
        return this;
    }
    default NodeExtra defaultData(DefaultDataMaker maker){
        NodeExtraRegistrar.defaultDataMakers.put(nodeId(),maker);
        return this;
    }

    default NodeExtra defaultDataFromMap(DefaultDataFromMapMaker maker) {
        return defaultData(ctx -> {
            CompoundTag compoundTag = new CompoundTag();
            Map<String, Object> map = maker.defaultData(ctx);
            setData(compoundTag, map);

            return compoundTag;
        });
    }

    default void setData(CompoundTag compoundTag, Map<String, Object> map) {
        for(var entry : map.entrySet()) {
            Tag tag = toTag(entry.getValue());
            compoundTag.put(entry.getKey(), tag);
        }
    }

    private @NotNull Tag toTag(Object value) {
        return switch(value) {
            case Number d -> DoubleTag.valueOf(d.doubleValue());
            case String v -> StringTag.valueOf(v);
            case Tag v -> v;
            case Map v -> {
                CompoundTag sub = new CompoundTag();
                setData(sub, v);
                yield sub;
            }
            case List v -> {
                ListTag tags = new ListTag();
                for(Object o : v) {
                    tags.add(toTag(o));
                }
                yield tags;
            }
            case byte[] v -> new ByteArrayTag(v);
            case int[] v -> new IntArrayTag(v);
            case long[] v -> new LongArrayTag(v);
            default -> throw new IllegalStateException("Unsupported type: " + value.getClass());
        };
    }

    interface DefaultDataMaker {
        CompoundTag defaultData(AdvancedGraphNodeFactory.Context ctx);
    }

    interface DefaultDataFromMapMaker {
        Map<String, Object> defaultData(AdvancedGraphNodeFactory.Context ctx);
    }
}
