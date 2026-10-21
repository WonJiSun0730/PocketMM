package com.example.projectmm.infrastructure.ai;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
public class TextKeyCondition implements Condition {
    public boolean matches(ConditionContext context,AnnotatedTypeMetadata metadata) {
        var attributes=metadata.getAnnotationAttributes(ConditionalOnTextKey.class.getName());
        boolean required=attributes==null||Boolean.TRUE.equals(attributes.get("value"));
        boolean configured=!context.getEnvironment().getProperty("gemini.api-key","").isBlank()
                || !context.getEnvironment().getProperty("openai.api-key","").isBlank();
        return configured==required;
    }
}
