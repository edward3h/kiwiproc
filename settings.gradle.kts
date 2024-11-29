rootProject.name = "kiwiproc"
include("shared", "querymeta", "processor", "runtime", "test-spring", "test-micronaut", "docs", ":docs:example")

apply(from = "catalog.settings.gradle.kts")
