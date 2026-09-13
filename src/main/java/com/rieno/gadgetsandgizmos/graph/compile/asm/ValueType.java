package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * TODO not an enum
 *
 * @see GraphRuntime#convertValue(AdvancedGraphDocument.Value, String)
 *
 */
public class ValueType {

    public final ResourceLocation name;
    public final Type innerType;
    private final InsnList defaultValueMaker;

    public ValueType(ResourceLocation name, Class<?> innerType, AbstractInsnNode AbstractInsnNode_first, AbstractInsnNode... defaultValueInsn) {
        this.innerType = Type.getType(innerType);
        this.name = name;
        var nodes = new InsnList();
        nodes.add(AbstractInsnNode_first);
        for(AbstractInsnNode node : defaultValueInsn) nodes.add(node);
        this.defaultValueMaker = nodes;
    }

    ValueType(String name, Class<?> innerType, AbstractInsnNode AbstractInsnNode_first, AbstractInsnNode... defaultValueInsn) {
        this(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, name), innerType, AbstractInsnNode_first, defaultValueInsn);
    }

    protected final Int2ObjectMap<InsnList> convertNodes = new Int2ObjectOpenHashMap<>();
    private static int staticId = 0;
    private final int id = staticId++;

    public static ValueType byName(ResourceLocation resource) {

    }

    public void convertViaOpcode(ValueType other, int opcode) {
        setConvertExpression(other, new InsnNode(opcode));
    }

    public void convertViaStaticMethod(ValueType other, Class<?> methodOwner, String methodName) {
        setConvertExpression(other, new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(methodOwner),
            methodName, Type.getMethodDescriptor(other.innerType, innerType),
            methodOwner.isInterface()
        ));
    }

    public void convertViaInstanceMethod(ValueType other, String methodName) {
        boolean anInterface = false;
        try {
            anInterface = Class.forName(innerType.getClassName()).isInterface();
        } catch(ClassNotFoundException e) {
        }
        setConvertExpression(other, new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            innerType.getInternalName(),
            methodName, Type.getMethodDescriptor(other.innerType),
            //innerType.isInterface()
            anInterface
        ));
    }

    public void setConvertExpression(ValueType other, AbstractInsnNode... nodes) {
        InsnList value = new InsnList();
        for(AbstractInsnNode node : nodes) value.add(node);
        convertNodes.put(other.id, value);
    }


    public void convertTo(MethodVisitor mv, ValueType other) {
        if(id == other.id || other==ValueTypes.ANY) return;
        var nodes = convertNodes.get(other.id);

        if(nodes != null) {
            nodes.accept(mv);
            return;

        }
        mv.visitInsn(Opcodes.POP + getSize() - 1);
        other.defaultValueMaker.accept(mv);
        /*if(other == ValueTypes.VALUE) return;
        var toValueNodes = convertNodes.get(ValueTypes.VALUE.id);
        if(tryConvertThrowValue(mv, other, toValueNodes)) {
            mv.visitInsn(Opcodes.POP + getSize() - 1);
            other.defaultValue().accept(mv);
            return;
        }*/
        return;
    }

    private boolean tryConvertThrowValue(MethodVisitor mv, ValueType other, InsnList toValueNodes) {
        if(toValueNodes == null) return false;
        var valueToOther = ValueTypes.VALUE.convertNodes.get(other.id);
        if(valueToOther == null) return false;
        toValueNodes.accept(mv);
        valueToOther.accept(mv);
        return true;
    }

    public void wrapToValue(MethodVisitor mv) {
        convertTo(mv, ValueTypes.VALUE);
    }

    public int getSize() {
        return innerType.getSize();
    }

    public String getDescriptor() {
        return innerType.getDescriptor();
    }

    @Getter
    private boolean cannotBeSaved = false;

    public ValueType unsavable() {
        cannotBeSaved = true;
        return this;
    }

    @Override
    public String toString() {
        return "ValueType(" + name + ")";
    }
}
