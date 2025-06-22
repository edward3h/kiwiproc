
plugins {
    id("java-convention")
    id("org.ethelred.kiwiproc")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.jakarta.inject)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.yaml)
}

kiwiProc {
    debug = true
}
