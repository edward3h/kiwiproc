package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilderFull;

import javax.lang.model.element.ExecutableElement;
import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;

@RecordBuilderFull
public record Signature(ReturnType returnType, String name, List<DAOResultInfo> params) {
    static Signature fromMethod(TypeUtils typeUtils, ExecutableElement element) {
        List<DAOResultInfo> params = new ArrayList<>();
        for (var parameterElement: element.getParameters()) {
            params.add(
                    new DAOResultInfo(parameterElement.getSimpleName().toString(),
                    parameterElement.asType().toString(),
                    SqlTypeMapping.get(JDBCType.INTEGER)) // TODO
            );
        }
        return new Signature(
                typeUtils.returnType(element.getReturnType()),
                element.getSimpleName().toString(),
                params
        );
    }

    public String returnTypeDeclaration() {
        return returnType.declaration();
    }
}
