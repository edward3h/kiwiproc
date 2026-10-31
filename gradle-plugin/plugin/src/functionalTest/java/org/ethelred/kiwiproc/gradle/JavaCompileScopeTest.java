/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaCompileScopeTest {
    @TempDir
    Path projectDir;

    private String dryRun(String task) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"scope-test\"\n");
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
                plugins {
                    `java-library`
                    id("org.ethelred.kiwiproc")
                }
                kiwiProc {
                    addDependencies = false
                }
                // unrelated compile task, e.g. static analysis with its own processors
                val otherCompile by tasks.registering(JavaCompile::class) {
                    source = sourceSets.main.get().java
                    classpath = files()
                    destinationDirectory = layout.buildDirectory.dir("other-classes")
                }
                """);
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments(task, "--dry-run")
                .withPluginClasspath()
                .build()
                .getOutput();
    }

    @Test
    void sourceSetCompileTaskDependsOnProcessorConfig() throws IOException {
        assertTrue(dryRun("compileJava").contains(":processorConfig"));
    }

    @Test
    void otherJavaCompileTaskDoesNotDependOnProcessorConfig() throws IOException {
        var output = dryRun("otherCompile");
        assertTrue(output.contains(":otherCompile"));
        assertFalse(output.contains(":processorConfig"), output);
    }
}
