package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.util.RecordInfo;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.util.UsageInterpreter;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.util.UsageStatistics;
import com.rieno.gadgetsandgizmos.graph.compile.util.BoxingTool;
import com.rieno.gadgetsandgizmos.graph.compile.util.CompileUtil;
import lombok.*;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class IOValue {
    private final boolean isRecord;
    private final boolean isBox;
    private final Class<?> clazz;
    private final Type type;
    private final FlowValue flowValue;
    @With
    private final UsageStatistics usage;

    @Setter
    private boolean couldRemoveRecordVariables;
    //@Setter
    //private boolean flatVariable;
    private final RecordInfo recordInfo;
    @Setter
    private boolean couldRemoveBoxing;
    @Builder.Default()
    @Setter
    private IOValueVariableState variableState=IOValueVariableState.KEEP;

    private final boolean needToBeFlat;


    @SneakyThrows
    public static IOValue make(@Nullable FlowValue value, Type type, boolean needToBeFlat) {
        Class<?> clazz = CompileUtil.type2class(type);
        var usageStat = value == null ? null : UsageInterpreter.usageStat(value);
        boolean record = clazz.isRecord();
        return IOValue.builder()
            .isRecord(record)
            .isBox(BoxingTool.getUnboxedType(clazz)!=null)
            .clazz(clazz)
            .type(type)
            .flowValue(value)
            .usage(usageStat)
            .recordInfo(   record ? RecordInfo.make(type) : null)
            .needToBeFlat(needToBeFlat)
            .build();
    }


    public IOValue withValue(FlowValue value) {
        return toBuilder()
            .flowValue(value)
            .usage(value == null ? null : UsageInterpreter.usageStat(value))
            .build();

    }


}
