package com.rieno.gadgetsandgizmos.graph.compile.util;

import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraph;
import com.rieno.gadgetsandgizmos.graph.compile.annotations.StateHolder;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.Objects;

public class GeneratorHelper extends GeneratorAdapter {


    public final ClassNode declaredNode;
    public final MethodNode node;
    public final Object2ObjectMap<String, PortVarEntry> portVariables = new Object2ObjectOpenHashMap<>();
    public final Object2ObjectMap<String, VarEntry> variables = new Object2ObjectOpenHashMap<>();
    public final Object2ObjectMap<String, UnboundStateField> fields = new Object2ObjectOpenHashMap<>();
    private final Label startLabel = new Label();
    private final Label endLabel = new Label();

    public GeneratorHelper(ClassNode declaredNode, MethodNode node) {
        super(Opcodes.ASM9, node, node.access, node.name, node.desc);
        this.declaredNode = declaredNode;
        this.node = node;
        visitCode();
        visitLabel(startLabel);
    }

    @Override
    public void visitEnd() {
        node.visitLabel(endLabel);
        super.visitEnd();
    }

    public void pushNull() {
        mv.visitInsn(Opcodes.ACONST_NULL);
    }


    //region if


    public void ifTrue(Label label) {
        super.ifZCmp(Opcodes.IFNE, label);
    }

    public void ifFalse(Label label) {
        super.ifZCmp(Opcodes.IFEQ, label);
    }
    public void ifPlainEquals(Type type,Label label) {
        super.ifCmp(type,Opcodes.IFEQ, label);
    }
    public void objectEquals(boolean nullable) {
        invoke(nullable ? Handle.NULLABLE_OBJECT_EQUALS : Handle.NOT_NULL_OBJECT_EQUALS);
    }

    //endregion

    //region invoke
    public void invokeVirtual(Type owner, String name, Type returnType, Type... arguments) {
        invokeVirtual(owner, new Method(name, Type.getMethodDescriptor(returnType, arguments)));
    }

    public void invokeStatic(Type owner, String name, Type returnType, Type... arguments) {
        invokeStatic(owner, new Method(name, Type.getMethodDescriptor(returnType, arguments)));
    }

    public void invokeConstructor(Type owner, Type... arguments) {
        invokeConstructor(owner, new Method("<init>", Type.getMethodDescriptor(Type.VOID_TYPE, arguments)));
    }


    public void storeField(String owner, FieldNode field) {
        boolean isStatic = (field.access & Opcodes.ACC_STATIC) != 0;
        visitFieldInsn(
            isStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD,
            owner,
            field.name,
            field.desc
        );
    }
    //endregion

    //region reflection


    public void invoke(java.lang.reflect.Method method) {
        invoke(this, method);
    }

    public static void invoke(MethodVisitor mv, java.lang.reflect.Method method) {
        invoke(mv, method, Type.getMethodDescriptor(method), method.getName());
    }

    public void invoke(Constructor<?> method) {
        invoke(this, method);
    }

    public static void invoke(MethodVisitor mv, Constructor<?> method) {
        invoke(mv, method, Type.getConstructorDescriptor(method), "<init>");
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

    public void get(java.lang.reflect.Field field) {
        Class<?> declaringClass = field.getDeclaringClass();
        int opcode = Modifier.isStatic(field.getModifiers()) ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
        visitFieldInsn(
            opcode,
            Type.getInternalName(declaringClass),
            field.getName(),
            Type.getDescriptor(field.getType())
        );
    }

    public void set(java.lang.reflect.Field field) {
        Class<?> declaringClass = field.getDeclaringClass();
        int opcode = Modifier.isStatic(field.getModifiers()) ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
        visitFieldInsn(
            opcode + 1,
            Type.getInternalName(declaringClass),
            field.getName(),
            Type.getDescriptor(field.getType())
        );
    }

    //endregion


    //region just locals
    public void loadArg(String arg) {
        var entry = variables.get(arg);
        loadInsn(entry.type, entry.index);
    }

    public void loadLocal(String arg) {
        var entry = variables.get(arg);
        Objects.requireNonNull(entry, () -> "Could find local '" + arg + "' in method '" + node.name + "' of '" + declaredNode.name + "' class");
        loadInsn(entry.type, entry.index);
    }

    public void storeArg(String arg) {
        var entry = variables.get(arg);
        storeInsn(entry.type, entry.index);
    }

    public void storeLocal(String arg) {
        var entry = variables.get(arg);
        storeInsn(entry.type, entry.index);
    }

    public void iinc(String arg, int amount) {
        super.iinc(variables.get(arg).index, amount);
    }

    /**
     * Generates the instruction to push a local variable on the stack.
     *
     * @param type  the type of the local variable to be loaded.
     * @param index an index in the frame's local variables array.
     */
    private void loadInsn(final Type type, final int index) {
        mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), index);
    }

    /**
     * Generates the instruction to store the top stack value in a local variable.
     *
     * @param type  the type of the local variable to be stored.
     * @param index an index in the frame's local variables array.
     */
    private void storeInsn(final Type type, final int index) {
        mv.visitVarInsn(type.getOpcode(Opcodes.ISTORE), index);
    }

    public void nameArg(int argNumber, String name) {
        int argIndex = getArgIndex(argNumber);
        Type type = getArgumentTypes()[argNumber];
        variables.put(name, new VarEntry(name, argIndex, type));
        mv.visitLocalVariable(
            name, type.getDescriptor(), null,
            startLabel, endLabel, argIndex
        );
    }

    public void nameLocal(int localI, String name, Type type) {
        variables.put(name, new VarEntry(name, localI, type));
        mv.visitLocalVariable(
            name, type.getDescriptor(), null,
            startLabel, endLabel, localI
        );
    }

    public void newLocal(String name, Type type) {
        nameLocal(newLocal(type), name, type);
    }
    //endregion

    //region port variables
    public PortVarEntry localOrNew(String name, ValueType type) {
        PortVarEntry entry = findEntry(name, type);
        if(entry != null) return entry;
        int i = newLocal(type.innerType);
        PortVarEntry value = new PortVarEntry(i, type, type.innerType);
        String key = entryInsertPos[0] < 0 ? name : name + "$" + entryInsertPos[0];
        portVariables.put(key, value);
        mv.visitLocalVariable(
            name, type.getDescriptor(), null,
            startLabel, endLabel, value.index()
        );
        return value;
    }

    public final int[] entryInsertPos = {-1};


    public void loadLocal(PortVarEntry entry) {
        loadInsn(entry.realType, entry.index);
    }

    public void storeLocal(PortVarEntry entry) {
        storeInsn(entry.realType, entry.index);
    }

    public PortVarEntry findEntry(String name, ValueType type) {
        entryInsertPos[0] = -1;
        return findEntry(name, type, entryInsertPos);
    }

    public PortVarEntry findEntry(String name, ValueType type, int @Nullable [] insertPos) {
        PortVarEntry entry = portVariables.get(name);
        if(entry == null) return null;
        if(entry.type.equals(type)) return entry;

        for(int i = 0; ; i++) {
            entry = portVariables.get(name + "__" + i);
            if(insertPos != null) insertPos[0] = i;
            if(entry == null) return null;
            if(entry.type.equals(type)) {
                return entry;
            }

        }
    }

    public PortVarEntry localOrNew(SnapNode node, int portIndex) {
        return localOrNew("var_" + node.id + "_" + portIndex, node.portTypes[portIndex]);
    }

    public PortVarEntry findEntry(SnapNode node, int portIndex) {
        return findEntry("var_" + node.id + "_" + portIndex, node.portTypes[portIndex]);
    }

    private int getArgIndex(final int arg) {
        int index = (getAccess() & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        Type[] argumentTypes = getArgumentTypes();
        for(int i = 0; i < arg; i++) {
            index += argumentTypes[i].getSize();
        }
        return index;
    }

    public boolean hasLocal(String name) {
        return variables.containsKey(name);
    }


    public record PortVarEntry(int index, ValueType type, Type realType) {}
    //endregion


    //region stateFields

    public void loadStateField(UnboundStateField entry, int nodeId) {
        loadStateField(entry.bind(nodeId));
    }

    public void loadStateField(BoundStateField entry) {
        Type owner = loadStateHolder();
        visitFieldInsn(
            Opcodes.GETFIELD,
            owner.getInternalName(),
            entry.name,
            entry.type.getDescriptor()
        );
    }

    public void storeField(UnboundStateField entry, int nodeId) {
        storeField(entry.bind(nodeId));
    }

    public void storeField(BoundStateField entry) {
        Type owner = loadStateHolder();
        visitFieldInsn(
            Opcodes.PUTFIELD,
            owner.getInternalName(),
            entry.name,
            entry.type.getDescriptor()
        );
    }

    public Type loadStateHolder() {
        Type type = Type.getType(AbstractJVMGraph.class);
        if(declaredNode.superName.equals(type.getInternalName())) {
            loadThis();
            return Type.getObjectType(declaredNode.name);
        }
        String stateHolderAnnoDesc = Type.getDescriptor(StateHolder.class);
        for(FieldNode field : declaredNode.fields) {
            for(AnnotationNode annotation : field.visibleAnnotations) {
                if(annotation.desc.equals(stateHolderAnnoDesc)) {
                    loadThis();
                    visitFieldInsn(
                        Opcodes.GETFIELD,
                        declaredNode.name,
                        field.name,
                        field.desc
                    );
                    return Type.getType(field.desc);
                }
            }
        }
        throw new RuntimeException("Could find '" + type.getClassName() + "' field in " + declaredNode.name);

    }

    private @NotNull String nodeFieldName(SnapNode sourceNode, String fieldName) {
        return "field_" + sourceNode.id + "$" + fieldName;
    }

    public record VarEntry(String name, int index, Type type) {}
    //endregion
}
