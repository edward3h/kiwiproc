import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
    id("org.asciidoctor.jvm.convert") version "4.0.3"
}

repositories {
    mavenCentral()
}

//configurations.create("asciidoctorExtensions")

dependencies {
//    add("asciidoctorExtensions","com.bmuschko:asciidoctorj-tabbed-code-extension:0.3")
}

tasks.named<AsciidoctorTask>("asciidoctor").configure {
    baseDirFollowsSourceDir()
//    configurations("asciidoctorExtensions")
}

tasks.named("build") {
    dependsOn(tasks.named("asciidoctor"))
}