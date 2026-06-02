plugins {
    `java-library`
    jacoco
    checkstyle
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
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

val junitVersion = "6.1.0"
dependencies {
    implementation("org.jspecify:jspecify:1.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("com.google.truth:truth:1.4.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

checkstyle {
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

configurations.named("checkstyle") {
    resolutionStrategy.force(
        "commons-beanutils:commons-beanutils:1.11.0",
        "org.codehaus.plexus:plexus-utils:3.6.1"
    )
}

dependencies {
    constraints {
        implementation("org.apache.commons:commons-lang3") {
            version { require("3.18.0") }
            because("Dependabot CVE: uncontrolled recursion below 3.18.0")
        }
        implementation("org.apache.commons:commons-compress") {
            version { require("1.26.0") }
            because("Dependabot CVE: DoS/OOM below 1.26.0")
        }
    }
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