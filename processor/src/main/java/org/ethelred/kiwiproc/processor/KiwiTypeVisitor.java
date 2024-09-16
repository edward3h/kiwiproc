package org.ethelred.kiwiproc.processor;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor14;
import org.ethelred.kiwiproc.processor.types.*;

public class KiwiTypeVisitor extends SimpleTypeVisitor14<KiwiType, Void> {
    private final TypeUtils utils;

    public KiwiTypeVisitor(TypeUtils utils) {
        this.utils = utils;
    }

    @Override
    public KiwiType visitPrimitive(PrimitiveType t, Void ignore) {
        return new PrimitiveKiwiType(t.toString(), false);
    }

    @Override
    public KiwiType visitArray(ArrayType t, Void ignore) {
        return new ContainerType(ValidContainerType.ARRAY, visit(t.getComponentType()));
    }

    @Override
    public KiwiType visitDeclared(DeclaredType t, Void ignore) {
        try {
            var primitiveType = utils.unboxedType(t);
            return new PrimitiveKiwiType(primitiveType.toString(), true);

        } catch (IllegalArgumentException e) {
            // not a boxed type - continue
        }
        if (CoreTypes.BASIC_TYPES.stream().anyMatch(bt -> utils.isSameType(t, utils.type(bt)))) {
            return new BasicType(utils.packageName(t), utils.className(t), utils.isNullable(t));
        }
        for (var vct : ValidContainerType.values()) {
            if (utils.isSameType(utils.erasure(t), utils.erasure(vct.javaType()))) {
                var typeArguments = t.getTypeArguments();
                return new ContainerType(vct, visit(typeArguments.get(0)));
            }
        }
        if (utils.isRecord(t)) {
            var componentTypes = new ArrayList<RecordTypeComponent>();
            for (var component : utils.recordComponents(t)) {
                componentTypes.add(
                        new RecordTypeComponent(component.getSimpleName().toString(), visit(component.asType())));
            }
            return new RecordType(utils.packageName(t), utils.className(t), List.copyOf(componentTypes));
        }
        return KiwiType.unsupported();
    }

    @Override
    public KiwiType visitNoType(NoType t, Void unused) {
        if (t.getKind().equals(TypeKind.VOID)) {
            return new VoidType();
        }
        return KiwiType.unsupported();
    }
}
