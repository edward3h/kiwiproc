/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;
import static org.ethelred.kiwiproc.processor.TestUtils.atLeastOne;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.sql.JDBCType;
import java.util.Set;
import java.util.stream.Stream;
import org.ethelred.kiwiproc.meta.ArrayComponent;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.BasicType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.ethelred.kiwiproc.processor.types.SqlArrayType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class SqlTypeMappingTest {

    Set<JDBCType> unsupportedTypes = Set.of(
            JDBCType.ARRAY, // special case below
            // Consider supporting the below, but I haven't seen them used in projects I've worked on.
            JDBCType.BINARY,
            JDBCType.VARBINARY,
            JDBCType.LONGVARBINARY,
            JDBCType.OTHER,
            JDBCType.JAVA_OBJECT,
            JDBCType.DISTINCT,
            JDBCType.STRUCT,
            JDBCType.BLOB,
            JDBCType.CLOB,
            JDBCType.NCLOB,
            JDBCType.REF,
            JDBCType.DATALINK,
            JDBCType.ROWID, // TODO?
            JDBCType.SQLXML,
            JDBCType.REF_CURSOR);

    public static Stream<Arguments> sqlMappingIsPresentForArrayType() {
        // arguments - JDBCType, String: db specific type name
        return Stream.of(arguments(JDBCType.INTEGER, "fail"));
    }

    @ParameterizedTest
    @EnumSource(JDBCType.class)
    public void sqlMappingIsPresentForJDBCType(JDBCType jdbcType) {
        if (unsupportedTypes.contains(jdbcType)) {
            return;
        }
        var columnMetaData = new ColumnMetaData(1, "columnName", false, jdbcType, "butt", "poop", null);
        var mapping = SqlTypeMappingRegistry.get(columnMetaData);
        assertThat(mapping).isNotNull();
        atLeastOne(
                assertThat(mapping.kiwiType()),
                s -> s.isInstanceOf(PrimitiveKiwiType.class),
                s -> s.isInstanceOf(BasicType.class));
    }

    @ParameterizedTest
    @MethodSource
    public void sqlMappingIsPresentForArrayType(JDBCType componentType, String dbType) {
        if (unsupportedTypes.contains(componentType)) {
            return;
        }
        var columnMetaData = new ColumnMetaData(
                1, "columnName", false, JDBCType.ARRAY, "butt", "poop", new ArrayComponent(componentType, dbType));
        var mapping = SqlTypeMappingRegistry.get(columnMetaData);
        assertThat(mapping).isNotNull();
        assertThat(mapping.kiwiType()).isInstanceOf(SqlArrayType.class);
    }
}
