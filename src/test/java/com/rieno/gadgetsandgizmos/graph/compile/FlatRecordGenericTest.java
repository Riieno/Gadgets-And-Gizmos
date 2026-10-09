package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.NonPublicReference;
import net.minecraft.nbt.DoubleTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry.register;

class FlatRecordGenericTest extends AbstractJVMGraphCompilerTest {

    static {
        TestTypeRegister.register();
        register("record", DefHelper.unary((Num2 nums00) -> {
                var nums0 = nums00;
                var nums = nums0;
                double tmp = Math.pow(nums.a, nums.b());
                Out out = new Out(Math.pow(tmp, nums.b));
                return out;
            }
        ));
        register("record_in", DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            TestSink.consume(nums);
            return new Out(Math.pow(tmp, nums.b));
        }));
        register("record_in_out", DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            Out out = new Out(Math.pow(tmp, nums.b));
            TestSink.consume(nums);
            TestSink.consume(out);
            return out;
        }));
        register("record_out", DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            Out out = new Out(Math.pow(tmp, nums.b));
            TestSink.consume(out);
            return out;
        }));
    }


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
    }

    @Test
    void record() {
        test(1, 2, "record", 1);
        test(2, 3, "record", 512);
    }

    @Test
    void record_in() {
        test(1, 2, "record_in", 1);
        test(2, 3, "record_in", 512);
    }

    @Test
    void record_out() {
        test(1, 2, "record_out", 1);
        test(2, 3, "record_out", 512);
    }

    @Test
    void record_in_out() {
        test(1, 2, "record_in_out", 1);
        test(2, 3, "record_in_out", 512);
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
    public record Num2(double a, double b) {}

    public record Out(double c) {}
}