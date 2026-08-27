
plugins {
    id("java-convention")
    id("org.ethelred.kiwiproc")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.jakarta.inject)
    testImplementation(libs.sqlite)
}

kiwiProc {
    dataSources {
        register("default") {
            driverClassName = "org.sqlite.JDBC"
            liquibaseChangelog = file("$projectDir/src/main/resources/changelog/changelog.xml")
        }
    }
}
