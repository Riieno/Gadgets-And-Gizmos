package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.graph.compile.util.InsnAdapter;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Setter;

public class CompileCtx {
    public Int2IntOpenHashMap varMap = new Int2IntOpenHashMap();
    public Int2ObjectMap<String> varToPortMap = new Int2ObjectOpenHashMap<>();
    public InsnAdapter.LabelCloner labelCloner=new InsnAdapter.LabelCloner();

    @Setter
    private int offset;

    {
        reset();
    }

    public void reset() {
        varMap.clear();
        varToPortMap.clear();
        labelCloner.clear();
        offset = Integer.MIN_VALUE;
    }

    public int getOffset() {
        if(offset == Integer.MIN_VALUE) throw new IllegalArgumentException("Offset is unset");
        return offset;
    }
    protected int __rawOffset(){
        return offset;
    }

}
