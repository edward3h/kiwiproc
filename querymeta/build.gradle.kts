plugins {
    id("java-convention")
    id("publishing-convention")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.postgresql)
    annotationProcessor(libs.recordbuilder.processor)
    implementation(libs.recordbuilder.core)
    testImplementation(libs.embeddedpostgres)
    testImplementation(libs.liquibase.core)
}
