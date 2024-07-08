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
    testAnnotationProcessor(libs.mapstruct.processor)
    testImplementation(project(":runtime"))
    testImplementation(libs.jakarta.inject)
    testImplementation("com.google.guava:guava:32.1.3-jre")
    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
    testImplementation("io.github.kiskae:compile-testing-extension:1.0.2")
    testImplementation(libs.embeddedpostgres)
    testImplementation(libs.liquibase.core)
    testImplementation(libs.mapstruct.compile)

}