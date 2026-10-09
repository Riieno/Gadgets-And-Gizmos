package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.AbstractJVMGraphCompilerTest;
import com.rieno.gadgetsandgizmos.graph.compile.TestTypeRegister;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.nbt.DoubleTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry.register;

public class OtherGenericTest extends AbstractJVMGraphCompilerTest {
public static DoubleList list=DoubleList.of(1,2,3,4);
    static {
        TestTypeRegister.register();

        register("lambdaLambda", DefHelper.binary("c",(Double a,Double b) -> {
                return list.doubleStream().filter(it->it==a+b).findFirst().orElse(-1);
            }
        ));
    }


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
    }

    @Test
    void test1() {
        test(1, 2, "lambdaLambda", 3);
        test(2, 3, "lambdaLambda", -1);
    }


    public void test(double a, double b, String type, double c) {
        int revision = doc.revision();
        doc = new AdvancedGraphDocument();
        doc.setRevision(revision + 1);
        setClassSubName(type);
        var one = value(DoubleTag.valueOf(a));
        var two = value(DoubleTag.valueOf(b));

        var plus = binary(type, one, "value", two, "value");
        assertEquals(AdvancedGraphDocument.Value.number(c), output(plus, "c"));
    }
}