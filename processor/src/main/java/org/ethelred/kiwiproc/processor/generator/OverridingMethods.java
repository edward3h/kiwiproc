/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processor.generator;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.TypeName;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

/**
 * Builds overriding methods that keep type-use annotations (such as jspecify {@code @Nullable}) on the return type
 * and parameters, which {@link MethodSpec#overriding(ExecutableElement)} drops.
 */
final class OverridingMethods {
    private OverridingMethods() {}

    static MethodSpec.Builder overriding(ExecutableElement method) {
        var base = MethodSpec.overriding(method).build();
        var builder = MethodSpec.methodBuilder(base.name())
                .addAnnotations(base.annotations())
                .addModifiers(base.modifiers())
                .addTypeVariables(base.typeVariables())
                .returns(withTypeAnnotations(base.returnType(), method.getReturnType()));
        var params = method.getParameters();
        for (int i = 0; i < base.parameters().size(); i++) {
            var param = base.parameters().get(i);
            var type = withTypeAnnotations(param.type(), params.get(i).asType());
            builder.addParameter(
                    ParameterSpec.builder(type, param.name(), param.modifiers().toArray(new Modifier[0]))
                            .addAnnotations(param.annotations())
                            .build());
        }
        return builder.varargs(base.varargs()).addExceptions(base.exceptions());
    }

    private static TypeName withTypeAnnotations(TypeName typeName, TypeMirror mirror) {
        var annotations =
                mirror.getAnnotationMirrors().stream().map(AnnotationSpec::get).toList();
        return annotations.isEmpty() ? typeName : typeName.annotated(annotations);
    }
}
