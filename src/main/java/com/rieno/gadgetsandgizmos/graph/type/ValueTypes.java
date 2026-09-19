package com.rieno.gadgetsandgizmos.graph.type;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import it.unimi.dsi.fastutil.booleans.Boolean2ObjectFunction;
import it.unimi.dsi.fastutil.doubles.Double2ObjectFunction;
import lombok.SneakyThrows;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;

/**
 * TODO not an enum
 *
 * @see GraphRuntime#convertValue(AdvancedGraphDocument.Value, String)
 *
 */
public interface ValueTypes {

    ValueType<Double> NUMBER = new ValueType<>("number",double.class, new InsnNode(Opcodes.DCONST_0));
    ValueType<Boolean> BOOL = new ValueType<>("bool",boolean.class, new InsnNode(Opcodes.ICONST_0));
    ValueType<String> STRING = new ValueType<>("string",String.class, new InsnNode(Opcodes.ACONST_NULL));
    ValueType<AdvancedGraphDocument.Value> VALUE = new ValueType<>("value",AdvancedGraphDocument.Value.class, new InsnNode(Opcodes.ACONST_NULL));


    ValueType<Object> ANY = new ValueType<>("any",Object.class, new InsnNode(Opcodes.ACONST_NULL)).unsavable();


    ValueType<Void> EXEC = new ValueType<Void>("exec",Void.class, new InsnNode(Opcodes.ACONST_NULL)).unsavable();
    ValueType<Void> VOID = new ValueType<Void>("void",Void.class, new InsnNode(Opcodes.ACONST_NULL)).unsavable();
    Integer afterAll = afterAll();

    @SneakyThrows
    static Integer afterAll() {
        if(afterAll != null) return 1;
        NUMBER.defaultForInnerType();
        BOOL.defaultForInnerType();
        STRING.defaultForInnerType();
        VALUE.defaultForInnerType();
        VOID.defaultForInnerType();
        NUMBER.convertViaLambda(BOOL, ValueTypes::num2bool);
        NUMBER.convertViaLambda(STRING, (Double2ObjectFunction<String>)Double::toString);
        NUMBER.convertViaLambda(VALUE, AdvancedGraphDocument.Value::number);

        BOOL.convertViaOpcode(NUMBER, Opcodes.I2D);
        BOOL.convertViaLambda(STRING, (Boolean2ObjectFunction<String>) Boolean::toString);
        BOOL.convertViaLambda(VALUE, AdvancedGraphDocument.Value::bool);

        STRING.convertViaLambda(NUMBER, ValueTypes::str2num);
        STRING.convertViaLambda(BOOL, ValueTypes::str2bool);
        STRING.convertViaLambda(VALUE, AdvancedGraphDocument.Value::string);

        VALUE.convertViaLambda(NUMBER, AdvancedGraphDocument.Value::asNumber);
        VALUE.convertViaLambda(BOOL, AdvancedGraphDocument.Value::asBoolean);
        VALUE.convertViaLambda(STRING, AdvancedGraphDocument.Value::asString);


        return 1;
    }


    public static boolean num2bool(double x) {return x != 0;}

    public static boolean str2bool(String x) {return x.equals("true");}

    public static double str2num(String x) {
        try {
            return Double.parseDouble(x);
        } catch(NumberFormatException e) {
            return 0;
        }
    }
}
