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
        boolean useTypedObjectSetter,
        boolean encodeAsUtf8Bytes)
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
            boolean encodeAsUtf8Bytes = false;
            if (columnMetaData.jdbcType() == JDBCType.OTHER
                    && SqlTypeMappingRegistry.isJsonDbType(columnMetaData.dbType())) {
                setter = "setObject";
                sqlType = Types.OTHER;
                useTypedObjectSetter = true;
                // H2 reports its JSON parameter class as byte[] ("[B") and, unlike PostgreSQL,
                // coerces a bound java.lang.String through its string-to-JSON conversion --
                // which re-quotes/escapes the text as a JSON *string value* instead of storing
                // it as the JSON document it already is (verified against H2 2.5.250: setObject
                // with a String, with or without Types.OTHER, produces a double-encoded
                // "{\"color\":\"red\"}" while setObject with the equivalent UTF-8 byte[]
                // round-trips correctly). Encoding to UTF-8 bytes first bypasses that coercion.
                // PostgreSQL's jsonb parameter class name is not byte[], so this is inert there.
                //
                // Keyed off dbClassName rather than a database-kind check because DatabaseKind
                // isn't available at this layer (DAOParameterInfo/SqlTypeMappingRegistry only see
                // JDBC-derived ColumnMetaData); threading it through would be a larger refactor
                // than this quirk warrants. Revisit if a future database needs a similar
                // byte[]-vs-String distinction that this heuristic doesn't capture correctly.
                if ("[B".equals(columnMetaData.dbClassName())) {
                    encodeAsUtf8Bytes = true;
                }
            }
            result.add(new DAOParameterInfo(
                    columnMetaData.index(),
                    methodParameterInfo,
                    setter,
                    sqlType,
                    mapper,
                    conversion,
                    useTypedObjectSetter,
                    encodeAsUtf8Bytes));
        }));
        return result;
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
