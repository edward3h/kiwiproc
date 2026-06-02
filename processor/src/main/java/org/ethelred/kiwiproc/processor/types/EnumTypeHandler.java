/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

class EnumTypeHandler extends DeclaredTypeHandler {
    EnumTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    @Override
    public KiwiType apply(DeclaredType t) {
        return new EnumType(utils.packageName(t), utils.className(t), utils.isNullable(t));
    }

    @Override
    public boolean test(DeclaredType t) {
        return ((TypeElement) t.asElement()).getKind() == ElementKind.ENUM;
    }
}
