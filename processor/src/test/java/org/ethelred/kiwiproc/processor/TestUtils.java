package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.processor.types.BasicType;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

public class TestUtils {
    static Map<Class<?>, Class<?>> boxedToPrimitive = CoreTypes.primitiveToBoxed.entrySet()
            .stream().map(e -> Map.entry(e.getValue(), e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    static KiwiType ofClass(Class<?> k, @Nullable Boolean nullable) {
        if (k.isPrimitive()) {
            return new PrimitiveKiwiType(k.getSimpleName(), nullable != null && nullable);
        }
        if (boxedToPrimitive.containsKey(k)) {
            return new PrimitiveKiwiType(boxedToPrimitive.get(k).getSimpleName(), nullable == null || nullable);
        }
        return new BasicType(k.getPackageName(), k.getSimpleName(), nullable != null && nullable);
    }

    static KiwiType ofClass(Class<?> k) {
        return ofClass(k, false);
    }
}
