package com.rieno.gadgetsandgizmos.graph.compile.node_type.generic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Lombok;
import lombok.NoArgsConstructor;
import net.neoforged.neoforge.common.util.Lazy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Arrays;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatistics {
    public boolean returnValue;
    public boolean instanceMethod;
    public boolean instanceField;
    public boolean fieldValue;
    public boolean recordField;
    public boolean otherMethod;
    public boolean other;
    public static final Lazy<Getter[]> myFields = Lazy.of(() -> {
        try {
            Field[] fields = UsageStatistics.class.getDeclaredFields();
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Getter[] getters = new Getter[fields.length];
            int amountOf=0;
            for(int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if(field.getType() != boolean.class) continue;
                VarHandle varHandle = lookup.unreflectVarHandle(field);
                getters[amountOf++] = (it) -> (boolean) varHandle.get(it);
            }
            return Arrays.copyOf(getters,amountOf);
        } catch(IllegalAccessException e) {
            throw Lombok.sneakyThrow(e);
        }
    });


    public static final int onlyFieldsMask = builder().instanceField(true).recordField(true).build().asMask();
    public static final int onlyReturnMask = builder().returnValue(true).build().asMask();
    public static final int allUsage = -1;
    public static final int noUsage = -1;

    public int asMask() {
        Getter[] getters = myFields.get();
        int mask = 0;
        for(int i = 0; i < getters.length; i++) {
            if(getters[i].get(this)) mask |= (1 << i);
        }
        return mask;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Used{");

        if(instanceMethod) sb.append("imethod,");
        if(instanceField) sb.append("ifield,");
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
    public boolean onlyAllowed(int allowed){
        int mask = asMask();
        return (mask & (~allowed)) == 0;
    }
    public boolean equals(int otherMask){
        int mask = asMask();
        return mask == otherMask;
    }
    public boolean onlyFields() {
        return onlyAllowed(onlyFieldsMask);
    }
    public boolean onlyReturn() {
        return onlyAllowed(onlyReturnMask);
    }
    public boolean noOne() {
        return onlyAllowed(noUsage);
    }

    public boolean isEmpty() {
        return asMask()==0;
    }

    public interface Getter {
        boolean get(UsageStatistics statistics);
    }
}
