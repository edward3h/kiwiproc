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
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.2.1")
    implementation(libs.publish.on.central)
}