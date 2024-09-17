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
            l.toArray(new $componentClass:T[$listVariable:L.size()])
            """,
            """
                    java.util.stream.Stream.of($containerVariable:L)
                    """),
    ITERABLE(
            Iterable.class,
            """
            List.copyOf($listVariable:L)""",
            """
                    java.util.stream.StreamSupport.stream($containerVariable:L.spliterator(), false)
                    """),
    COLLECTION(Collection.class),
    LIST(List.class),
    SET(Set.class, """
            new java.util.LinkedHashSet<>($listVariable:L)
            """),
    OPTIONAL(
            Optional.class,
            """
            $listVariable:L.isEmpty() ? Optional.empty() : Optional.of($listVariable:L.get(0))
            """);

    private final Class<?> javaType;

    public String fromListTemplate() {
        return fromListTemplate;
    }

    private final String fromListTemplate;
    private final String toStreamTemplate;

    ValidContainerType(Class<?> javaType, String fromListTemplate, String toStreamTemplate) {
        this.javaType = javaType;
        this.fromListTemplate = fromListTemplate;
        this.toStreamTemplate = toStreamTemplate;
    }

    ValidContainerType(Class<?> javaType) {
        this(javaType, "List.copyOf($listVariable:L)", "$containerVariable:L.stream()");
    }

    ValidContainerType(Class<?> javaType, String fromListTemplate) {
        this(javaType, fromListTemplate, "$containerVariable:L.stream()");
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

    public String toStreamTemplate() {
        return toStreamTemplate;
    }
}
