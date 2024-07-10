package org.ethelred.buildsupport;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class ProcessorConfigTask extends DefaultTask {
    @ServiceReference("embeddedPostgres")
    abstract Property<EmbeddedPostgresService> getService();

    @OutputFile
    abstract RegularFileProperty getConfigFile();

    @InputFile
    abstract RegularFileProperty getLiquibaseChangelog();

    @Input
    abstract Property<String> getDependencyInjectionStyle();

    @TaskAction
    public void run() {
        var liquibaseFile = getLiquibaseChangelog().get().getAsFile();
        var connectionInfo = getService().get().getPreparedDatabase(liquibaseFile);
        // the ":shared" json support is not available in buildSrc
        var config =
                """
                {
                    "dataSources": {
                        "default": {
                            "named": "default",
                            "url": "jdbc:postgresql://localhost:%d/%s?user=%s",
                            "database": "%2$s",
                            "username":  "%3$s"
                        }
                    },
                    "dependencyInjectionStyle": "%s"
                }
                """.formatted(
                    connectionInfo.getPort(),
                        connectionInfo.getDbName(),
                        connectionInfo.getUser(),
                        getDependencyInjectionStyle().get()
                );
        var outputPath = getConfigFile().get().getAsFile().toPath();
        try {
            Files.writeString(outputPath, config, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
