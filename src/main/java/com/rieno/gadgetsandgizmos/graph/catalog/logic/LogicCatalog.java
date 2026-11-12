package com.rieno.gadgetsandgizmos.graph.catalog.logic;

import com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.GenericLambdaNode;

public class LogicCatalog extends JVMRegistry implements DefHelper {
    public static final Integer initialized = register();

    public LogicCatalog() {super(null);}

    private static GenericLambdaNode<?> body(BoolBool2Bool body) {
        return GenericLambdaNode.make(1, body);
    }

    private static GenericLambdaNode<?> body(Bool2Bool body) {
        return GenericLambdaNode.make(1, body);
    }


    public static Integer register() {
        if(initialized != null) return 1;
        register("and", body((a, b) -> a & b));
        register("or", body((a, b) -> a | b));
        register("not", body((value) -> !value));

        return 1;
    }

    interface BoolBool2Bool {
        boolean get(boolean a, boolean b);
    }

    interface Bool2Bool {
        boolean get(boolean value);
    }
}
