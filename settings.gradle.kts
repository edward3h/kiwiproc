pluginManagement {
    includeBuild("gradle-plugin")
}

rootProject.name = "kiwiproc"
include("shared", "querymeta", "processor", "runtime", "test-spring", "test-micronaut", "docs", ":docs:example", "test-any")
includeBuild("gradle-plugin")
includeBuild(".")
apply(from = "catalog.settings.gradle.kts")

