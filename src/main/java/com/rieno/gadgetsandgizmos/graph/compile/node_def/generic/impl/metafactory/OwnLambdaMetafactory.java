package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.metafactory;

import com.rieno.gadgetsandgizmos.graph.compile.util.HandleExtractor;
import lombok.SneakyThrows;
import org.objectweb.asm.Handle;

import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @see java.lang.invoke.LambdaMetafactory#metafactory(MethodHandles.Lookup, String, MethodType, MethodType, MethodHandle, MethodType)
 */
public class OwnLambdaMetafactory {
    public static final Handle myMetaFactory = HandleExtractor.
        <MethodHandles.Lookup, String, MethodType, MethodType, MethodType, Integer, String, String, MethodType, CallSite>
        getMethod(OwnLambdaMetafactory::metafactory);
    public static final Handle originalMetaFactory = HandleExtractor.getMethod(LambdaMetafactory::metafactory);


    @SneakyThrows
    public static CallSite metafactory(MethodHandles.Lookup caller,
                                       String interfaceMethodName,
                                       MethodType factoryType,
                                       MethodType interfaceMethodType,
                                       MethodType dynamicMethodType,
                                       int tag,
                                       String ownerClassName,
                                       String name,
                                       MethodType desc
    )
        throws LambdaConversionException, ClassNotFoundException, IllegalAccessException {

        Class<?> owner = Class.forName(ownerClassName);
        var lookup = LookupFinder.findFullPrivilegeAccessLookup(owner, caller);

        MethodHandle impl;
        if(name.equals("<init>")) {
            Constructor<?> constructor = owner.getDeclaredConstructor(desc.parameterArray());
            constructor.setAccessible(true);
            impl = lookup.unreflectConstructor(constructor);
        } else {
            Method found = owner.getDeclaredMethod(name, desc.parameterArray());
            found.setAccessible(true);
            impl = lookup.unreflect(found);
        }
        return LambdaMetafactory.metafactory(lookup, interfaceMethodName, factoryType, interfaceMethodType, impl, dynamicMethodType);

    }


}
