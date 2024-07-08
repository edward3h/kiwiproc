package org.ethelred.kiwiproc.processor.templates;

import org.ethelred.kiwiproc.processor.KiwiProcessor;

public interface GeneratedMixin {
    default String generated() {
        return """
    @Generated("%s")""".formatted(KiwiProcessor.class.getName());
    }

    default String generatedImport() {
        return "import javax.annotation.processing.Generated;";
    }
}
