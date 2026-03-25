package io.github.arun0009.idempotent.core.annotation;

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

    /**
     * Specifies the key used to uniquely identify the idempotent request.
     * The key can be derived dynamically using a SpEL (Spring Expression Language) expression.
     *
     * @return the unique key identifying the idempotent request
     */
    String key() default "";

    /**
     * TTL (Time To Live) for idempotent item in store.
     * Defaults to 5 minutes.
     * @return the duration after which the idempotent key should expire
     */
    String duration() default "PT5M";

    /**
     * flag to set true if hashing idempotent key before storing. Set this to true if key is entity.
     * @return n/a
     */
    boolean hashKey() default false;
}
