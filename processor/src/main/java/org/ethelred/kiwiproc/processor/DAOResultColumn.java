/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.meta.SqlName;
import org.ethelred.kiwiproc.processor.types.KiwiType;

public record DAOResultColumn(
        SqlName name,
        SqlTypeMapping sqlTypeMapping,
        KiwiType targetType,
        ResultPart resultPart,
        Conversion conversion) {

    public TypeMapping asTypeMapping() {
        return new TypeMapping(sqlTypeMapping.kiwiType(), targetType);
    }
}
