pluginManagement {
    includeBuild("gradle-plugin")
}

rootProject.name = "kiwiproc"
include("shared", "querymeta", "processor", "runtime", "spring-autoconfigure", "test-spring", "test-micronaut", "test-micronaut5", "docs", ":docs:example", "test-any", "test-mysql", "test-h2")
includeBuild("gradle-plugin")
includeBuild(".")
apply(from = "catalog.settings.gradle.kts")

