package com.rieno.gadgetsandgizmos.graph.compile.util;

import org.objectweb.asm.Handle;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class HandleExtractor {
    static <R, P> Handle getMethod(Function<R, P> func) {
        return PtrExtractor.tryExtractMethod(1);
    }

    static <R> Handle getMethod(Supplier<R> func) {
        return PtrExtractor.tryExtractMethod(1);
    }

    static <P1,P2,R> Handle getMethod(BiFunction<P1,P2,R> func) {
        return PtrExtractor.tryExtractMethod(1);
    }
}
