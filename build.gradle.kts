plugins {
	id("org.ethelred.kiwiproc").apply(false) // needed so the shared service can be used by sibling subprojects
}

apply(from = "version.gradle.kts")

group = "org.ethelred.kiwiproc"
