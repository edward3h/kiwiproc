package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilderFull;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import org.ethelred.kiwiproc.meta.ParsedQuery;
import org.jspecify.annotations.Nullable;

@RecordBuilderFull
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

    public String fromList() {
        if (signature.returnType() instanceof ContainerType containerType) {
            var container = containerType.type();
            var template = container.fromListTemplate();
            if (template.contains("%s")) { // hacky
                return template.formatted(containerType.containedType());
            } else {
                return template;
            }
        }
        return """
                l.empty() ? null: l.get(0)
                """;
    }
}
