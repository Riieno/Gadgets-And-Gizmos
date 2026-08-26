package com.rieno.gadgetsandgizmos.compat.computercraft.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Declare the Lua peripheral type names documented by one adapter class
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PeripheralTypeDoc {
    String[] value() default {};

    String[] prefixes() default {};
}
