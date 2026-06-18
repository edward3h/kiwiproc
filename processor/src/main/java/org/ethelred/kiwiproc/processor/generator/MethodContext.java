/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.generator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

class MethodContext {
    private final Set<String> parameterNames = new HashSet<>();
    private final Map<Object, String> patchedNames = new HashMap<>();
    private int patchedNameCount = 0;

    void registerParameterName(String name) {
        parameterNames.add(name);
    }

    // one-off name, never re-looked-up, so always allocate fresh rather than caching by name
    String patchName(String name) {
        return patchName(new Object(), name);
    }

    // key must uniquely identify the logical entity, since two entities can request the same name
    String patchName(Object key, String name) {
        return patchedNames.computeIfAbsent(key, k -> {
            var newName = name;
            while (parameterNames.contains(newName) || patchedNames.containsValue(newName)) {
                newName = name + (++patchedNameCount);
            }
            return newName;
        });
    }

    @Nullable String patchedNameFor(Object key) {
        return patchedNames.get(key);
    }
}
