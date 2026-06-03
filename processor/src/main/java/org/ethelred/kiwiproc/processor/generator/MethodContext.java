/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.generator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

class MethodContext {
    private final Set<String> parameterNames = new HashSet<>();
    private final Map<String, String> patchedNames = new HashMap<>();
    private int patchedNameCount = 0;

    void registerParameterName(String name) {
        parameterNames.add(name);
    }

    String patchName(String name) {
        return patchedNames.computeIfAbsent(name, k -> {
            var newName = k;
            while (parameterNames.contains(newName) || patchedNames.containsValue(newName)) {
                newName = k + (++patchedNameCount);
            }
            return newName;
        });
    }

    @Nullable String patchedNameFor(String name) {
        return patchedNames.get(name);
    }
}
