package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.graph.compile.util.BoxingTool;
import org.objectweb.asm.Type;

@FunctionalInterface
public interface ShouldFlatInputPredicate {
    ShouldFlatInputPredicate NO_ONE = (type, argumentTypes, argIndex) -> false;
    ShouldFlatInputPredicate RECORD = (type, argumentTypes, argIndex) -> type.isRecord();
    ShouldFlatInputPredicate PRIMITIVE_WRAPPERS = (type, argumentTypes, argIndex) -> BoxingTool.getUnboxedType(type) != null;
    ShouldFlatInputPredicate RECORD_OR_PRIMITIVE_WRAPPERS = RECORD.or(PRIMITIVE_WRAPPERS);

    boolean test(Class<?> type, Type[] argumentTypes, int argIndex);

    default ShouldFlatInputPredicate not(){
        return (type, argumentTypes, argIndex) -> !test(type, argumentTypes, argIndex);
    }
    default ShouldFlatInputPredicate or(ShouldFlatInputPredicate predicate){
        //if(this==NO_ONE)return predicate;
        //if(predicate==NO_ONE)return this;
        return (type, argumentTypes, argIndex) -> test(type, argumentTypes, argIndex) || predicate.test(type, argumentTypes, argIndex);
    }
    default ShouldFlatInputPredicate and(ShouldFlatInputPredicate predicate){
        //if(this==NO_ONE || predicate==NO_ONE)return NO_ONE;
        return (type, argumentTypes, argIndex) -> test(type, argumentTypes, argIndex) && predicate.test(type, argumentTypes, argIndex);
    }
}
