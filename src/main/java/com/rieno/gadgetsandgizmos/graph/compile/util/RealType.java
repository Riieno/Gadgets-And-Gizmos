package com.rieno.gadgetsandgizmos.graph.compile.util;

import lombok.AllArgsConstructor;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@AllArgsConstructor
public enum RealType {
    VOID(-1),
    INT(Opcodes.ICONST_0),
    LONG(Opcodes.LCONST_0),
    FLOAT(Opcodes.FCONST_0),
    DOUBLE(Opcodes.DCONST_0),
    OBJECT(Opcodes.ACONST_NULL)

    ;
    public final int defaultValue;

    public static RealType fromType(Type type){
        @MagicConstant(valuesFromClass = Type.class)
        int sort = type.getSort();
        return fromTypeSort(sort);
    }

    public static  RealType fromTypeSort(@MagicConstant(valuesFromClass = Type.class) int sort) {
        return switch(sort) {
            case Type.VOID -> VOID;
            case Type.BOOLEAN,
                 Type.CHAR,
                 Type.BYTE,
                 Type.SHORT,
                 Type.INT -> INT;
            case Type.FLOAT -> FLOAT;
            case Type.LONG -> LONG;
            case Type.DOUBLE -> DOUBLE;
            case Type.ARRAY,
                 Type.OBJECT,
                 Type.METHOD -> OBJECT;
            default -> throw new IllegalStateException("Unexpected value: " + sort);
        };
    }

}
