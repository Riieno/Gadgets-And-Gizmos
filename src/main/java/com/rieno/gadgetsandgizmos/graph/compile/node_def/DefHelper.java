package com.rieno.gadgetsandgizmos.graph.compile.node_def;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.GenericLambdaNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.Options;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.ShouldFlatOutputPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface DefHelper {
    static GenericLambdaNode numberBin(NumberBinaryNode.InlinedBody inlinedBody) {
        return GenericLambdaNode.make(
            Options.builder().outputPortDefName("c").build(),
            1,
            inlinedBody
        );
    }

    static GenericLambdaNode numberBin2(BiFunction<Double, Double, Double> inlinedBody) {
        return GenericLambdaNode.make(
            Options.builder().outputPortDefName("c").build(),
            1,
            inlinedBody
        );
    }

    static <A> GenericLambdaNode source(@Nullable String outputPort, Supplier<A> inlinedBody) {
        return GenericLambdaNode.make(
            options(outputPort),
            1,
            inlinedBody
        );
    }
    static <A, B> GenericLambdaNode unary(@Nullable String outputPort, Function<A, B> inlinedBody) {
        return GenericLambdaNode.make(
            options(outputPort),
            1,
            inlinedBody
        );
    }
    static <A, B> GenericLambdaNode unary(Function<A, B> inlinedBody) {
        return GenericLambdaNode.make(
            options(null),
            1,
            inlinedBody
        );
    }

    static <A, B, C> GenericLambdaNode binary(@Nullable String outputPort, BiFunction<A, B, C> inlinedBody) {
        return GenericLambdaNode.make(
            options(outputPort),
            1,
            inlinedBody
        );
    }
    static <A, B, C,D> GenericLambdaNode func3(@Nullable String outputPort, Function3<A, B, C,D> inlinedBody) {
        return GenericLambdaNode.make(
            options(outputPort),
            1,
            inlinedBody
        );
    }
    static <P1,P2,P3,P4,R> GenericLambdaNode func4(@Nullable String outputPort, Function4<P1,P2,P3,P4,R> inlinedBody) {
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
            .flatOutputPredicate(ShouldFlatOutputPredicate.BOXED)
            .build();
    }
}
