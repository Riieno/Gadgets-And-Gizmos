package com.rieno.gadgetsandgizmos.graph.compile.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.Map;

public class InsnAdapter {

    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final int SINGLE_TYPE_SIZE_2 = 0;
    private static final int SINGLE_TYPE_SIZE_1 = 1;
    private static final int MULTI_TYPE = 2;

    public static void newarray(final MethodVisitor methodVisitor, final Type type) {
        int arrayType;
        switch (type.getSort()) {
            case Type.BOOLEAN:
                arrayType = Opcodes.T_BOOLEAN;
                break;
            case Type.CHAR:
                arrayType = Opcodes.T_CHAR;
                break;
            case Type.BYTE:
                arrayType = Opcodes.T_BYTE;
                break;
            case Type.SHORT:
                arrayType = Opcodes.T_SHORT;
                break;
            case Type.INT:
                arrayType = Opcodes.T_INT;
                break;
            case Type.FLOAT:
                arrayType = Opcodes.T_FLOAT;
                break;
            case Type.LONG:
                arrayType = Opcodes.T_LONG;
                break;
            case Type.DOUBLE:
                arrayType = Opcodes.T_DOUBLE;
                break;
            default:
                methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, type.getInternalName());
                return;
        }
        methodVisitor.visitIntInsn(Opcodes.NEWARRAY, arrayType);
    }

    public static void box(MethodVisitor mv, Type type) {
        switch(type.getSort()) {
            case Type.BYTE -> invoke(mv, Handle.method(() -> Byte.class.getDeclaredMethod("valueOf", byte.class)));
            case Type.SHORT -> invoke(mv, Handle.method(() -> Short.class.getDeclaredMethod("valueOf", short.class)));
            case Type.INT -> invoke(mv, Handle.method(() -> Integer.class.getDeclaredMethod("valueOf", int.class)));
            case Type.LONG -> invoke(mv, Handle.method(() -> Long.class.getDeclaredMethod("valueOf", long.class)));

            case Type.FLOAT -> invoke(mv, Handle.method(() -> Float.class.getDeclaredMethod("valueOf", float.class)));
            case Type.DOUBLE ->
                invoke(mv, Handle.method(() -> Double.class.getDeclaredMethod("valueOf", double.class)));

            case Type.BOOLEAN ->
                invoke(mv, Handle.method(() -> Boolean.class.getDeclaredMethod("valueOf", boolean.class)));
        }
    }

    public static void unbox(MethodVisitor mv, Type type) {
        switch(type.getSort()) {
            case Type.BYTE -> invoke(mv, Handle.method(() -> Byte.class.getDeclaredMethod("byteValue")));
            case Type.SHORT -> invoke(mv, Handle.method(() -> Short.class.getDeclaredMethod("shortValue")));
            case Type.INT -> invoke(mv, Handle.method(() -> Integer.class.getDeclaredMethod("intValue")));
            case Type.LONG -> invoke(mv, Handle.method(() -> Long.class.getDeclaredMethod("longValue")));

            case Type.FLOAT -> invoke(mv, Handle.method(() -> Float.class.getDeclaredMethod("floatValue")));
            case Type.DOUBLE -> invoke(mv, Handle.method(() -> Double.class.getDeclaredMethod("doubleValue")));

            case Type.BOOLEAN -> invoke(mv, Handle.method(() -> Boolean.class.getDeclaredMethod("booleanValue")));
        }
    }

    public static void invoke(MethodVisitor mv, java.lang.reflect.Method method) {
        invoke(mv, method, Type.getMethodDescriptor(method), method.getName());
    }

    public static void invoke(MethodVisitor mv, Constructor<?> method) {
        invoke(mv, method, Type.getConstructorDescriptor(method), "<init>");
    }

    public static void invoke(MethodVisitor mv, org.objectweb.asm.Handle method) {
        boolean isInterface = method.isInterface();
        var owner = method.getOwner();
        var name = method.getName();
        var desc = method.getDesc();
        switch(method.getTag()) {
            case Opcodes.H_INVOKEVIRTUAL -> mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner,name,desc,isInterface);
            case Opcodes.H_INVOKESTATIC -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner,name,desc,isInterface);
            case Opcodes.H_INVOKESPECIAL -> mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner,name,desc,isInterface);
            case Opcodes.H_NEWINVOKESPECIAL -> {
                int argumentCount = Type.getArgumentCount(desc);
                Type[] argumentTypes = Type.getArgumentTypes(desc);
                if(argumentCount==0){
                    mv.visitTypeInsn(Opcodes.NEW,owner);
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner,name,desc,isInterface);
                } else if(argumentCount == 1) {
                    mv.visitTypeInsn(Opcodes.NEW,owner);

                    if(argumentTypes[0].getSize() == 2) {
                        mv.visitInsn(Opcodes.DUP_X2);
                        swap_top1_bottom2(mv);
                    } else {
                        mv.visitInsn(Opcodes.DUP_X1);
                        mv.visitInsn(Opcodes.SWAP);
                    }
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner,name,desc,isInterface);
                }else if(argumentCount==2 && argumentTypes[0].getSize()==1 && argumentTypes[1].getSize()==1){
                    mv.visitTypeInsn(Opcodes.NEW,owner);
                    mv.visitInsn(Opcodes.DUP_X2);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner,name,desc,isInterface);
                }else{
                    int variant = storeArgumentsAsArr(mv, argumentTypes);
                    mv.visitTypeInsn(Opcodes.NEW,owner);
                    mv.visitInsn(Opcodes.DUP_X1);
                    mv.visitInsn(Opcodes.SWAP);

                    loadArgumentsFromArr(mv, argumentTypes, variant);
                }
            }
            case Opcodes.H_INVOKEINTERFACE -> mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner,name,desc,isInterface);
        }
    }

    public static void loadArgumentsFromArr(
        MethodVisitor mv,
        Type[] argumentTypes,
        @MagicConstant(intValues = {SINGLE_TYPE_SIZE_1,SINGLE_TYPE_SIZE_2,MULTI_TYPE}) int variant
    ) {
        Type sample = argumentTypes[0];
        int sampleLoadInsn=sample.getOpcode(Opcodes.IALOAD);
        switch(variant) {
            case InsnAdapter.SINGLE_TYPE_SIZE_1 -> {
                for(int i = 0; i < argumentTypes.length; i++) {
                    mv.visitInsn(Opcodes.DUP);//[arr,arr]
                    push(mv,i);//[arr,arr,index]
                    mv.visitInsn(sampleLoadInsn);//[arr,value]
                    mv.visitInsn(Opcodes.SWAP);//[value,arr]
                }

            }
            case InsnAdapter.SINGLE_TYPE_SIZE_2 -> {
                for(int i = 0; i < argumentTypes.length; i++) {
                    mv.visitInsn(Opcodes.DUP);//[arr,arr]
                    push(mv,i);//[arr,arr,index]
                    mv.visitInsn(sampleLoadInsn);//[arr,value_0,value_1]
                    swap_top2_bottom1(mv);//[value_0,value_1,arr]
                }
            }
            case InsnAdapter.MULTI_TYPE -> {
                for(int i = 0; i < argumentTypes.length; i++) {
                    Type type = argumentTypes[i];
                    mv.visitInsn(Opcodes.DUP);//[arr,arr]
                    push(mv,i);//[arr,arr,index]
                    mv.visitInsn(Opcodes.AALOAD);//[arr,boxed]
                    unbox(mv,type);
                    if(type.getSize()==2){//[arr,unboxed_0,unboxed_1]
                        swap_top2_bottom1(mv);//[unboxed_0,unboxed_1,arr]
                    }else{//[arr,unboxed]
                        mv.visitInsn(Opcodes.SWAP);//[unboxed, arr]
                    }
                }
            }
        }
        mv.visitInsn(Opcodes.POP);
    }

    @MagicConstant(intValues = {SINGLE_TYPE_SIZE_1,SINGLE_TYPE_SIZE_2,MULTI_TYPE})
    public static int storeArgumentsAsArr(MethodVisitor mv, Type[] argumentTypes) {
        int variant;
        boolean differentTypes=false;
        for(int i = 1; i < argumentTypes.length; i++) {
            if(!argumentTypes[i].equals(argumentTypes[0])) {
                differentTypes=true;
                break;
            }
        }
        push(mv, argumentTypes.length);
        if(!differentTypes) {
            Type sample = argumentTypes[0];
            newarray(mv, sample);
            int arrayStore=sample.getOpcode(Opcodes.IASTORE);
            if(sample.getSize() == 2) {
                variant= SINGLE_TYPE_SIZE_2;
                for(int i = 0; i < argumentTypes.length; i++) {
                    //[...,num0_0,num0_1,arr]
                    mv.visitInsn(Opcodes.DUP_X2);//[...,arr, num0_0,num0_1, arr]
                    mv.visitInsn(Opcodes.DUP_X2);//[...,arr, arr, num0_0,num0_1, arr]
                    mv.visitInsn(Opcodes.POP);//[...,arr, arr, num0_0,num0_1]
                    push(mv,i);//[...,arr, arr, num0_0,num0_1, index]
                    swap_top1_bottom2(mv);
                    mv.visitInsn(arrayStore);//[..., arr]
                }
            }else{
                variant=SINGLE_TYPE_SIZE_1;
                for(int i = 0; i < argumentTypes.length; i++) {
                    mv.visitInsn(Opcodes.DUP_X1);
                    mv.visitInsn(Opcodes.SWAP);
                    push(mv,i);
                    mv.visitInsn(Opcodes.SWAP);
                    mv.visitInsn(arrayStore);
                }
            }
        }else{
            variant=MULTI_TYPE;
            newarray(mv, OBJECT_TYPE);
            for(int i = argumentTypes.length - 1; i >= 0; i--) {
                Type argumentType = argumentTypes[i];
                if(argumentType.getSize() == 2){
                    mv.visitInsn(Opcodes.DUP_X2);
                    swap_top1_bottom2(mv);
                }else{
                    mv.visitInsn(Opcodes.DUP_X1);
                    mv.visitInsn(Opcodes.SWAP);
                }

                box(mv, argumentType);
                push(mv,i);
                mv.visitInsn(Opcodes.SWAP);
                mv.visitInsn(Opcodes.AASTORE);
            }
        }
        return variant;
    }

    /**
     * [.., value_0,value_1, other] -> [.., other, value_0,value_1]
     * */
    public static void swap_top1_bottom2(MethodVisitor mv) {
        mv.visitInsn(Opcodes.DUP_X2);
        mv.visitInsn(Opcodes.POP);
    }
    /**
     * [.., other, value_0,value_1] -> [.., value_0,value_1, other]
     * */
    public static void swap_top2_bottom1(MethodVisitor mv) {
        mv.visitInsn(Opcodes.DUP2_X1);
        mv.visitInsn(Opcodes.POP2);
    }

    public static void push(MethodVisitor mv, final int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    private static void invoke(MethodVisitor mv, Executable method, String methodDescriptor, String name) {

        Class<?> declaringClass = method.getDeclaringClass();
        boolean isInterface = declaringClass.isInterface();
        int opcode = Modifier.isStatic(method.getModifiers()) ? Opcodes.INVOKESTATIC :
            Modifier.isPrivate(method.getModifiers()) || method instanceof Constructor<?> ? Opcodes.INVOKESPECIAL :
                isInterface ? Opcodes.INVOKEINTERFACE :
                    Opcodes.INVOKEVIRTUAL;

        mv.visitMethodInsn(
            opcode,
            Type.getInternalName(declaringClass),
            name,

            methodDescriptor,
            isInterface


        );
    }

    /**
     * @see org.objectweb.asm.tree.AbstractInsnNode#clone(Map)
     * */
    public static @NotNull InsnAdapter.LabelCloner labelCloner() {
        return new LabelCloner();
    }

    /**
     * @see org.objectweb.asm.tree.AbstractInsnNode#clone(Map)
     * */
    public static class LabelCloner extends Object2ObjectOpenHashMap<LabelNode, LabelNode> {
        @Override
        public LabelNode get(Object k) {
            LabelNode labelNode = super.get(k);
            if(labelNode==null)put((LabelNode) k,labelNode=new LabelNode());
            return labelNode;
        }

        public InsnList clone(InsnList oldList, boolean doCleanup) {
            InsnList newList = new InsnList();
            for(AbstractInsnNode insnNode : oldList) {
                newList.add(insnNode.clone(this));
            }
            if(doCleanup) clear();
            return newList;

        }
    }
}
