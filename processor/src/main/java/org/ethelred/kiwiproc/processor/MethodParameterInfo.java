/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.*;
import java.util.stream.Collectors;
import javax.lang.model.element.VariableElement;
import org.ethelred.kiwiproc.meta.JavaName;
import org.ethelred.kiwiproc.processor.types.CollectionType;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.TypeUtils;
import org.ethelred.kiwiproc.processor.types.ValidCollection;
import org.jspecify.annotations.Nullable;

@KiwiRecordBuilder
public record MethodParameterInfo(
        VariableElement variableElement,
        JavaName name,
        KiwiType type,
        boolean isRecordComponent,
        String methodParameterName,
        boolean batchIterate,
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
        var batchIterate = false;
        ValidCollection batchCollection = null;
        if (kind == QueryMethodKind.BATCH
                && effectiveType instanceof CollectionType collectionType
                && !collectionType.isSimple()) {
            batchIterate = true;
            effectiveType = collectionType.containedType();
            batchCollection = collectionType.type();
        }
        if (kind == QueryMethodKind.BATCH) {
//            System.err.printf("%s it %s et %s%n", kind, batchIterate, effectiveType);
        }
        Set<MethodParameterInfo> result = new HashSet<>();

        var simple = MethodParameterInfoBuilder.builder()
                .variableElement(variableElement)
                .name(new JavaName(variableElement.getSimpleName().toString()))
                .type(effectiveType)
                .isRecordComponent(false)
                .methodParameterName(variableElement.getSimpleName().toString())
                .batchIterate(batchIterate)
                .batchCollection(batchCollection)
                .build();
        result.add(simple);

        if (types.isRecord(variableElement.asType())) {
            result.addAll( fromRecord(types, variableElement));
        }

        return result;
    }

    private static Set<MethodParameterInfo> fromRecord(TypeUtils types, VariableElement variableElement) {
        var type = types.asTypeElement(variableElement.asType());
        var components = Objects.requireNonNull(type).getRecordComponents();
        return components.stream()
                .map(component -> MethodParameterInfoBuilder.builder()
                        .variableElement(variableElement)
                        .name(new JavaName(component.getSimpleName().toString()))
                        .type(types.kiwiType(component.asType()))
                        .isRecordComponent(true)
                        .methodParameterName(variableElement.getSimpleName().toString())
                        .batchIterate(false)
                        .build())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
