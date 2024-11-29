/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public enum QueryMethodKind {
    DEFAULT(ExecutableElement::isDefault),
    QUERY(SqlQueryPrism::isPresent) {
        @Override
        public String getSql(ExecutableElement element) {
            var prism = SqlQueryPrism.getInstanceOn(element);
            return prism.sql().isBlank() ? prism.value() : prism.sql();
        }
    },
    UPDATE(SqlUpdatePrism::isPresent) {
        @Override
        public String getSql(ExecutableElement element) {
            var prism = SqlUpdatePrism.getInstanceOn(element);
            return prism.sql().isBlank() ? prism.value() : prism.sql();
        }

        @Override
        public boolean validateReturn(TypeUtils typeUtils, TypeMirror returnType) {
            //            var result = typeUtils.returnType(returnType);
            //            var kind = result.baseTypeKind();
            //                return kind == TypeKind.VOID
            //                        || (!result.isMultiValued() && (kind == TypeKind.INT || kind == TypeKind.LONG ||
            // kind == TypeKind.BOOLEAN));
            return true;
        }
    },
    BATCH(SqlBatchPrism::isPresent) {
        @Override
        public String getSql(ExecutableElement element) {
            var prism = SqlBatchPrism.getInstanceOn(element);
            return prism.sql().isBlank() ? prism.value() : prism.sql();
        }

        @Override
        public boolean validateReturn(TypeUtils typeUtils, TypeMirror returnType) {

            //                var result = typeUtils.returnType(returnType);
            //                var kind = result.baseTypeKind();
            //                return kind == TypeKind.VOID
            //                        || (result.isMultiValued() && (kind == TypeKind.INT || kind == TypeKind.LONG ||
            // kind == TypeKind.BOOLEAN));
            return true;
        }
    };

    private final Predicate<ExecutableElement> isKind;

    QueryMethodKind(Predicate<ExecutableElement> isKind) {
        this.isKind = isKind;
    }

    static Set<QueryMethodKind> forMethod(ExecutableElement methodElement) {
        return Stream.of(values()).filter(p -> p.isKind.test(methodElement)).collect(Collectors.toSet());
    }

    public String getSql(ExecutableElement element) {
        throw new UnsupportedOperationException();
    }

    public boolean validateReturn(TypeUtils typeUtils, TypeMirror returnType) {
        return true;
    }
}
