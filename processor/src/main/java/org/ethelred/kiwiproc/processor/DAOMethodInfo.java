/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import org.ethelred.kiwiproc.meta.ParsedQuery;
import org.ethelred.kiwiproc.processor.types.ContainerType;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.jspecify.annotations.Nullable;

@KiwiRecordBuilder
public record DAOMethodInfo(
        ExecutableElement methodElement,
        Signature signature,
        QueryMethodKind kind,
        ParsedQuery parsedSql,
        List<DAOParameterInfo> parameterMapping,
        List<DAOResultColumn> multipleColumns,
        @Nullable DAOResultColumn singleColumn) {

    public KiwiType resultComponentType() {
        var kiwiType = signature.returnType();
        if (kiwiType instanceof ContainerType containerType) {
            return containerType.containedType();
        }
        return kiwiType;
    }

    public boolean singleResult() {
        var kiwiType = signature.returnType();
        if (kiwiType instanceof ContainerType containerType) {
            return !containerType.type().isMultiValued();
        }
        return true;
    }
}
