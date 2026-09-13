package com.rieno.gadgetsandgizmos.graph.compile.node_type.data.jvm;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class __BlockData {
     static final Object2ObjectMap<Class<?>, Object2ObjectArrayMap<String,IPropGetter>> getters=new Object2ObjectOpenHashMap<>();
     static final Object2ObjectMap<Class<?>, Object2ObjectArrayMap<String,IPropSetter>> setters=new Object2ObjectOpenHashMap<>();

     static final Object2ObjectMap<Class<?>, Object2ObjectArrayMap<String,IPropGetter>> gettersWithFilter=new Object2ObjectOpenHashMap<>();
     static final Object2ObjectMap<Class<?>, Object2ObjectArrayMap<String,IPropSetter>> settersWithFilter=new Object2ObjectOpenHashMap<>();

    public static void register(Class<?> dataClass) {

    }



    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface PropGetter {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface PropSetter {}

    //public record PortEntry(String port)

    public interface IPropGetter {}

    public interface IPropSetter {}
}
