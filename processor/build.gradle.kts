plugins {
    id("java-convention")
}

dependencies {
    annotationProcessor(libs.metainfservices)
    annotationProcessor(libs.avaje.prisms)
    annotationProcessor(libs.recordbuilder.processor)
    annotationProcessor(libs.jstachio.processor)
    implementation(project(":shared"))
    implementation(project(":querymeta"))
    implementation(libs.utilitary)
    implementation(libs.avaje.json.asProvider())
    implementation(libs.avaje.prisms)
    implementation(libs.metainfservices)
    implementation(libs.postgresql)
    implementation(libs.recordbuilder.core)
    implementation(libs.jstachio.compile)
    implementation(libs.mapstruct.processor)
    testAnnotationProcessor(libs.mapstruct.processor)
    testImplementation(project(":runtime"))
    testImplementation(libs.jakarta.inject)
    testImplementation(libs.bundles.compile.testing)
    testImplementation(libs.embeddedpostgres)
    testImplementation(libs.liquibase.core)
    testImplementation(libs.mapstruct.compile)

}