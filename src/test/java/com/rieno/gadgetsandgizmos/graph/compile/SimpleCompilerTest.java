package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.nbt.DoubleTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleCompilerTest extends AbstractJVMGraphCompilerTest {


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
    }

    @Test
    void plus() {
        test(1, 2, "+", 3);
    }

    @Test
    void minus() {
        test(1, 2, "-", -1);
        test(2, 1, "-", 1);
    }

    @Test
    void mul() {
        test(2, 3, "*", 6);
    }

    @Test
    void div() {
        test(1, 2, "/", 1 / 2f);
        test(4, 2, "/", 2);
    }

    @Test
    void pow() {
        test(1, 2, "pow", 1);
        test(2, 3, "pow", 8);
    }
    @Test
    void multiple() {
        doc.setRevision(doc.revision() + 1);
        var one = value(1);
        var two = value(2);
        var three = value(2);

        var three2 = binary("+", one, "value", two, "value");
        var nine = binary("pow", three2, "c", three, "value");
        String c = "c";
        assertEquals(AdvancedGraphDocument.Value.number(9), output(nine, c));
    }



    public void test(double a, double b, String type, double c) {
        doc.setRevision(doc.revision() + 1);
        var one = value(DoubleTag.valueOf(a));
        var two = value(DoubleTag.valueOf(b));

        var plus = binary(type, one, "value", two, "value");
        assertEquals(AdvancedGraphDocument.Value.number(c), output(plus, "c"));
    }
}