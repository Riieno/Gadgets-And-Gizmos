package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.impl;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.generic.GenericLambdaNode;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.PtrExtractor;
import com.rieno.gadgetsandgizmos.graph.eval.Ports;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;

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
