package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.graph.compile.util.BoxingTool;

@FunctionalInterface
public interface ShouldFlatOutputPredicate {
    ShouldFlatOutputPredicate NO_ONE = (type) -> false;
    ShouldFlatOutputPredicate RECORD = Class::isRecord;
    ShouldFlatOutputPredicate BOXED = (type) -> BoxingTool.getUnboxedType(type) != null;
    ShouldFlatOutputPredicate RECORD_OR_BOXED = RECORD.or(BOXED);

    boolean test(Class<?> type);

    default ShouldFlatOutputPredicate not(){
        return (type) -> !test(type);
    }
    default ShouldFlatOutputPredicate or(ShouldFlatOutputPredicate predicate){
        //if(this==NO_ONE)return predicate;
        //if(predicate==NO_ONE)return this;
        return (type) -> test(type) || predicate.test(type);
    }
    default ShouldFlatOutputPredicate and(ShouldFlatOutputPredicate predicate){
        //if(this==NO_ONE || predicate==NO_ONE)return NO_ONE;
        return (type) -> test(type) && predicate.test(type);
    }
}
