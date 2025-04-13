/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.DeclaredType;

public class CollectionTypeHandler extends DeclaredTypeHandler {
    CollectionTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    public KiwiType collectionOf(ValidCollection validCollection, KiwiType containedType) {
        if (containedType instanceof CollectionType collectionType) {
            containedType = collectionType.asSimple();
        }
        if (containedType instanceof RecordType || containedType.isSimple()) {
            return new CollectionType(validCollection, containedType.withIsNullable(false));
        }
        return KiwiType.unsupported();
    }

    private Optional<ValidCollection> findMatch(DeclaredType t) {
        return Stream.of(ValidCollection.values())
                .filter(vct -> isSameType(t, vct.javaType()))
                .findFirst();
    }

    @Override
    public KiwiType apply(DeclaredType declaredType) {
        return findMatch(declaredType)
                .map(vct -> {
                    var containedType = declaredType.getTypeArguments().get(0);
                    return collectionOf(vct, visit(containedType));
                })
                .orElseThrow();
    }

    @Override
    public boolean test(DeclaredType declaredType) {
        return findMatch(declaredType).isPresent();
    }
}
