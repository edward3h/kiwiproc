plugins {
    application
    id("com.diffplug.spotless")
    id("org.ethelred.kiwiproc")
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
}

dependencies {
    implementation("org.jspecify:jspecify:1.0.0")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
}
