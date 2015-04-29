package net.minecraftforge.gradle.util.caching;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 
 * @author abrarsyed
 *
 * This annotation is used to mark outputs that should be cached.
 * This is only effective if used with a CacheContainer and an ICachableTask
 */
@Target( { ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Cached { }
