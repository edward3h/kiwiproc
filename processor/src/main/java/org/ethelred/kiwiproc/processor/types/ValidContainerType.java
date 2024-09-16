package org.ethelred.kiwiproc.processor.types;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public enum ValidContainerType {
    ARRAY(
            Array.class,
            """
            l.toArray(new %s[l.size()])
            """,
            """
                    java.util.Arrays.copyOf(%s, %<s.length, Object[].class)
                    """),
    ITERABLE(Iterable.class),
    COLLECTION(Collection.class),
    LIST(List.class),
    SET(Set.class, """
            new java.util.LinkedHashSet<>(l)
            """),
    OPTIONAL(
            Optional.class,
            """
            l.isEmpty() ? Optional.empty() : Optional.of(l.get(0))
            """,
            """
                    %s.stream().toArray()""");

    private final Class<?> javaType;

    public String fromListTemplate() {
        return fromListTemplate;
    }

    private final String fromListTemplate;
    private final String toObjectArrayTemplate;

    ValidContainerType(Class<?> javaType, String fromListTemplate, String toObjectArrayTemplate) {
        this.javaType = javaType;
        this.fromListTemplate = fromListTemplate;
        this.toObjectArrayTemplate = toObjectArrayTemplate;
    }

    ValidContainerType(Class<?> javaType) {
        this(javaType, "List.copyOf(l)", "%s.toArray()");
    }

    ValidContainerType(Class<?> javaType, String fromListTemplate) {
        this(javaType, fromListTemplate, "%s.toArray()");
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

    public String toObjectArrayTemplate() {
        return toObjectArrayTemplate;
    }
}
