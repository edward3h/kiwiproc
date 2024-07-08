package org.ethelred.kiwiproc.processor.templates;

import io.jstach.jstache.JStache;
import org.ethelred.kiwiproc.processor.DAOClassInfo;
import org.ethelred.kiwiproc.processor.Signature;

@JStache(template =
        // language=mustache
        """
                {{#classInfo}}
        package {{packageName}};
        
       
        {{generatedImport}}
        
        {{generated}}
            {{#rowRecord}}
            public record {{name}}(
            {{#params}}{{^@first}}, {{/@first}}{{javaType}} {{name}}{{/params}}
            ) {}
            {{/rowRecord}}
                {{/classInfo}}
                """)
public record RowRecordTemplate(DAOClassInfo classInfo, Signature rowRecord) implements GeneratedMixin {

}
