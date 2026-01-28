plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.3.1"
    id("com.diffplug.spotless").version("8.2.1")
    id("org.danilopianini.publish-on-central").version("9.1.9")
}

apply(from = "../../version.gradle.kts")
group = "org.ethelred.kiwiproc"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation(libs.jspecify)

    annotationProcessor(libs.avaje.json.processor)
    implementation(libs.avaje.json.asProvider())
    implementation(libs.embeddedpostgres)
    implementation(libs.liquibase.core)
    implementation(libs.postgresql)
    // quick fix - should make postgres version/arch configurable
    runtimeOnly(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.7.0"))
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-arm64v8")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
    testImplementation(gradleTestKit())
val junitVersion = "5.14.1"
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website = "https://edward3h.github.io/kiwiproc/"
    vcsUrl = "https://github.com/edward3h/kiwiproc.git"

    // Define the plugin
    val kiwiproc by plugins.creating {
        id = "org.ethelred.kiwiproc"
        implementationClass = "org.ethelred.kiwiproc.gradle.KiwiProcPlugin"
        displayName = "KiwiProc Gradle Plugin"
        description = "Configure and run embedded databases for KiwiProc generation"
        tags.addAll("Java", "database", "annotation processor")
    }
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Add a task to run the functional tests
val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

tasks.named<Task>("check") {
    // Run the functional tests as part of `check`
    dependsOn(functionalTest)
}

tasks.named<Test>("test") {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    expand("version" to project.version)
}

spotless {
    java {
        cleanthat()
        importOrder()
        removeUnusedImports()
        palantirJavaFormat()
        formatAnnotations()
        licenseHeader("/* (C) Edward Harman \$YEAR */")
    }
}

// plugin is published as a library as well, for the shared processorconfig classes
publishOnCentral {
    repoOwner = "edward3h"
    projectDescription = "Java build time SQL support"
    projectLongName = "kiwiproc"
    projectUrl = "https://github.com/edward3h/kiwiproc"
    scmConnection = "https://github.com/edward3h/kiwiproc.git"
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                developers {
                    developer {
                        name = "Edward Harman"
                        email = "jaq@ethelred.org"
                    }
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey").toString()
    val signingPassword = findProperty("signingPassword").toString()
    useInMemoryPgpKeys(signingKey, signingPassword)
}
