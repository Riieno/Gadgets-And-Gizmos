package com.rieno.gadgetsandgizmos.graph.struct;

import it.unimi.dsi.fastutil.objects.ObjectLists;

public class MutableSingletonList<T> extends ObjectLists.Singleton<T> {
    protected MutableSingletonList(T element) {
        super(element);
    }

    @Override
    public T get(int i) {
        return super.get(i);
    }

    @Override
    public T set(int index, T t) {
        return super.set(index, t);
    }
}
