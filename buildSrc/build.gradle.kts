plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.embeddedpostgres)
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.0.BETA3")
    runtimeOnly(libs.liquibase.core)
}