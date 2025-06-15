plugins {
    id("java-convention")
    id("publishing-convention")
}

dependencies {
    annotationProcessor(libs.jspecify)
    annotationProcessor(libs.avaje.prisms)
    annotationProcessor(libs.recordbuilder.processor)
    implementation(project(":shared"))
    implementation(project(":querymeta"))
    implementation("org.ethelred.kiwiproc:plugin:0.3-SNAPSHOT")
    implementation(libs.utilitary)
    implementation(libs.avaje.json.asProvider())
    implementation(libs.avaje.prisms)
    implementation(libs.postgresql)
    implementation(libs.recordbuilder.core)
    implementation(libs.mapstruct.processor)
    implementation(libs.javapoet)
    testAnnotationProcessor(libs.mapstruct.processor)
    testImplementation(project(":runtime"))
    testImplementation(libs.jakarta.inject)
    testImplementation(libs.bundles.compile.testing)
    testImplementation(libs.embeddedpostgres)
    testImplementation(libs.liquibase.core)
    testImplementation(libs.mapstruct.compile)
}