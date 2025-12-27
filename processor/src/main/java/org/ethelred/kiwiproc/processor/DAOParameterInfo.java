/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.lang.model.element.Element;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.TypeUtils;

public record DAOParameterInfo(
        int index, MethodParameterInfo source, String setter, int sqlType, TypeMapping mapper, Conversion conversion)
        implements Supplier<Element> {
    public static List<DAOParameterInfo> from(
            CoreTypes coreTypes, TypeUtils typeUtils, Map<ColumnMetaData, MethodParameterInfo> parameterMapping) {
        List<DAOParameterInfo> result = new ArrayList<>(parameterMapping.size());

        parameterMapping.forEach(((columnMetaData, methodParameterInfo) -> {
            var sqlTypeMapping = SqlTypeMappingRegistry.get(columnMetaData);
            if (sqlTypeMapping.specialCase()) {
                // TODO
            }
            var setter = "set" + sqlTypeMapping.accessorSuffix();
            //            System.err.println(methodParameterInfo);
            var mapper = new TypeMapping(methodParameterInfo.type(), sqlTypeMapping.kiwiType());
            result.add(new DAOParameterInfo(
                    columnMetaData.index(),
                    methodParameterInfo,
                    setter,
                    columnMetaData.jdbcType().getVendorTypeNumber(),
                    mapper,
                    coreTypes.lookup(mapper)));
        }));
        return result;
    }

    public String javaAccessorSuffix() {
        return source.isRecordComponent() ? ".%s()".formatted(source.name().name()) : "";
    }

    @Override
    public Element get() {
        return source.variableElement();
    }
}
