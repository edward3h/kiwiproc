package org.ethelred.kiwiproc.processor.generator;

import static org.ethelred.kiwiproc.processor.generator.RuntimeTypes.*;

import com.palantir.javapoet.*;
import javax.lang.model.element.Modifier;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.processor.DAODataSourceInfo;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;

public class TransactionManagerGenerator {
    private final DependencyInjectionStyle dependencyInjectionStyle;
    private final KiwiTypeConverter kiwiTypeConverter;

    public TransactionManagerGenerator(
            DependencyInjectionStyle dependencyInjectionStyle, KiwiTypeConverter kiwiTypeConverter) {
        this.dependencyInjectionStyle = dependencyInjectionStyle;
        this.kiwiTypeConverter = kiwiTypeConverter;
    }

    public JavaFile generate(DAODataSourceInfo dataSourceInfo) {
        var constructorSpec = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(DataSource.class, "dataSource")
                        .addAnnotation(AnnotationSpec.builder(kiwiTypeConverter
                                        .getDependencyInjectionType(dependencyInjectionStyle)
                                        .named())
                                .addMember("value", "$S", dataSourceInfo.dataSourceName())
                                .build())
                        .build())
                .addStatement("super(dataSource)")
                .build();
        var typeSpecBuilder = TypeSpec.classBuilder(
                        ClassName.get(dataSourceInfo.packageName(), dataSourceInfo.className("TransactionManager")))
                .addAnnotation(AnnotationSpec.builder(ClassName.bestGuess("javax.annotation.processing.Generated"))
                        .addMember("value", "$S", "org.ethelred.kiwiproc.processor.KiwiProcessor")
                        .build())
                .addAnnotation(kiwiTypeConverter
                        .getDependencyInjectionType(dependencyInjectionStyle)
                        .singleton())
                .addAnnotation(AnnotationSpec.builder(kiwiTypeConverter
                                .getDependencyInjectionType(dependencyInjectionStyle)
                                .named())
                        .addMember("value", "$S", dataSourceInfo.dataSourceName())
                        .build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(ABSTRACT_TRANSACTION_MANAGER)
                .addMethod(constructorSpec);
        return JavaFile.builder(dataSourceInfo.packageName(), typeSpecBuilder.build())
                .build();
    }
}
