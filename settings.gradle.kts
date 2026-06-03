pluginManagement {
    includeBuild("gradle-plugin")
}

rootProject.name = "kiwiproc"
include("shared", "querymeta", "processor", "runtime", "spring-autoconfigure", "test-spring", "test-micronaut", "docs", ":docs:example", "test-any", "test-mysql", "test-h2")
// Micronaut 5 requires JVM 25 (Gradle itself must run on 25)
if (JavaVersion.current().majorVersion.toInt() >= 25) {
    include("test-micronaut5")
}
includeBuild("gradle-plugin")
includeBuild(".")
apply(from = "catalog.settings.gradle.kts")

