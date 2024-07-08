package org.ethelred.kiwiproc.processor;

import org.jspecify.annotations.Nullable;

import javax.lang.model.type.TypeKind;

public record ReturnType(
        String baseType,
        TypeKind baseTypeKind,
        @Nullable ContainerType containerType

) {
    public boolean isMultiValued() {
        return containerType != null && containerType.isMultiValued();
    }

    public String declaration() {
        if (containerType != null) {
            return "%s<%s>".formatted(containerType.javaType().getName(), baseType);
        }
        return baseType;
    }
}
