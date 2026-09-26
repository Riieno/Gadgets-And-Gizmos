package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.impl;

import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;

import java.util.function.BiFunction;
import java.util.function.Function;

public class Tst {
    public <T> ProcessedLambda lambda(T lambda){
        return ProcessedLambda.make(
            Options.defaultOptions,
            null,
            1,
            lambda
        );
    }
    static {
        ValueTypes.afterAll();
    }
    public static void main(String[] args) {
        run();
    }

    private static void run() {
        Tst ist=new Tst();
        ProcessedLambda lambda;
        //lambda = ist.<BiFunction<Double, Double, Double>>lambda((x, y) -> x + y);
        //System.out.println(lambda);
        lambda = ist.<Function<In2, Double>>lambda(in -> in.x + in.y);
        System.out.println(lambda);
    }
    public  record In2(double x, double y){}

}
