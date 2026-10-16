package com.example.projectmm.infrastructure.ai;
import java.lang.annotation.*;
import org.springframework.context.annotation.Conditional;
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(TextKeyCondition.class)
public @interface ConditionalOnTextKey { boolean value() default true; }
