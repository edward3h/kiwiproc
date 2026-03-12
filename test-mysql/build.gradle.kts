
plugins {
    id("java-convention")
    id("org.ethelred.kiwiproc")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.jakarta.inject)
    testImplementation(libs.mysql)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.junit5)
}

kiwiProc {
    dataSources {
        register("default") {
            driverClassName = "com.mysql.cj.jdbc.Driver"
            liquibaseChangelog = file("$projectDir/src/main/resources/changelog/changelog.xml")
        }
    }
}
