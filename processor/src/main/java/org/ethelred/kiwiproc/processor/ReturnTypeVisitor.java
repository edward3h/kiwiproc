package org.ethelred.kiwiproc.processor;

import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor14;
import java.util.Optional;

class ReturnTypeVisitor extends SimpleTypeVisitor14<Optional<ReturnType>, Void> {
    private final TypeUtils typeUtils;

    public ReturnTypeVisitor(TypeUtils typeUtils) {
        super(Optional.empty());
        this.typeUtils = typeUtils;
    }

    @Override
    public Optional<ReturnType> visitPrimitive(PrimitiveType t, Void unused) {
        return Optional.of(new ReturnType(t.toString(), t.getKind(), null));
    }

    @Override
    public Optional<ReturnType> visitArray(ArrayType t, Void unused) {
        var componentType = visit(t.getComponentType());
        return componentType.map(s -> new ReturnType(s.baseType(), s.baseTypeKind(), ContainerType.ARRAY));
    }

    @Override
    public Optional<ReturnType> visitDeclared(DeclaredType t, Void unused) {
        try {
            var unboxed = typeUtils.unboxedType(t);
            return visitPrimitive(unboxed, null);
        } catch (IllegalArgumentException e) {
            // ignore
        }
        var containerType = typeUtils.containerType(t);
        if (containerType != null) {
            var componentType = visit(t.getTypeArguments().get(0));
            return componentType.map(s -> new ReturnType(s.baseType(), s.baseTypeKind(), containerType));
        }
        return Optional.of(new ReturnType(typeUtils.toString(t), t.getKind(), null));
    }

    @Override
    public Optional<ReturnType> visitNoType(NoType t, Void unused) {
        return Optional.of(new ReturnType("void", TypeKind.VOID, null));
    }
}
