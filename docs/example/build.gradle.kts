import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.testcontainers:testcontainers:1.20.4")
        classpath("org.testcontainers:postgresql:1.20.4")
    }
}

plugins {
    application
    id("com.diffplug.spotless")
    id("org.liquibase.gradle") version "2.2.2"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jspecify:jspecify:1.0.0")
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
    liquibaseRuntime("info.picocli:picocli:4.7.6")
    liquibaseRuntime("ch.qos.logback:logback-core:1.2.3")
    liquibaseRuntime("ch.qos.logback:logback-classic:1.2.3")
    liquibaseRuntime("javax.xml.bind:jaxb-api:2.3.1")
    liquibaseRuntime("org.postgresql:postgresql:42.7.4")
}

liquibase {
    jvmArgs = "-Duser.dir=${project.projectDir}"
    activities.register("main") {
        arguments = mapOf(
            "changelogFile" to "src/main/resources/changelog.xml",
            "url" to "jdbc:postgresql://localhost/postgres",
            "logLevel" to "warning",
            "username" to "postgres",
            "password" to "password"
        )
        println(arguments)
    }

}

lateinit var postgres: PostgreSQLContainer<*>

val startDatabase by tasks.registering {
    doFirst {
        postgres = PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("password")
        postgres.portBindings = listOf("5432:5432")
        postgres.start()
    }
}

val stopDatabase by tasks.registering {
    doFirst {
        postgres.stop()
    }
}


tasks.named("update") {
    finalizedBy(stopDatabase)
    dependsOn(startDatabase)
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(tasks.named("update"))
    finalizedBy(stopDatabase)
    options.compilerArgs.add("-Aorg.ethelred.kiwiproc.configuration=${project.file("src/main/resources/kiwi-config.json")}")
}