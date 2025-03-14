import org.ethelred.buildsupport.ProcessorConfigTask

plugins {
    id("java-convention")
    id("org.ethelred.embeddedpostgres")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.spring.starter)
    testImplementation(libs.spring.test)
    testRuntimeOnly(libs.spring.starter.jdbc)
    testRuntimeOnly(libs.postgresql)
}


val processorConfig = tasks.named<ProcessorConfigTask>("processorConfig") {
//    debug = true
    dependencyInjectionStyle = "SPRING"
}

tasks.named<ProcessResources>("processTestResources") {
    from(processorConfig.get().getApplicationConfigFile())
}