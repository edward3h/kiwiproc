plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    id("com.diffplug.spotless").version("7.0.4")
}

group = "org.ethelred.kiwiproc"
version = "0.3-SNAPSHOT"

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
    testImplementation(gradleTestKit())
val junitVersion = "5.13.1"
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    // Define the plugin
    val kiwiproc by plugins.creating {
        id = "org.ethelred.kiwiproc"
        implementationClass = "org.ethelred.kiwiproc.gradle.KiwiProcPlugin"
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