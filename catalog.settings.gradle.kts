
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("avaje-json", "1.7")
            version("jstachio", "1.3.2")
            version("junit", "5.10.0")
            version("recordbuilder", "37")
            version("mapstruct", "1.5.5.Final")

            library("avaje-json", "io.avaje", "avaje-jsonb").versionRef("avaje-json")
            library("avaje-json-processor", "io.avaje", "avaje-jsonb-generator").versionRef("avaje-json")
            library("avaje-prisms", "io.avaje:avaje-prisms:1.11")

            library("jakarta-inject", "jakarta.inject:jakarta.inject-api:2.0.1")

            library("jetbrains-annotations", "org.jetbrains:annotations:24.0.1")

            library("metainfservices", "org.kohsuke.metainf-services:metainf-services:1.11")

            library("utilitary", "com.karuslabs:utilitary:2.0.1")
            library("postgresql", "org.postgresql:postgresql:42.5.1")

            library("jstachio-processor", "io.jstach", "jstachio-apt").versionRef("jstachio")
            library("jstachio-compile", "io.jstach", "jstachio-annotation").versionRef("jstachio")

            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit")
            library("junit-params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit")

            library("recordbuilder-processor", "io.soabase.record-builder", "record-builder-processor").versionRef("recordbuilder")
            library("recordbuilder-core", "io.soabase.record-builder", "record-builder-core").versionRef("recordbuilder")

            library("embeddedpostgres", "io.zonky.test:embedded-postgres:2.0.4")
            library("liquibase-core", "org.liquibase:liquibase-core:4.16.1")

            library("mapstruct-processor", "org.mapstruct", "mapstruct-processor").versionRef("mapstruct")
            library("mapstruct-compile", "org.mapstruct", "mapstruct").versionRef("mapstruct")

            library("jspecify", "org.jspecify:jspecify:0.3.0")

        }
    }
}