package com.rieno.gadgetsandgizmos.graph.compile.asm;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;

/**
 * TODO not an enum
 *
 * @see GraphRuntime#convertValue(AdvancedGraphDocument.Value, String)
 *
 */
public interface ValueTypes {

    ValueType NUMBER = new ValueType("number",double.class, new InsnNode(Opcodes.DCONST_0));
    ValueType BOOL = new ValueType("bool",boolean.class, new InsnNode(Opcodes.ICONST_0));
    ValueType STRING = new ValueType("string",String.class, new InsnNode(Opcodes.ACONST_NULL));
    ValueType VALUE = new ValueType("value",AdvancedGraphDocument.Value.class, new InsnNode(Opcodes.ACONST_NULL));


    ValueType ANY = new ValueType("any",Object.class, new InsnNode(Opcodes.ACONST_NULL)).unsavable();


    ValueType EXEC = new ValueType("exec",Void.class, new InsnNode(Opcodes.ACONST_NULL)).unsavable();
    ValueType VOID = new ValueType("void",Void.class, new InsnNode(Opcodes.ACONST_NULL)).unsavable();
    Integer afterAll = afterAll();

    static Integer afterAll() {
        if(afterAll != null) return 1;
        NUMBER.convertViaStaticMethod(BOOL, ValueTypes.class, "num2bool");
        NUMBER.convertViaStaticMethod(STRING, Double.class, "toString");
        NUMBER.convertViaStaticMethod(VALUE, AdvancedGraphDocument.Value.class, "number");

        BOOL.convertViaOpcode(NUMBER, Opcodes.I2D);
        BOOL.convertViaStaticMethod(STRING, Boolean.class, "toString");
        BOOL.convertViaStaticMethod(VALUE, AdvancedGraphDocument.Value.class, "bool");

        STRING.convertViaStaticMethod(NUMBER, ValueTypes.class, "str2num");
        STRING.convertViaStaticMethod(BOOL, ValueTypes.class, "str2num");
        STRING.convertViaStaticMethod(VALUE, AdvancedGraphDocument.Value.class, "string");

        VALUE.convertViaInstanceMethod(NUMBER, "asNumber");
        VALUE.convertViaInstanceMethod(BOOL, "asBoolean");
        VALUE.convertViaInstanceMethod(STRING, "asString");


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
