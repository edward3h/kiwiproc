package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record DAOParameterInfo(int index, String javaAccessor, String setter, TypeMapping mapper, @Nullable String arrayComponent) {
    public static List<DAOParameterInfo> from(TypeUtils typeUtils, Map<ColumnMetaData, MethodParameterInfo> parameterMapping) {
        List<DAOParameterInfo> result = new ArrayList<>(parameterMapping.size());

        parameterMapping.forEach(((columnMetaData, methodParameterInfo) -> {
            String accessor = methodParameterInfo.name();
            if (methodParameterInfo.isRecordComponent()) {
                accessor = "%s.%s()".formatted(methodParameterInfo.recordParameterName(), methodParameterInfo.name());
            }
            var jdbcType = columnMetaData.sqlType();
            var sqlTypeMapping = SqlTypeMapping.get(jdbcType);
            if (sqlTypeMapping.specialCase()) {
                // TODO
            }
            var setter = "set" + sqlTypeMapping.accessorSuffix();
            var mapper = new TypeMapping(methodParameterInfo.type().toString(), sqlTypeMapping.baseType().getName());
            result.add(new DAOParameterInfo(columnMetaData.index(), accessor, setter,mapper, null));
        }));
        return result;
    }
}
