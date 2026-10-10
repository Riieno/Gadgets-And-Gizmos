package com.rieno.gadgetsandgizmos.graph.compile;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.function.Predicate;

public class TestSink {
    static StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    public static void consume(Object obj) {

    }

    public static StackWalker.StackFrame currentTestFrame() {
        return walker.walk(stackFrameStream -> stackFrameStream.filter(
            new Predicate<StackWalker.StackFrame>() {
                @Override
                @SneakyThrows
                public boolean test(StackWalker.StackFrame it) {
                    Method method = it.getDeclaringClass().getDeclaredMethod(it.getMethodName(), it.getMethodType().parameterArray());
                    return method.getAnnotation(Test.class) != null;
                }
            }
        ).toList()).getLast();
    }
}
