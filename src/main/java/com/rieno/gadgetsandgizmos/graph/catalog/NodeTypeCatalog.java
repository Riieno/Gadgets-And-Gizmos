package com.rieno.gadgetsandgizmos.graph.catalog;

import com.rieno.gadgetsandgizmos.graph.catalog.logic.LogicCatalog;
import com.rieno.gadgetsandgizmos.graph.catalog.math.MathCatalog;
import com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;

public class NodeTypeCatalog extends JVMRegistry implements DefHelper {
    public static final Integer initialized = register();

    public NodeTypeCatalog() {super(null);}

    public static Integer register() {
        if(initialized != null) return 1;
        MathCatalog.register();
        LogicCatalog.register();

        return 1;
    }
}
