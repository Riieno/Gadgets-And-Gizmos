package com.rieno.gadgetsandgizmos.graph.compile.util;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.objectweb.asm.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.lang.invoke.LambdaMetafactory;

public class PtrExtractor {
    static StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static Handle tryExtractMethod(int callerDepth) {
        var frames = walker.walk(it -> it.skip(callerDepth ).limit(2).toList());
        if(frames.size() < 2) return null;
        var frame = frames.get(1);
        var frameNext = frames.get(0);
        try {

            String searchDesc = frame.getMethodType().descriptorString();
            String searchName = frame.getMethodName();
            int searchByte = frame.getByteCodeIndex();

            int[] lastOffset = {0};
            MyMethodNode[] foundMethod = {null};
            new ClassReader(ClassNodeUtil.getClassBytes(frame.getClassName())) {
                @Override
                protected void readBytecodeInstructionOffset(int bytecodeOffset) {
                    lastOffset[0] = bytecodeOffset;
                }
            }.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if(name.equals(searchName) && descriptor.equals(searchDesc)) {
                        return foundMethod[0] = new MyMethodNode(access, name, descriptor, signature, exceptions, lastOffset);
                    }
                    return null;
                }
            }, 0);
            MyMethodNode found = foundMethod[0];
            if(found == null) return null;
            int foundInsn = found.bciToInsn.getOrDefault(searchByte, -1);
            if(foundInsn == -1) return null;
            AbstractInsnNode[] array = found.instructions.toArray();
            AbstractInsnNode node = array[foundInsn];
            if(!(node instanceof MethodInsnNode insnNode) || !matchesFrame(insnNode, frameNext)) return null;
            var prev = prevInsn(node);
            if(!(prev instanceof InvokeDynamicInsnNode dynamicInsnNode)) return null;
            if(!isLambdaMetafactory(dynamicInsnNode.bsm))return null;
            return findHandle(dynamicInsnNode.bsmArgs);


        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Handle findHandle(Object[] args) {
        for(Object arg : args) {
            if(arg instanceof Handle handle) {
                return handle;
            }
        }
        return null;
    }

    private static boolean isLambdaMetafactory(Handle bsm) {
        return bsm.getOwner().equals(Type.getInternalName(LambdaMetafactory.class)) && bsm.getName().equals("metafactory");
    }

    private static boolean matchesFrame(MethodInsnNode insnNode, StackWalker.StackFrame frameNext) {
        if(!insnNode.name.equals(frameNext.getMethodName()) || !insnNode.desc.equals(frameNext.getDescriptor())) {
            return false;
        }
        String ownerName = insnNode.owner.replace('/', '.');
        if(ownerName.equals(frameNext.getClassName())) {
            return true;
        }
        try {
            Class<?> classA = Class.forName(frameNext.getClassName());
            Class<?> classB = Class.forName(ownerName);
            return classA.isAssignableFrom(classB) || classB.isAssignableFrom(classA);
        } catch(ClassNotFoundException e) {
            return false;
        }
    }

    private static AbstractInsnNode prevInsn(AbstractInsnNode node) {
        do {
            node = node.getPrevious();
        } while(node != null && node.getOpcode() == -1);
        return node;
    }

    public static FieldNode tryExtractField() {
        ;
        ;
        throw null;
    }

    private static class MyMethodNode extends MethodNode {
        public IntArrayList offsets = new IntArrayList();
        public Int2IntMap bciToInsn = new Int2IntOpenHashMap();

        public MyMethodNode(int access, String name, String descriptor, String signature, String[] exceptions, int[] lastOffset) {
            super(Opcodes.ASM9, access, name, descriptor, signature, exceptions);
            instructions = new InsnList() {
                @Override
                public void add(AbstractInsnNode insnNode) {
                    int size = size();
                    super.add(insnNode);
                    int offset = lastOffset[0];
                    offsets.add(offset);
                    bciToInsn.put(offset, size);
                }
            };
        }

    }
}
