/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.util.Map;
import org.ethelred.kiwiproc.processor.types.CollectionType;
import org.ethelred.kiwiproc.processor.types.KiwiType;
import org.ethelred.kiwiproc.processor.types.PrimitiveKiwiType;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;

public class KiwiTypeConverter {
    public TypeName fromKiwiType(KiwiType kiwiType) {
        return fromKiwiType(kiwiType, false);
    }

    public TypeName fromKiwiType(KiwiType kiwiType, boolean asParameter) {
        if (kiwiType instanceof CollectionType collectionType) {
            return ParameterizedTypeName.get(
                    ClassName.get(collectionType.type().javaType()), fromKiwiType(collectionType.containedType(), asParameter));
        } else if (kiwiType instanceof PrimitiveKiwiType primitiveKiwiType && asParameter) {
            var pretendNullable = primitiveKiwiType.withIsNullable(true);
            return ClassName.get(pretendNullable.packageName(), pretendNullable.className());
        } else {
            return ClassName.get(kiwiType.packageName(), kiwiType.className());
        }
    }

    DependencyInjectionTypes getDependencyInjectionType(DependencyInjectionStyle dependencyInjectionStyle) {
        var result = dependencyInjectionTypesMap.get(dependencyInjectionStyle);
        if (result == null) {
            throw new IllegalArgumentException("Missing annotation types for " + dependencyInjectionStyle);
        }
        return result;
    }

    record DependencyInjectionTypes(ClassName singleton, ClassName named) {}

    private final Map<DependencyInjectionStyle, DependencyInjectionTypes> dependencyInjectionTypesMap = Map.of(
            DependencyInjectionStyle.JAKARTA,
            new DependencyInjectionTypes(
                    ClassName.get("jakarta.inject", "Singleton"), ClassName.get("jakarta.inject", "Named")),
            DependencyInjectionStyle.SPRING,
            new DependencyInjectionTypes(
                    ClassName.get("org.springframework.stereotype", "Repository"),
                    ClassName.get("org.springframework.beans.factory.annotation", "Qualifier")));
}
