plugins {
    id("java-convention")
    id("publishing-convention")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.postgresql)
    implementation(libs.mysql)
    implementation("org.ethelred.kiwiproc:plugin:${project.version}")

    annotationProcessor(libs.recordbuilder.processor)
    implementation(libs.recordbuilder.core)
    testImplementation(libs.embeddedpostgres)
    testImplementation(libs.liquibase.core)
}
