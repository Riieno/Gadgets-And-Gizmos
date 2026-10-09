package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.node_def.ConstDValue;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.control_flow.Gate;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.data.GetData;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.data.SetData;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.event.EventListenerNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.event.TickerNode;

public class TestTypeRegister extends JVMRegistry implements DefHelper {
    public static final Integer was = register();

    public static Integer register() {
        if(was != null) return 1;
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
        return 1;
    }
}
