package org.ethelred.kiwiproc.processor;

import org.ethelred.kiwiproc.processor.templates.*;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;

import javax.annotation.processing.Filer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;

public class DAOGenerator implements ClassNameMixin {
    private final Filer filer;
    private final DependencyInjectionStyle dependencyInjectionStyle;

    public DAOGenerator(Filer filer, DependencyInjectionStyle dependencyInjectionStyle) {

        this.filer = filer;
        this.dependencyInjectionStyle = dependencyInjectionStyle;
    }

    @FunctionalInterface
    interface Renderer {
        void render(Writer w, DAOClassInfo classInfo) throws IOException;
    }

    private void generate(DAOClassInfo classInfo, String className, Renderer renderer) {
        try {
            var fqcn = classInfo.packageName() + "." + className;
            var file = filer.createSourceFile(fqcn, classInfo.element());
            System.err.println("Generating " + file.getName());
            try (var w = file.openWriter()) {
                renderer.render(w, classInfo);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void generateProvider(DAOClassInfo classInfo) {
        generate(classInfo, className("Provider", classInfo), (w, ci) -> ProviderTemplateRenderer.of().execute(new ProviderTemplate(dependencyInjectionStyle, ci), w));
    }

    public void generateMapper(DAOClassInfo classInfo, Set<TypeMapping> mappings) {
        generate(classInfo, className("Mapper", classInfo), (w, ci) -> MapperTemplateRenderer.of().execute(new MapperTemplate(ci, mappings), w));
    }

    public void generateImpl(DAOClassInfo classInfo) {
        generate(classInfo, className("Impl", classInfo), (w, ci) -> ImplTemplateRenderer.of().execute(new ImplTemplate(ci), w));
    }

    public void generateRowRecord(DAOClassInfo classInfo, Signature rowRecord) {
        generate(classInfo, rowRecord.name(), (w, ci) -> RowRecordTemplateRenderer.of().execute(new RowRecordTemplate(ci, rowRecord), w));
    }
}
