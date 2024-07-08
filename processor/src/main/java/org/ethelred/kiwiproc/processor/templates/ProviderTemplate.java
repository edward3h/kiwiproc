package org.ethelred.kiwiproc.processor.templates;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheLambda;
import org.ethelred.kiwiproc.processor.ClassNameMixin;
import org.ethelred.kiwiproc.processor.DAOClassInfo;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;

import java.util.List;

@JStache(
        template =
        // language=mustache
        """
        {{#classInfo}}
        package {{packageName}};
        
        import org.ethelred.kiwiproc.impl.AbstractDAOProvider;
        import org.ethelred.kiwiproc.api.DAOContext;
        import javax.sql.DataSource;
        import java.sql.SQLException;
        {{#diImports}}
        import {{.}};
        {{/diImports}}
        {{generatedImport}}
        
        {{generated}}
        @Singleton
        public class {{#className}}Provider{{/className}} extends AbstractDAOProvider<{{daoName}}> {
            public {{#className}}Provider{{/className}}(@Named("{{dataSourceName}}") DataSource dataSource) {
                super(dataSource);
            }
            
            public String getDataSourceName() {
                return "{{dataSourceName}}";
            }
            
            public {{daoName}} withContext(DAOContext context) throws SQLException {
                return new {{#className}}Impl{{/className}}(context);
            }
        }
        {{/classInfo}}
        """)
public record ProviderTemplate(DependencyInjectionStyle dependencyInjectionStyle, DAOClassInfo classInfo) implements ClassNameMixin, GeneratedMixin {

    @JStacheLambda
    List<String> diImports() {
        return dependencyInjectionStyle.getImports();
    }
}
