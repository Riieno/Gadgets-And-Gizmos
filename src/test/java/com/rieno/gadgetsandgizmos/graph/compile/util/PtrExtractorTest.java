package com.rieno.gadgetsandgizmos.graph.compile.util;

import net.minecraft.Util;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

class PtrExtractorTest implements Opcodes {

    @Test
    void tryExtractMethod() {
        assertEquals(handle(H_INVOKESTATIC, Integer.class, "bitCount", "(I)I", false), HandleExtractor.getMethod(Integer::bitCount));
        assertEquals(handle(H_INVOKEVIRTUAL, Integer.class, "byteValue", "()B", false), HandleExtractor.getMethod(Integer::byteValue));
        assertEquals(handle(H_INVOKESTATIC, Integer.class, "toBinaryString", "(I)Ljava/lang/String;", false), HandleExtractor.getMethod(Integer::toBinaryString));
        assertEquals(handle(H_INVOKEINTERFACE, Map.class, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true), HandleExtractor.<Map,Object,Object>getMethod(Map::get));
        assertEquals(handle(H_INVOKESTATIC, Map.class, "of", "()Ljava/util/Map;", true), HandleExtractor.<Map>getMethod(Map::of));
    }

    final static String[] tags = Util.make(() -> {
        int max = Opcodes.H_INVOKEINTERFACE;
        String[] strings = new String[max + 1];
        for(Field field : Opcodes.class.getDeclaredFields()) {
            if(!field.getName().startsWith("H_")) continue;
            try {
                int tag = field.getInt(null);
                strings[tag] = field.getName();
            } catch(IllegalAccessException e) {

            }
        }

        return strings;

    });

    static void assertEquals(Handle expected, Handle actual) {
        try {
            Assertions.assertEquals(tags[expected.getTag()], tags[actual.getTag()],"tag mismatch");
            Assertions.assertEquals(expected.getOwner(),actual.getOwner(),"owner mismatch");
            Assertions.assertEquals(expected.getName(),actual.getName(),"name mismatch");
            Assertions.assertEquals(expected.getDesc(),actual.getDesc(),"desc mismatch");
            Assertions.assertEquals(expected.isInterface(),actual.isInterface(),"isInterface mismatch");
        } catch(AnnotationFormatError e) {
            StackTraceElement[] stackTrace = e.getStackTrace();
            int index=0;
            for(int i = 0; i < stackTrace.length; i++) {

                if(stackTrace[i].getMethodName().equals("assertEquals")) {
                    index=i+1;
                    break;
                }
            }
            e.setStackTrace(Arrays.copyOfRange(stackTrace,index+1,stackTrace.length-1));
            throw e;
        }
    }

    private @NotNull Handle handle(int tag, Class<?> owner, String name, String desc, boolean isInterface) {
        return new Handle(tag, Type.getInternalName(owner), name, desc, isInterface);
    }
}