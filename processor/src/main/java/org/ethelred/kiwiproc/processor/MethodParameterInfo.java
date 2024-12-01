/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.*;
import java.util.stream.Collectors;
import javax.lang.model.element.VariableElement;
import org.ethelred.kiwiproc.meta.JavaName;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.jspecify.annotations.Nullable;

@KiwiRecordBuilder
public record MethodParameterInfo(
        VariableElement variableElement,
        JavaName name,
        KiwiType type,
        boolean isRecordComponent,
        @Nullable String recordParameterName) {

    static Set<MethodParameterInfo> fromElements(TypeUtils types, List<? extends VariableElement> variableElements) {
        return variableElements.stream()
                .flatMap(variableElement -> fromElement(types, variableElement).stream())
                .collect(Collectors.toSet());
    }

    static Set<MethodParameterInfo> fromElement(TypeUtils types, VariableElement variableElement) {
        if (types.isRecord(variableElement.asType())) {
            return fromRecord(types, variableElement);
        } else {
            return fromSingle(types, variableElement);
        }
    }

    private static Set<MethodParameterInfo> fromSingle(TypeUtils types, VariableElement variableElement) {
        var info = MethodParameterInfoBuilder.builder()
                .variableElement(variableElement)
                .name(new JavaName(variableElement.getSimpleName().toString()))
                .type(types.kiwiType(variableElement.asType()))
                .isRecordComponent(false)
                .recordParameterName(null)
                .build();
        return Set.of(info);
    }

    private static Set<MethodParameterInfo> fromRecord(TypeUtils types, VariableElement variableElement) {
        var type = types.asTypeElement(variableElement.asType());
        var components = Objects.requireNonNull(type).getRecordComponents();
        var parameterInfos = components.stream()
                .map(component -> MethodParameterInfoBuilder.builder()
                        .variableElement(variableElement)
                        .name(new JavaName(component.getSimpleName().toString()))
                        .type(types.kiwiType(component.asType()))
                        .isRecordComponent(true)
                        .recordParameterName(variableElement.getSimpleName().toString())
                        .build())
                .collect(Collectors.toCollection(HashSet::new));
        parameterInfos.addAll(fromSingle(types, variableElement));
        return parameterInfos;
    }
}
