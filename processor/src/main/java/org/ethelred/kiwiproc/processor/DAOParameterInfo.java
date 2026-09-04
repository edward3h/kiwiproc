/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.sql.JDBCType;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.lang.model.element.Element;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.TypeUtils;

public record DAOParameterInfo(
        int index,
        MethodParameterInfo source,
        String setter,
        int sqlType,
        TypeMapping mapper,
        Conversion conversion,
        boolean useTypedObjectSetter)
        implements Supplier<Element> {
    public static List<DAOParameterInfo> from(
            CoreTypes coreTypes, TypeUtils typeUtils, Map<ColumnMetaData, MethodParameterInfo> parameterMapping) {
        List<DAOParameterInfo> result = new ArrayList<>(parameterMapping.size());

        parameterMapping.forEach(((columnMetaData, methodParameterInfo) -> {
            var sqlTypeMapping = SqlTypeMappingRegistry.get(columnMetaData);
            var setter = "set" + sqlTypeMapping.accessorSuffix();
            var mapper = new TypeMapping(methodParameterInfo.type(), sqlTypeMapping.kiwiType());
            var conversion = coreTypes.lookup(mapper);
            // For unknown SQL types (e.g. MySQL parameter metadata unavailable), fall back to assignment
            if (!conversion.isValid() && columnMetaData.jdbcType() == JDBCType.OTHER) {
                conversion = new AssignmentConversion();
            }
            boolean useTypedObjectSetter = false;
            var sqlType = columnMetaData.jdbcType().getVendorTypeNumber();
            // Enum parameters must use setObject(index, value, Types.OTHER) so PostgreSQL
            // accepts both VARCHAR-backed and native enum columns without a type mismatch.
            if (innerConversion(conversion) instanceof EnumToStringConversion) {
                setter = "setObject";
                sqlType = Types.OTHER;
                useTypedObjectSetter = true;
            }
            // json/jsonb parameters must use setObject(index, value, Types.OTHER) too: PostgreSQL
            // rejects a plain setString()/varchar bind against a jsonb column ("column is of type
            // jsonb but expression is of type character varying"). Types.OTHER + a String is
            // pgjdbc's documented lightweight alternative to constructing a PGobject.
            if (columnMetaData.jdbcType() == JDBCType.OTHER && isJsonDbType(columnMetaData.dbType())) {
                setter = "setObject";
                sqlType = Types.OTHER;
                useTypedObjectSetter = true;
            }
            result.add(new DAOParameterInfo(
                    columnMetaData.index(),
                    methodParameterInfo,
                    setter,
                    sqlType,
                    mapper,
                    conversion,
                    useTypedObjectSetter));
        }));
        return result;
    }

    private static boolean isJsonDbType(String dbType) {
        return "json".equalsIgnoreCase(dbType) || "jsonb".equalsIgnoreCase(dbType);
    }

    public String javaAccessorSuffix() {
        return source.isRecordComponent() ? ".%s()".formatted(source.name().name()) : "";
    }

    private static Conversion innerConversion(Conversion conversion) {
        if (conversion instanceof NullableSourceConversion nsc) {
            return nsc.conversion();
        }
        return conversion;
    }

    @Override
    public Element get() {
        return source.variableElement();
    }
}
