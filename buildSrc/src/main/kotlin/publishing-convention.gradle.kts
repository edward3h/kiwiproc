import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    java
    id("maven-publish")
    id("signing")
}

group = rootProject.group
version = rootProject.version

tasks.withType<Javadoc>() {
    if(JavaVersion.current().isJava9Compatible()) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name = "kiwiproc"
                description = "Java build time SQL support"
                url = "https://github.com/edward3h/kiwiproc"
                licenses {
                    license {
                        name = "GNU General Public License, Version 3"
                        url = "https://choosealicense.com/licenses/gpl-3.0/"
                    }
                }
                developers {
                    developer {
                        name = "Edward Harman"
                        email = "jaq@ethelred.org"
                    }
                }
                scm {
                    connection = "https://github.com/edward3h/kiwiproc.git"
                    developerConnection = "git@github.com:edward3h/kiwiproc.git"
                    url = "https://github.com/edward3h/kiwiproc"
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey").toString()
    val signingPassword = findProperty("signingPassword").toString()
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}
