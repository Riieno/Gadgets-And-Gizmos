package com.rieno.gadgetsandgizmos.graph.type;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.graph.compile.util.InsnAdapter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

/**
 * TODO not an enum
 *
 * @see GraphRuntime#convertValue(AdvancedGraphDocument.Value, String)
 *
 */
public class ValueType<T> implements ConvertHelpers<T>{

    public final ResourceLocation name;
    public final Type innerType;
    private final InsnList defaultValueMaker;
    private static final Object2ObjectMap<ResourceLocation,ValueType<?>> allTypes=new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<Class<?>,ValueType<?>> allTypesByClass=new Object2ObjectOpenHashMap<>();
    private final Class<?> innerClass;

    public ValueType(ResourceLocation name, Class<?> innerType, AbstractInsnNode AbstractInsnNode_first, AbstractInsnNode... defaultValueInsn) {
        this.innerType = Type.getType(innerType);
        this.innerClass=innerType;
        this.name = name;
        var nodes = new InsnList();
        nodes.add(AbstractInsnNode_first);
        for(AbstractInsnNode node : defaultValueInsn) nodes.add(node);
        this.defaultValueMaker = nodes;
        synchronized(ValueType.class){
            allTypes.put(name,this);
        }
    }
    public ValueType<T> defaultForInnerType(){
        allTypesByClass.put(innerClass,this);
        return this;
    }

    ValueType(String name, Class<?> innerType, AbstractInsnNode AbstractInsnNode_first, AbstractInsnNode... defaultValueInsn) {
        this(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, name), innerType, AbstractInsnNode_first, defaultValueInsn);
    }

    protected final Int2ObjectMap<InsnList> convertNodes = new Int2ObjectOpenHashMap<>();
    private static int staticId = 0;
    private final int id = staticId++;

    public static ValueType<?> byName(ResourceLocation resource) {
        return allTypes.get(resource);
    }
    public static ValueType<?> byClass(Class<?> type, boolean allowSuperTypes) {
        Class<?> curType = type;
        do{
            ValueType<?> valueType = allTypes.get(curType);
            if(valueType != null || !allowSuperTypes) return valueType;
            curType=curType.getSuperclass();
        }while(curType != null);
        for(ValueType<?> value : allTypes.values()) {
            if(value.innerClass.isAssignableFrom(type)) {
                return value;
            }
        }
        return null;
    }

    public void setConvertExpression(ValueType<?> other, AbstractInsnNode... nodes) {
        InsnList value = new InsnList();
        InsnAdapter.LabelCloner clonedLabels = InsnAdapter.labelCloner();
        for(AbstractInsnNode node : nodes) value.add(node.clone(clonedLabels));
        value.toArray();
        convertNodes.put(other.id, value);
    }


    public void convertTo(MethodVisitor mv, ValueType<?> other) {
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

    private boolean tryConvertThrowValue(MethodVisitor mv, ValueType<?> other, InsnList toValueNodes) {
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

    public ValueType<T> unsavable() {
        cannotBeSaved = true;
        return this;
    }

    @Override
    public String toString() {
        return "ValueType(" + name + ")";
    }

}
