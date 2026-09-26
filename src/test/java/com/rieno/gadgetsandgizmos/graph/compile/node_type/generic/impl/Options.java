package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.impl;

import lombok.Builder;
import lombok.NonNull;

@Builder(toBuilder = true)
public record Options(
    @NonNull
    ShouldFlatInputPredicate flatInputPredicate,
    boolean flatOutputRecord,
    boolean isPure,
    @NonNull
    String outputPortDefName
) {
    public static final Options defaultOptions = new Options(
        ShouldFlatInputPredicate.RECORD.and((type, argumentTypes, argIndex) -> argumentTypes.length == 1).or(ShouldFlatInputPredicate.PRIMITIVE_WRAPPERS),
        true,
        false,
        "value"
    );

    public static OptionsBuilder builder() {
        return defaultOptions.toBuilder();
    }

}