package com.rieno.gadgetsandgizmos.compat.computercraft.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Document one Lua-facing peripheral method beside its implementation
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PeripheralDoc {
    String name();

    String signature();

    String description();

    String[] examples() default {};

    String since() default "2";

    String deprecatedBy() default "";

    boolean hidden() default false;
}
