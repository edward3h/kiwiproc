plugins {
	id("org.ethelred.kiwiproc").apply(false) // needed so the shared service can be used by sibling subprojects
	id("jacoco-report-aggregation")
}

apply(from = "version.gradle.kts")

group = "org.ethelred.kiwiproc"

repositories {
    mavenCentral()
}

dependencies {
    jacocoAggregation(project(":shared"))
    jacocoAggregation(project(":querymeta"))
    jacocoAggregation(project(":processor"))
    jacocoAggregation(project(":runtime"))
    jacocoAggregation(project(":test-spring"))
    jacocoAggregation(project(":test-micronaut"))
    jacocoAggregation(project(":test-any"))
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName.set("test")
        }
    }
}
