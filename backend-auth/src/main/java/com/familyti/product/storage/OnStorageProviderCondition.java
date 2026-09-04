package com.familyti.product.storage;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

public class OnStorageProviderCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes =
                metadata.getAnnotationAttributes(ConditionalOnStorageProvider.class.getName());
        if (attributes == null) {
            return false;
        }

        String required = StorageProperties.normalize((String) attributes.get("value"));
        String active = StorageProperties.normalize(
                context.getEnvironment().getProperty(StorageProperties.PROPERTY));

        return required.equals(active);
    }
}