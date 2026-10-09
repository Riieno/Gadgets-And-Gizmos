package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraphCompilerTest;
import com.rieno.gadgetsandgizmos.graph.compile.TestSink;
import com.rieno.gadgetsandgizmos.graph.compile.TestTypeRegister;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenericNodeType_AccessTest extends AbstractJVMGraphCompilerTest {

    static {
        TestTypeRegister.register();
    }


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
    }

    @Test
    void nonPublicRecordButNoUsage() {
        record Num2(double a,double b){}
        DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            return new Out(Math.pow(tmp, nums.b));
        });
    }
    @Test
    void nonPublicInput() {
        Assertions.assertThrows(NonPublicReference.class,()->{
            record Num2(double a,double b){}
            DefHelper.unary((Num2 nums) -> {
                double tmp = Math.pow(nums.a, nums.b);
                TestSink.consume(nums);
                return new Out(Math.pow(tmp, nums.b));
            });
        });
    }
    @Test
    void nonPublicInside() {
        Assertions.assertThrows(NonPublicReference.class,()->{
            record Num2(double a,double b){}
            DefHelper.unary((Num2 nums) -> {
                double tmp = Math.pow(nums.a, nums.b);
                TestSink.consume(new Num2(nums.a,nums.b));
                return new Out(Math.pow(tmp, nums.b));
            });
        });
    }

    public record Out(double c) {}
}