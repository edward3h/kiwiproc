/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import javax.lang.model.type.DeclaredType;

public class OptionalTypeHandler extends DeclaredTypeHandler {
    private final Map<Class<?>, Function<DeclaredType, KiwiType>> optionalMappings = Map.of(
            Optional.class, this::fromOptional,
            OptionalInt.class, (ignore -> new PrimitiveKiwiType("int", false)),
            OptionalLong.class, (ignore -> new PrimitiveKiwiType("long", false)),
            OptionalDouble.class, (ignore -> new PrimitiveKiwiType("double", false)));

    private KiwiType fromOptional(DeclaredType declaredType) {
        var containedType = declaredType.getTypeArguments().get(0);
        var containedKiwiType = visit(containedType);
        return containedKiwiType;
    }

    OptionalTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    private Optional<Class<?>> findMatch(DeclaredType t) {
        return optionalMappings.keySet().stream()
                .filter(cl -> isSameType(t, cl))
                .findFirst();
    }

    @Override
    public KiwiType apply(DeclaredType declaredType) {
        var typeMatch = findMatch(declaredType).orElseThrow();
        return findMatch(declaredType)
                .map(optionalMappings::get)
                .map(fn -> fn.apply(declaredType))
                .map(contained -> new OptionalType(typeMatch, contained.withIsNullable(false)))
                .orElseThrow();
    }

    @Override
    public boolean test(DeclaredType declaredType) {
        return findMatch(declaredType).isPresent();
    }
}
