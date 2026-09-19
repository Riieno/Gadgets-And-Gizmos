package com.rieno.gadgetsandgizmos.graph.compile.util;

import com.mojang.datafixers.util.Function3;
import org.objectweb.asm.Handle;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class HandleExtractor {
    public static <R, P> Handle getMethod(Function<R, P> func) {
        return PtrExtractor.tryExtractMethod(1);
    }

    public static <R> Handle getMethod(Supplier<R> func) {
        return PtrExtractor.tryExtractMethod(1);
    }

    public static <P1,P2,R> Handle getMethod(BiFunction<P1,P2,R> func) {
        return PtrExtractor.tryExtractMethod(1);
    }
    public static <P1,P2,P3,R> Handle getMethod(Function3<P1,P2,P3,R> func) {
        return PtrExtractor.tryExtractMethod(1);
    }
}
