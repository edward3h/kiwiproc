package org.ethelred.kiwiproc.processor;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public enum ContainerType {
    ARRAY(Array.class, """
            l.toArray(new %s[l.size()])
            """),
    ITERABLE(Iterable.class),
    COLLECTION(Collection.class),
    LIST(List.class),
    SET(Set.class, """
            new LinkedHashSet<>(l)
            """),
    OPTIONAL(Optional.class, """
            l.isEmpty() ? Optional.empty() : Optional.of(l.get(0))
            """)
    ;

    private final Class<?> javaType;

    public String fromListTemplate() {
        return fromListTemplate;
    }

    private final String fromListTemplate;

    ContainerType(Class<?> javaType, String fromListTemplate) {
        this.javaType = javaType;
        this.fromListTemplate = fromListTemplate;
    }

    ContainerType(Class<?> javaType) {
        this(javaType, "List.copyOf(l)");
    }

    public boolean isMultiValued() {
        return this != OPTIONAL;
    }
    public Class<?> javaType() {
        return javaType;
    }
}
