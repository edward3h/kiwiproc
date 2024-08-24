package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilderFull;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;

@KiwiRecordBuilder
public record Signature(KiwiType returnType, String methodName, List<DAOResultColumn> params) {
    static Signature fromMethod(TypeUtils typeUtils, ExecutableElement element) {
        List<DAOResultColumn> params = new ArrayList<>();
        for (var parameterElement : element.getParameters()) {
            //            params.add(
            //                    new DAOResultColumn(
            //                            parameterElement.getSimpleName().toString(),
            // SqlTypeMapping.get(JDBCType.INTEGER)) // TODO
            //                    );
        }
        return new Signature(
                typeUtils.kiwiType(element.getReturnType()),
                element.getSimpleName().toString(),
                params);
    }
}
