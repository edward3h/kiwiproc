/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.VariableElement;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.jspecify.annotations.Nullable;

public record DAOParameterInfo(
        int index,
        String javaAccessor,
        String setter,
        int sqlType,
        TypeMapping mapper,
        VariableElement element,
        @Nullable String arrayComponent) {
    public static List<DAOParameterInfo> from(
            TypeUtils typeUtils, Map<ColumnMetaData, MethodParameterInfo> parameterMapping) {
        List<DAOParameterInfo> result = new ArrayList<>(parameterMapping.size());

        parameterMapping.forEach(((columnMetaData, methodParameterInfo) -> {
            String accessor = methodParameterInfo.name();
            if (methodParameterInfo.isRecordComponent()) {
                accessor = "%s.%s()".formatted(methodParameterInfo.recordParameterName(), methodParameterInfo.name());
            }
            var sqlTypeMapping = SqlTypeMapping.get(columnMetaData);
            if (sqlTypeMapping.specialCase()) {
                // TODO
            }
            var setter = "set" + sqlTypeMapping.accessorSuffix();
            var mapper = new TypeMapping(methodParameterInfo.type(), sqlTypeMapping.kiwiType());
            result.add(new DAOParameterInfo(
                    columnMetaData.index(),
                    accessor,
                    setter,
                    columnMetaData.sqlType().getVendorTypeNumber(),
                    mapper,
                    methodParameterInfo.variableElement(),
                    null));
        }));
        return result;
    }
}
