/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import java.sql.JDBCType;
import java.util.Map;
import java.util.Optional;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.ValidCollection;

public record DAOBatchIterator(MethodParameterInfo source, ValidCollection validCollection) {
    public static Optional<DAOBatchIterator> from(Map.Entry<ColumnMetaData, MethodParameterInfo> entry) {
        return from(entry.getKey(), entry.getValue());
    }

    private static Optional<DAOBatchIterator> from(ColumnMetaData columnMetaData, MethodParameterInfo parameterInfo) {
        System.err.println("batchiterator.from " + parameterInfo);
        if (!parameterInfo.isRecordComponent()
                && columnMetaData.jdbcType() != JDBCType.ARRAY
                && parameterInfo.batchCollection() != null) {
            return Optional.of(new DAOBatchIterator(parameterInfo, parameterInfo.batchCollection()));
        } else if (parameterInfo.recordParent() != null) {
            System.err.println("recordParent? " + parameterInfo);
            return from(columnMetaData, parameterInfo.recordParent());
        }
        return Optional.empty();
    }

    public String baseName() {
        return source.name().name();
    }
}
