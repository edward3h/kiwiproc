package org.ethelred.kiwiproc.processor.generator;

import static org.ethelred.kiwiproc.processor.generator.RuntimeTypes.ABSTRACT_PROVIDER;
import static org.ethelred.kiwiproc.processor.generator.RuntimeTypes.DAO_CONTEXT;

import com.palantir.javapoet.*;
import javax.lang.model.element.Modifier;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.processor.DAOClassInfo;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;

public class ProviderGenerator {
    private final DependencyInjectionStyle dependencyInjectionStyle;
    private final KiwiTypeConverter kiwiTypeConverter;

    public ProviderGenerator(DependencyInjectionStyle dependencyInjectionStyle, KiwiTypeConverter kiwiTypeConverter) {
        this.dependencyInjectionStyle = dependencyInjectionStyle;
        this.kiwiTypeConverter = kiwiTypeConverter;
    }

    public JavaFile generate(DAOClassInfo classInfo) {
        var daoName = ClassName.get(classInfo.packageName(), classInfo.daoName());
        var superClass = ParameterizedTypeName.get(ABSTRACT_PROVIDER, daoName);
        var constructorSpec = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(DataSource.class, "dataSource")
                        .addAnnotation(AnnotationSpec.builder(kiwiTypeConverter
                                        .getDependencyInjectionType(dependencyInjectionStyle)
                                        .named())
                                .addMember("value", "$S", classInfo.dataSourceName())
                                .build())
                        .build())
                .addStatement("super(dataSource)")
                .build();
        var typeSpecBuilder = TypeSpec.classBuilder(
                        ClassName.get(classInfo.packageName(), classInfo.className("Provider")))
                .addOriginatingElement(classInfo.element())
                .addAnnotation(AnnotationSpec.builder(ClassName.bestGuess("javax.annotation.processing.Generated"))
                        .addMember("value", "$S", "org.ethelred.kiwiproc.processor.KiwiProcessor")
                        .build())
                .addAnnotation(kiwiTypeConverter
                        .getDependencyInjectionType(dependencyInjectionStyle)
                        .singleton())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superClass)
                .addMethod(constructorSpec);
        var withContextMethod = MethodSpec.methodBuilder("withContext")
                .addModifiers(Modifier.PROTECTED)
                .addAnnotation(Override.class)
                .addParameter(ParameterSpec.builder(DAO_CONTEXT, "daoContext").build())
                .returns(daoName)
                .addStatement("return new $L(daoContext)", classInfo.className("Impl"))
                .build();
        typeSpecBuilder.addMethod(withContextMethod);
        return JavaFile.builder(classInfo.packageName(), typeSpecBuilder.build())
                .build();
    }
}
