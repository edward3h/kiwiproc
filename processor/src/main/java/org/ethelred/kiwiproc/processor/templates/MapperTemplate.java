package org.ethelred.kiwiproc.processor.templates;

import io.jstach.jstache.JStache;
import org.ethelred.kiwiproc.processor.ClassNameMixin;
import org.ethelred.kiwiproc.processor.DAOClassInfo;
import org.ethelred.kiwiproc.processor.TypeMapping;

import java.util.Set;

@JStache(template =
        // language=mustache
        """
        {{#classInfo}}
        package {{packageName}};
        
        import org.ethelred.kiwiproc.impl.BaseMapper;
        import org.mapstruct.Mapper;
        {{generatedImport}}
        
        {{generated}}
        @Mapper
        public abstract class {{#className}}Mapper{{/className}} extends BaseMapper {
        {{#mappings}}
        public abstract {{target}} {{methodName}}({{source}} value);
        {{/mappings}}
        }
        {{/classInfo}}
        """)
public record MapperTemplate(DAOClassInfo classInfo, Set<TypeMapping> mappings) implements ClassNameMixin, GeneratedMixin {
}
