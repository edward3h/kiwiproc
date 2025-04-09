/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import com.google.common.truth.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.ObjectType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.jspecify.annotations.Nullable;

public class TestUtils {
    static Map<Class<?>, Class<?>> boxedToPrimitive = CoreTypes.primitiveToBoxed.entrySet().stream()
            .map(e -> Map.entry(e.getValue(), e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    static KiwiType ofClass(Class<?> k, @Nullable Boolean nullable) {
        if (k.isPrimitive()) {
            return new PrimitiveKiwiType(k.getSimpleName(), nullable != null && nullable);
        }
        if (boxedToPrimitive.containsKey(k)) {
            return new PrimitiveKiwiType(boxedToPrimitive.get(k).getSimpleName(), nullable == null || nullable);
        }
        return new ObjectType(k.getPackageName(), k.getSimpleName(), nullable != null && nullable);
    }

    static KiwiType ofClass(Class<?> k) {
        return ofClass(k, false);
    }

    @SafeVarargs
    static <S extends Subject> void atLeastOne(S subject, Consumer<S>... assertions) {
        List<AssertionError> errors = new ArrayList<>(assertions.length);
        for (var assertion : assertions) {
            try {
                assertion.accept(subject);
                return; // no exception if the assertion succeeds
            } catch (AssertionError error) {
                errors.add(error);
            }
        }
        // no assertion succeeded
        var message = errors.stream().map(Throwable::getMessage).collect(Collectors.joining("\n"));
        throw new AssertionError(message);
    }
}
