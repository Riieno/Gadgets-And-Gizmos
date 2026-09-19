package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Iterables;
import com.google.gson.internal.reflect.ReflectionHelper;
import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphCompiler;
import com.rieno.gadgetsandgizmos.graph.compile.asm.AsmExpression;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.HandleExtractor;
import com.rieno.gadgetsandgizmos.graph.compile.util.InsnAdapter;
import com.rieno.gadgetsandgizmos.graph.compile.util.UnboundStateField;
import com.rieno.gadgetsandgizmos.graph.type.ValueType;
import com.rieno.gadgetsandgizmos.graph.type.ValueTypes;
import lombok.SneakyThrows;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

import java.lang.reflect.RecordComponent;
import java.util.List;

public class GenericNodeType extends JVMNodeType {
    private final MyBody inlinedBody;
    private static final Cache<Integer, InlinedGenericNodeType.MyBody<?, ?>> inlinedBodies = CacheBuilder.
        newBuilder()
        .weakValues()
        .build();
    private static int inlinedBodiesIdx = 0;
    protected final int inlinedBodyIndex = inlinedBodiesIdx++;
    private final Handle getBodyHandle = HandleExtractor.getMethod(GenericNodeType::body);
    private final Handle invokeBodyHandle = HandleExtractor.<MyBody, Record, Record>getMethod(MyBody::calculate);
    public final Class<? extends Record> recordIn, recordOut;

    @SneakyThrows
    protected <In extends Record, Out extends Record> GenericNodeType(MyBody<In, Out> inlinedBody, Class<In> recordIn, Class<Out> recordOut) {
        super();
        checkParam(recordIn, "inputType");
        checkParam(recordOut, "outputType");
        for(RecordComponent component : recordIn.getRecordComponents()) {
            input(component.getName(), typeOrVoid(component.getType(), ValueTypes.VOID));
        }
        for(RecordComponent component : recordOut.getRecordComponents()) {
            output(component.getName(), typeOrVoid(component.getType(), ValueTypes.VOID));
        }
        this.recordIn = recordIn;
        this.recordOut = recordOut;
        inlinedBodies.put(inlinedBodyIndex, inlinedBody);
        this.inlinedBody = inlinedBody;

    }

    private <Out extends Record> void checkParam(Class<Out> recordOut, String name) {
        if(!recordOut.isRecord()) throw new IllegalArgumentException(name + " must be record");
        if(recordOut == Record.class) throw new IllegalArgumentException(name + " must be not an abstract record");
    }

    protected static final UnboundStateField[] field = {UnboundStateField.make("inlineBody", Type.getType(MyBody.class), null)};

    @Override
    public @Nullable Iterable<UnboundStateField> stateFields(SnapNode self, JVMGraphCompiler.Cache cache) {
        Iterable<UnboundStateField> iterable = super.stateFields(self, cache);
        if(!needBodyField()) return iterable;
        List<UnboundStateField> b = List.of(
            field[0].withInitExpression(AsmExpression.list(mv -> {
                InsnAdapter.push(mv, inlinedBodyIndex);
                InsnAdapter.invoke(mv, getBodyHandle);
            }))
        );
        if(iterable == null) return b;
        return Iterables.concat(iterable, b);
    }

    protected boolean needBodyField() {
        return true;
    }

    protected ValueType<?> typeOrVoid(Class<?> classType, ValueType<?> def) {
        ValueType<?> type = ValueType.byClass(classType, true);
        return type == null ? def : type;
    }

    public static MyBody body(int i) {return (MyBody) inlinedBodies.getIfPresent(i);}

    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        makeInputRecord(mv, inputs);
        mv.loadStateField(field[0].bind(node));
        for(RecordComponent component : recordOut.getRecordComponents()) {
            String name = component.getName();
            mv.dup();
            InsnAdapter.invoke(mv,component.getAccessor());
            outputs.store(mv, name);
        }
        mv.pop();
        ;
    }

    public void makeInputRecord(GeneratorHelper mv, Inputs inputs) {
        mv.newInstance(Type.getType(recordIn));
        mv.dup();
        for(RecordComponent component : this.recordIn.getRecordComponents()) {
            inputs.load(mv,component.getName());
        }
        InsnAdapter.invoke(mv,ReflectionHelper.getCanonicalRecordConstructor(recordIn));
    }

    public interface MyBody<In extends Record, Out extends Record> {
        Out calculate(In in);
    }
}
