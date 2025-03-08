/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.sql.JDBCType;
import org.ethelred.kiwiproc.processor.types.BasicType;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.ethelred.kiwiproc.processor.types.SqlArrayType;
import org.jspecify.annotations.Nullable;

// @KiwiRecordBuilder
@RecordBuilder // TODO staged builder is not correctly ignoring fields with defaults
@RecordBuilder.Options(
        addSingleItemCollectionBuilders = true,
        useImmutableCollections = true,
        onceOnlyAssignment = true)
public record SqlTypeMapping(
        JDBCType jdbcType,
        Class<?> baseType,
        @Nullable String accessorSuffix,
        boolean specialCase,
        boolean isNullable,
        @Nullable SqlTypeMapping componentType,
        @Nullable String componentDbType,
        @Nullable String dbType)
        implements SqlTypeMappingBuilder.With {

    public SqlTypeMapping {
        if (accessorSuffix == null) {
            if (baseType.isPrimitive()) {
                accessorSuffix = Util.capitalizeFirst(baseType.getSimpleName());
            } else {
                accessorSuffix = "Object";
            }
        }
    }

    public KiwiType kiwiType() {
        if (jdbcType == JDBCType.ARRAY) {
            return new SqlArrayType(componentType.kiwiType(), componentType, componentDbType);
        }
        if (CoreTypes.primitiveToBoxed.containsKey(baseType)) {
            return new PrimitiveKiwiType(baseType().getSimpleName(), isNullable);
        }
        return new BasicType(baseType.getPackageName(), baseType.getSimpleName(), isNullable);
    }
}
