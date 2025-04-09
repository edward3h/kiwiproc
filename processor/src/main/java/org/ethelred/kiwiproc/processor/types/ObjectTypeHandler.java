/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

import javax.lang.model.type.DeclaredType;
import org.ethelred.kiwiproc.processor.CoreTypes;

public class ObjectTypeHandler extends DeclaredTypeHandler {
    ObjectTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    @Override
    public KiwiType apply(DeclaredType declaredType) {
        return new ObjectType(
                utils.packageName(declaredType), utils.className(declaredType), utils.isNullable(declaredType));
    }

    @Override
    public boolean test(DeclaredType declaredType) {
        return CoreTypes.OBJECT_TYPES.stream().anyMatch(bt -> isSameType(declaredType, bt));
    }
}
