import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    java
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish")
}

group = rootProject.group
version = rootProject.version

tasks.withType<Javadoc> {
    if(JavaVersion.current().isJava9Compatible()) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.DEFAULT, automaticRelease = true)
    signAllPublications()
    pom {
        name = "kiwiproc"
        description = "Java build time SQL support"
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

signing {
    val signingKey = findProperty("signingKey").toString()
    val signingPassword = findProperty("signingPassword").toString()
    useInMemoryPgpKeys(signingKey, signingPassword)
}
