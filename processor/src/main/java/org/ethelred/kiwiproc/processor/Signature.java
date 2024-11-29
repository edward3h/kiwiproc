/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import org.ethelred.kiwiproc.processor.types.KiwiType;

@KiwiRecordBuilder
public record Signature(KiwiType returnType, String methodName, List<String> paramNames) {
    static Signature fromMethod(TypeUtils typeUtils, ExecutableElement element) {
        List<String> params = element.getParameters().stream()
                .map(VariableElement::getSimpleName)
                .map(Object::toString)
                .toList();
        return new Signature(
                typeUtils.kiwiType(element.getReturnType()),
                element.getSimpleName().toString(),
                params);
    }
}
