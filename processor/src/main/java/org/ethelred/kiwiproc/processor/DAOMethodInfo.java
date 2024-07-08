package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilderFull;
import org.ethelred.kiwiproc.meta.ParsedQuery;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@RecordBuilderFull
public record DAOMethodInfo(
        Signature signature,
        QueryMethodKind kind,
        ParsedQuery parsedSql,
        List<DAOParameterInfo> parameterMapping,
        @Nullable Signature rowRecord,
        @Nullable DAOResultInfo singleColumn) implements ClassNameMixin {

    public Stream<TypeMapping> mappers() {
        return Stream.concat(
                parameterMapping.stream().map(DAOParameterInfo::mapper),
                returnTypeMapping().stream()
        ).filter(m -> !m.isIdentity());
    }

    public Optional<String> internalComponentType() {
        if (rowRecord != null) {
            return Optional.of(rowRecord.name());
        }
        if (singleColumn != null) {
            return Optional.of(singleColumn.javaType());
        }
        return Optional.empty();
    }

    public String resultComponentType() {
        return signature.returnType().baseType();
    }

    public Optional<TypeMapping> returnTypeMapping() {
        return internalComponentType()
                .map(ict -> new TypeMapping(ict, signature.returnType().baseType()))
                .filter(m -> !m.isIdentity());
    }

    public String fromList() {
        var container = signature.returnType().containerType();
        if (container != null) {
            var template = container.fromListTemplate();
            if (template.contains("%s")) { // hacky
                return template.formatted(signature.returnType().baseType());
            } else {
                return template;
            }
        }
        return """
                l.empty() ? null: l.get(0)
                """;
    }
}
