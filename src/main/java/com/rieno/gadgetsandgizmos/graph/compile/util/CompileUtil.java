package com.rieno.gadgetsandgizmos.graph.compile.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.SneakyThrows;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public class CompileUtil {
    private static final InsnList DUMMY = new InsnList();
    private static Int2ObjectMap<int[]> numeric2doubleConv = new Int2ObjectOpenHashMap<>();

    public static boolean isReturn(AbstractInsnNode node) {
        return isReturn(node.getOpcode());
    }

    public static Type opcode2type(@MagicConstant(valuesFromClass = Opcodes.class) int opcode) {
        int sort = InsnUtil_Helper.opcodeToSort(opcode);
        if(sort == -1) return null;
        return typeFromSort(sort, javaLangObject);
    }

    public static final Type javaLangObject = Type.getType(Object.class);

    public static Type typeFromSort(@MagicConstant(valuesFromClass = Type.class) int sort, Type object) {
        return switch(sort) {
            case Type.VOID -> Type.VOID_TYPE;
            case Type.BOOLEAN -> Type.BOOLEAN_TYPE;
            case Type.CHAR -> Type.CHAR_TYPE;
            case Type.BYTE -> Type.BYTE_TYPE;
            case Type.SHORT -> Type.SHORT_TYPE;
            case Type.INT -> Type.INT_TYPE;
            case Type.FLOAT -> Type.FLOAT_TYPE;
            case Type.LONG -> Type.LONG_TYPE;
            case Type.DOUBLE -> Type.DOUBLE_TYPE;
            case Type.OBJECT -> object;
            default -> throw new IllegalStateException("Unexpected sort: " + sort);
        };
    }

    public static Class<?> classFromSort(@MagicConstant(valuesFromClass = Type.class) int sort, Class<?> object, Class<?> array) {
        return switch(sort) {
            case Type.VOID -> void.class;
            case Type.BOOLEAN -> boolean.class;
            case Type.CHAR -> char.class;
            case Type.BYTE -> byte.class;
            case Type.SHORT -> short.class;
            case Type.INT -> int.class;
            case Type.FLOAT -> float.class;
            case Type.LONG -> long.class;
            case Type.DOUBLE -> double.class;
            case Type.OBJECT -> object;
            case Type.ARRAY -> array;
            default -> throw new IllegalStateException("Unexpected sort: " + sort);
        };
    }

    public static int int2numeric(@MagicConstant(valuesFromClass = Type.class) int sortB) {
        return switch(sortB) {
            case Type.VOID -> -1;
            case Type.BOOLEAN -> Opcodes.I2B;
            case Type.CHAR -> Opcodes.I2C;
            case Type.BYTE -> Opcodes.I2B;
            case Type.SHORT -> Opcodes.I2S;
            case Type.INT -> Opcodes.NOP;
            case Type.FLOAT -> Opcodes.I2F;
            case Type.LONG -> Opcodes.I2L;
            case Type.DOUBLE -> Opcodes.I2D;
            default -> Opcodes.NOP;
        };
    }

    public static int numeric2double(@MagicConstant(valuesFromClass = Type.class) int sortA) {
        return switch(sortA) {
            case Type.VOID -> -1;
            case Type.BOOLEAN,
                 Type.CHAR,
                 Type.BYTE,
                 Type.SHORT,
                 Type.INT -> Opcodes.I2D;
            case Type.FLOAT -> Opcodes.F2D;
            case Type.LONG -> Opcodes.L2D;
            case Type.DOUBLE -> -1;
            default -> -1;

        };
    }

    private static int defaultValueOfSort(@MagicConstant(valuesFromClass = Type.class) int sort) {
        return RealType.fromTypeSort(sort).defaultValue;
    }

    public static boolean isReturn(int opcode) {
        return Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN;
    }

    @SneakyThrows
    public static Class<?> type2class(@NotNull Type type) {
        @MagicConstant(valuesFromClass = Type.class)
        int sort = type.getSort();
        return switch(sort) {
            case Type.VOID -> void.class;
            case Type.BOOLEAN -> boolean.class;
            case Type.CHAR -> char.class;
            case Type.BYTE -> byte.class;
            case Type.SHORT -> short.class;
            case Type.INT -> int.class;
            case Type.FLOAT -> float.class;
            case Type.LONG -> long.class;
            case Type.DOUBLE -> double.class;
            default -> Class.forName(type.getClassName());
        };
    }

    public static int indexOf(AbstractInsnNode o) {
        return DUMMY.indexOf(o);
    }

}
