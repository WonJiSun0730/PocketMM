package com.example.projectmm.infrastructure.ai;

import java.lang.annotation.*;
import org.springframework.context.annotation.Conditional;

/** Provider availability must not depend on deployment profiles such as prod/local. */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(GeminiKeyCondition.class)
public @interface ConditionalOnGeminiKey {
    boolean value() default true;
}
