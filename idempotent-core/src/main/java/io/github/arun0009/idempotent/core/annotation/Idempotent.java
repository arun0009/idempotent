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
     * Idempotent Key string.
     * @return n/a
     */
    String key() default "";

    /**
     * TTL (Time To Live) for idempotent item in store (redis/dynamo).
     * This is a more flexible alternative to ttlInSeconds that allows specifying
     * the duration in any time unit.
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
