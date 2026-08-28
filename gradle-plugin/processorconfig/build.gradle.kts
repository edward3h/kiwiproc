// extracted from :plugin so :maven-plugin can depend on these shared classes
// without pulling in :plugin's heavy embedded-database runtime deps (see #370)
plugins {
    `java-library`
    checkstyle
    id("com.diffplug.spotless")
    id("signing")
    id("com.vanniktech.maven.publish")
}

apply(from = "../../version.gradle.kts")
group = "org.ethelred.kiwiproc"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jspecify)

    annotationProcessor(libs.avaje.json.processor)
    api(libs.avaje.json.asProvider())

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testImplementation("com.google.truth:truth:1.4.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

checkstyle {
    configFile = file("../../config/checkstyle/checkstyle.xml")
}

configurations.named("checkstyle") {
    resolutionStrategy.force(
        "commons-beanutils:commons-beanutils:1.11.0",
        "org.codehaus.plexus:plexus-utils:4.1.0"
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

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name = "kiwiproc-processorconfig"
        description = "Shared processor configuration classes for KiwiProc build plugins"
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

publishing {
    repositories {
        maven {
            name = "itLocal"
            url = uri(rootProject.layout.buildDirectory.dir("it-repo"))
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String?
    val signingPassword = findProperty("signingPassword") as String?
    isRequired = !signingKey.isNullOrBlank()
    if (isRequired) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
}
