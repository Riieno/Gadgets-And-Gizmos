package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.graph.GraphDataNbt;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Retain the addon codec API while sharing lossless graph data conversion
public final class AdvancedGraphDataCodec{
    private AdvancedGraphDataCodec(){}

    // Preserve the numeric and array kinds in a saved graph value
    public static GraphDataValue fromTag(Tag tag){ return GraphDataNbt.fromTag(tag); }

    // Rebuild a detached tag from immutable graph data
    public static Tag toTag(GraphDataValue val){ return GraphDataNbt.toTag(val); }

    public static GraphDataValue.CompoundValue fromCompound(CompoundTag tag){ return GraphDataNbt.fromCompound(tag); }

    public static CompoundTag toCompound(GraphDataValue.CompoundValue val){ return GraphDataNbt.toCompound(val); }
}
