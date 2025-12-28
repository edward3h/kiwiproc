/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.*;
import java.util.stream.Collectors;
import javax.lang.model.element.VariableElement;
import org.ethelred.kiwiproc.meta.JavaName;
import org.ethelred.kiwiproc.processor.types.*;
import org.jspecify.annotations.Nullable;

@KiwiRecordBuilder
public record MethodParameterInfo(
        VariableElement variableElement,
        JavaName name,
        KiwiType type,
        @Nullable MethodParameterInfo recordParent,
        @Nullable ValidCollection batchCollection)
        implements MethodParameterInfoBuilder.With {

    static Set<MethodParameterInfo> fromElements(
            TypeUtils types, List<? extends VariableElement> variableElements, QueryMethodKind kind) {
        return variableElements.stream()
                .flatMap(variableElement -> fromElement(types, variableElement, kind).stream())
                .collect(Collectors.toSet());
    }

    static Set<MethodParameterInfo> fromElement(
            TypeUtils types, VariableElement variableElement, QueryMethodKind kind) {
        var effectiveType = types.kiwiType(variableElement.asType());
        ValidCollection batchCollection = null;
        if (kind == QueryMethodKind.BATCH
                && effectiveType instanceof CollectionType collectionType
                && !collectionType.isSimple()) {
            effectiveType = collectionType.containedType();
            batchCollection = collectionType.type();
        }
        Set<MethodParameterInfo> result = new HashSet<>();

        var simple = MethodParameterInfoBuilder.builder()
                .variableElement(variableElement)
                .name(new JavaName(variableElement.getSimpleName().toString()))
                .type(effectiveType)
                .batchCollection(batchCollection)
                .build();
        result.add(simple);

        if (effectiveType instanceof RecordType recordType) {
            result.addAll(fromRecord(recordType, variableElement, simple));
        }

        return result;
    }

    private static Set<MethodParameterInfo> fromRecord(
            RecordType recordType, VariableElement variableElement, MethodParameterInfo recordParent) {
        var components = recordType.components();
        return components.stream()
                .map(component -> MethodParameterInfoBuilder.builder()
                        .variableElement(variableElement)
                        .name(component.name())
                        .type(component.type())
                        .recordParent(recordParent)
                        .build())
                .collect(Collectors.toCollection(HashSet::new));
    }

    public boolean isRecordComponent() {
        return recordParent != null;
    }

    public boolean batchIterate() {
        return batchCollection != null || (recordParent != null && recordParent.batchIterate());
    }
}
