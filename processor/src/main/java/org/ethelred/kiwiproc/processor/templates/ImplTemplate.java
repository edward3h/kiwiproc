package org.ethelred.kiwiproc.processor.templates;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheLambda;
import io.jstach.jstache.JStachePartial;
import io.jstach.jstache.JStachePartials;
import org.ethelred.kiwiproc.processor.ClassNameMixin;
import org.ethelred.kiwiproc.processor.DAOClassInfo;
import org.ethelred.kiwiproc.processor.DAOParameterInfo;

@JStache(template =
        // language=mustache
        """
        {{#classInfo}}
        package {{packageName}};
        
        import org.ethelred.kiwiproc.impl.AbstractDAO;
        import org.ethelred.kiwiproc.exception.UncheckedSQLException;
        import org.ethelred.kiwiproc.api.DAOContext;
        import org.mapstruct.factory.Mappers;
        import java.sql.SQLException;
        import java.util.List;
        import java.util.ArrayList;
        {{generatedImport}}
        
        {{generated}}
        public class {{#className}}Impl{{/className}} extends AbstractDAO<{{daoName}}> implements {{daoName}} {
            private final {{#className}}Mapper{{/className}} mapper;
            public {{#className}}Impl{{/className}}(DAOContext context) {
                super(context);
                mapper = Mappers.getMapper({{#className}}Mapper{{/className}}.class);
            }
            
            {{#methods}}
            @Override
            {{#signature}}
            public {{returnTypeDeclaration}} {{name}}(
            {{#params}}{{^@first}}, {{/@first}}{{javaType}} {{name}}{{/params}}
            ){{/signature}} {
                try {
                    var connection = context.getConnection();
                    try (var statement = connection.prepareStatement("{{parsedSql.parsedSql}}")) {
                        {{#kind.BATCH}}
                        {{> setBatchParameters}}
                        {{/kind.BATCH}}
                        {{^kind.BATCH}}
                        {{> setParameters}}
                        {{/kind.BATCH}}
                        
                        {{#kind.QUERY}}
                        var resultSet = statement.executeQuery();
                        List<{{resultComponentType}}> l = new ArrayList<>();
                        while (resultSet.next()) {
                            {{#rowRecord}}
                            var rawValue = new {{internalComponentType}}(
                                {{#params}}
                                {{^@first}}, {{/@first}}
                                resultSet.get{{sqlTypeMapping.accessorSuffix}}("{{name}}")
                                {{/params}}
                            );
                            {{/rowRecord}}
                            {{#singleColumn}}
                            var rawValue = resultSet.get{{sqlTypeMapping.accessorSuffix}}("{{name}}");
                            {{/singleColumn}}
                            {{#returnTypeMapping}}
                            var value = mapper.{{methodName}}(rawValue);
                            {{/returnTypeMapping}}
                            {{^returnTypeMapping}}
                            var value = rawValue;
                            {{/returnTypeMapping}}
                            l.add(value);
                        }
                       
                        return {{fromList}};
                        {{/kind.QUERY}}
                    }
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                }
            }
            {{/methods}}
        }
        {{/classInfo}}
        """)
@JStachePartials({
        @JStachePartial(name = "setParameters", template =
                // language=mustache
                """
                // Test {{parameterMapping}} 
                {{#parameterMapping}}
                statement.{{setter}}({{index}}, {{#typeMapping}}{{/typeMapping}});
                {{/parameterMapping}}
                """),
        @JStachePartial(name = "setBatchParameters",
                template =
        // language=mustache
                        """
                TODO
                """)
})
public record ImplTemplate(DAOClassInfo classInfo) implements ClassNameMixin, GeneratedMixin {
    @JStacheLambda
    @JStacheLambda.Raw
    public String typeMapping(DAOParameterInfo parameterInfo) {
        if (parameterInfo.mapper().isIdentity()) {
            return parameterInfo.javaAccessor();
        }
        return "%s(%s)".formatted(parameterInfo.mapper().methodName(), parameterInfo.javaAccessor());
    }
}
