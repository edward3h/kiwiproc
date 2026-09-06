plugins {
	id("org.ethelred.kiwiproc").apply(false) // needed so the shared service can be used by sibling subprojects
	id("jacoco-report-aggregation")
	id("com.github.jakemarsden.git-hooks").apply(false)
}

apply(from = "version.gradle.kts")

// The git-hooks plugin writes directly to <rootDir>/.git/hooks, which only exists as a
// directory in the main checkout. In a git worktree, .git is a file pointing at the shared
// gitdir, so that write fails at configuration time and breaks every Gradle invocation.
// Hooks are shared across worktrees anyway (installed once from the main checkout), so just
// skip applying the plugin entirely when .git isn't a plain directory.
if (rootDir.resolve(".git").isDirectory) {
    apply(plugin = "com.github.jakemarsden.git-hooks")
    configure<com.github.jakemarsden.githooksgradleplugin.GitHooksExtension> {
        hooks.set(mapOf("pre-commit" to "build"))
    }
}

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
    if (JavaVersion.current().majorVersion.toInt() >= 25) {
        jacocoAggregation(project(":test-micronaut5"))
    }
    jacocoAggregation(project(":test-any"))
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName.set("test")
        }
    }
}
