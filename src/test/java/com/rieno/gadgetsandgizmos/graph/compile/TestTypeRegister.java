package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.node_def.BinaryNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.ConstDValue;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.NumberBinaryNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.control_flow.Gate;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.data.GetData;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.data.SetData;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.event.EventListenerNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.event.TickerNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.record.InlinedGenericRecordFunctionNode;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class TestTypeRegister extends JVMRegistry implements DefHelper {

    public static void register() {
        register("inline", DefHelper.unary((Num2 nums00) -> {
                var nums0 = nums00;
                var nums = nums0;
                double tmp = Math.pow(nums.a, nums.b());
                Out out = new Out(Math.pow(tmp, nums.b));
                return out;
            }
        ));
        register("inline_in", DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            TestSink.consume(nums);
            return new Out(Math.pow(tmp, nums.b));
        }));
        register("inline_in_out", DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            Out out = new Out(Math.pow(tmp, nums.b));
            TestSink.consume(nums);
            TestSink.consume(out);
            return out;
        }));
        register("inline_out", DefHelper.unary((Num2 nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            Out out = new Out(Math.pow(tmp, nums.b));
            TestSink.consume(out);
            return out;
        }));
        final ConstDValue value = new ConstDValue();
        register("const", value);
        //register("+", new BinOpNode(Opcodes.DADD));
        register("+", DefHelper.numberBin(Double::sum));
        register("-", DefHelper.numberBin((a, b) -> a - b));
        register("*", DefHelper.numberBin((a, b) -> a * b));
        register("/", DefHelper.numberBin((a, b) -> a / b));

        record SinCos(double sin, double cos) {}
        register("sincos", DefHelper.unary((Double a) -> new SinCos(Math.sin(a), Math.cos(a))));

        register("pow", DefHelper.numberBin((a, b) -> Math.pow(a, b)));
        register("math0", DefHelper.numberBin((a, b) -> {
            double tmp = Math.pow(a, b);
            return Math.pow(tmp, b);
        }));
        register("gate", new Gate());
        register("tick", new TickerNode());
        register("event-listener", new EventListenerNode());
        register("set_data", new SetData());
        register("get_data", new GetData());

    }

    public record Num2(double a, double b) {}

    public record Out(double c) {}
}
