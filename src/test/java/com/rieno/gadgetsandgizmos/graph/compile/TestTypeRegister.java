package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.BinOpNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.BinaryNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.ConstDValue;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.control_flow.Gate;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.data.GetData;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.data.SetData;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.event.EventListenerNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.event.TickerNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class TestTypeRegister extends JVMRegistry{
    public static void register(){
        final ConstDValue value = new ConstDValue();
        register("const", value);
        register("+", new BinOpNode(Opcodes.DADD));
        register("-", new BinOpNode(Opcodes.DSUB));
        register("*", new BinOpNode(Opcodes.DMUL));
        register("/", new BinOpNode(Opcodes.DDIV));
        register("pow", BinaryNode.impl(ValueTypes.NUMBER, (mv, inputs, outputs, data) -> {
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

        register("gate",new Gate());
        register("tick",new TickerNode());
        register("event-listener",new EventListenerNode());
        register("set_data",new SetData());
        register("get_data",new GetData());

    }
}
