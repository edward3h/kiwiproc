plugins {
    `java-library`
    jacoco
    checkstyle
    id("com.diffplug.spotless")
    id("org.gradlex.maven-plugin-development") version "1.0.3"
}

apply(from = "../../version.gradle.kts")
group = "org.ethelred.kiwiproc"
base.archivesName.set("kiwiproc-maven-plugin")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":processorconfig"))
    implementation(libs.jspecify)

    implementation("org.apache.maven:maven-plugin-api:3.9.16")
    implementation("org.apache.maven.plugin-tools:maven-plugin-annotations:3.13.1")
    implementation("org.apache.maven:maven-core:3.9.16")

    implementation(libs.avaje.json.asProvider())
    implementation(libs.embeddedpostgres)
    implementation(libs.liquibase.core)
    implementation(libs.postgresql)
    implementation(libs.h2)
    runtimeOnly(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:18.4.0"))
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-arm64v8")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.0")
    testImplementation("com.google.truth:truth:1.4.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPlugin {
    // groupId/version/description picked up from project coordinates;
    // artifactId defaults to the Gradle project name "maven-plugin", which Maven
    // reserves for plugins of the maven team (artifactIds of the form
    // "maven-___-plugin"), so use the project's "kiwiproc-___" naming convention instead.
    artifactId.set("kiwiproc-maven-plugin")
}

// Add a source set for the maven-invoker-based functional test suite (mirrors :plugin's pattern)
val itRepoDir = rootProject.layout.buildDirectory.dir("it-repo")

val functionalTestSourceSet = sourceSets.create("functionalTest")

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
    "functionalTestImplementation"(project(":processorconfig"))
    "functionalTestImplementation"(libs.avaje.json.asProvider())
    "functionalTestImplementation"(libs.maven.invoker)
}

val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    systemProperty("kiwiproc.version", project.version)
    systemProperty("kiwiproc.it.repo.url", itRepoDir.get().asFile.toURI().toString())
}

tasks.named<Task>("check") {
    dependsOn(functionalTest)
}

tasks.named<Test>("test") {
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

checkstyle {
    configFile = file("../../config/checkstyle/checkstyle.xml")
}

configurations.named("checkstyle") {
    resolutionStrategy.force(
        "commons-beanutils:commons-beanutils:1.11.0",
        "org.codehaus.plexus:plexus-utils:4.0.3"
    )
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
