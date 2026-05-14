plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("com.github.jakemarsden:git-hooks-gradle-plugin:0.0.2")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.4.0")
    implementation(libs.vanniktech.maven.publish)
}