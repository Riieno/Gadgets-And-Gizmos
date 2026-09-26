package com.rieno.gadgetsandgizmos.graph.compile.util;

import it.unimi.dsi.fastutil.Hash;
import org.objectweb.asm.tree.AbstractInsnNode;

public enum IndexInsnHashStrategy implements Hash.Strategy<AbstractInsnNode> {
    INSTANCE;

    @Override
    public int hashCode(AbstractInsnNode o) {
        if(o == null) return 0;
        int index = CompileUtil.indexOf(o);
        return index == -1 ? o.hashCode() : index;
    }

    @Override
    public boolean equals(AbstractInsnNode a, AbstractInsnNode b) {
        if(a==b)return true;
        if(a==null || b==null)return false;
        int ai = CompileUtil.indexOf(a);
        if(ai==-1)return false;
        int bi = CompileUtil.indexOf(b);
        return ai==bi;
    }
}
