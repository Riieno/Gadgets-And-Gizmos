package com.rieno.gadgetsandgizmos.graph.type;

import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import com.rieno.gadgetsandgizmos.graph.compile.util.InsnAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

public class InternalLambdaStorage {
    private static Function<?, ?>[] array;
    private static final Method getMethod = Handle.method(() -> InternalLambdaStorage.class.getDeclaredMethod("get", int.class));
    private static final Method invokeFunction = Handle.method(() -> Function.class.getDeclaredMethod("apply", Object.class));

    public static Function<?, ?> get(int index) {
        return array[index];
    }

    static int add(Function<?, ?> lambda) {
        Function<?, ?>[] functions = Arrays.copyOf(array, array.length + 1);
        functions[array.length] = lambda;
        array = functions;
        return functions.length - 1;
    }

    static  void invokeLambda(Function<?, ?> mapper, Type inType, Type outType, MethodVisitor visitor) {
        int index = add(mapper);
        //[..,unboxedInput]
        GeneratorAdapter helper = new GeneratorAdapter(visitor,0,"nil","()V");


        InsnAdapter.box(helper, inType);//[..,boxedInput]
        helper.push(index);//[..,boxedInput,index]
        InsnAdapter.invoke(helper, getMethod);//[..,boxedInput,func]

        helper.swap();//[..,func,boxedInput]

        InsnAdapter.invoke(helper, invokeFunction);//[..,boxedOutput]

        InsnAdapter.unbox(helper, outType);//[..,unboxedOutput]


    }
}
