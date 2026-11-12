package com.rieno.gadgetsandgizmos.graph.catalog.math;

import com.rieno.gadgetsandgizmos.graph.compile.JVMRegistry;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.DefHelper;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.GenericLambdaNode;
import com.rieno.gadgetsandgizmos.graph.compile.node_def.generic.Marker;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;

import static com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeOperations.finiteIndex;

@SuppressWarnings("Convert2MethodRef")
public class MathCatalog extends JVMRegistry implements DefHelper {
    public static final Integer initialized = register();

    public MathCatalog() {super(null);}


    public static Integer register() {
        if(initialized != null) return 1;
        register("add", body((a, b) -> a + b));
        register("subtract", body((a, b) -> a - b));
        register("multiply", body((a, b) -> a * b));
        register("divide", body((a, b) -> a / b));
        register("modulo", body((a, b) -> a % b));
        register("min", body((a, b) -> Math.min(a, b)));
        register("max", body((a, b) -> Math.max(a, b)));
        register("average", body((a, b) -> (a + b) / 2));
        register("absolute", body((a) -> Math.abs(a)));
        //TODO exec?!
        //register("lerp", body((a, b, amount) -> __));//STATE
        register("lerp_map", body((a, b, amount) -> a * amount + (1 - amount) * b));
        register("sin", body(value -> Math.sin(value)));
        register("cos", body(value -> Math.cos(value)));
        register("-x", body(value -> -value));

        register("math_sqrt", body(In -> Math.sqrt(In)));
        register("math_tan", body(In -> Math.tan(In)));
        register("math_acos", body(In -> Math.acos(In)));
        register("math_asin", body(In -> Math.asin(In)));
        register("math_atan", body(In -> Math.atan(In)));
        register("math_atan2", body("Out", (Y, X) -> Math.atan2(Y, X)));
        register("math_power", body("Result", (Base, Exp) -> Math.pow(Base, Exp)));
        register("math_exp", body("Out", (In) -> Math.exp(In)));
        register("math_ln", body("Out", (In) -> Math.log(In)));
        register("math_log", body("Out", (In, Base) -> Math.log(In) / Math.log(Base)));
        class DoubleCircularList {
            double[] values = new double[0];
            int idx;
            double avg = 0;


            void setSize(int size) {
                if(size == values.length) return;
                if(size <= 0)throw new IllegalArgumentException("'size' must be >=1, but "+size);
                double[] newVals = new double[size];
                double avg = 0;
                int iter=Math.min(size,values.length);
                for(int i = 0; i < iter; i++) {
                    double v = get(i);
                    newVals[i] = v;
                    avg += v;
                }
                this.avg = avg / size;
                this.idx = 0;
                this.values = newVals;
            }

            double get(int i) {
                return values[(i + idx) % values.length];
            }

            void add(double value) {
                int currentIdx = idx;
                idx = idx + 1;
                double oldValue = values[currentIdx];

                values[currentIdx]=value;
                if(idx < values.length) {
                    avg = avg + (value - oldValue) / values.length;
                } else {
                    idx=0;
                    double avg=0;
                    for(double v : values) avg += v;
                    this.avg=avg/values.length;


                }
            }
        }
        register("math_average", DefHelper.func3("Average", (Double In, Double Samples, @Marker.State DoubleCircularList list) -> {
            int samples = finiteIndex(Samples, 1);
            samples = Math.max(1, Math.min(100, samples));
            list.setSize(samples);
            list.add(In);
            return list.avg;
        }));
        register("math_delta", DefHelper.binary("Delta", (Double In, @Marker.State Double[] prev) -> {
            Double old = prev[0];
            prev[0]=In;
            return old == null ? 0 : In - old;
        }));
        record MinMax(double min, double max) {}
        register("math_peak_tracker", DefHelper.func3(null, (Double In, @Marker.PreviousOutput Double min, @Marker.PreviousOutput Double max) ->
            new MinMax(
                Math.min(Double.isNaN(min) ? In : min, In),
                Math.max(Double.isNaN(max) ? In : max, In)
            )
        ));

        register("round", body(value -> Math.round(value)));
        register("floor", body(value -> Math.floor(value)));
        register("ceil", body(value -> Math.ceil(value)));
        register("clamp", body((value, min, max) -> Math.clamp(value, min, max)));
        register("map_range", body((value, in_min, in_max, out_min, out_max) ->
            (value - in_min) * (out_max - out_min) / (in_max - in_min) + out_min
        ));

        //register("curve", DefHelper.func4("value", (double value, double min, double max, double speed) -> __));


        register("vector_multiply", DefHelper.binary("value", (Vector3 a, Vector3 b) -> a.multiply(b)));
        register("vector_subtract", DefHelper.binary("value", (Vector3 a, Vector3 b) -> a.subtract(b)));
        register("vector_add", DefHelper.binary("value", (Vector3 a, Vector3 b) -> a.add(b)));
        register("vector_invert", DefHelper.unary("value", (Vector3 value) -> value.invert()));
        register("vector_magnitude", DefHelper.unary("value", (Vector3 value) -> value.magnitude()));
        register("vector_difference", DefHelper.binary("value", (Vector3 a, Vector3 b) -> a.difference(b)));
        register("vector_distance", DefHelper.binary("value", (Vector3 a, Vector3 b) -> a.distance(b)));

        //register("quaternion_to_euler", DefHelper.binary("euler", (Quaternion quaternion) -> Vector3 ?);
        //register("quaternion_to_tait_bryan", DefHelper.binary("tait_bryan", (Quaternion quaternion) -> Vector3 ?);
        //register("euler_to_quaternion", DefHelper.binary("quaternion", (Vector3 euler) -> Vector3 ?);
        //register("quaternion_to_tait_bryan", DefHelper.binary("quaternion", (Vector3 tait_bryan) -> Vector3 ?);
        //register("euler_to_tait_bryan", DefHelper.binary("tait_bryan", (Vector3 euler) -> Vector3 ?);
        //register("tait_bryan_to_euler", DefHelper.binary("euler", (Vector3 tait_bryan) -> Vector3 ?);

        register("random", DefHelper.source("value", () -> Math.random()));
        register("random_int", DefHelper.unary("value", (Double max) -> (double) (int) (Math.random() * max)));
        register("random_float_in_range", DefHelper.binary("value", (Double min, Double max) -> Math.random() * (max - min) + min));
        register("random_int_in_range", DefHelper.binary("value", (Double min, Double max) -> (int) (Math.random() * (max - min)) + min));
        return 1;
    }

    private static GenericLambdaNode<?> body(NumNum2Num body) {return GenericLambdaNode.make(1, body);}

    private static GenericLambdaNode<?> body(String outputPort, NumNum2Num body) {return GenericLambdaNode.make(DefHelper.options(outputPort), 1, body);}

    private static GenericLambdaNode<?> body(NumNumNum2Num body) {return GenericLambdaNode.make(1, body);}

    private static GenericLambdaNode<?> body(NumNumNumNum2Num body) {return GenericLambdaNode.make(1, body);}

    private static GenericLambdaNode<?> body(NumNumNumNumNum2Num body) {return GenericLambdaNode.make(1, body);}

    private static GenericLambdaNode<?> body(Num2Num body) {return GenericLambdaNode.make(1, body);}

    private static GenericLambdaNode<?> body(String outputPort, Num2Num body) {return GenericLambdaNode.make(DefHelper.options(outputPort), 1, body);}

    interface NumNum2Num {
        double get(double a, double b);
    }

    interface NumNumNum2Num {
        double get(double a, double b, double c);
    }

    interface NumNumNumNum2Num {
        double get(double a, double b, double c, double d);
    }

    interface NumNumNumNumNum2Num {
        double get(double p1, double p2, double p3, double p4, double p5);
    }

    interface Num2Num {
        double get(double value);
    }
}
