
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("avaje-json", "3.9")
            version("junit", "6.0.3")
            version("recordbuilder", "51")
            version("mapstruct", "1.6.3")
            version("springboot", "3.5.9")

            library("avaje-json", "io.avaje", "avaje-jsonb").versionRef("avaje-json")
            library("avaje-json-processor", "io.avaje", "avaje-jsonb-generator").versionRef("avaje-json")
            library("avaje-prisms", "io.avaje:avaje-prisms:2.0")

            library("jakarta-inject", "jakarta.inject:jakarta.inject-api:2.0.1")

            library("jetbrains-annotations", "org.jetbrains:annotations:26.0.2-1")

            library("utilitary", "com.karuslabs:utilitary:3.0.0")
            library("postgresql", "org.postgresql:postgresql:42.7.8")

            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit")
            library("junit-params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit")

            library("recordbuilder-processor", "io.soabase.record-builder", "record-builder-processor").versionRef("recordbuilder")
            library("recordbuilder-core", "io.soabase.record-builder", "record-builder-core").versionRef("recordbuilder")

            library("embeddedpostgres", "io.zonky.test:embedded-postgres:2.2.0")
            library("liquibase-core", "org.liquibase:liquibase-core:5.0.1")

            library("mapstruct-processor", "org.mapstruct", "mapstruct-processor").versionRef("mapstruct")
            library("mapstruct-compile", "org.mapstruct", "mapstruct").versionRef("mapstruct")

            library("jspecify", "org.jspecify:jspecify:1.0.0")

            library("javapoet", "com.palantir.javapoet:javapoet:0.11.0")
            library("guava", "com.google.guava:guava:33.5.0-jre")
            library("compile-testing", "com.google.testing.compile:compile-testing:0.23.0")
            library("compile-testing-extension", "io.github.kiskae:compile-testing-extension:1.0.2")

            library("yaml", "org.yaml:snakeyaml:2.5")

            library("spring-starter", "org.springframework.boot", "spring-boot-starter").versionRef("springboot")
            library("spring-starter-jdbc", "org.springframework.boot", "spring-boot-starter-jdbc").versionRef("springboot")
            library("spring-test", "org.springframework.boot", "spring-boot-starter-test").versionRef("springboot")

            library("publish-on-central", "org.danilopianini:publish-on-central:9.1.9")

            bundle("compile-testing", listOf("guava", "compile-testing", "compile-testing-extension"))
        }
    }
}