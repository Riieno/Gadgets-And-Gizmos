package com.rieno.gadgetsandgizmos.graph.compile.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class BoxingTool {
    public static final Map<String, Entry> wrapperInternalName2Entry = Map.ofEntries(
        typeEntry(Boolean.class, Boolean::booleanValue),
        typeEntry(Byte.class, Byte::byteValue),
        typeEntry(Character.class, Character::charValue),
        typeEntry(Short.class, Short::shortValue),
        typeEntry(Integer.class, Integer::intValue),
        typeEntry(Long.class, Long::longValue),
        typeEntry(Float.class, Float::floatValue),
        typeEntry(Double.class, Double::doubleValue)
    );
    public static final Map<String, Entry> primitiveToEntry;
    static {
        ObjectArrayList<Entry> entries = new ObjectArrayList<>(wrapperInternalName2Entry.values());
        Object2ObjectOpenHashMap<String, Entry> map = new Object2ObjectOpenHashMap<>();
        for(Entry entry : entries) {
            map.put(entry.unboxed.getClassName(),entry);
        }
        primitiveToEntry = Object2ObjectMaps.unmodifiable(map);
    }

    @Nullable
    public static Entry boxingEntryForPrimitive(Type type) {return primitiveToEntry.get(type.getClassName());}
    public static Entry boxingEntryForPrimitive(Class<?> type) {return primitiveToEntry.get(type.getName());}
    @Nullable
    public static Entry boxingEntry(Type type) {return wrapperInternalName2Entry.get(type.getInternalName());}

    @Nullable
    public static Entry boxingEntry(String internalName) {return wrapperInternalName2Entry.get(internalName);}

    @Nullable
    public static Entry boxingEntry(Class<?> type) {return wrapperInternalName2Entry.get(type.getName().replace('.', '/'));}

    public record Entry(Type boxed, Type unboxed, Handle boxingMethod, Handle unboxingMethod) {}

    public static boolean isBoxingUnboxing(MethodInsnNode call) {
        return classifyBoxingOrUnboxing(call) != 0;
    }

    public static final int NOTHING = 0;
    public static final int BOXING = 1;
    public static final int UNBOXING = 2;

    @MagicConstant(valuesFromClass = BoxingTool.class)
    public static int classifyBoxingOrUnboxing(MethodInsnNode call) {
        var entry = boxingEntry(call.owner);
        if(entry == null) return BoxingTool.NOTHING;
        if(call.name.equals(entry.unboxingMethod.getName())) return BoxingTool.UNBOXING;
        if(call.name.equals("valueOf") && call.desc.equals(entry.boxingMethod.getDesc())) return BoxingTool.BOXING;
        return BoxingTool.NOTHING;
    }

    public static Type getUnboxedType(Type boxedType) {
        if(boxedType.getSort() != Type.OBJECT) return null;
        return getUnboxedType(boxedType.getInternalName());
    }

    public static @Nullable Type getUnboxedType(Class<?> type) {
        return getUnboxedType(type.getName().replace('.', '/'));
    }

    public static @Nullable Type getUnboxedType(String internalName) {
        return switch(internalName) {
            case "java/lang/Boolean" -> Type.BOOLEAN_TYPE;
            case "java/lang/Character" -> Type.CHAR_TYPE;
            case "java/lang/Byte" -> Type.BYTE_TYPE;
            case "java/lang/Short" -> Type.SHORT_TYPE;
            case "java/lang/Integer" -> Type.INT_TYPE;
            case "java/lang/Float" -> Type.FLOAT_TYPE;
            case "java/lang/Long" -> Type.LONG_TYPE;
            case "java/lang/Double" -> Type.DOUBLE_TYPE;
            default -> null;
        };
    }

    private static String getUnboxingMethod(String owner) {
        switch(owner) {
            case "java/lang/Boolean":
                return "booleanValue";
            case "java/lang/Character":
                return "charValue";
            case "java/lang/Byte":
                return "byteValue";
            case "java/lang/Short":
                return "shortValue";
            case "java/lang/Integer":
                return "intValue";
            case "java/lang/Float":
                return "floatValue";
            case "java/lang/Long":
                return "longValue";
            case "java/lang/Double":
                return "doubleValue";
        }
        return null;
    }

    public static <T> Map.Entry<String, Entry> typeEntry(Class<T> type, Function<T, Object> unwrapperFunc) {
        Handle unwrapper = Objects.requireNonNull(PtrExtractor.tryExtractMethod(1));
        Type boxed = Type.getType(type);
        Type unboxed = Type.getReturnType(unwrapper.getDesc());
        return Map.entry(boxed.getInternalName(), new Entry(
                boxed,
                unboxed,
                new Handle(Opcodes.H_INVOKESTATIC, boxed.getInternalName(), "valueOf", Type.getMethodDescriptor(boxed, unboxed), false),
                unwrapper
            )
        );
    }
}
