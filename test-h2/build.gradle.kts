
plugins {
    id("java-convention")
    id("org.ethelred.kiwiproc")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.jakarta.inject)
    testImplementation(libs.h2)
}

kiwiProc {
    dataSources {
        register("default") {
            driverClassName = "org.h2.Driver"
            liquibaseChangelog = file("$projectDir/src/main/resources/changelog/changelog.xml")
        }
    }
}
