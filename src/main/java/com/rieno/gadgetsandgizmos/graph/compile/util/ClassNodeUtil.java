package com.rieno.gadgetsandgizmos.graph.compile.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.ServiceNotAvailableError;

import java.io.IOException;
import java.lang.reflect.Field;

public class ClassNodeUtil {
    public static ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        ClassNode classNode;
        int options = ClassReader.SKIP_FRAMES;
        try {

            Field field = MixinService.class.getDeclaredField("instance");
            field.setAccessible(true);
            Object o = field.get(null);
            if(o == null) throw new NoSuchFieldException();
            classNode = MixinService.getService().getBytecodeProvider().getClassNode(name, true, options);
        } catch(ServiceNotAvailableError | NoSuchFieldException | IllegalAccessException er) {
            new ClassReader(name).accept(classNode = new ClassNode(), options);
        }
        return classNode;
    }
}
