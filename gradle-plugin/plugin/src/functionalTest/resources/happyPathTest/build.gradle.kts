plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    id("org.ethelred.kiwiproc")
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
val junitVersion = "6.0.3"
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.postgresql:postgresql:42.7.8")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

kiwiProc {
    addDependencies = false
}