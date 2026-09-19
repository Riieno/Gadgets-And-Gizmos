package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.node_type.BinOpNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.BinaryNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.ConstDValue;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.NumberBinaryNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.control_flow.Gate;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.data.GetData;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.data.SetData;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.event.EventListenerNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.event.TickerNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.InlinedGenericNodeType;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class TestTypeRegister extends JVMRegistry {
    public static void register() {
        final ConstDValue value = new ConstDValue();
        register("const", value);
        register("+", new BinOpNode(Opcodes.DADD));
        register("-", new BinOpNode(Opcodes.DSUB));
        register("*", new BinOpNode(Opcodes.DMUL));
        register("/", new BinOpNode(Opcodes.DDIV));
        register("pow", new NumberBinaryNode((a, b) -> Math.pow(a, b)));
        register("math0", new NumberBinaryNode((a, b) -> {
            double tmp = Math.pow(a, b);
            return Math.pow(tmp, b);
        }));
        register("inline", new InlinedGenericNodeType(Num2.class, Out.class,
            (Num2 nums00) -> {
                var nums0 = nums00;
                var nums = nums0;
                double tmp = Math.pow(nums.a, nums.b());
                Out out = new Out(Math.pow(tmp, nums.b));
                return out;
            }
        ));
        register("inline_in", new InlinedGenericNodeType(Num2.class, Out.class, (nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            TestSink.consume(nums);
            return new Out(Math.pow(tmp, nums.b));
        }));
        register("inline_in_out", new InlinedGenericNodeType(Num2.class, Out.class, (nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            Out out = new Out(Math.pow(tmp, nums.b));
            TestSink.consume(nums);
            TestSink.consume(out);
            return out;
        }));
        register("inline_out", new InlinedGenericNodeType(Num2.class, Out.class, (nums) -> {
            double tmp = Math.pow(nums.a, nums.b);
            Out out = new Out(Math.pow(tmp, nums.b));
            TestSink.consume(out);
            return out;
        }));
        register("pow0", BinaryNode.impl(ValueTypes.NUMBER, (mv, inputs, outputs, data) -> {
            inputs.load(mv, "a");
            inputs.load(mv, "b");
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(Math.class),
                "pow", "(DD)D",
                false
            );

            outputs.store(mv, "c");
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
