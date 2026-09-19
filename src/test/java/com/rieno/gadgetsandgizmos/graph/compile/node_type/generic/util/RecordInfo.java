package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.util;

import com.google.gson.internal.reflect.ReflectionHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.ClassNodeUtil;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.SneakyThrows;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Set;
import java.util.function.Supplier;

public class RecordInfo {
    public final Type type;
    public final Object2ObjectArrayMap<String, Type> fieldMap = new Object2ObjectArrayMap<>();
    public final Lazy<Object2ObjectMap<String, MethodNode>> methodMap;
    public final Constructor<?> canonicalCtor;

    public MethodNode findMethod(String name, String methodDesc, boolean checkReturnType) {
        MethodNode node = methodMap.get().get(methodKey(name, methodDesc));
        if(checkReturnType && node != null && !node.desc.equals(methodDesc)) return null;
        return node;
    }

    private @NotNull String methodKey(String name, String methodDesc) {
        return name + methodDesc.substring(0, methodDesc.lastIndexOf(')'));
    }

    @SneakyThrows
    public RecordInfo(Type myType) {
        this.type = myType;
        Class<?> recordClass = Class.forName(myType.getClassName());

        for(RecordComponent component : recordClass.getRecordComponents()) {
            fieldMap.put(component.getName(), Type.getType(component.getType()));
        }
        methodMap = Lazy.of(methodMapSup(ClassNodeUtil.getClassNodeOrNull(recordClass.getName())));
        canonicalCtor= ReflectionHelper.getCanonicalRecordConstructor(recordClass);


    }

    private @NotNull Supplier<Object2ObjectMap<String, MethodNode>> methodMapSup(ClassNode classNode) {
        if(classNode == null)return Object2ObjectMaps::emptyMap;
        return () -> {
            var map = new Object2ObjectOpenHashMap<String, MethodNode>();
            for(MethodNode method : classNode.methods) {
                map.put(methodKey(method.name, method.desc), method);
            }
            return map;
        };
    }

    public static RecordInfo make(Type type) {
        return new RecordInfo(type);
    }

    public Set<String> fieldNamesAsSet() {
        return fieldMap.keySet();
    }
}
