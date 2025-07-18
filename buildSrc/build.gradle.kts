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
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.1.0")
    implementation(libs.publish.on.central)
}