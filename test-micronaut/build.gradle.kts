import org.ethelred.buildsupport.ProcessorConfigTask

plugins {
    id("java-convention")
    id("org.ethelred.embeddedpostgres")
    id("io.micronaut.library") version "4.4.3"
}

micronaut {
    version = "4.6.2"
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("io.micronaut.sql:micronaut-jdbc-hikari")
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.yaml)
}

val processorConfig = tasks.named<ProcessorConfigTask>("processorConfig")

tasks.named<ProcessResources>("processTestResources") {
    from(processorConfig.get().getApplicationConfigFile())
}