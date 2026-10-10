package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraphCompilerTest;
import com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry;
import com.rieno.gadgetsandgizmos.graph.compile.TestSink;
import com.rieno.gadgetsandgizmos.graph.compile.TestTypeRegister;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenericNodeType_AccessTest extends AbstractJVMGraphCompilerTest {



    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
        JVMRegistry.instance.entries.remove("test");
    }

    @Test
    void nonPublicRecordButNoUsage() {
        record Num2(double a,double b){}
        JVMRegistry.register("test",DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            return new Out(Math.pow(tmp, nums.b));
        }));
        test(2,3,"test",8*8*8);
    }
    @Test
    void nonPublicInput() {
        record Num2(double a,double b){}
        JVMRegistry.register("test",DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            TestSink.consume(nums);
            return new Out(Math.pow(tmp, nums.b));
        }));
        test(2,3,"test",8*8*8);
    }
    @Test
    void nonPublicInside() {
        record Num2(double a,double b){}
        JVMRegistry.register("test",DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            TestSink.consume(new Num2(nums.a,nums.b));
            return new Out(Math.pow(tmp, nums.b));
        }));
        test(2,3,"test",8*8*8);
    }

    record Out(double c) {}


    public void test(double a, double b, String type, double c) {
        StackWalker.StackFrame frame = TestSink.currentTestFrame();
        super.simpleTest(a, b, type, c, frame.getMethodName());
    }

}