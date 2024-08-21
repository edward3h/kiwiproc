package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;

import java.sql.JDBCType;
import java.util.Set;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class SqlTypeMappingTest {

    Set<JDBCType> unsupportedTypes = Set.of(
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
    }
}
