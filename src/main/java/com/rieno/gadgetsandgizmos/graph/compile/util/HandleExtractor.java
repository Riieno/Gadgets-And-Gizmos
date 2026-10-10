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
    public static <T> Handle getFunction(T func) {
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

    public static <P1,P2,P3,P4,R> Handle getMethod(TFunc4<P1,P2,P3,P4,R> func) { return PtrExtractor.tryExtractMethod(1);}
    public static <P1,P2,P3,P4,P5,R> Handle getMethod(TFunc5<P1,P2,P3,P4,P5,R> func) { return PtrExtractor.tryExtractMethod(1);}
    public static <P1,P2,P3,P4,P5,P6,R> Handle getMethod(TFunc6<P1,P2,P3,P4,P5,P6,R> func) { return PtrExtractor.tryExtractMethod(1);}
    public static <P1,P2,P3,P4,P5,P6,P7,R> Handle getMethod(TFunc7<P1,P2,P3,P4,P5,P6,P7,R> func) { return PtrExtractor.tryExtractMethod(1);}
    public static <P1,P2,P3,P4,P5,P6,P7,P8,R> Handle getMethod(TFunc8<P1,P2,P3,P4,P5,P6,P7,P8,R> func) { return PtrExtractor.tryExtractMethod(1);}
    public static <P1,P2,P3,P4,P5,P6,P7,P8,P9,R> Handle getMethod(TFunc9<P1,P2,P3,P4,P5,P6,P7,P8,P9,R> func) { return PtrExtractor.tryExtractMethod(1);}
    public static <P1,P2,P3,P4,P5,P6,P7,P8,P9,P10,R> Handle getMethod(TFunc10<P1,P2,P3,P4,P5,P6,P7,P8,P9,P10,R> func) { return PtrExtractor.tryExtractMethod(1);}

    public interface TFunc4<P1,P2,P3,P4,R>{
        R run(P1 p1,P2 p2,P3 p3,P4 p4) throws Exception;
    }
    public interface TFunc5<P1,P2,P3,P4,P5,R>{
        R run(P1 p1,P2 p2,P3 p3,P4 p4, P5 p5) throws Exception;
    }
    public interface TFunc6<P1,P2,P3,P4,P5,P6,R>{
        R run(P1 p1,P2 p2,P3 p3,P4 p4, P5 p5, P6 p6) throws Exception;
    }
    public interface TFunc7<P1,P2,P3,P4,P5,P6,P7,R>{
        R run(P1 p1,P2 p2,P3 p3,P4 p4, P5 p5, P6 p6, P7 p7) throws Exception;
    }
    public interface TFunc8<P1,P2,P3,P4,P5,P6,P7,P8,R>{
        R run(P1 p1,P2 p2,P3 p3,P4 p4, P5 p5, P6 p6, P7 p7, P8 p8) throws Exception;
    }
    public interface TFunc9<P1,P2,P3,P4,P5,P6,P7,P8,P9,R>{
        R run(P1 p1,P2 p2,P3 p3,P4 p4, P5 p5, P6 p6, P7 p7, P8 p8, P9 p9) throws Exception;
    }
    public interface TFunc10<P1,P2,P3,P4,P5,P6,P7,P8,P9,P10,R>{
        R run(P1 p1,P2 p2,P3 p3,P4 p4, P5 p5, P6 p6, P7 p7, P8 p8, P9 p9, P10 p10) throws Exception;
    }
}
