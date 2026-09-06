package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import lombok.RequiredArgsConstructor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public enum ValueType {
    NUMBER(double.class) {
        @Override
        public AbstractInsnNode convertTo(ValueType other) {
            return switch(other) {
                case NUMBER -> null;
                case BOOL -> new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(ValueType.class),
                    "num2bool", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.DOUBLE_TYPE),
                    false
                );
                case STRING -> new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(Double.class),
                    "toString", Type.getMethodDescriptor(Type.getType(String.class), Type.DOUBLE_TYPE),
                    false
                );
                case VALUE -> new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(AdvancedGraphDocument.Value.class),
                    "number", Type.getMethodDescriptor(Type.getType(AdvancedGraphDocument.Value.class), Type.DOUBLE_TYPE),
                    false
                );
            };
        }
    },
    BOOL(boolean.class) {
        @Override
        public AbstractInsnNode convertTo(ValueType other) {
            return switch(other) {
                case NUMBER -> new InsnNode(Opcodes.I2D);
                case BOOL -> null;
                case STRING -> new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(Boolean.class),
                    "toString", Type.getMethodDescriptor(Type.getType(String.class), Type.BOOLEAN_TYPE),
                    false
                );
                case VALUE -> new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(AdvancedGraphDocument.Value.class),
                    "bool", Type.getMethodDescriptor(Type.getType(AdvancedGraphDocument.Value.class), Type.BOOLEAN_TYPE),
                    false
                );
            };
        }
    },
    STRING (String.class){
        @Override
        public AbstractInsnNode convertTo(ValueType other) {
            return switch(other) {
                case NUMBER -> new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(ValueType.class),
                    "str2num", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(String.class)),
                    false
                );
                case BOOL -> new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(ValueType.class),
                    "str2bool", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(String.class)),
                    false
                );
                case STRING -> null;
                case VALUE -> new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(AdvancedGraphDocument.Value.class),
                    "string", Type.getMethodDescriptor(Type.getType(AdvancedGraphDocument.Value.class), Type.getType(String.class)),
                    false
                );
            };
        }
    },
    VALUE(AdvancedGraphDocument.Value.class) {

        private final Type type = Type.getType(AdvancedGraphDocument.Value.class);

        @Override
        public AbstractInsnNode convertTo(ValueType other) {
            return switch(other) {
                case NUMBER -> new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    type.getInternalName(),
                    "asNumber", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, type),
                    false
                );
                case BOOL -> new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    type.getInternalName(),
                    "asBoolean", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, type),
                    false
                );

                case STRING -> new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    type.getInternalName(),
                    "asString", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, type),
                    false
                );

                case VALUE -> null;
            };
        }
    },
    ;

    public final Type innerType;

    ValueType(Class<?> innerType) {
        this.innerType = Type.getType(innerType);
    }

    public static boolean num2bool(double x) {return x != 0;}

    public static boolean str2bool(String x) {return x.equals("true");}

    public static double str2num(String x) {
        try {
            return Double.parseDouble(x);
        } catch(NumberFormatException e) {
            return 0;
        }
    }
    protected final AbstractInsnNode[] convertNode=new AbstractInsnNode[4];
    public final static ValueType[] all=values();
    public final int id=ordinal();
    static {
        for(ValueType type : all) {
            for(ValueType subtype : all) {
                type.convertNode[subtype.id]=type.convertTo(subtype);
            }
        }
    }

    protected abstract AbstractInsnNode convertTo(ValueType other);

    public void convertTo(MethodVisitor mv, ValueType valueType) {
        convertNode[valueType.id].accept(mv);
    }

    public int getSize() {
        return innerType.getSize();
    }

    public String getDescriptor() {
        return innerType.getDescriptor();
    }
}
