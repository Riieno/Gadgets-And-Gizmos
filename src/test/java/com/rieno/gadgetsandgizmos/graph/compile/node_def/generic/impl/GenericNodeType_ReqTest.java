package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraphCompilerTest;
import com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry;
import com.rieno.gadgetsandgizmos.graph.compile.TestTypeRegister;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry.register;

public class GenericNodeType_ReqTest extends AbstractJVMGraphCompilerTest {
    public static DoubleList list=DoubleList.of(1,2,3,4);
    static {

    }


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
        JVMRegistry.instance.entries.remove("test");
    }

    @Test
    void allowOuterStaticVar() {
        register("test", DefHelper.binary("c",(Double a,Double b) ->
            list.getDouble((int) (a + b))
        ));
        simpleTest(1,2,"test",4,"allowOuterStaticVar");
    }
    @Test
    void allowInnerLambdasWithInnerCtx() {
        register("test", DefHelper.binary("c",(Double a,Double b) ->
            list.doubleStream()
                .filter(it-> it == a + b)
                .findFirst().orElse(-1)
        ));

        simpleTest(1,2,"test",2,"allowInnerLambdasWithInnerCtx");
    }

    @Test
    void allowContextVariables() {
        double v=2;
        JVMRegistry.register("test",DefHelper.binary("c",(Double a,Double b) -> {
            return a*b*v;
        }));
        simpleTest(1,2,"test",4,"xy2");
        simpleTest(2,2,"test",8,"xy2_1");
    }

    public void test(double a, double b, String type, double c) {
        super.simpleTest(a, b, type, c, type);
    }
}