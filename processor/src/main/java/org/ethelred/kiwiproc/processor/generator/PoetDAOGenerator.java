/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.generator;

import com.karuslabs.utilitary.Logger;
import com.palantir.javapoet.JavaFile;
import java.io.IOException;
import javax.annotation.processing.Filer;
import org.ethelred.kiwiproc.processor.*;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;

public class PoetDAOGenerator implements DAOGenerator {
    private final Logger logger;
    private final Filer filer;
    private final DependencyInjectionStyle dependencyInjectionStyle;
    private final KiwiTypeConverter kiwiTypeConverter;
    private final InstanceGenerator instanceGenerator;
    private final ProviderGenerator providerGenerator;
    private final CoreTypes coreTypes;

    public PoetDAOGenerator(Logger logger, Filer filer, DependencyInjectionStyle dependencyInjectionStyle) {
        this.logger = logger;
        this.filer = filer;
        this.dependencyInjectionStyle = dependencyInjectionStyle;
        this.kiwiTypeConverter = new KiwiTypeConverter();
        this.coreTypes = new CoreTypes();
        this.instanceGenerator = new InstanceGenerator(logger, kiwiTypeConverter, coreTypes);
        this.providerGenerator = new ProviderGenerator(dependencyInjectionStyle, kiwiTypeConverter);
    }

    public void generateProvider(DAOClassInfo classInfo) {
        var javaFile = providerGenerator.generate(classInfo);
        writeJavaFile(classInfo::element, javaFile);
    }

    public void generateImpl(DAOClassInfo classInfo) {
        var javaFile = instanceGenerator.generate(classInfo);
        writeJavaFile(classInfo::element, javaFile);
    }

    private void writeJavaFile(ElementSupplier elementSupplier, JavaFile javaFile) {
        javaFile = javaFile.toBuilder()
                .skipJavaLangImports(true)
                .indent("    ")
                .addFileComment("GENERATED CODE - DO NOT EDIT")
                .build();
        try {
            logger.note(
                    elementSupplier.getElement(),
                    "Generating " + javaFile.typeSpec().name());
            javaFile.writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void generateImplementations(DAOClassInfo classInfo) {
        generateProvider(classInfo);
        generateImpl(classInfo);
    }
}
