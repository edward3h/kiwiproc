
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("avaje-json", "1.7")
            version("junit", "5.10.0")
            version("recordbuilder", "37")
            version("mapstruct", "1.5.5.Final")
            version("ethelred-util", "2.2")

            library("avaje-json", "io.avaje", "avaje-jsonb").versionRef("avaje-json")
            library("avaje-json-processor", "io.avaje", "avaje-jsonb-generator").versionRef("avaje-json")
            library("avaje-prisms", "io.avaje:avaje-prisms:1.11")

            library("jakarta-inject", "jakarta.inject:jakarta.inject-api:2.0.1")

            library("jetbrains-annotations", "org.jetbrains:annotations:24.0.1")

            library("metainfservices", "org.kohsuke.metainf-services:metainf-services:1.11")

            library("utilitary", "com.karuslabs:utilitary:2.0.1")
            library("postgresql", "org.postgresql:postgresql:42.7.3")

            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit")
            library("junit-params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit")

            library("recordbuilder-processor", "io.soabase.record-builder", "record-builder-processor").versionRef("recordbuilder")
            library("recordbuilder-core", "io.soabase.record-builder", "record-builder-core").versionRef("recordbuilder")

            library("embeddedpostgres", "io.zonky.test:embedded-postgres:2.0.7")
            library("liquibase-core", "org.liquibase:liquibase-core:4.16.1")

            library("mapstruct-processor", "org.mapstruct", "mapstruct-processor").versionRef("mapstruct")
            library("mapstruct-compile", "org.mapstruct", "mapstruct").versionRef("mapstruct")

            library("jspecify", "org.jspecify:jspecify:0.3.0")

            library("javapoet", "com.palantir.javapoet:javapoet:0.2.0")
            library("guava", "com.google.guava:guava:32.1.3-jre")
            library("compile-testing", "com.google.testing.compile:compile-testing:0.21.0")
            library("compile-testing-extension", "io.github.kiskae:compile-testing-extension:1.0.2")

            library("ethelred-util", "org.ethelred.util", "common").versionRef("ethelred-util")
            library("yaml", "org.yaml:snakeyaml:2.2")

            bundle("compile-testing", listOf("guava", "compile-testing", "compile-testing-extension"))
        }
    }
}