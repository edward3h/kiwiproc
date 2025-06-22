import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle

plugins {
    id("java-convention")
    id("org.ethelred.kiwiproc")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.spring.starter)
    testImplementation(libs.spring.test)
    testRuntimeOnly(libs.spring.starter.jdbc)
    testRuntimeOnly(libs.postgresql)
}

kiwiProc {
    debug = true
    dependencyInjectionStyle = DependencyInjectionStyle.SPRING
}
