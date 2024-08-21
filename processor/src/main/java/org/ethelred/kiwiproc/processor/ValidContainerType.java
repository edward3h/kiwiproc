package org.ethelred.kiwiproc.processor;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public enum ValidContainerType {
    ARRAY(Array.class, """
            l.toArray(new %s[l.size()])
            """),
    ITERABLE(Iterable.class),
    COLLECTION(Collection.class),
    LIST(List.class),
    SET(Set.class, """
            new java.util.LinkedHashSet<>(l)
            """),
    OPTIONAL(Optional.class, """
            l.isEmpty() ? Optional.empty() : Optional.of(l.get(0))
            """);

    private final Class<?> javaType;

    public String fromListTemplate() {
        return fromListTemplate;
    }

    private final String fromListTemplate;

    ValidContainerType(Class<?> javaType, String fromListTemplate) {
        this.javaType = javaType;
        this.fromListTemplate = fromListTemplate;
    }

    ValidContainerType(Class<?> javaType) {
        this(javaType, "List.copyOf(l)");
    }

    public boolean isMultiValued() {
        return this != OPTIONAL;
    }

    public Class<?> javaType() {
        return javaType;
    }

    @Override
    public String toString() {
        return javaType().getName();
    }
}
