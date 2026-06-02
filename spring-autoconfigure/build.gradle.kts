plugins {
    id("java-convention")
    id("publishing-convention")
}

dependencies {
    api(project(":runtime"))
    compileOnly(libs.spring.autoconfigure)
    compileOnly(libs.spring.starter.jdbc)
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.starter.jdbc)
    testRuntimeOnly(libs.h2)
}
