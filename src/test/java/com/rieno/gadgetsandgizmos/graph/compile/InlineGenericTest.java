package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.nbt.DoubleTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InlineGenericTest extends AbstractJVMGraphCompilerTest {


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
    }

    @Test
    void checksForNonPublicRecords() {
        Assertions.fail("TODO");
    }
    @Test
    void inline() {
        test(1, 2, "inline", 1);
        test(2, 3, "inline", 512);
    }
    @Test
    void inline_in() {
        test(1, 2, "inline_in", 1);
        test(2, 3, "inline_in", 512);
    }
    @Test
    void inline_out() {
        test(1, 2, "inline_out", 1);
        test(2, 3, "inline_out", 512);
    }
    @Test
    void inline_in_out() {
        test(1, 2, "inline_in_out", 1);
        test(2, 3, "inline_in_out", 512);
    }



    public void test(double a, double b, String type, double c) {
        int revision = doc.revision();
        doc=new AdvancedGraphDocument();
        doc.setRevision(revision + 1);
        setClassSubName(type);
        var one = value(DoubleTag.valueOf(a));
        var two = value(DoubleTag.valueOf(b));

        var plus = binary(type, one, "value", two, "value");
        assertEquals(AdvancedGraphDocument.Value.number(c), output(plus, "c"));
    }
}