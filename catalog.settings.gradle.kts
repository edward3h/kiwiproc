
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("avaje-json", "2.4")
            version("junit", "5.11.4")
            version("recordbuilder", "44")
            version("mapstruct", "1.6.3")

            library("avaje-json", "io.avaje", "avaje-jsonb").versionRef("avaje-json")
            library("avaje-json-processor", "io.avaje", "avaje-jsonb-generator").versionRef("avaje-json")
            library("avaje-prisms", "io.avaje:avaje-prisms:1.38")

            library("jakarta-inject", "jakarta.inject:jakarta.inject-api:2.0.1")

            library("jetbrains-annotations", "org.jetbrains:annotations:26.0.2")

            library("utilitary", "com.karuslabs:utilitary:3.0.0")
            library("postgresql", "org.postgresql:postgresql:42.7.5")

            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit")
            library("junit-params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit")

            library("recordbuilder-processor", "io.soabase.record-builder", "record-builder-processor").versionRef("recordbuilder")
            library("recordbuilder-core", "io.soabase.record-builder", "record-builder-core").versionRef("recordbuilder")

            library("embeddedpostgres", "io.zonky.test:embedded-postgres:2.1.0")
            library("liquibase-core", "org.liquibase:liquibase-core:4.31.0")

            library("mapstruct-processor", "org.mapstruct", "mapstruct-processor").versionRef("mapstruct")
            library("mapstruct-compile", "org.mapstruct", "mapstruct").versionRef("mapstruct")

            library("jspecify", "org.jspecify:jspecify:1.0.0")

            library("javapoet", "com.palantir.javapoet:javapoet:0.6.0")
            library("guava", "com.google.guava:guava:33.4.0-jre")
            library("compile-testing", "com.google.testing.compile:compile-testing:0.21.0")
            library("compile-testing-extension", "io.github.kiskae:compile-testing-extension:1.0.2")

            library("yaml", "org.yaml:snakeyaml:2.3")

            bundle("compile-testing", listOf("guava", "compile-testing", "compile-testing-extension"))
        }
    }
}