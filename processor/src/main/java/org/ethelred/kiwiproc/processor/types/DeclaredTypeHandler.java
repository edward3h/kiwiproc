/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

import java.util.function.Function;
import java.util.function.Predicate;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

abstract class DeclaredTypeHandler implements Predicate<DeclaredType>, Function<DeclaredType, KiwiType> {
    protected final KiwiTypeVisitor visitor;
    protected final TypeUtils utils;

    DeclaredTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        this.visitor = visitor;
        this.utils = utils;
    }

    protected KiwiType visit(TypeMirror t) {
        return visitor.visit(t);
    }

    protected boolean isSameType(DeclaredType t, Class<?> cl) {
        return utils.isSameType(utils.erasure(t), utils.erasure(cl));
    }
}
