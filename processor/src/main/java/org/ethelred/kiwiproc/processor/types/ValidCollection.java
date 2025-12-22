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
                    """,
            """
                    org.ethelred.kiwiproc.impl.ArraySupport.iterator($containerVariable:L)"""),
    ITERABLE(
            Iterable.class,
            """
            List.copyOf($listVariable:L)""",
            """
                    java.util.stream.StreamSupport.stream($containerVariable:L.spliterator(), false)
                    """,
            """
                    $containerVariable:L.iterator()"""),
    COLLECTION(Collection.class),
    LIST(List.class),
    SET(Set.class, """
            new java.util.LinkedHashSet<>($listVariable:L)
            """);

    private final Class<?> javaType;
    private String toIteratorTemplate;

    public String fromListTemplate() {
        return fromListTemplate;
    }

    private final String fromListTemplate;
    private final String toStreamTemplate;

    ValidCollection(Class<?> javaType, String fromListTemplate, String toStreamTemplate, String toIteratorTemplate) {
        this.javaType = javaType;
        this.fromListTemplate = fromListTemplate;
        this.toStreamTemplate = toStreamTemplate;
        this.toIteratorTemplate = toIteratorTemplate;
    }

    ValidCollection(Class<?> javaType) {
        this(javaType, "List.copyOf($listVariable:L)");
    }

    ValidCollection(Class<?> javaType, String fromListTemplate) {
        this(javaType, fromListTemplate, "$containerVariable:L.stream()", "$containerVariable:L.iterator()");
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

    public boolean hasKnownSize() {
        return this != ITERABLE;
    }

    public String toIteratorTemplate() {
        return toIteratorTemplate;
    }
}
