package com.rieno.gadgetsandgizmos.graph.compile.util;

import com.rieno.gadgetsandgizmos.graph.compile.asm.FieldInitExpr;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import lombok.*;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@With
@ToString
@Accessors(fluent = true)//record like getters
@Getter
public final class UnboundStateField {
    final String name;
    final Type type;
    @Nullable
    final FieldInitExpr initExpression;

    public static UnboundStateField make(String name, Class<?> type, FieldInitExpr initExpression) {
        return new UnboundStateField(name, Type.getType(type), initExpression);
    }

    public static UnboundStateField make(String name, Type type, FieldInitExpr initExpression) {
        return new UnboundStateField(name, type, initExpression);
    }

    public BoundStateField bind(int nodeId) {
        return new BoundStateField(nodeId, name, type);
    }

    public BoundStateField bind(SnapNode sourceNode) {
        return bind(sourceNode.id);
    }

}
