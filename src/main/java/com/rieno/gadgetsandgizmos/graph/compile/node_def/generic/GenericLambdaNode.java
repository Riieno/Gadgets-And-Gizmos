package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic;

import com.google.common.collect.Iterables;
import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphCompiler;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.CompileCtx;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.Options;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.ProcessedLambda;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.ShouldFlatInputPredicate;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.UnboundStateField;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import lombok.NonNull;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Arrays;
import java.util.List;

public class GenericLambdaNode<Function> extends JVMNodeType {
    public Function function;
    public ProcessedLambda processedLambda;

    public GenericLambdaNode(@NonNull Function function,@NonNull ProcessedLambda processedLambda) {
        this.function = function;
        this.processedLambda = processedLambda;
        this.declaredInputPort.putAll(processedLambda.inputPorts);
        this.declaredOutputPort.putAll(processedLambda.outputPorts);
    }

    public static <Function> GenericLambdaNode<Function> make(Function function){
        return make(1,function);
    }
    public static <Function> GenericLambdaNode<Function> make(String outputType,Function function){
        return make(
            Options.builder().outputPortDefName(outputType).flatInputPredicate(ShouldFlatInputPredicate.PRIMITIVE_WRAPPERS).build(),
            1,
            function
        );
    }
    public static <Function> GenericLambdaNode<Function> make(int searchLevelOffset, Function function){
        return make(Options.defaultOptions, 1 + searchLevelOffset, function);
    }

    public static <Function> @NotNull GenericLambdaNode<Function> make(Options defaultOptions, int searchLevelOffset, Function function) {
        return new GenericLambdaNode<>(
            function,
            ProcessedLambda.make(
                defaultOptions,
                null,
                1 + searchLevelOffset,
                function
            )
        );
    }

    @Override
    public @Nullable Iterable<UnboundStateField> stateFields(SnapNode self, JVMGraphCompiler.Cache cache) {
        Iterable<UnboundStateField> iterable = super.stateFields(self, cache);
        if(iterable==null){
            return List.of(processedLambda.stateFields);
        }
        return Iterables.concat(List.of(processedLambda.stateFields),iterable);
    }

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        CompileCtx ctx = context.getPropOrSet("__processed_lambda_ctx__", CompileCtx::new);
        ctx.reset();
        for(AbstractInsnNode insnNode : processedLambda.insnArray) {
            if(insnNode instanceof ProcessedLambda.Compilable compilable) {
                compilable.compileOutputPortCalculations(mv,node,inputs,outputs,data,ctx);
            }else{
                insnNode.clone(ctx.labelCloner).accept(mv);
            }
        }
    }


/*    @Override
    public abstract Ports eval(Ports inputPorts);*/
}
