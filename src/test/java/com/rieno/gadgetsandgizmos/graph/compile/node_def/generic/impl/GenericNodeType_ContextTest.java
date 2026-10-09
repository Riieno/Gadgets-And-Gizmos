package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraphCompilerTest;
import com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry;
import com.rieno.gadgetsandgizmos.graph.compile.TestTypeRegister;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenericNodeType_ContextTest extends AbstractJVMGraphCompilerTest {

    static {
        TestTypeRegister.register();
    }


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
    }

    @Test
    void test() {
        double v=2;
        JVMRegistry.register("xy2",DefHelper.binary("c",(Double x,Double y) -> {
            return x*y*v;
        }));
        test(1,2,"xy2",4);
        test(2,2,"xy2",8);
    }

    public void test(double a, double b, String type, double c) {
        super.simpleTest(a, b, type, c, type);
    }
}