package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.metafactory;

import lombok.SneakyThrows;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LookupFinder {
    public static final Map<Module, MethodHandles.Lookup> lookups = Collections.synchronizedMap(new WeakHashMap<>());
    public static final AtomicLong idx = new AtomicLong();

    public static MethodHandles.Lookup lookup(){
        return MethodHandles.lookup();
    }

    @SneakyThrows
    static MethodHandles.Lookup findFullPrivilegeAccessLookup(Class<?> owner, MethodHandles.Lookup caller) {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, caller);
        if(lookup.hasFullPrivilegeAccess()) return lookup;
        MethodHandles.Lookup moduleLookup = lookups.computeIfAbsent(owner.getModule(), aClass -> doMagicToFindLookup(lookup, idx.getAndIncrement()));
        return MethodHandles.privateLookupIn(owner,moduleLookup);
    }

    @SneakyThrows
    private static MethodHandles.Lookup doMagicToFindLookup(MethodHandles.Lookup lookup, final long idx) {
        ClassReader reader = new ClassReader(LookupFinder.class.getName());
        ClassWriter writer = new ClassWriter(reader, 0);
        String packageName = lookup.lookupClass().getPackageName().replace('.','/');
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if(!name.startsWith("<") && !name.equals("lookup"))return null;
                if(name.equals("<clinit>")){
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor.visitInsn(Opcodes.RETURN);
                    methodVisitor.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, packageName + "/LookupFinder$" + idx, signature, superName, interfaces);
            }
        }, 0);
        Method method = lookup.defineClass(writer.toByteArray()).getDeclaredMethod("lookup");
        method.setAccessible(true);
        return (MethodHandles.Lookup) method.invoke(null);
    }
}
