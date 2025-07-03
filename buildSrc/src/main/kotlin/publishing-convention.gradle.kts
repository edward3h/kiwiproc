import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    java
    id("maven-publish")
    id("signing")
    id("org.danilopianini.publish-on-central")
}

group = rootProject.group
version = rootProject.version

tasks.withType<Javadoc> {
    if(JavaVersion.current().isJava9Compatible()) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishOnCentral {
    repoOwner = "edward3h"
    projectDescription = "Java build time SQL support"
    projectLongName = "kiwiproc"
    projectUrl = "https://github.com/edward3h/kiwiproc"
    scmConnection = "https://github.com/edward3h/kiwiproc.git"
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                developers {
                    developer {
                        name = "Edward Harman"
                        email = "jaq@ethelred.org"
                    }
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey").toString()
    val signingPassword = findProperty("signingPassword").toString()
    useInMemoryPgpKeys(signingKey, signingPassword)
}
