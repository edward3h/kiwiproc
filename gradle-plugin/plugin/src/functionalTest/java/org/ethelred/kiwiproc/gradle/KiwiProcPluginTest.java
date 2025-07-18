/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.Objects;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

public class KiwiProcPluginTest {
    @Test
    void runHappyPathBuild() {
        var testProjectDir =
                Objects.requireNonNull(getClass().getResource("/happyPathTest")).getFile();

        BuildResult result = GradleRunner.create()
                .withProjectDir(new File(testProjectDir))
                .withArguments("test")
                .withPluginClasspath()
                .build();
        assertNotNull(result.task(":test"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":test").getOutcome());
    }
}
