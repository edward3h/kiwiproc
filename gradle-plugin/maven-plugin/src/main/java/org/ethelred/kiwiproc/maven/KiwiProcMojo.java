/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfig;
import org.h2.jdbcx.JdbcDataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Generates the kiwiproc annotation-processor configuration ({@code config.json}) and a test
 * properties file describing how to connect to the build-time database(s).
 *
 * <p>For each configured (or default) datasource: starts/reuses an embedded PostgreSQL or H2
 * database and applies the Liquibase changelog, or passes through an external JDBC URL
 * (optionally applying Liquibase to it too).
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = false)
public class KiwiProcMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter
    private List<DataSourceParameter> dataSources = new ArrayList<>();

    @Parameter(defaultValue = "${project.basedir}/src/main/resources/changelog.xml")
    private File liquibaseChangelog;

    @Parameter(defaultValue = "JAKARTA")
    private DependencyInjectionStyle dependencyInjectionStyle;

    @Parameter(defaultValue = "false")
    private boolean debug;

    @Parameter(defaultValue = "${project.build.directory}/kiwiproc/config.json")
    private File configFile;

    @Parameter(defaultValue = "${project.build.directory}/generated-test-resources/kiwiproc")
    private File testResourcesOutputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        if (dependencyInjectionStyle == null) {
            dependencyInjectionStyle = DependencyInjectionStyle.JAKARTA;
        }

        var effectiveDataSources = effectiveDataSources();

        var configsByName = effectiveDataSources.stream()
                .map(this::toDataSourceConfig)
                .collect(Collectors.toMap(DataSourceConfig::named, x -> x, (a, b) -> a, LinkedHashMap::new));

        var processorConfig = new ProcessorConfig(configsByName, dependencyInjectionStyle, debug);

        writeConfigFile(processorConfig);
        writeTestProperties(processorConfig);
        registerTestResourcesDirectory();
    }

    private List<DataSourceParameter> effectiveDataSources() throws MojoExecutionException {
        if (!dataSources.isEmpty()) {
            return dataSources;
        }
        if (liquibaseChangelog != null && liquibaseChangelog.exists()) {
            var defaultDataSource = new DataSourceParameter();
            defaultDataSource.setName("default");
            defaultDataSource.setLiquibaseChangelog(liquibaseChangelog);
            return List.of(defaultDataSource);
        }
        throw new MojoExecutionException(
                "kiwiproc: no <dataSources> configured, and no Liquibase changelog found at " + liquibaseChangelog);
    }

    private DataSourceConfig toDataSourceConfig(DataSourceParameter dataSource) {
        if (dataSource.isExternal()) {
            return externalDataSourceConfig(dataSource);
        }
        if (dataSource.isMySQL()) {
            throw new IllegalArgumentException(
                    "kiwiproc-maven-plugin: embedded MySQL is not yet supported (datasource '" + dataSource.getName()
                            + "'). Use an external datasource with jdbcUrl instead.");
        }
        var liquibaseFile = requireLiquibaseChangelog(dataSource);
        if (dataSource.isH2()) {
            var connectionInfo = EmbeddedH2Manager.getInstance().getPreparedDatabase(liquibaseFile);
            return new DataSourceConfig(dataSource.getName(), connectionInfo.url(), null, null, null, "org.h2.Driver");
        }
        var connectionInfo = EmbeddedPostgresManager.getInstance().getPreparedDatabase(liquibaseFile);
        return new DataSourceConfig(
                dataSource.getName(),
                "jdbc:postgresql://localhost:%d/%s?user=%s"
                        .formatted(connectionInfo.getPort(), connectionInfo.getDbName(), connectionInfo.getUser()),
                connectionInfo.getDbName(),
                connectionInfo.getUser(),
                null,
                null);
    }

    private File requireLiquibaseChangelog(DataSourceParameter dataSource) {
        var changelog = dataSource.getLiquibaseChangelog();
        if (changelog == null || !changelog.exists()) {
            throw new IllegalArgumentException("kiwiproc-maven-plugin: datasource '" + dataSource.getName()
                    + "' requires a liquibaseChangelog (embedded databases need a schema). Got: " + changelog);
        }
        return changelog;
    }

    private DataSourceConfig externalDataSourceConfig(DataSourceParameter dataSource) {
        var changelog = dataSource.getLiquibaseChangelog();
        if (changelog != null && changelog.exists()) {
            DataSource ds;
            var url = dataSource.getJdbcUrl();
            if (dataSource.isH2()) {
                var h2Ds = new JdbcDataSource();
                h2Ds.setURL(url);
                if (dataSource.getUsername() != null) {
                    h2Ds.setUser(dataSource.getUsername());
                }
                if (dataSource.getPassword() != null) {
                    h2Ds.setPassword(dataSource.getPassword());
                }
                ds = h2Ds;
            } else {
                var pgDs = new PGSimpleDataSource();
                pgDs.setURL(url);
                if (dataSource.getDatabase() != null) {
                    pgDs.setDatabaseName(dataSource.getDatabase());
                }
                if (dataSource.getUsername() != null) {
                    pgDs.setUser(dataSource.getUsername());
                }
                if (dataSource.getPassword() != null) {
                    pgDs.setPassword(dataSource.getPassword());
                }
                ds = pgDs;
            }
            liquibaseUpdate(changelog, ds);
        }

        return new DataSourceConfig(
                dataSource.getName(),
                dataSource.getJdbcUrl(),
                dataSource.getDatabase(),
                dataSource.getUsername(),
                dataSource.getPassword(),
                dataSource.getDriverClassName());
    }

    private void liquibaseUpdate(File changelog, DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            var liquibaseConnection = new JdbcConnection(connection);
            var liquibase = new Liquibase(
                    changelog.getName(), new DirectoryResourceAccessor(changelog.getParentFile()), liquibaseConnection);
            liquibase.update();
        } catch (SQLException | FileNotFoundException | LiquibaseException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeConfigFile(ProcessorConfig processorConfig) throws MojoExecutionException {
        try {
            Files.createDirectories(configFile.getParentFile().toPath());
            JsonType<ProcessorConfig> jsonType = Jsonb.builder().build().type(ProcessorConfig.class);
            try (var writer = Files.newBufferedWriter(configFile.toPath())) {
                jsonType.toJson(processorConfig, writer);
            }
            getLog().info("kiwiproc: wrote " + configFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + configFile, e);
        }
    }

    private void writeTestProperties(ProcessorConfig processorConfig) throws MojoExecutionException {
        try {
            Files.createDirectories(testResourcesOutputDirectory.toPath());
            var properties = new Properties();
            processorConfig
                    .dataSources()
                    .values()
                    .forEach(dataSourceConfig -> properties.setProperty(
                            propertyKey(processorConfig.dependencyInjectionStyle(), dataSourceConfig.named()),
                            dataSourceConfig.url()));
            var propsFile = testResourcesOutputDirectory.toPath().resolve("application-test.properties");
            try (var writer = Files.newBufferedWriter(propsFile)) {
                properties.store(writer, "Generated by kiwiproc-maven-plugin");
            }
            getLog().info("kiwiproc: wrote " + propsFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write application-test.properties", e);
        }
    }

    private String propertyKey(DependencyInjectionStyle style, String datasourceName) {
        if (style == DependencyInjectionStyle.SPRING) {
            if ("default".equals(datasourceName)) {
                return "spring.datasource.url";
            }
            getLog().warn("kiwiproc: SPRING DI style with non-default datasource '" + datasourceName
                    + "' generates property key 'datasources." + datasourceName
                    + ".url' which Spring Boot does not recognise natively."
                    + " Configure the DataSource bean manually.");
        }
        return "datasources.%s.url".formatted(datasourceName);
    }

    private void registerTestResourcesDirectory() {
        var resource = new Resource();
        resource.setDirectory(testResourcesOutputDirectory.getAbsolutePath());
        project.addTestResource(resource);
    }

    // --- setters used by Plexus configurator and tests ---

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public void setDataSources(List<DataSourceParameter> dataSources) {
        this.dataSources = dataSources;
    }

    public void setLiquibaseChangelog(File liquibaseChangelog) {
        this.liquibaseChangelog = liquibaseChangelog;
    }

    public void setDependencyInjectionStyle(DependencyInjectionStyle dependencyInjectionStyle) {
        this.dependencyInjectionStyle = dependencyInjectionStyle;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    public void setTestResourcesOutputDirectory(File testResourcesOutputDirectory) {
        this.testResourcesOutputDirectory = testResourcesOutputDirectory;
    }
}
