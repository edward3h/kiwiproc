/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven.it;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import io.avaje.jsonb.Jsonb;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KiwiProcMojoInvokerTest {

    private static final String KIWIPROC_VERSION = System.getProperty("kiwiproc.version");
    private static final String IT_REPO_URL = System.getProperty("kiwiproc.it.repo.url");

    /**
     * maven-invoker's {@code MavenCommandLineBuilder} only finds an {@code mvn} executable
     * via the project directory or an explicit maven home (env {@code MAVEN_HOME}/{@code M2_HOME}
     * or {@link DefaultInvoker#setMavenHome}); it does not scan {@code PATH} the way a shell
     * would, so version-manager shims (asdf, sdkman, etc.) are invisible to it even though
     * {@code mvn} works fine from a terminal. Ask the {@code mvn} already on this process's
     * {@code PATH} to report its own home directory and feed that back to the invoker.
     */
    private static final File MAVEN_HOME = detectMavenHome();

    private static File detectMavenHome() {
        try {
            var process = new ProcessBuilder("mvn", "-version")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes());
            }
            process.waitFor();
            for (var line : output.lines().toList()) {
                if (line.startsWith("Maven home:")) {
                    return new File(line.substring("Maven home:".length()).trim());
                }
            }
            throw new IllegalStateException("'mvn -version' did not report a Maven home. Output:\n" + output);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to detect Maven home via 'mvn -version'", e);
        }
    }

    @Test
    void defaultEmbeddedPostgresGeneratesConfigAtGenerateSourcesPhase(@TempDir Path tempDir)
            throws IOException, MavenInvocationException {
        var projectDir = copyFixture(tempDir, "default-embedded-postgres");

        var outcome = runGenerateSources(projectDir);
        assertWithMessage("mvn generate-sources log:\n" + String.join("\n", outcome.log()))
                .that(outcome.exitCode())
                .isEqualTo(0);

        var config = readProcessorConfig(projectDir);
        assertThat(config.dataSources()).hasSize(1);
        var ds = config.dataSources().get("default");
        assertThat(ds.url()).startsWith("jdbc:postgresql://localhost:");

        var props = readTestProperties(projectDir);
        assertThat(props.getProperty("datasources.default.url")).isEqualTo(ds.url());
    }

    @Test
    void embeddedMySQLGeneratesConfigAtGenerateSourcesPhase(@TempDir Path tempDir)
            throws IOException, MavenInvocationException {
        var projectDir = copyFixture(tempDir, "embedded-mysql");

        var outcome = runGenerateSources(projectDir);
        assertWithMessage("mvn generate-sources log:\n" + String.join("\n", outcome.log()))
                .that(outcome.exitCode())
                .isEqualTo(0);

        var config = readProcessorConfig(projectDir);
        assertThat(config.dataSources()).hasSize(1);
        var ds = config.dataSources().get("default");
        assertThat(ds.url()).startsWith("jdbc:mysql://");
        assertThat(ds.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");

        var props = readTestProperties(projectDir);
        assertThat(props.getProperty("datasources.default.url")).isEqualTo(ds.url());
    }

    @Test
    void multipleDataSourcesMapPlexusListOfBeansCorrectly(@TempDir Path tempDir)
            throws IOException, MavenInvocationException {
        var projectDir = copyFixture(tempDir, "multiple-datasources");

        var outcome = runGenerateSources(projectDir);
        assertWithMessage("mvn generate-sources log:\n" + String.join("\n", outcome.log()))
                .that(outcome.exitCode())
                .isEqualTo(0);

        var config = readProcessorConfig(projectDir);
        assertThat(config.dataSources()).hasSize(2);

        var defaultDs = config.dataSources().get("default");
        assertThat(defaultDs.url()).startsWith("jdbc:h2:tcp://localhost:");
        assertThat(defaultDs.driverClassName()).isEqualTo("org.h2.Driver");

        var externalDs = config.dataSources().get("external");
        assertThat(externalDs.url()).isEqualTo("jdbc:h2:mem:extdb");
    }

    private record InvocationOutcome(int exitCode, List<String> log) {}

    private InvocationOutcome runGenerateSources(Path projectDir) throws MavenInvocationException {
        var log = new ArrayList<String>();
        InvocationRequest request = new DefaultInvocationRequest();
        request.setBaseDirectory(projectDir.toFile());
        request.setGoals(List.of("generate-sources"));
        request.setBatchMode(true);
        request.setUpdateSnapshots(true);
        request.setShowErrors(true);
        var properties = new Properties();
        properties.setProperty("kiwiproc.version", KIWIPROC_VERSION);
        properties.setProperty("kiwiproc.it.repo.url", IT_REPO_URL);
        request.setProperties(properties);
        request.setOutputHandler(log::add);

        var invoker = new DefaultInvoker();
        invoker.setMavenHome(MAVEN_HOME);
        var result = invoker.execute(request);
        return new InvocationOutcome(result.getExitCode(), log);
    }

    private Path copyFixture(Path tempDir, String name) throws IOException {
        var source = Path.of("src/functionalTest/resources/it", name);
        var target = tempDir.resolve(name);
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (var path : stream.toList()) {
                var dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(path, dest);
                }
            }
        }
        return target;
    }

    private ProcessorConfig readProcessorConfig(Path projectDir) throws IOException {
        var json = Files.readString(projectDir.resolve("target/kiwiproc/config.json"));
        return Jsonb.builder().build().type(ProcessorConfig.class).fromJson(json);
    }

    private Properties readTestProperties(Path projectDir) throws IOException {
        var props = new Properties();
        try (var in = Files.newInputStream(
                projectDir.resolve("target/generated-test-resources/kiwiproc/application-test.properties"))) {
            props.load(in);
        }
        return props;
    }
}
