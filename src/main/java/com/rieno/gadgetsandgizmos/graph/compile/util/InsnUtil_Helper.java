package com.rieno.gadgetsandgizmos.graph.compile.util;

import org.intellij.lang.annotations.MagicConstant;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InsnUtil_Helper {
    @MagicConstant(valuesFromClass = Type.class)
    public static int opcodeToSort(@MagicConstant(valuesFromClass = Opcodes.class) int opcode) {
        return switch(opcode) {
            case Opcodes.ILOAD -> Type.INT;
            case Opcodes.LLOAD -> Type.LONG;
            case Opcodes.FLOAD -> Type.FLOAT;
            case Opcodes.DLOAD -> Type.DOUBLE;
            case Opcodes.ALOAD -> Type.OBJECT;

            case Opcodes.IALOAD -> Type.INT;
            case Opcodes.LALOAD -> Type.LONG;
            case Opcodes.FALOAD -> Type.FLOAT;
            case Opcodes.DALOAD -> Type.DOUBLE;
            case Opcodes.AALOAD -> Type.OBJECT;
            case Opcodes.BALOAD -> Type.BYTE;
            case Opcodes.CALOAD -> Type.CHAR;
            case Opcodes.SALOAD -> Type.SHORT;

            case Opcodes.ISTORE -> Type.INT;
            case Opcodes.LSTORE -> Type.LONG;
            case Opcodes.FSTORE -> Type.FLOAT;
            case Opcodes.DSTORE -> Type.DOUBLE;
            case Opcodes.ASTORE -> Type.OBJECT;

            case Opcodes.IASTORE -> Type.INT;
            case Opcodes.LASTORE -> Type.LONG;
            case Opcodes.FASTORE -> Type.FLOAT;
            case Opcodes.DASTORE -> Type.DOUBLE;
            case Opcodes.AASTORE -> Type.OBJECT;
            case Opcodes.BASTORE -> Type.BYTE;
            case Opcodes.CASTORE -> Type.CHAR;
            case Opcodes.SASTORE -> Type.SHORT;

            case Opcodes.IADD -> Type.INT;
            case Opcodes.LADD -> Type.LONG;
            case Opcodes.FADD -> Type.FLOAT;
            case Opcodes.DADD -> Type.DOUBLE;

            case Opcodes.ISUB -> Type.INT;
            case Opcodes.LSUB -> Type.LONG;
            case Opcodes.FSUB -> Type.FLOAT;
            case Opcodes.DSUB -> Type.DOUBLE;

            case Opcodes.IMUL -> Type.INT;
            case Opcodes.LMUL -> Type.LONG;
            case Opcodes.FMUL -> Type.FLOAT;
            case Opcodes.DMUL -> Type.DOUBLE;

            case Opcodes.IDIV -> Type.INT;
            case Opcodes.LDIV -> Type.LONG;
            case Opcodes.FDIV -> Type.FLOAT;
            case Opcodes.DDIV -> Type.DOUBLE;

            case Opcodes.IREM -> Type.INT;
            case Opcodes.LREM -> Type.LONG;
            case Opcodes.FREM -> Type.FLOAT;
            case Opcodes.DREM -> Type.DOUBLE;

            case Opcodes.INEG -> Type.INT;
            case Opcodes.LNEG -> Type.LONG;
            case Opcodes.FNEG -> Type.FLOAT;
            case Opcodes.DNEG -> Type.DOUBLE;

            case Opcodes.ISHL -> Type.INT;
            case Opcodes.LSHL -> Type.LONG;
            case Opcodes.ISHR -> Type.INT;
            case Opcodes.LSHR -> Type.LONG;

            case Opcodes.IUSHR -> Type.INT;
            case Opcodes.LUSHR -> Type.LONG;

            case Opcodes.IAND -> Type.INT;
            case Opcodes.LAND -> Type.LONG;

            case Opcodes.IOR -> Type.INT;
            case Opcodes.LOR -> Type.LONG;

            case Opcodes.IXOR -> Type.INT;
            case Opcodes.LXOR -> Type.LONG;


            case Opcodes.LCMP -> Type.LONG;
            case Opcodes.FCMPL -> Type.FLOAT;
            case Opcodes.FCMPG -> Type.FLOAT;
            case Opcodes.DCMPL -> Type.DOUBLE;
            case Opcodes.DCMPG -> Type.DOUBLE;

            case Opcodes.IFEQ -> Type.BOOLEAN;
            case Opcodes.IFNE -> Type.BOOLEAN;
            case Opcodes.IFLT -> Type.BOOLEAN;
            case Opcodes.IFGE -> Type.BOOLEAN;
            case Opcodes.IFGT -> Type.BOOLEAN;
            case Opcodes.IFLE -> Type.BOOLEAN;

            case Opcodes.IF_ICMPEQ -> Type.INT;
            case Opcodes.IF_ICMPNE -> Type.INT;
            case Opcodes.IF_ICMPLT -> Type.INT;
            case Opcodes.IF_ICMPGE -> Type.INT;
            case Opcodes.IF_ICMPGT -> Type.INT;
            case Opcodes.IF_ICMPLE -> Type.INT;
            case Opcodes.IF_ACMPEQ -> Type.OBJECT;
            case Opcodes.IF_ACMPNE -> Type.OBJECT;

            case Opcodes.IRETURN -> Type.INT;
            case Opcodes.LRETURN -> Type.LONG;
            case Opcodes.FRETURN -> Type.FLOAT;
            case Opcodes.DRETURN -> Type.DOUBLE;
            case Opcodes.ARETURN -> Type.OBJECT;
            case Opcodes.RETURN -> Type.VOID;
            default -> -1;
        };
    }
}
