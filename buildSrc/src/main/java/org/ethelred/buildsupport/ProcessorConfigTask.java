package org.ethelred.buildsupport;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@UntrackedTask(because = "A new Postgres instance is started for each Gradle run, and the database port and name are chosen at random.")
public abstract class ProcessorConfigTask extends DefaultTask {
    @ServiceReference("embeddedPostgres")
    abstract Property<EmbeddedPostgresService> getService();

    @OutputFile
    abstract RegularFileProperty getConfigFile();

    @OutputFile
    abstract RegularFileProperty getApplicationConfigFile();

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
                //language=JSON
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
        var applicationConfig =
                //language=yaml
                """
                datasources:
                  default:
                    url: "jdbc:postgresql://localhost:%d/%s?user=%s"
                """.formatted(connectionInfo.getPort(), connectionInfo.getDbName(), connectionInfo.getUser());
        if (getApplicationConfigFile().isPresent()) {
            outputPath = getApplicationConfigFile().get().getAsFile().toPath();
            try {
                Files.writeString(outputPath, applicationConfig, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
