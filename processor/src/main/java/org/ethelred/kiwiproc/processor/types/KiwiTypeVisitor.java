/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import java.util.List;
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor14;

public class KiwiTypeVisitor extends SimpleTypeVisitor14<KiwiType, Void> {

    private final List<DeclaredTypeHandler> declaredTypeHandlers;
    private final CollectionTypeHandler collectionTypeHandler;

    public KiwiTypeVisitor(TypeUtils utils) {
        collectionTypeHandler = new CollectionTypeHandler(this, utils);
        declaredTypeHandlers = List.of(
                new BoxedTypeHandler(this, utils),
                new ObjectTypeHandler(this, utils),
                collectionTypeHandler,
                new MapTypeHandler(this, utils),
                new RecordTypeHandler(this, utils),
                new OptionalTypeHandler(this, utils));
    }

    @Override
    public KiwiType visitPrimitive(PrimitiveType t, Void ignore) {
        return new PrimitiveKiwiType(t.toString(), false);
    }

    @Override
    public KiwiType visitArray(ArrayType t, Void ignore) {
        return collectionTypeHandler.collectionOf(ValidCollection.ARRAY, visit(t.getComponentType()));
    }

    @Override
    public KiwiType visitDeclared(DeclaredType t, Void ignore) {
        return declaredTypeHandlers.stream()
                .filter(h -> h.test(t))
                .map(h -> h.apply(t))
                .findFirst()
                .orElse(KiwiType.unsupported());
    }

    @Override
    public KiwiType visitNoType(NoType t, Void unused) {
        if (t.getKind().equals(TypeKind.VOID)) {
            return new VoidType();
        }
        return KiwiType.unsupported();
    }
}
