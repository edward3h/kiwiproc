plugins {
    `java-gradle-plugin`
    jacoco
    id("com.gradle.plugin-publish") version "2.1.0"
    id("com.diffplug.spotless").version("8.4.0")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
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
    implementation(libs.mysql)
    implementation(libs.testcontainers.mysql)
    // quick fix - should make postgres version/arch configurable
    runtimeOnly(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:18.3.0"))
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-arm64v8")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
    testImplementation(gradleTestKit())
val junitVersion = "6.0.3"
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
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
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
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = "kiwiproc"
        description = "Java build time SQL support"
        url = "https://github.com/edward3h/kiwiproc"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        scm {
            connection = "https://github.com/edward3h/kiwiproc.git"
            url = "https://github.com/edward3h/kiwiproc"
        }
        developers {
            developer {
                name = "Edward Harman"
                email = "jaq@ethelred.org"
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey").toString()
    val signingPassword = findProperty("signingPassword").toString()
    useInMemoryPgpKeys(signingKey, signingPassword)
}
