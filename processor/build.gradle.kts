plugins {
    id("java-convention")
}

dependencies {
    annotationProcessor(libs.avaje.prisms)
    annotationProcessor(libs.recordbuilder.processor)
    implementation(project(":shared"))
    implementation(project(":querymeta"))
    implementation(libs.utilitary)
    implementation(libs.avaje.json.asProvider())
    implementation(libs.avaje.prisms)
    implementation(libs.postgresql)
    implementation(libs.recordbuilder.core)
    implementation(libs.mapstruct.processor)
    implementation(libs.javapoet)
    implementation(libs.ethelred.util)
    testAnnotationProcessor(libs.mapstruct.processor)
    testImplementation(project(":runtime"))
    testImplementation(libs.jakarta.inject)
    testImplementation(libs.bundles.compile.testing)
    testImplementation(libs.embeddedpostgres)
    testImplementation(libs.liquibase.core)
    testImplementation(libs.mapstruct.compile)
}