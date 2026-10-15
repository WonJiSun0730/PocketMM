package com.example.projectmm.infrastructure.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GeminiKeyCondition implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var attributes=metadata.getAnnotationAttributes(ConditionalOnGeminiKey.class.getName());
        boolean required=attributes==null || Boolean.TRUE.equals(attributes.get("value"));
        boolean configured=!context.getEnvironment().getProperty("gemini.api-key", "").isBlank();
        return configured==required;
    }
}
