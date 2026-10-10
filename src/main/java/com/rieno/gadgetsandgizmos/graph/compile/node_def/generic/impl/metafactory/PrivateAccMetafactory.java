package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.metafactory;

import com.rieno.gadgetsandgizmos.graph.compile.util.HandleExtractor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

public class PrivateAccMetafactory {
    public static final Handle ctor = HandleExtractor.getMethod(PrivateAccMetafactory::privateCtor);
    public static final Handle field = HandleExtractor.getMethod(PrivateAccMetafactory::privateField);
    public static final Handle method = HandleExtractor.getMethod(PrivateAccMetafactory::privateMethod);
    public static final Map<String, WeakReference<Class<?>>> hiddenClassNameToClass = new ConcurrentHashMap<>();

    @SneakyThrows
    public static CallSite privateCtor(MethodHandles.Lookup caller, String methodName, MethodType invokeDesc, String ownerClassName, String rawDesc) {
        Class<?> owner = findClass(ownerClassName);
        var lookup = LookupFinder.findFullPrivilegeAccessLookup(owner, caller);
        Constructor<?> constructor = owner.getDeclaredConstructor(unpackMethodArguments(rawDesc));
        constructor.setAccessible(true);
        return new ConstantCallSite(lookup.unreflectConstructor(constructor));
    }

    @SneakyThrows
    public static CallSite privateField(MethodHandles.Lookup caller, String methodName, MethodType invokeDesc, String ownerClassName, String name, int originalOpcode) {
        Class<?> owner = findClass(ownerClassName);
        var lookup = LookupFinder.findFullPrivilegeAccessLookup(owner, caller);
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        MethodHandle handle = switch(originalOpcode) {
            case Opcodes.PUTSTATIC,
                 Opcodes.PUTFIELD -> lookup.unreflectSetter(field);
            case Opcodes.GETFIELD,
                 Opcodes.GETSTATIC -> lookup.unreflectGetter(field);
            default -> throw new IllegalStateException("Unexpected value: " + originalOpcode);
        };

        //if(originalOpcode>Opcodes.PUTSTATIC){
        //}
        handle = handle.asType(invokeDesc);
        return new ConstantCallSite(handle);
    }

    @SneakyThrows
    public static CallSite privateMethod(MethodHandles.Lookup caller, String methodName, MethodType invokeDesc, String ownerClassName, String name, String rawDesc) {
        Class<?> owner = findClass(ownerClassName);
        var lookup = LookupFinder.findFullPrivilegeAccessLookup(owner, caller);
        Method found = owner.getDeclaredMethod(name, unpackMethodArguments(rawDesc));
        found.setAccessible(true);
        return new ConstantCallSite(lookup.unreflect(found));
    }


    private static Class<?> findClass(String ownerClassName) throws ClassNotFoundException {
        Class<?> hiddenClass = findHiddenClass(ownerClassName);
        if(hiddenClass != null) return hiddenClass;
        return Class.forName(ownerClassName);

    }

    private static @Nullable Class<?> findHiddenClass(String ownerClassName) {
        WeakReference<Class<?>> reference = hiddenClassNameToClass.computeIfPresent(ownerClassName, (s, classWeakReference) -> {
            if(classWeakReference.get() == null) return null;
            return classWeakReference;
        });
        if(reference == null) return null;
        Class<?> aClass = reference.get();
        if(aClass == null) {
            hiddenClassNameToClass.remove(ownerClassName);
            return null;
        }
        return aClass;
    }

    public static void hiddenClass(Class<?> hiddenClass) {
        hiddenClassNameToClass.put(hiddenClass.getName(), new WeakReference<>(hiddenClass));
    }

    private static Class<?>[] unpackMethodArguments(String rawDesc) throws ClassNotFoundException {
        StringTokenizer tokenizer = new StringTokenizer(rawDesc, ";");
        int amountOf = tokenizer.countTokens();
        if(amountOf == 0) throw new IllegalArgumentException("Excepted array of classes but found nothing");
        Class<?>[] parameterTypes = new Class[amountOf - 1];
        tokenizer.nextToken();
        for(int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = findClass(tokenizer.nextToken());
        }

        return parameterTypes;
    }

    private static MethodType unpackMethodDescriptor(String rawDesc) throws ClassNotFoundException {
        StringTokenizer tokenizer = new StringTokenizer(rawDesc, ";");
        int amountOf = tokenizer.countTokens();
        if(amountOf == 0) throw new IllegalArgumentException("Excepted array of classes but found nothing");
        Class<?>[] parameterTypes = new Class[amountOf - 1];
        Class<?> returnType = findClass(tokenizer.nextToken());
        for(int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = findClass(tokenizer.nextToken());
        }

        return MethodType.methodType(returnType, parameterTypes);
    }
}
