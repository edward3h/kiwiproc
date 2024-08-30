package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;
import static org.ethelred.kiwiproc.processor.TestUtils.atLeastOne;

import java.sql.JDBCType;
import java.util.Set;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.BasicType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.ethelred.kiwiproc.processor.types.SqlArrayType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @ParameterizedTest
    @EnumSource(JDBCType.class)
    public void sqlMappingIsPresentForJDBCType(JDBCType jdbcType) {
        if (unsupportedTypes.contains(jdbcType)) {
            return;
        }
        var columnMetaData = new ColumnMetaData(1, "columnName", false, jdbcType, null);
        var mapping = SqlTypeMapping.get(columnMetaData);
        assertThat(mapping).isNotNull();
        atLeastOne(
                assertThat(mapping.kiwiType()),
                s -> s.isInstanceOf(PrimitiveKiwiType.class),
                s -> s.isInstanceOf(BasicType.class));
    }

    @ParameterizedTest
    @EnumSource(JDBCType.class)
    public void sqlMappingIsPresentForArrayType(JDBCType componentType) {
        if (unsupportedTypes.contains(componentType)) {
            return;
        }
        var columnMetaData = new ColumnMetaData(1, "columnName", false, JDBCType.ARRAY, componentType);
        var mapping = SqlTypeMapping.get(columnMetaData);
        assertThat(mapping).isNotNull();
        assertThat(mapping.kiwiType()).isInstanceOf(SqlArrayType.class);
    }
}
