/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.CollectionType;
import org.ethelred.kiwiproc.processor.types.ValidCollection;

import java.sql.JDBCType;
import java.util.Map;
import java.util.Optional;

public record DAOBatchIterator(MethodParameterInfo source, ValidCollection validCollection) {
    public static Optional<DAOBatchIterator> from(Map.Entry<ColumnMetaData, MethodParameterInfo> entry) {
        return from(entry.getKey(), entry.getValue());
    }

    private static Optional<DAOBatchIterator> from(ColumnMetaData columnMetaData, MethodParameterInfo parameterInfo) {
        if (!parameterInfo.isRecordComponent() && columnMetaData.jdbcType() != JDBCType.ARRAY && parameterInfo.batchIterate()) {
            return Optional.of(new DAOBatchIterator(parameterInfo, parameterInfo.batchCollection()));
        }
        return Optional.empty();
    }
}
