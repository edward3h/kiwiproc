plugins {
    id("java-convention")
    id("org.ethelred.kiwiproc")
    id("io.micronaut.library") version "4.6.1"
}

kiwiProc {
//    debug = true
}

micronaut {
    version = "4.9.2"
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("io.micronaut.sql:micronaut-jdbc-hikari")
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.yaml)
}
