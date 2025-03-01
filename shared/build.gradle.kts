plugins {
    id("java-convention")
    id("publishing-convention")
}

dependencies {
    annotationProcessor(libs.avaje.json.processor)
    implementation(libs.jetbrains.annotations)
    implementation(libs.avaje.json.asProvider())
}