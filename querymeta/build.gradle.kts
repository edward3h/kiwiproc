plugins {
    id("java-convention")
    id("publishing-convention")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.postgresql)
    implementation("org.ethelred.kiwiproc:plugin:0.3-SNAPSHOT")

    annotationProcessor(libs.recordbuilder.processor)
    implementation(libs.recordbuilder.core)
    testImplementation(libs.embeddedpostgres)
    testImplementation(libs.liquibase.core)
}
