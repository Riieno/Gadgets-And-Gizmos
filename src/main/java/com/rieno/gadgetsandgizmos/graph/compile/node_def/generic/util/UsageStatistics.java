package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Lombok;
import lombok.RequiredArgsConstructor;
import net.neoforged.neoforge.common.util.Lazy;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

@Builder
@RequiredArgsConstructor
@AllArgsConstructor

public class UsageStatistics {
    public boolean returnValue;
    public boolean instanceMethod;
    public boolean unwrapForWrapper;
    public boolean wrapForWrapper;
    public boolean instancePutField;
    public boolean instanceGetField;
    public boolean fieldValue;
    public boolean recordField;
    public boolean otherMethod;
    //public boolean usedInInsn;
    //public boolean usedInJump;
    public boolean other;
    public final AbstractInsnNode source;
    public static final Lazy<FieldAccessors[]> myFields = Lazy.of(() -> {
        try {
            Field[] fields = UsageStatistics.class.getDeclaredFields();
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            FieldAccessors[] fieldAccessors = new FieldAccessors[fields.length];
            int amountOf = 0;
            for(int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if(field.getType() != boolean.class || !Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers()))
                    continue;
                VarHandle varHandle = lookup.unreflectVarHandle(field);
                fieldAccessors[amountOf++] = new FieldAccessors() {
                    @Override
                    public boolean get(UsageStatistics statistics) {return (boolean) varHandle.get(statistics);}

                    @Override
                    public void set(UsageStatistics statistics, boolean value) {varHandle.set(statistics, value);}
                };
            }
            return Arrays.copyOf(fieldAccessors, amountOf);
        } catch(IllegalAccessException e) {
            throw Lombok.sneakyThrow(e);
        }
    });


    public static final int onlyFieldsMask = builder().instanceGetField(true).recordField(true).build().asMask();
    public static final int onlyReturnMask = builder().returnValue(true).build().asMask();
    public static final int onlyUnwrapperAndReturnMask = builder().returnValue(true).build().asMask();
    public static final int allUsage = -1;
    public static final int noUsage = -1;

    public int asMask() {
        FieldAccessors[] fieldAccessors = myFields.get();
        int mask = 0;
        for(int i = 0; i < fieldAccessors.length; i++) {
            if(fieldAccessors[i].get(this)) mask |= (1 << i);
        }
        return mask;
    }

    public void setMask(int mask) {
        FieldAccessors[] fieldAccessors = myFields.get();
        for(int i = 0; i < fieldAccessors.length; i++) {
            fieldAccessors[i].set(this, ((mask >> i) & 1) == 1);
        }

    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Used{");

        if(instanceMethod) sb.append("imethod,");
        if(instancePutField) sb.append("putfield,");
        if(instanceGetField) sb.append("getfield,");
        if(fieldValue) sb.append("fieldv,");
        if(recordField) sb.append("rfield,");
        if(otherMethod) sb.append("omethod,");
        if(other) sb.append("o,");
        if(returnValue) sb.append("ret,");
        sb.append('}');
        return sb.toString();
    }

    public static int mask(boolean... values) {
        int mask = 0;
        for(int i = 0; i < values.length; i++) {
            if(values[i]) mask |= (1 << i);
        }
        return mask;
    }

    public boolean onlyAllowed(int allowed) {
        return asMaskExcept(allowed) == 0;
    }

    private int asMaskExcept(int ignored) {
        int mask = asMask();
        return mask & (~ignored);
    }

    public boolean onlyAllowedAndIgnore(int allowed, int ignored) {
        return (asMaskExcept(allowed) | ignored) == ignored;
    }

    public boolean equals(int otherMask) {
        int mask = asMask();
        return mask == otherMask;
    }

    public boolean onlyFields() {
        return onlyAllowed(onlyFieldsMask);
    }

    public boolean onlyReturn() {
        return onlyAllowed(onlyReturnMask);
    }

    public boolean onlyUnwrapperAndReturn() {return onlyAllowed(onlyUnwrapperAndReturnMask);}

    public boolean noOne() {
        return onlyAllowed(noUsage);
    }

    public boolean isEmpty() {
        return asMask() == 0;
    }

    public void setOr(UsageStatistics other) {
        setMask(asMask() | other.asMask());
    }

    public interface FieldAccessors {
        boolean get(UsageStatistics statistics);

        void set(UsageStatistics statistics, boolean value);
    }
}
