import org.ethelred.buildsupport.ProcessorConfigTask

plugins {
    id("java-convention")
    id("org.ethelred.embeddedpostgres")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.jakarta.inject)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.yaml)
}

val processorConfig = tasks.named<ProcessorConfigTask>("processorConfig") {
    debug = true
}

tasks.named<ProcessResources>("processTestResources") {
    from(processorConfig.get().getApplicationConfigFile())
}