package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphCompiler;
import com.rieno.gadgetsandgizmos.graph.compile.asm.*;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.Options;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.ProcessedLambda;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.impl.ShouldFlatInputPredicate;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.BoundStateField;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.UnboundStateField;
import com.rieno.gadgetsandgizmos.graph.struct.NodeCalculator;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GenericLambdaNode<Function> extends JVMNodeType {
    public Function function;
    public ProcessedLambda processedLambda;
    @Nullable
    private ExecMode execMode;
    private ProcessedLambda simpleBody;
    public final Lazy<ImmutableMap<String,UnboundStateField>> stateFieldsForCachedPort=Lazy.of(() -> {
        if (processedLambda.outputPorts.isEmpty())return ImmutableMap.of();
        var fields = ImmutableMap.<String,UnboundStateField>builder();

        for(var entry : Object2ObjectMaps.fastIterable(processedLambda.outputPorts)) {
            String name = entry.getKey();
            ValueType<?> type = entry.getValue();

            fields.put(name,UnboundStateField.make(
                "cachedPort$"+name,type.innerType, FieldInitExpr.list(type::defaultValue)
            ));
        }

        return fields.build();

    });


    public GenericLambdaNode(@NonNull Function function, @NonNull ProcessedLambda processedLambda) {
        this.function = function;
        this.processedLambda = processedLambda;
        this.declaredInputPort.putAll(processedLambda.inputPorts);
        this.declaredOutputPort.putAll(processedLambda.outputPorts);
    }

    public static <Function> GenericLambdaNode<Function> make(Function function) {
        return make(1, function);
    }

    public static <Function> GenericLambdaNode<Function> make(String outputType, Function function) {
        return make(
            Options.builder().outputPortDefName(outputType).flatInputPredicate(ShouldFlatInputPredicate.PRIMITIVE_WRAPPERS).build(),
            1,
            function
        );
    }

    public static <Function> GenericLambdaNode<Function> make(int searchLevelOffset, Function function) {
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
        var fieldsIter = new ObjectOpenHashSet<Iterable<UnboundStateField>>();
        fieldsIter.add(List.of(processedLambda.stateFields));
        fieldsIter.add(super.stateFields(self,cache));
        if(execMode==ExecMode.RunOnExecOtherwiseCachedOutput){
            fieldsIter.add(stateFieldsForCachedPort.get().values());
        }
        fieldsIter.remove(null);
        if (fieldsIter.isEmpty())return null;
        return Iterables.concat(fieldsIter);
    }


    @Override
    public void typeProperties(CompilationContext.TypeProperties properties, SnapNode self) {
        super.typeProperties(properties, self);
        properties.modeSensitivePortCalculator = execMode != null;
    }


    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        if(execMode == null) {
            processedLambda.compile(mv, node, inputs, outputs, data, context);
            return;
        }
        switch(execMode) {
            case RunOnExec -> {
                if(context.calculatorMode == NodeCalculator.Mode.ExecFollow) {
                    processedLambda.compile(mv, node, inputs, outputs, data, context);
                }else{

                }
            }
            case RunOnExecOtherwiseCachedOutput -> {
                ImmutableMap<String, UnboundStateField> map = stateFieldsForCachedPort.get();
                if(context.calculatorMode == NodeCalculator.Mode.ExecFollow) {
                    processedLambda.compile(mv, node, inputs, (mv1, port) -> {
                        mv1.storeField(map.get(port).bind(node.id));
                    }, data, context);
                }else{
                    map.forEach((s, stateField) -> {
                        mv.loadStateField(stateField.bind(node.id));
                        outputs.store(mv,s);
                    });
                }

            }
            case RunSelfOnExecOtherwiseRunSecondBody -> {
                if(context.calculatorMode == NodeCalculator.Mode.ExecFollow) {
                    processedLambda.compile(mv, node, inputs, outputs, data, context);
                }else{
                    simpleBody.compile(mv, node, inputs, outputs, data, context);
                }
            }
        }

    }

    @Override
    public void addInputIds(SnapNode node, CompilationContext context, IntArrayList inputs) {
        if(execMode!=null && !execMode.needInputIfNonExec && context.calculatorMode != NodeCalculator.Mode.ExecFollow){
            return;
        }
        super.addInputIds(node, context, inputs);
    }

    public GenericLambdaNode<Function> execWithMode(ExecMode execMode, boolean passThroughExecSignal) {
        return exec(null, passThroughExecSignal, execMode);
    }

    public GenericLambdaNode<Function> execWithNonExecBody(@NonNull ProcessedLambda nonExecBody, boolean passThroughExecSignal) {
        return exec(nonExecBody, passThroughExecSignal, ExecMode.RunSelfOnExecOtherwiseRunSecondBody);
    }

    private @NotNull GenericLambdaNode<Function> exec(ProcessedLambda nonExecBody, boolean passThroughExecSignal, ExecMode execMode) {
        if(execMode == ExecMode.RunSelfOnExecOtherwiseRunSecondBody && nonExecBody == null) {
            throw new IllegalArgumentException(execMode.name() + " requires nonExecBody");
        }
        this.execMode = execMode;
        this.simpleBody = nonExecBody;
        input("exec", ValueTypes.EXEC);
        if(passThroughExecSignal){
            output("exec", ValueTypes.EXEC);
        }

        //this.passThroughExecSignal = passThroughExecSignal;
        return this;
    }

    public GenericLambdaNode<Function> execWithNonExecBody(GenericLambdaNode<?> nonExecBody, boolean passThroughExecSignal) {
        return execWithNonExecBody(nonExecBody.processedLambda, passThroughExecSignal);
    }

    @RequiredArgsConstructor
    public enum ExecMode {
        RunOnExec(false,false),
        RunOnExecOtherwiseCachedOutput(false,false),
        RunSelfOnExecOtherwiseRunSecondBody(true,true)

        ;
        public final boolean secondBodyRequired;
        public final boolean needInputIfNonExec;

    }

/*    @Override
    public abstract Ports eval(Ports inputPorts);*/
}
