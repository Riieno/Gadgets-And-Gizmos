package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry.register;

public class TypeConversionTest extends AbstractJVMGraphCompilerTest {

    static {

    }


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
        JVMRegistry.instance.entries.remove("test");

    }

    @Test
    void double2Object() {
        register("test", DefHelper.binary("c",(Object a,Object b) ->
            (double)(a.hashCode()+b.hashCode())*Double.NaN
        ));
        simpleTest(1,2,"test",Double.NaN,"double2Object");
    }

    public void test(double a, double b, String type, double c) {
        super.simpleTest(a, b, type, c, type);
    }
}