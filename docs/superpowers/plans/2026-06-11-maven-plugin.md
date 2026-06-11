# Maven Plugin (GH#333) Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a Maven plugin equivalent to the existing Gradle plugin (`org.ethelred.kiwiproc`), so Maven users get embedded-database + Liquibase + processor-config generation with a `<plugin>` declaration instead of hand-writing `kiwiproc-config.json`.

**Architecture:** Add a new `maven-plugin` subproject inside the existing `gradle-plugin` composite build (sibling of `:plugin`), built using the `org.gradlex.maven-plugin-development` Gradle plugin (so it stays in the normal Gradle build, no separate `mvn` toolchain needed). It depends on `:plugin` to reuse `ProcessorConfig`/`DataSourceConfig`/`DependencyInjectionStyle` (and their generated Avaje JSON adapters). A single `KiwiProcMojo` (`@Mojo(name = "generate", defaultPhase = GENERATE_SOURCES)`) manages embedded PostgreSQL (zonky) and embedded H2 (TCP server) singletons, runs Liquibase, writes `target/kiwiproc/config.json` + `target/generated-test-resources/kiwiproc/application-test.properties`, and registers the latter directory as a test resource on the `MavenProject`.

**Scope (MVP, per user decision):** PostgreSQL (default, embedded) + H2 (embedded, via `driverClassName=org.h2.Driver`) + external databases (any JDBC URL, with optional Liquibase). **No embedded MySQL/Testcontainers** in this iteration — that can be a follow-up mirroring the Gradle plugin's `EmbeddedMySQLService`.

**Tech Stack:** Java 17, Gradle (`org.gradlex.maven-plugin-development:1.0.3`), `maven-plugin-api`/`maven-plugin-annotations` (3.9.x), Avaje JSON (`io.avaje:avaje-jsonb`), zonky `embedded-postgres`, H2, Liquibase core, JUnit 5 + Truth.

---

## Context

GH#333 asks for a Maven equivalent of the Gradle plugin. The Gradle plugin (`gradle-plugin/plugin`):
- Manages embedded Postgres/H2/MySQL databases for the build, applies Liquibase migrations.
- Generates `build/processorConfig/config.json` (consumed by the `processor` module via `-Aorg.ethelred.kiwiproc.configuration=...`).
- Generates `build/processorConfig/application-test.properties` (datasource URL for tests), wired into test resources.
- Already publishes a small shared library of POJOs (`org.ethelred.kiwiproc.processorconfig.{ProcessorConfig,DataSourceConfig,DependencyInjectionStyle}`) as part of the `:plugin` artifact — these are reused as-is.

`docs/src/docs/asciidoc/maven_setup.adoc` currently documents the fully-manual approach (hand-write `kiwiproc-config.json`, point at an external DB). That page stays as the "no plugin / external DB" path; the new `maven_plugin.adoc` documents the plugin-based path, mirroring `gradle_plugin.adoc`.

---

## Chunk 1: Module skeleton + dependency wiring

**Files:**
- Modify: `gradle-plugin/settings.gradle.kts`
- Create: `gradle-plugin/maven-plugin/build.gradle.kts`
- Create: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/package-info.java`

- [ ] **Step 1: Register the new subproject**

Edit `gradle-plugin/settings.gradle.kts`:

```kotlin
apply(from = "../catalog.settings.gradle.kts")

rootProject.name = "gradle-plugin"
include("plugin")
include("maven-plugin")
```

- [ ] **Step 2: Create `gradle-plugin/maven-plugin/build.gradle.kts`**

```kotlin
plugins {
    `java-library`
    jacoco
    checkstyle
    id("com.diffplug.spotless").version("8.6.0")
    id("org.gradlex.maven-plugin-development") version "1.0.3"
}

apply(from = "../../version.gradle.kts")
group = "org.ethelred.kiwiproc"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":plugin"))
    implementation(libs.jspecify)

    implementation("org.apache.maven:maven-plugin-api:3.9.9")
    implementation("org.apache.maven.plugin-tools:maven-plugin-annotations:3.13.1")
    implementation("org.apache.maven:maven-core:3.9.9")

    implementation(libs.avaje.json.asProvider())
    implementation(libs.embeddedpostgres)
    implementation(libs.liquibase.core)
    implementation(libs.postgresql)
    implementation(libs.h2)
    runtimeOnly(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:18.4.0"))
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-arm64v8")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testImplementation("com.google.truth:truth:1.4.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPlugin {
    // groupId/artifactId/description picked up from project coordinates;
    // artifactId defaults to the Gradle project name "maven-plugin" - rename if needed.
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

checkstyle {
    configFile = file("../../config/checkstyle/checkstyle.xml")
}

configurations.named("checkstyle") {
    resolutionStrategy.force(
        "commons-beanutils:commons-beanutils:1.11.0",
        "org.codehaus.plexus:plexus-utils:4.0.3"
    )
}

spotless {
    java {
        cleanthat()
        importOrder()
        removeUnusedImports()
        palantirJavaFormat()
        formatAnnotations()
        licenseHeader("/* (C) Edward Harman \$YEAR */")
    }
}
```

Note: `../../config/checkstyle/checkstyle.xml` and `../../version.gradle.kts` paths mirror `gradle-plugin/plugin/build.gradle.kts` (one level deeper than that file, so one extra `../`). Verify the relative path resolves — `gradle-plugin/plugin/build.gradle.kts` uses `../../config/checkstyle/checkstyle.xml` from `gradle-plugin/plugin/`, and `gradle-plugin/maven-plugin/` is a sibling at the same depth, so the same `../../` is correct.

- [ ] **Step 3: Add a placeholder package so the build resolves**

Create `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/package-info.java`:

```java
/* (C) Edward Harman 2026 */
/** Maven plugin equivalent of the Gradle kiwiproc plugin. */
package org.ethelred.kiwiproc.maven;
```

- [ ] **Step 4: Verify the new module builds**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:build`
Expected: BUILD SUCCESSFUL (empty plugin.xml with zero mojos generated is fine at this stage).

- [ ] **Step 5: Commit**

```bash
git add gradle-plugin/settings.gradle.kts gradle-plugin/maven-plugin
git commit -m "feat: scaffold maven-plugin module"
```

---

## Chunk 2: Embedded database managers + DataSourceParameter

**Files:**
- Create: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/EmbeddedPostgresManager.java`
- Create: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/EmbeddedH2Manager.java`
- Create: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/DataSourceParameter.java`
- Test: `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/EmbeddedPostgresManagerTest.java`
- Test: `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/EmbeddedH2ManagerTest.java`

These mirror `EmbeddedPostgresService`/`EmbeddedH2Service` from `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/`, but as plain static-singleton classes (no Gradle `BuildService`) since a Maven build runs in a single JVM for the whole reactor — a static singleton has equivalent lifetime semantics.

- [ ] **Step 1: Write failing test for `EmbeddedPostgresManager`**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmbeddedPostgresManagerTest {
    @Test
    void preparesDatabaseAndAppliesLiquibase() throws IOException {
        var changelog = writeChangelog();

        var info = EmbeddedPostgresManager.getInstance().getPreparedDatabase(changelog.toFile());

        assertThat(info.getPort()).isGreaterThan(0);
        assertThat(info.getDbName()).isNotEmpty();
        assertThat(info.getUser()).isNotEmpty();
    }

    private Path writeChangelog() throws IOException {
        var dir = Files.createTempDirectory("kiwiproc-maven-test");
        var changelog = dir.resolve("changelog.xml");
        Files.writeString(
                changelog,
                """
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
                """);
        return changelog;
    }
}
```

- [ ] **Step 2: Run test to verify it fails (class doesn't exist)**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "*EmbeddedPostgresManagerTest*"`
Expected: compile error, `EmbeddedPostgresManager` not found.

- [ ] **Step 3: Implement `EmbeddedPostgresManager`**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import io.zonky.test.db.postgres.embedded.ConnectionInfo;
import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import io.zonky.test.db.postgres.embedded.PreparedDbProvider;
import java.io.File;
import java.sql.SQLException;

/**
 * Process-wide singleton wrapping an embedded PostgreSQL instance, lazily started and reused for
 * the lifetime of the Maven JVM (i.e. for the whole `mvn` invocation).
 */
public final class EmbeddedPostgresManager {
    private static final EmbeddedPostgresManager INSTANCE = new EmbeddedPostgresManager();

    private PreparedDbProvider dbProvider;

    private EmbeddedPostgresManager() {}

    public static EmbeddedPostgresManager getInstance() {
        return INSTANCE;
    }

    private synchronized PreparedDbProvider getDbProvider() {
        if (dbProvider == null) {
            dbProvider = PreparedDbProvider.forPreparer(ignore -> {});
        }
        return dbProvider;
    }

    // synchronized because Liquibase has issues running concurrently
    public synchronized ConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        try {
            var connectionInfo = getDbProvider().createNewDatabase();
            var dataSource = getDbProvider().createDataSourceFromConnectionInfo(connectionInfo);
            LiquibasePreparer.forFile(liquibaseChangelog).prepare(dataSource);
            return connectionInfo;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "*EmbeddedPostgresManagerTest*"`
Expected: PASS (downloads embedded-postgres binaries on first run; may take a minute).

- [ ] **Step 5: Write failing test for `EmbeddedH2Manager`**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmbeddedH2ManagerTest {
    @Test
    void preparesDatabaseAndAppliesLiquibase() throws IOException {
        var changelog = writeChangelog();

        var info = EmbeddedH2Manager.getInstance().getPreparedDatabase(changelog.toFile());

        assertThat(info.url()).startsWith("jdbc:h2:tcp://localhost:");
        assertThat(info.url()).contains("MODE=PostgreSQL");
    }

    private Path writeChangelog() throws IOException {
        var dir = Files.createTempDirectory("kiwiproc-maven-test-h2");
        var changelog = dir.resolve("changelog.xml");
        Files.writeString(
                changelog,
                """
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
                """);
        return changelog;
    }
}
```

- [ ] **Step 6: Implement `EmbeddedH2Manager`**

Direct port of `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/EmbeddedH2Service.java`, dropping the `BuildService`/`AutoCloseable` Gradle wiring in favour of a static singleton (the TCP server and keep-alive connections live for the JVM's lifetime, which is fine for a single `mvn` run):

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;

public final class EmbeddedH2Manager {
    private static final EmbeddedH2Manager INSTANCE = new EmbeddedH2Manager();

    private Server tcpServer;
    private int dbCounter = 0;
    private final List<Connection> keepAliveConnections = new ArrayList<>();

    public record H2ConnectionInfo(String url) {}

    private EmbeddedH2Manager() {}

    public static EmbeddedH2Manager getInstance() {
        return INSTANCE;
    }

    private synchronized Server getServer() {
        if (tcpServer == null) {
            try {
                tcpServer =
                        Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return tcpServer;
    }

    public synchronized H2ConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        var server = getServer();
        var dbName = "kiwi_" + (++dbCounter);
        var url = "jdbc:h2:tcp://localhost:" + server.getPort() + "/mem:" + dbName + ";MODE=PostgreSQL";

        var ds = new JdbcDataSource();
        ds.setURL(url);

        try {
            keepAliveConnections.add(ds.getConnection());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        try (var connection = ds.getConnection()) {
            var liquibaseConnection = new JdbcConnection(connection);
            var liquibase = new Liquibase(
                    liquibaseChangelog.getName(),
                    new DirectoryResourceAccessor(liquibaseChangelog.getParentFile()),
                    liquibaseConnection);
            liquibase.update();
        } catch (SQLException | FileNotFoundException | LiquibaseException e) {
            throw new RuntimeException(e);
        }

        return new H2ConnectionInfo(url);
    }
}
```

- [ ] **Step 7: Run H2 test, verify it passes**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "*EmbeddedH2ManagerTest*"`
Expected: PASS

- [ ] **Step 8: Create `DataSourceParameter` (Mojo nested config POJO)**

This is the Maven-config equivalent of `KiwiProcDataSource`. Plain mutable POJO with a no-arg constructor and getters/setters so Maven's Plexus configurator can populate it from `<dataSource>` XML elements.

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import java.io.File;
import org.jspecify.annotations.Nullable;

/** One {@code <dataSource>} entry inside the plugin's {@code <dataSources>} configuration. */
public class DataSourceParameter {
    private String name = "default";

    @Nullable
    private File liquibaseChangelog;

    @Nullable
    private String jdbcUrl;

    @Nullable
    private String database;

    @Nullable
    private String username;

    @Nullable
    private String password;

    @Nullable
    private String driverClassName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public @Nullable File getLiquibaseChangelog() {
        return liquibaseChangelog;
    }

    public void setLiquibaseChangelog(@Nullable File liquibaseChangelog) {
        this.liquibaseChangelog = liquibaseChangelog;
    }

    public @Nullable String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(@Nullable String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public @Nullable String getDatabase() {
        return database;
    }

    public void setDatabase(@Nullable String database) {
        this.database = database;
    }

    public @Nullable String getUsername() {
        return username;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    public @Nullable String getPassword() {
        return password;
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public @Nullable String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(@Nullable String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public boolean isExternal() {
        return jdbcUrl != null;
    }

    public boolean isMySQL() {
        return "com.mysql.cj.jdbc.Driver".equals(driverClassName);
    }

    public boolean isH2() {
        return "org.h2.Driver".equals(driverClassName) || (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:"));
    }
}
```

(`isMySQL` is included only so that, if a user points `driverClassName`/`jdbcUrl` at MySQL, `KiwiProcMojo` can fail fast with a clear "not yet supported" error rather than silently mis-detecting it as Postgres — see Chunk 3 Step 3.)

- [ ] **Step 9: Run full module test suite**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test`
Expected: PASS (2 tests)

- [ ] **Step 10: Commit**

```bash
git add gradle-plugin/maven-plugin/src
git commit -m "feat: add embedded postgres/h2 managers and datasource parameter for maven plugin"
```

---

## Chunk 3: `KiwiProcMojo` — config.json + application-test.properties

**Files:**
- Create: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java`
- Test: `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/KiwiProcMojoTest.java`

The Mojo is bound to `generate-sources` (runs before `compile`, so the annotation processor can read `config.json`). It writes the test-properties file under `target/generated-test-resources/kiwiproc/` and registers that directory as a test resource via `MavenProject.addTestResource(...)` — this happens early enough (during `generate-sources`) that it is still picked up by `process-test-resources` later in the same reactor build.

- [ ] **Step 1: Write failing test — single embedded Postgres datasource**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import io.avaje.jsonb.Jsonb;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfig;
import org.junit.jupiter.api.Test;

class KiwiProcMojoTest {

    @Test
    void generatesConfigForDefaultEmbeddedPostgres() throws IOException, MojoExecutionException {
        var projectDir = Files.createTempDirectory("kiwiproc-maven-mojo");
        var changelog = projectDir.resolve("src/main/resources/changelog.xml");
        Files.createDirectories(changelog.getParent());
        Files.writeString(
                changelog,
                """
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
                """);

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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "*KiwiProcMojoTest*"`
Expected: compile error, `KiwiProcMojo` not found.

- [ ] **Step 3: Implement `KiwiProcMojo`**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
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
        var effectiveDataSources = effectiveDataSources();

        var configsByName = effectiveDataSources.stream()
                .map(this::toDataSourceConfig)
                .collect(Collectors.toMap(DataSourceConfig::named, x -> x, (a, b) -> a, java.util.LinkedHashMap::new));

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
                "kiwiproc: no <dataSources> configured, and no Liquibase changelog found at "
                        + liquibaseChangelog);
    }

    private DataSourceConfig toDataSourceConfig(DataSourceParameter dataSource) {
        if (dataSource.isExternal()) {
            return externalDataSourceConfig(dataSource);
        }
        if (dataSource.isMySQL()) {
            throw new IllegalArgumentException(
                    "kiwiproc-maven-plugin: embedded MySQL is not yet supported (datasource '"
                            + dataSource.getName() + "'). Use an external datasource with jdbcUrl instead.");
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
            throw new IllegalArgumentException(
                    "kiwiproc-maven-plugin: datasource '" + dataSource.getName()
                            + "' requires a liquibaseChangelog (embedded databases need a schema). Got: "
                            + changelog);
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
            processorConfig.dataSources().values().forEach(dataSourceConfig -> properties.setProperty(
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
```

Note: `dependencyInjectionStyle` has a `@Parameter(defaultValue = "JAKARTA")` but the field type is an enum and no-arg construction won't set it — Maven's Plexus configurator handles `defaultValue` for enums via `String` conversion fine, but in **unit tests** (which bypass Plexus) the field will be `null` unless set explicitly. Handle this in `execute()` defensively:

- [ ] **Step 3a: Add a null-safe default for `dependencyInjectionStyle` and `debug` is already a primitive (defaults to `false`)**

At the top of `execute()`, add:

```java
if (dependencyInjectionStyle == null) {
    dependencyInjectionStyle = DependencyInjectionStyle.JAKARTA;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "*KiwiProcMojoTest*"`
Expected: PASS

- [ ] **Step 5: Add a second test — embedded H2 datasource**

Add to `KiwiProcMojoTest`:

```java
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
```

Extract the changelog XML literal used by both tests into a `private static final String CHANGELOG_XML = """ ... """;` constant on the test class to avoid duplication.

- [ ] **Step 6: Run all mojo tests, verify pass**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "*KiwiProcMojoTest*"`
Expected: PASS (2 tests)

- [ ] **Step 7: Run full module suite**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:build`
Expected: BUILD SUCCESSFUL — `plugin.xml` should now list the `generate` goal. Inspect with:
`unzip -p gradle-plugin/maven-plugin/build/libs/maven-plugin-*.jar META-INF/maven/plugin.xml`

- [ ] **Step 8: Commit**

```bash
git add gradle-plugin/maven-plugin/src
git commit -m "feat: implement kiwiproc-maven-plugin generate mojo"
```

---

## Chunk 4: External-datasource test + edge cases

**Files:**
- Test: `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/KiwiProcMojoTest.java`

- [ ] **Step 1: Write failing test — external datasource without Liquibase**

```java
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
```

This exercises `externalDataSourceConfig` with `changelog == null` (no Liquibase run) — confirms the "no DB available at test time" path doesn't blow up and just passes the URL through.

- [ ] **Step 2: Run test, verify it passes (no new prod code expected)**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "*KiwiProcMojoTest*"`
Expected: PASS (3 tests). If it fails, fix `externalDataSourceConfig`/`toDataSourceConfig` rather than adding new code paths.

- [ ] **Step 3: Write failing test — registerTestResourcesDirectory adds the resource to the project**

```java
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
```

- [ ] **Step 4: Run test, verify it passes**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "*KiwiProcMojoTest*"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add gradle-plugin/maven-plugin/src
git commit -m "test: cover external datasource and test-resource registration in maven plugin"
```

---

## Chunk 5: Documentation

**Files:**
- Create: `docs/src/docs/asciidoc/maven_plugin.adoc`
- Modify: `docs/src/docs/asciidoc/index.adoc`
- Modify: `docs/src/docs/asciidoc/maven_setup.adoc`

- [ ] **Step 1: Create `docs/src/docs/asciidoc/maven_plugin.adoc`**

Mirror the structure of `gradle_plugin.adoc`, adapted to Maven `<configuration>` XML. Cover:

1. Adding the plugin and binding `generate` to `generate-sources` (it's the default phase, so an empty `<executions>` with just the goal is enough).
2. Default behaviour: single "default" datasource, embedded PostgreSQL, changelog at `src/main/resources/changelog.xml`, `JAKARTA` DI style.
3. All configuration properties (`dependencyInjectionStyle`, `debug`, `liquibaseChangelog`, `dataSources`).
4. Embedded H2 via `driverClassName=org.h2.Driver` (link to `databases.adoc#h2-limitations`).
5. Multiple / external datasources (Postgres + H2 external; explicitly note MySQL embedded is not yet supported, link to GH#333 or a follow-up issue if one exists).
6. How `config.json` feeds the `maven-compiler-plugin` (`-Aorg.ethelred.kiwiproc.configuration=${project.build.directory}/kiwiproc/config.json` — note this path is now **generated automatically**, contrasting with the manual `kiwiproc-config.json` in `maven_setup.adoc`).
7. The `application-test.properties` test-resources file (same property-key rules as Gradle: `datasources.<name>.url` / `spring.datasource.url`).

Example top-level snippet:

```xml
<plugin>
    <groupId>org.ethelred.kiwiproc</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>{revnumber}</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

(Confirm the published `artifactId` once Chunk 1's `mavenPlugin {}` block / `publishing-convention` is finalised — it may default to `maven-plugin` from the Gradle project name; consider setting an explicit `archivesName`/coordinates if a more descriptive id like `kiwiproc-maven-plugin` is preferred. Update this doc to match whatever is actually published.)

- [ ] **Step 2: Link the new page from `index.adoc`**

```diff
 include::gradle_plugin.adoc[]

+include::maven_plugin.adoc[]
+
 include::maven_setup.adoc[]
```

- [ ] **Step 3: Add a pointer from `maven_setup.adoc` to the new plugin**

At the top of `maven_setup.adoc`, after the existing intro paragraph, add a note:

```asciidoc
TIP: If you'd rather not manage an external database and `kiwiproc-config.json` by hand, see
xref:#_maven_plugin[Maven Plugin] for a Maven plugin that mirrors the Gradle plugin's embedded-database
support (PostgreSQL and H2).
```

- [ ] **Step 4: Build docs to verify rendering**

Run: `./gradlew :docs:asciidoctor` (check actual task name in `docs/build.gradle.kts` if this fails)
Expected: BUILD SUCCESSFUL, no broken xref warnings.

- [ ] **Step 5: Commit**

```bash
git add docs/src/docs/asciidoc
git commit -m "docs: document the kiwiproc maven plugin"
```

---

## Chunk 6: CI

**Files:**
- Modify: `.github/workflows/gradle.yml`

The new `maven-plugin` module lives in the `gradle-plugin` composite build, which is **not** currently exercised by the root `./gradlew build` (only its `:plugin` artifact is consumed via `includeBuild` substitution). Add an explicit step so its tests run in CI.

- [ ] **Step 1: Add a build step for the `gradle-plugin` composite build**

In `.github/workflows/gradle.yml`, in the `build` job, after "Build with Gradle Wrapper" and before "Generate coverage report", add:

```yaml
    - name: Build gradle-plugin (incl. maven-plugin)
      run: cd gradle-plugin && ./gradlew build
```

- [ ] **Step 2: Verify locally**

Run: `cd gradle-plugin && ./gradlew build`
Expected: BUILD SUCCESSFUL across `:plugin` and `:maven-plugin`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/gradle.yml
git commit -m "ci: build gradle-plugin composite build (incl. maven-plugin) in CI"
```

---

## Out of scope / follow-ups

- Embedded MySQL support for the Maven plugin (mirror `EmbeddedMySQLService` via Testcontainers) — `KiwiProcMojo` currently throws `IllegalArgumentException` for `driverClassName=com.mysql.cj.jdbc.Driver`.
- Maven Central publishing of the `maven-plugin` artifact (reuse `publishing-convention` patterns from `gradle-plugin/plugin/build.gradle.kts`'s `mavenPublishing {}` block — left out of this plan to keep scope to "make it work and be testable"; do as a quick follow-up once this lands).
- An end-to-end "happy path" integration test analogous to `gradle-plugin/plugin/src/functionalTest` (e.g. using `maven-invoker-plugin` against a real `mvn` install) — the unit tests in Chunks 2-4 cover the generation logic; a full `mvn compile` smoke test would need a real Maven installation in CI.
