package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraphCompilerTest;
import com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry;
import com.rieno.gadgetsandgizmos.graph.compile.TestTypeRegister;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenericNodeType_ContextTest extends AbstractJVMGraphCompilerTest {

    @Test
    void test() {
        double v=2;
        JVMRegistry.register("ab2",DefHelper.binary("c",(Double a,Double b) -> {
            return a*b*v;
        }));
        test(1,2,"ab2",4);
        test(2,2,"ab2",8);
    }

    @Test
    void testWithThis() {
        double v=2;
        JVMRegistry.register("ab2",DefHelper.numberBin((double a,double b) -> {
            return processABV(a,b,v);
        }));
        test(1,2,"ab2",4);
        test(2,2,"ab2",8);
    }

    double processABV(double a,double b,double v){
        return a*b*v;
    }

    public void test(double a, double b, String type, double c) {
        super.simpleTest(a, b, type, c, type);
    }
}