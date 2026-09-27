package com.rieno.gadgetsandgizmos.graph.compile.node_def;

import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.GenericLambdaNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.Options;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface DefHelper {
    static JVMNodeType numberBin(NumberBinaryNode.InlinedBody inlinedBody) {
        return GenericLambdaNode.make(
            Options.builder().outputPortDefName("c").build(),
            1,
            inlinedBody
        );
    }

    static JVMNodeType numberBin2(BiFunction<Double, Double, Double> inlinedBody) {
        return GenericLambdaNode.make(
            Options.builder().outputPortDefName("c").build(),
            1,
            inlinedBody
        );
    }

    static <A, B> JVMNodeType unary(@Nullable String outputPort, Function<A, B> inlinedBody) {
        return GenericLambdaNode.make(
            options(outputPort),
            1,
            inlinedBody
        );
    }
    static <A, B> JVMNodeType unary(Function<A, B> inlinedBody) {
        return GenericLambdaNode.make(
            options(null),
            1,
            inlinedBody
        );
    }

    static <A, B, C> JVMNodeType binary(@Nullable String outputPort, BiFunction<A, B, C> inlinedBody) {
        return GenericLambdaNode.make(
            options(outputPort),
            1,
            inlinedBody
        );
    }

    static Options options(@Nullable String outputPort) {
        if(outputPort == null) {return Options.defaultOptions;}
        return Options
            .builder()
            .outputPortDefName(outputPort)
            .flatOutputRecord(false)
            .build();
    }
}
