
plugins {
    id("java-convention")
    id("org.ethelred.kiwiproc")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.jakarta.inject)
    testImplementation(libs.postgresql)
    testRuntimeOnly(libs.yaml)
}

kiwiProc {
//    debug = true
    dataSources {
        register("datetime") {
            liquibaseChangelog = file("$projectDir/src/main/resources/datetime/changelog.xml")
        }
        if (project.hasProperty("kiwiproc.periodic-table.url")) {
            register("periodic-table") {
                jdbcUrl = project.property("kiwiproc.periodic-table.url").toString()
            }
        } else {
            register("periodic-table") {
                liquibaseChangelog = file("$projectDir/src/main/resources/periodic/changelog.xml")
            }
        }
    }
}
