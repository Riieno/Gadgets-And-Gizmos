package com.rieno.gadgetsandgizmos.graph.compile.util.helper;

import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.metafactory.PrivateAccMetafactory;
import com.rieno.gadgetsandgizmos.graph.compile.util.CompileUtil;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;

public class InvokePrivateHelper {
    public static void privateField(MethodVisitor mv, FieldInsnNode fieldInsnNode) {
        privateField(mv, fieldInsnNode.getOpcode(), fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc);
    }

    public static void privateField(MethodVisitor mv, int opcode, String owner, String fieldName, String desc) {
        mv.visitInvokeDynamicInsn(
            "privateField_" + fieldName,
            //Type.getMethodDescriptor(Type.getType(desc),owner),
            Type.getMethodDescriptor(Type.getType(desc), CompileUtil.javaLangObject),
            PrivateAccMetafactory.field,
            owner,
            fieldName,
            opcode
        );
    }

    public static void privateMethod(MethodVisitor mv, CallerCtx callerCtx, Method method) {
        if(Modifier.isPublic(method.getModifiers()) && Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            InsnAdapter.invoke(mv, method);
            return;
        }
        privateMethod(
            mv,
            callerCtx,
            method.getDeclaringClass(),
            Modifier.isStatic(method.getModifiers()),
            method.getName(),
            method.getReturnType(),
            method.getParameterTypes()
        );
    }

    public static void privateMethod(MethodVisitor mv, MethodInsnNode method) {
        privateMethod(
            mv,
            Type.getObjectType(method.owner),
            method.getOpcode() == Opcodes.INVOKESTATIC,
            method.name,
            Type.getReturnType(method.desc),
            Type.getArgumentTypes(method.desc)
        );
    }

    public static void privateGetRecordField(MethodVisitor mv, CallerCtx callerCtx, Class<?> recordType, String fieldName) {
        if(!recordType.isRecord())
            throw new IllegalArgumentException("'%s' is not a record".formatted(recordType.getName()));
        RecordComponent found = null;
        for(RecordComponent component : recordType.getRecordComponents()) {
            if(component.getName().equals(fieldName)) {
                found = component;
                break;
            }
        }
        if(found == null)
            throw new IllegalArgumentException("No record component with name '%s' for class '%s'".formatted(fieldName, recordType));
        privateMethod(mv, callerCtx, found.getAccessor());
    }

    public static void privateMethod(MethodVisitor mv, CallerCtx callerCtx, Class<?> owner, boolean isStatic, String methodName, Class<?> returnType, Class<?>... arguments) {
        Type[] args;
        var nonHiddenReturnType = publicOrObjectType(returnType, callerCtx);
        var nonHiddenArgs = publicOrObjectType(arguments, callerCtx);
        privateMethod(mv, owner.getName(), isStatic, methodName, nonHiddenArgs, nonHiddenReturnType, classes(returnType, arguments));
    }

    private static String @NotNull [] classes(Class<?> returnType, Class<?>[] arguments) {
        String[] classes = new String[arguments.length + 1];
        int i = 0;
        for(Class<?> aClass : ArrayUtils.add(arguments, returnType)) {
            classes[i++] = aClass.getName();
        }
        return classes;
    }

    public static void privateMethod(MethodVisitor mv, Type owner, boolean isStatic, String methodName, Type returnType, Type... arguments) {
        String[] classes = new String[arguments.length + 1];
        int i = 0;
        for(var aClass : ArrayUtils.add(arguments, returnType)) {
            classes[i++] = aClass.getClassName();
        }
        privateMethod(mv, owner.getClassName(), isStatic, methodName, arguments, returnType, classes);
    }

    private static void privateMethod(MethodVisitor mv, String ownerType, boolean isStatic, String methodName, Type[] nonHiddenArgs, Type nonHiddenReturnType, String[] orderedClassesFromDescriptor) {
        Type[] args;
        if(isStatic) {
            args = nonHiddenArgs;
        } else {
            args = initArguments(nonHiddenArgs, 1);
            args[0] = CompileUtil.javaLangObject;
        }


        mv.visitInvokeDynamicInsn(
            "privateMethod_" + methodName,
            //Type.getMethodDescriptor(Type.getType(desc),owner),
            Type.getMethodDescriptor(nonHiddenReturnType, args),
            PrivateAccMetafactory.method,
            ownerType,
            methodName,
            String.join(";", orderedClassesFromDescriptor)
        );
    }

    private static Type @NotNull [] initArguments(Type[] arguments, int offset) {
        Type[] args;
        args = new Type[arguments.length + offset];
        System.arraycopy(arguments, 0, args, offset, arguments.length);
        return args;
    }

    private static Type publicOrObjectType(Class<?> type, CallerCtx callerCtx) {
        return isPrivateOrHidden(type, callerCtx) ? CompileUtil.javaLangObject : Type.getType(type);
    }

    public static boolean isPrivateOrHidden(Class<?> type, CallerCtx callerCtx) {
        return type.isHidden() || Modifier.isPrivate(type.getModifiers()) || !callerCtx.isAccessible(type);
    }

    private static Type[] publicOrObjectType(Class<?>[] types, CallerCtx callerCtx) {
        var newTypes = new Type[types.length];
        for(int i = 0; i < types.length; i++) {
            newTypes[i] = publicOrObjectType(types[i], callerCtx);
        }
        return newTypes;
    }

    public static void privateConstructor(MethodVisitor mv, CallerCtx callerCtx, Constructor<?> ctor) {
        Class<?> ownerClass = ctor.getDeclaringClass();
        String methodDescriptor = Type.getMethodDescriptor(publicOrObjectType(ownerClass, callerCtx), publicOrObjectType(ctor.getParameterTypes(), callerCtx));

        mv.visitInvokeDynamicInsn(
            PrivateAccMetafactory.ctor.getName(),
            methodDescriptor,
            PrivateAccMetafactory.ctor,
            ownerClass.getName(),
            String.join(";", classes(ownerClass, ctor.getParameterTypes()))
        );
    }

    public static boolean isPrivateOrHidden(CallerCtx callerCtx, Constructor<?> ctor) {
        if(isPrivateOrHidden(ctor.getDeclaringClass(), callerCtx)) return true;
        for(Class<?> param : ctor.getParameterTypes()) {
            if(isPrivateOrHidden(param, callerCtx)) return true;
        }
        return false;
    }
    public static boolean isPrivateOrHidden(CallerCtx callerCtx, Method method) {
        if(isPrivateOrHidden(method.getDeclaringClass(), callerCtx)) return true;
        if(isPrivateOrHidden(method.getReturnType(), callerCtx)) return true;
        for(Class<?> param : method.getParameterTypes()) {
            if(isPrivateOrHidden(param, callerCtx)) return true;
        }
        return false;
    }

    public static record CallerCtx(
        @Nullable String packageName,
        @Nullable Class<?> caller
    ) {

        public static CallerCtx make(@NonNull Class<?> caller) {
            return new CallerCtx(caller.getPackageName(), caller);
        }

        public static CallerCtx make(@NonNull String packageName) {
            return new CallerCtx(packageName, null);
        }

        public static CallerCtx make() {
            return new CallerCtx(null,null);
        }

        public boolean isAccessible(Class<?> type) {
            if(Modifier.isPublic(type.getModifiers())) return true;

            if(caller != null) {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(caller, MethodHandles.lookup());
                    lookup.findClass(type.getName());
                    return true;
                } catch(IllegalAccessException | ClassNotFoundException e) {
                }
                if(type.getName().startsWith(caller.getName())) return true;
            } else if(packageName == null) {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.lookup();
                    lookup.findClass(type.getName());
                    return true;
                } catch(IllegalAccessException | ClassNotFoundException e) {
                    return false;
                }
            }
            int mask = Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED;
            if((type.getModifiers() & mask) == 0 && type.getPackageName().equals(packageName)) {
                return true;
            }
            return false;
        }
    }
}
