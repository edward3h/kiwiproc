package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilderFull;
import java.util.*;
import java.util.stream.Collectors;
import javax.lang.model.element.VariableElement;
import org.jspecify.annotations.Nullable;

@RecordBuilderFull
public record MethodParameterInfo(
        VariableElement variableElement,
        String name,
        KiwiType type,
        boolean isRecordComponent,
        @Nullable String recordParameterName) {

    static Map<String, MethodParameterInfo> fromElements(
            TypeUtils types, List<? extends VariableElement> variableElements) {
        return variableElements.stream()
                .flatMap(variableElement -> fromElement(types, variableElement).stream())
                .collect(Collectors.toMap(MethodParameterInfo::name, x -> x, MethodParameterInfo::merge));
    }

    private static MethodParameterInfo merge(MethodParameterInfo a, MethodParameterInfo b) {
        if (a.isRecordComponent == b.isRecordComponent) {
            throw new IllegalStateException("Can't have 2 parameters with same methodName");
        }
        if (a.isRecordComponent) {
            return b;
        }
        return a;
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
                .name(variableElement.getSimpleName().toString())
                .type(types.kiwiType(variableElement.asType()))
                .isRecordComponent(false)
                .recordParameterName(null)
                .variableElement(variableElement)
                .build();
        return Set.of(info);
    }

    private static Set<MethodParameterInfo> fromRecord(TypeUtils types, VariableElement variableElement) {
        var type = types.asTypeElement(variableElement.asType());
        var components = Objects.requireNonNull(type).getRecordComponents();
        var parameterInfos = components.stream()
                .map(component -> MethodParameterInfoBuilder.builder()
                        .name(component.getSimpleName().toString())
                        .type(types.kiwiType(component.asType()))
                        .isRecordComponent(true)
                        .recordParameterName(variableElement.getSimpleName().toString())
                        .variableElement(variableElement)
                        .build())
                .collect(Collectors.toCollection(HashSet::new));
        parameterInfos.addAll(fromSingle(types, variableElement));
        return parameterInfos;
    }
}
