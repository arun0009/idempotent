package com.codeweave.idempotent.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * &#64;Idempotent is used to signal that the annotated method is idempotent:<br/>
 * Calling this method one or multiple times with the same parameter will always return the same result.<br/>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    String key() default "";

    long ttlInSeconds() default 300L;

    boolean hashKey() default false;
}
