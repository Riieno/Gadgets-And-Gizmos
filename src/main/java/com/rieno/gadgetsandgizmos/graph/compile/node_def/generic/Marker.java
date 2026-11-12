package com.rieno.gadgetsandgizmos.graph.compile.node_def.generic;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Marker {
    @Retention(RetentionPolicy.RUNTIME)
    public  @interface Name {
        String value();
    }
    public  @interface Data {
    }
    public  @interface State {

        boolean ref() default false;
    }
    public @interface PreviousOutput {
        /**
         * Port name
         * */
        String value() default "\n";
        /**
         * default value for boolean
         * */
        boolean defBool() default false;
        /**
         * default value for int/long
         * */
        long defInt() default 0;
        /**
         * default value for float/double
         * */
        double defFlt() default Double.NaN;
        /**
         * default value for String
         * */
        String defStr() default "";
        /**
         * default value for String
         * */
        boolean nullable() default false;
    }
}
