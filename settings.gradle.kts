rootProject.name = "kiwiproc"
include("shared", "querymeta", "processor", "runtime", "test-spring", "test-micronaut")

apply(from = "catalog.settings.gradle.kts")
