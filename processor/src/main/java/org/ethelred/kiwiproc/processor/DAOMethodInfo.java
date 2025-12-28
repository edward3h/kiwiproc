/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import org.ethelred.kiwiproc.meta.ParsedQuery;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.MapType;

@KiwiRecordBuilder
public record DAOMethodInfo(
        ExecutableElement methodElement,
        Signature signature,
        QueryMethodKind kind,
        ParsedQuery parsedSql,
        List<DAOParameterInfo> parameterMapping,
        List<DAOResultColumn> columns,
        List<DAOBatchIterator> batchIterators)
        implements Supplier<Element> {

    public KiwiType valueComponentType() {
        var kiwiType = signature.returnType();
        return kiwiType.valueComponentType();
    }

    public Optional<KiwiType> keyComponentType() {
        var kiwiType = signature.returnType();
        if (kiwiType instanceof MapType mapType) {
            return Optional.of(mapType.keyType().valueComponentType());
        }
        return Optional.empty();
    }

    public RowCount expectedRows() {
        var kiwiType = signature.returnType();
        return kiwiType.expectedRows();
    }

    @Override
    public Element get() {
        return methodElement;
    }
}
