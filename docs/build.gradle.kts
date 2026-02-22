import de.undercouch.gradle.tasks.download.Download
import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
    id("org.asciidoctor.jvm.convert") version "4.0.5"
        id("de.undercouch.download") version "5.7.0"
}

repositories {
    mavenCentral()
}

//configurations.create("asciidoctorExtensions")

dependencies {
//    add("asciidoctorExtensions","com.bmuschko:asciidoctorj-tabbed-code-extension:0.3")
}

tasks.named<AsciidoctorTask>("asciidoctor").configure {
    dependsOn(conversionsTable)
    inputs.files("src/docs/asciidoc")
    baseDirFollowsSourceDir()
//    configurations("asciidoctorExtensions")
    attributes(
        mapOf(
            "build-dir" to layout.buildDirectory.get().toString(),
            "revnumber" to rootProject.version,
            "jakarta-dependency" to libs.jakarta.inject
        )
    )
}

val copyJavadoc = tasks.register<Copy>("copyJavadoc") {
    dependsOn(":shared:javadoc", ":runtime:javadoc")
    into(layout.buildDirectory.dir("docs/asciidoc/javadoc"))
    from(project(":shared").tasks.named<Javadoc>("javadoc").map { it.destinationDir!! }) {
        into("shared")
    }
    from(project(":runtime").tasks.named<Javadoc>("javadoc").map { it.destinationDir!! }) {
        into("runtime")
    }
}

tasks.named("asciidoctor") {
    finalizedBy(copyJavadoc)
}

tasks.named("build") {
    dependsOn(tasks.named("asciidoctor"))
}

var conversionsTable = tasks.register<JavaExec>("generateConversionsTable") {
    classpath(project(":processor").tasks.named("compileJava"))
    mainClass = "org.ethelred.kiwiproc.processor.CoreTypes"
    args(layout.buildDirectory.file("conversions.adoc").get().toString())
}
