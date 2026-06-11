/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import io.avaje.jsonb.Jsonb;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfig;
import org.junit.jupiter.api.Test;

class KiwiProcMojoTest {

    private static final String CHANGELOG_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <databaseChangeLog
                    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">
                <changeSet id="1" author="test">
                    <createTable tableName="widget">
                        <column name="id" type="int"/>
                    </createTable>
                </changeSet>
            </databaseChangeLog>
            """;

    @Test
    void generatesConfigForDefaultEmbeddedPostgres() throws IOException, MojoExecutionException {
        var projectDir = Files.createTempDirectory("kiwiproc-maven-mojo");
        var changelog = projectDir.resolve("src/main/resources/changelog.xml");
        Files.createDirectories(changelog.getParent());
        Files.writeString(changelog, CHANGELOG_XML);

        var mojo = new KiwiProcMojo();
        mojo.setLog(new SystemStreamLog());
        mojo.setProject(testProject(projectDir));
        mojo.setLiquibaseChangelog(changelog.toFile());
        mojo.setConfigFile(projectDir.resolve("target/kiwiproc/config.json").toFile());
        mojo.setTestResourcesOutputDirectory(
                projectDir.resolve("target/generated-test-resources/kiwiproc").toFile());

        mojo.execute();

        var configJson = Files.readString(projectDir.resolve("target/kiwiproc/config.json"));
        var config = Jsonb.builder().build().type(ProcessorConfig.class).fromJson(configJson);
        assertThat(config.dataSources()).hasSize(1);
        var ds = config.dataSources().get("default");
        assertThat(ds.url()).startsWith("jdbc:postgresql://localhost:");

        var propsPath = projectDir.resolve("target/generated-test-resources/kiwiproc/application-test.properties");
        var props = new Properties();
        try (var in = Files.newInputStream(propsPath)) {
            props.load(in);
        }
        assertThat(props.getProperty("datasources.default.url")).isEqualTo(ds.url());
    }

    @Test
    void generatesConfigForEmbeddedH2() throws IOException, MojoExecutionException {
        var projectDir = Files.createTempDirectory("kiwiproc-maven-mojo-h2");
        var changelog = projectDir.resolve("src/main/resources/changelog.xml");
        Files.createDirectories(changelog.getParent());
        Files.writeString(changelog, CHANGELOG_XML);

        var dataSource = new DataSourceParameter();
        dataSource.setName("default");
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setLiquibaseChangelog(changelog.toFile());

        var mojo = new KiwiProcMojo();
        mojo.setLog(new SystemStreamLog());
        mojo.setProject(testProject(projectDir));
        mojo.setDataSources(List.of(dataSource));
        mojo.setConfigFile(projectDir.resolve("target/kiwiproc/config.json").toFile());
        mojo.setTestResourcesOutputDirectory(
                projectDir.resolve("target/generated-test-resources/kiwiproc").toFile());

        mojo.execute();

        var configJson = Files.readString(projectDir.resolve("target/kiwiproc/config.json"));
        var config = Jsonb.builder().build().type(ProcessorConfig.class).fromJson(configJson);
        var ds = config.dataSources().get("default");
        assertThat(ds.url()).startsWith("jdbc:h2:tcp://localhost:");
        assertThat(ds.driverClassName()).isEqualTo("org.h2.Driver");
    }

    @Test
    void passesThroughExternalDataSourceUnchanged() throws IOException, MojoExecutionException {
        var projectDir = Files.createTempDirectory("kiwiproc-maven-mojo-external");

        var dataSource = new DataSourceParameter();
        dataSource.setName("default");
        dataSource.setJdbcUrl("jdbc:postgresql://db.example.com:5432/mydb");
        dataSource.setUsername("appuser");

        var mojo = new KiwiProcMojo();
        mojo.setLog(new SystemStreamLog());
        mojo.setProject(testProject(projectDir));
        mojo.setDataSources(List.of(dataSource));
        mojo.setConfigFile(projectDir.resolve("target/kiwiproc/config.json").toFile());
        mojo.setTestResourcesOutputDirectory(
                projectDir.resolve("target/generated-test-resources/kiwiproc").toFile());

        mojo.execute();

        var configJson = Files.readString(projectDir.resolve("target/kiwiproc/config.json"));
        var config = Jsonb.builder().build().type(ProcessorConfig.class).fromJson(configJson);
        var ds = config.dataSources().get("default");
        assertThat(ds.url()).isEqualTo("jdbc:postgresql://db.example.com:5432/mydb");
        assertThat(ds.username()).isEqualTo("appuser");
    }

    @Test
    void registersGeneratedDirectoryAsTestResource() throws IOException, MojoExecutionException {
        var projectDir = Files.createTempDirectory("kiwiproc-maven-mojo-resources");
        var changelog = projectDir.resolve("src/main/resources/changelog.xml");
        Files.createDirectories(changelog.getParent());
        Files.writeString(changelog, CHANGELOG_XML);

        var project = testProject(projectDir);
        var mojo = new KiwiProcMojo();
        mojo.setLog(new SystemStreamLog());
        mojo.setProject(project);
        mojo.setLiquibaseChangelog(changelog.toFile());
        mojo.setConfigFile(projectDir.resolve("target/kiwiproc/config.json").toFile());
        var testResourcesDir = projectDir.resolve("target/generated-test-resources/kiwiproc");
        mojo.setTestResourcesOutputDirectory(testResourcesDir.toFile());

        mojo.execute();

        assertThat(project.getTestResources()).hasSize(1);
        assertThat(project.getTestResources().get(0).getDirectory())
                .isEqualTo(testResourcesDir.toAbsolutePath().toString());
    }

    private MavenProject testProject(Path projectDir) {
        var model = new Model();
        var build = new Build();
        build.setDirectory(projectDir.resolve("target").toString());
        model.setBuild(build);
        var project = new MavenProject(model);
        project.setFile(projectDir.resolve("pom.xml").toFile());
        return project;
    }
}
