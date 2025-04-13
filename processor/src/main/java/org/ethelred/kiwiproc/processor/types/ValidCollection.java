/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public enum ValidCollection {
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
            """);

    private final Class<?> javaType;

    public String fromListTemplate() {
        return fromListTemplate;
    }

    private final String fromListTemplate;
    private final String toStreamTemplate;

    ValidCollection(Class<?> javaType, String fromListTemplate, String toStreamTemplate) {
        this.javaType = javaType;
        this.fromListTemplate = fromListTemplate;
        this.toStreamTemplate = toStreamTemplate;
    }

    ValidCollection(Class<?> javaType) {
        this(javaType, "List.copyOf($listVariable:L)", "$containerVariable:L.stream()");
    }

    ValidCollection(Class<?> javaType, String fromListTemplate) {
        this(javaType, fromListTemplate, "$containerVariable:L.stream()");
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
