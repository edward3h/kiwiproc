/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

import java.util.Optional;
import javax.lang.model.type.DeclaredType;

final class BoxedTypeHandler extends DeclaredTypeHandler {

    BoxedTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    private Optional<KiwiType> evaluate(DeclaredType t) {
        try {
            var primitiveType = utils.unboxedType(t);
            return Optional.of(new PrimitiveKiwiType(primitiveType.toString(), true));

        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public KiwiType apply(DeclaredType declaredType) {
        return evaluate(declaredType).orElseThrow();
    }

    @Override
    public boolean test(DeclaredType declaredType) {
        return evaluate(declaredType).isPresent();
    }
}
