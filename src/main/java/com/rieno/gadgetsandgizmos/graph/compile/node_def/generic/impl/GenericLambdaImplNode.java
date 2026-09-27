package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.GenericLambdaNode;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.eval.Ports;
import net.minecraft.nbt.CompoundTag;

public class GenericLambdaImplNode extends GenericLambdaNode<ProcessedLambda> {
    protected GenericLambdaImplNode(ProcessedLambda processedLambda) {
        super(processedLambda,processedLambda);

    }

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {

    }

    @Override
    public Ports eval(Ports inputPorts) {throw null;}
}
