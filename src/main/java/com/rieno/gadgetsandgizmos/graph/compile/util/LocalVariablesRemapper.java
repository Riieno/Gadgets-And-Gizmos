package com.rieno.gadgetsandgizmos.graph.compile.util;

import org.objectweb.asm.*;

public class LocalVariablesRemapper extends MethodVisitor {

    /** The type of the java.lang.Object class. */
    private static final Type OBJECT_TYPE = Type.getObjectType("java/lang/Object");

    /**
     * Constructs a new {@link LocalVariablesRemapper}.
     *
     * @param api the ASM API version implemented by this visitor. Must be one of the {@code
     *     ASM}<i>x</i> values in {@link Opcodes}.
     * @param methodVisitor the method visitor to which this adapter delegates calls.
     */
    protected LocalVariablesRemapper(final int api, final MethodVisitor methodVisitor) {
        super(api, methodVisitor);
    }

    @Override
    public void visitVarInsn(final int opcode, final int varIndex) {
        Type varType;
        switch (opcode) {
            case Opcodes.LLOAD:
            case Opcodes.LSTORE:
                varType = Type.LONG_TYPE;
                break;
            case Opcodes.DLOAD:
            case Opcodes.DSTORE:
                varType = Type.DOUBLE_TYPE;
                break;
            case Opcodes.FLOAD:
            case Opcodes.FSTORE:
                varType = Type.FLOAT_TYPE;
                break;
            case Opcodes.ILOAD:
            case Opcodes.ISTORE:
                varType = Type.INT_TYPE;
                break;
            case Opcodes.ALOAD:
            case Opcodes.ASTORE:
            case Opcodes.RET:
                varType = OBJECT_TYPE;
                break;
            default:
                throw new IllegalArgumentException("Invalid opcode " + opcode);
        }
        super.visitVarInsn(opcode, remapLocal(varIndex, varType));
    }

    @Override
    public void visitIincInsn(final int varIndex, final int increment) {
        super.visitIincInsn(remapLocal(varIndex, Type.INT_TYPE), increment);
    }


    @Override
    public void visitLocalVariable(
        final String name,
        final String descriptor,
        final String signature,
        final Label start,
        final Label end,
        final int index) {
        int remappedIndex = remapLocal(index, Type.getType(descriptor));
        super.visitLocalVariable(name, descriptor, signature, start, end, remappedIndex);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(
        final int typeRef,
        final TypePath typePath,
        final Label[] start,
        final Label[] end,
        final int[] index,
        final String descriptor,
        final boolean visible) {
        Type type = Type.getType(descriptor);
        int[] remappedIndex = new int[index.length];
        for (int i = 0; i < remappedIndex.length; ++i) {
            remappedIndex[i] = remapLocal(index[i], type);
        }
        return super.visitLocalVariableAnnotation(
            typeRef, typePath, start, end, remappedIndex, descriptor, visible);
    }

    // -----------------------------------------------------------------------------------------------




    public int remapLocal(final int varIndex, final Type type) {

        return varIndex;
    }

}
