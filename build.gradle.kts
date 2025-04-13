plugins {
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "org.ethelred.kiwiproc"
version = "0.2"

nexusPublishing {
	repositories {
		create("sonatype") {
			//only for users registered in Sonatype after 24 Feb 2021
			nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
			snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
		}
	}
}
