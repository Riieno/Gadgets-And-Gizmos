package com.rieno.gadgetsandgizmos.graph.compile.util;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Function;

public class ArrayTransformUtil {
    @SuppressWarnings("unchecked")
    public static <A,B> B[] map(A[] a, Class<B> typeB, Function<A,B> mapper){
        B[] b = (B[]) Array.newInstance(typeB, a.length);
        for(int i = 0; i < a.length; i++) {
            b[i]=mapper.apply(a[i]);
        }
        return b;
    }
}
