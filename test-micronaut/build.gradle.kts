plugins {
    id("java-convention")
    id("io.micronaut.library") version "4.4.0"
}

micronaut {
    version = "4.4.2"
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
     testImplementation("io.micronaut.test:micronaut-test-junit5")
}