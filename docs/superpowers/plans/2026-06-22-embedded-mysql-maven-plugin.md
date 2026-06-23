# Embedded MySQL support for kiwiproc-maven-plugin Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add embedded MySQL support to `kiwiproc-maven-plugin` (resolves GH#371), and fix the latent bug where an external MySQL datasource with a Liquibase changelog is incorrectly wrapped in a `PGSimpleDataSource`.

**Architecture:** A new `EmbeddedMySQLManager` singleton (mirrors the existing `EmbeddedH2Manager`/`EmbeddedPostgresManager` pattern in `gradle-plugin/maven-plugin`) wraps a lazily-started TestContainers `MySQLContainer`, using the exact algorithm already proven in the Gradle plugin's `EmbeddedMySQLService`. `KiwiProcMojo` is updated to call it instead of throwing, and its external-datasource path gets a MySQL branch.

**Tech Stack:** Java 17, Gradle, JUnit 5 + Google Truth, Liquibase, TestContainers (`org.testcontainers:mysql`), MySQL Connector/J (`com.mysql:mysql-connector-j`).

**Spec:** `docs/superpowers/specs/2026-06-22-embedded-mysql-maven-plugin-design.md`

**Module:** all paths below are relative to `gradle-plugin/maven-plugin/` unless stated otherwise. Build/test commands assume `cd gradle-plugin` first (this module has its own Gradle build, separate from the root project).

---

## Chunk 1: EmbeddedMySQLManager + unit test

### Task 1: Add MySQL dependencies to the maven-plugin build

**Files:**
- Modify: `gradle-plugin/maven-plugin/build.gradle.kts:24-39`

- [ ] **Step 1: Add the dependencies**

In the `dependencies { ... }` block, alongside the existing `implementation(libs.h2)` line, add:

```kotlin
    implementation(libs.mysql)
    implementation(libs.testcontainers.mysql)
```

Both are already declared in `catalog.settings.gradle.kts` (`com.mysql:mysql-connector-j:9.7.0`, `org.testcontainers:mysql:1.21.4`) and already used the same way by `gradle-plugin/plugin/build.gradle.kts` and the root project's `test-mysql/build.gradle.kts` — no version catalog changes needed.

- [ ] **Step 2: Verify the build still configures correctly**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:dependencies --configuration runtimeClasspath`
Expected: command succeeds, output includes `com.mysql:mysql-connector-j` and `org.testcontainers:mysql`.

- [ ] **Step 3: Commit**

```bash
git add gradle-plugin/maven-plugin/build.gradle.kts
git commit -m "Add MySQL/Testcontainers dependencies to kiwiproc-maven-plugin"
```

---

### Task 2: Write the failing test for EmbeddedMySQLManager

**Files:**
- Create: `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/EmbeddedMySQLManagerTest.java`

This mirrors `EmbeddedH2ManagerTest.java` exactly in structure (same changelog fixture, same assertion style), adapted for MySQL's URL shape.

- [ ] **Step 1: Write the test**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmbeddedMySQLManagerTest {
    @Test
    void preparesDatabaseAndAppliesLiquibase() throws IOException {
        var changelog = writeChangelog();

        var info = EmbeddedMySQLManager.getInstance().getPreparedDatabase(changelog.toFile());

        assertThat(info.url()).startsWith("jdbc:mysql://");
        assertThat(info.url()).contains("kiwi_");
        assertThat(info.username()).isNotEmpty();
        assertThat(info.password()).isNotEmpty();
    }

    private Path writeChangelog() throws IOException {
        var dir = Files.createTempDirectory("kiwiproc-maven-test-mysql");
        var changelog = dir.resolve("changelog.xml");
        Files.writeString(changelog, """
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

- [ ] **Step 2: Run it to verify it fails to compile (the class doesn't exist yet)**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:compileTestJava`
Expected: FAIL with "cannot find symbol: class EmbeddedMySQLManager"

- [ ] **Step 3: Commit the test**

```bash
git add gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/EmbeddedMySQLManagerTest.java
git commit -m "Add failing test for EmbeddedMySQLManager"
```

---

### Task 3: Implement EmbeddedMySQLManager

**Files:**
- Create: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/EmbeddedMySQLManager.java`
- Reference: `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/EmbeddedMySQLService.java` (algorithm source)
- Reference: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/EmbeddedH2Manager.java` (singleton shape to match)

- [ ] **Step 1: Write the implementation**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.sql.DriverManager;
import java.sql.SQLException;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.testcontainers.containers.MySQLContainer;

/**
 * Process-wide singleton wrapping an embedded MySQL instance (via Testcontainers), lazily
 * started and reused for the lifetime of the Maven JVM (i.e. for the whole `mvn` invocation).
 */
public final class EmbeddedMySQLManager {
    private static final EmbeddedMySQLManager INSTANCE = new EmbeddedMySQLManager();

    private MySQLContainer<?> container;
    private int dbCounter = 0;

    public record MySQLConnectionInfo(String url, String username, String password) {}

    private EmbeddedMySQLManager() {}

    public static EmbeddedMySQLManager getInstance() {
        return INSTANCE;
    }

    private synchronized MySQLContainer<?> getContainer() {
        if (container == null) {
            container = new MySQLContainer<>("mysql:8.4").withEnv("MYSQL_ROOT_HOST", "%");
            container.start();
        }
        return container;
    }

    // synchronized because Liquibase has issues in multi-threaded use
    public synchronized MySQLConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        var c = getContainer();
        var dbName = "kiwi_" + (++dbCounter);
        var adminUrl = "jdbc:mysql://" + c.getHost() + ":" + c.getMappedPort(3306) + "/"
                + "?allowPublicKeyRetrieval=true&useSSL=false";
        try (var conn = DriverManager.getConnection(adminUrl, "root", c.getPassword());
                var stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE `" + dbName + "`");
            stmt.executeUpdate("GRANT ALL PRIVILEGES ON `" + dbName + "`.* TO '" + c.getUsername() + "'@'%'");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        var url = "jdbc:mysql://" + c.getHost() + ":" + c.getMappedPort(3306) + "/" + dbName
                + "?allowPublicKeyRetrieval=true&useSSL=false&user=" + c.getUsername() + "&password="
                + c.getPassword();

        var ds = new MysqlDataSource();
        ds.setURL(url);
        ds.setUser(c.getUsername());
        ds.setPassword(c.getPassword());

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

        return new MySQLConnectionInfo(url, c.getUsername(), c.getPassword());
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.EmbeddedMySQLManagerTest"`
Expected: PASS. (First run pulls the `mysql:8.4` Docker image — may take a minute. Requires Docker running locally.)

- [ ] **Step 3: Commit**

```bash
git add gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/EmbeddedMySQLManager.java
git commit -m "Implement EmbeddedMySQLManager for kiwiproc-maven-plugin"
```

---

## Chunk 2: Wire embedded MySQL into KiwiProcMojo + fix external MySQL bug

### Task 4: Write the failing KiwiProcMojoTest for embedded MySQL

**Files:**
- Modify: `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/KiwiProcMojoTest.java`

This mirrors the existing `generatesConfigForEmbeddedH2()` test (lines 68-95).

- [ ] **Step 1: Add the test**

Add this test method to `KiwiProcMojoTest`, next to `generatesConfigForEmbeddedH2()`:

```java
    @Test
    void generatesConfigForEmbeddedMySQL() throws IOException, MojoExecutionException {
        var projectDir = Files.createTempDirectory("kiwiproc-maven-mojo-mysql");
        var changelog = projectDir.resolve("src/main/resources/changelog.xml");
        Files.createDirectories(changelog.getParent());
        Files.writeString(changelog, CHANGELOG_XML);

        var dataSource = new DataSourceParameter();
        dataSource.setName("default");
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
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
        assertThat(ds.url()).startsWith("jdbc:mysql://");
        assertThat(ds.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.KiwiProcMojoTest.generatesConfigForEmbeddedMySQL"`
Expected: FAIL with `IllegalArgumentException: kiwiproc-maven-plugin: embedded MySQL is not yet supported...`

- [ ] **Step 3: Commit**

```bash
git add gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/KiwiProcMojoTest.java
git commit -m "Add failing test for embedded MySQL in KiwiProcMojo"
```

---

### Task 5: Implement embedded MySQL in KiwiProcMojo.toDataSourceConfig()

**Files:**
- Modify: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java:99-122`

- [ ] **Step 1: Replace the throw with a real implementation**

Replace lines 103-107:

```java
        if (dataSource.isMySQL()) {
            throw new IllegalArgumentException(
                    "kiwiproc-maven-plugin: embedded MySQL is not yet supported (datasource '" + dataSource.getName()
                            + "'). Use an external datasource with jdbcUrl instead.");
        }
```

with:

```java
        if (dataSource.isMySQL()) {
            var liquibaseFile = requireLiquibaseChangelog(dataSource);
            var connectionInfo = EmbeddedMySQLManager.getInstance().getPreparedDatabase(liquibaseFile);
            return new DataSourceConfig(
                    dataSource.getName(),
                    connectionInfo.url(),
                    null,
                    connectionInfo.username(),
                    connectionInfo.password(),
                    "com.mysql.cj.jdbc.Driver");
        }
```

Note: `requireLiquibaseChangelog(dataSource)` is also called a few lines further down for the H2/Postgres case (line 108) — this duplicates that call for the MySQL branch since each branch returns early. This matches the existing structure (the H2 branch at line 109-112 also resolves `liquibaseFile` once at line 108 and reuses it for both H2 and Postgres) — for MySQL we resolve it inline since it's a separate early return.

- [ ] **Step 2: Run the test to verify it passes**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.KiwiProcMojoTest"`
Expected: PASS (all `KiwiProcMojoTest` methods, including the new one).

- [ ] **Step 3: Commit**

```bash
git add gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java
git commit -m "Wire embedded MySQL support into KiwiProcMojo"
```

---

### Task 6: Write the failing test for external MySQL + changelog

**Files:**
- Modify: `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/KiwiProcMojoTest.java`

This is a new scenario not covered by the existing `passesThroughExternalDataSourceUnchanged()` test (which sets no changelog). It uses `EmbeddedMySQLManager`'s container as a real MySQL target for the external-datasource path, since there's no other MySQL server available in tests — this is acceptable because the test is verifying the Mojo's *external-datasource Liquibase wiring*, not the embedded-database lifecycle itself.

- [ ] **Step 1: Add the test**

```java
    @Test
    void appliesLiquibaseToExternalMySQLDataSource() throws IOException, MojoExecutionException {
        // Use the embedded MySQL container as a stand-in "external" server: KiwiProcMojo's
        // externalDataSourceConfig() should build a working MysqlDataSource for Liquibase here,
        // not the PGSimpleDataSource it incorrectly builds today for any non-H2 driver.
        var setupChangelog = writeEmptyChangelog(); // starts the container, returns a schema-less kiwi_N database
        var connectionInfo = EmbeddedMySQLManager.getInstance().getPreparedDatabase(setupChangelog);

        var projectDir = Files.createTempDirectory("kiwiproc-maven-mojo-external-mysql");
        var changelog = projectDir.resolve("src/main/resources/changelog.xml");
        Files.createDirectories(changelog.getParent());
        Files.writeString(changelog, CHANGELOG_XML);

        var dataSource = new DataSourceParameter();
        dataSource.setName("default");
        dataSource.setJdbcUrl(connectionInfo.url());
        dataSource.setUsername(connectionInfo.username());
        dataSource.setPassword(connectionInfo.password());
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setLiquibaseChangelog(changelog.toFile());

        var mojo = new KiwiProcMojo();
        mojo.setLog(new SystemStreamLog());
        mojo.setProject(testProject(projectDir));
        mojo.setDataSources(List.of(dataSource));
        mojo.setConfigFile(projectDir.resolve("target/kiwiproc/config.json").toFile());
        mojo.setTestResourcesOutputDirectory(
                projectDir.resolve("target/generated-test-resources/kiwiproc").toFile());

        mojo.execute(); // must not throw — proves externalDataSourceConfig() used a working MySQL DataSource

        var configJson = Files.readString(projectDir.resolve("target/kiwiproc/config.json"));
        var config = Jsonb.builder().build().type(ProcessorConfig.class).fromJson(configJson);
        var ds = config.dataSources().get("default");
        assertThat(ds.url()).isEqualTo(connectionInfo.url());
        assertThat(ds.username()).isEqualTo(connectionInfo.username());
    }

    private File writeEmptyChangelog() throws IOException {
        var dir = Files.createTempDirectory("kiwiproc-maven-mojo-external-mysql-setup");
        var changelog = dir.resolve("changelog.xml");
        Files.writeString(changelog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">
                </databaseChangeLog>
                """);
        return changelog.toFile();
    }
```

The setup changelog is empty purely so `getPreparedDatabase` returns a schema-less database for the Mojo's own Liquibase pass (using `CHANGELOG_XML`) to act on first — it is not working around a checksum/collision failure (Liquibase tracks applied changesets by filename+id+author+checksum and would simply skip a second identical run, not error).

- [ ] **Step 2: Run it to verify it fails**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.KiwiProcMojoTest.appliesLiquibaseToExternalMySQLDataSource"`
Expected: FAIL — `externalDataSourceConfig()` currently builds a `PGSimpleDataSource` for a `jdbc:mysql://` URL, which throws an `SQLException` (wrapped in `RuntimeException` from `liquibaseUpdate()`) since the PostgreSQL driver/datasource rejects a non-`jdbc:postgresql:` URL.

- [ ] **Step 3: Commit**

```bash
git add gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/KiwiProcMojoTest.java
git commit -m "Add failing test for external MySQL datasource with Liquibase changelog"
```

---

### Task 7: Fix externalDataSourceConfig() to handle MySQL

**Files:**
- Modify: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java:133-172`
- Add import: `com.mysql.cj.jdbc.MysqlDataSource`

- [ ] **Step 1: Add the import**

Add to the import block (alongside the existing `org.h2.jdbcx.JdbcDataSource` / `org.postgresql.ds.PGSimpleDataSource` imports):

```java
import com.mysql.cj.jdbc.MysqlDataSource;
```

- [ ] **Step 2: Add the MySQL branch**

In `externalDataSourceConfig()`, change the `if (dataSource.isH2()) { ... } else { ... }` two-way branch into a three-way branch. Replace:

```java
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
```

with:

```java
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
            } else if (dataSource.isMySQL()) {
                var mysqlDs = new MysqlDataSource();
                mysqlDs.setURL(url);
                if (dataSource.getUsername() != null) {
                    mysqlDs.setUser(dataSource.getUsername());
                }
                if (dataSource.getPassword() != null) {
                    mysqlDs.setPassword(dataSource.getPassword());
                }
                ds = mysqlDs;
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
```

- [ ] **Step 3: Run the test to verify it passes**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.KiwiProcMojoTest"`
Expected: PASS (all tests, including `appliesLiquibaseToExternalMySQLDataSource`).

- [ ] **Step 4: Run the full unit test suite for the module**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:test`
Expected: PASS, no regressions.

- [ ] **Step 5: Commit**

```bash
git add gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java
git commit -m "Fix externalDataSourceConfig() to use MysqlDataSource for external MySQL datasources"
```

---

## Chunk 3: Functional (maven-invoker) test

### Task 8: Add the embedded-mysql functional test fixture

**Files:**
- Create: `gradle-plugin/maven-plugin/src/functionalTest/resources/it/embedded-mysql/pom.xml`
- Create: `gradle-plugin/maven-plugin/src/functionalTest/resources/it/embedded-mysql/src/main/resources/changelog.xml`

This mirrors `src/functionalTest/resources/it/default-embedded-postgres/` exactly, with a `<dataSources>` block selecting MySQL (since MySQL needs `driverClassName` set, unlike the Postgres default).

- [ ] **Step 1: Create the changelog**

```xml
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
```

- [ ] **Step 2: Create the pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  Fixture project for KiwiProcMojoInvokerTest. kiwiproc.version and kiwiproc.it.repo.url
  are supplied by the invoking test via -D properties; this pom is not meant to be run
  standalone.
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.ethelred.kiwiproc.it</groupId>
    <artifactId>embedded-mysql</artifactId>
    <version>1.0</version>
    <packaging>jar</packaging>

    <repositories>
        <repository>
            <id>kiwiproc-it-local</id>
            <url>${kiwiproc.it.repo.url}</url>
            <releases><enabled>false</enabled></releases>
            <snapshots><enabled>true</enabled></snapshots>
        </repository>
    </repositories>
    <pluginRepositories>
        <pluginRepository>
            <id>kiwiproc-it-local</id>
            <url>${kiwiproc.it.repo.url}</url>
            <releases><enabled>false</enabled></releases>
            <snapshots><enabled>true</enabled></snapshots>
        </pluginRepository>
    </pluginRepositories>

    <build>
        <plugins>
            <plugin>
                <groupId>org.ethelred.kiwiproc</groupId>
                <artifactId>kiwiproc-maven-plugin</artifactId>
                <version>${kiwiproc.version}</version>
                <executions>
                    <execution>
                        <id>kiwiproc-generate</id>
                        <goals>
                            <goal>generate</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <dataSources>
                        <dataSource>
                            <name>default</name>
                            <driverClassName>com.mysql.cj.jdbc.Driver</driverClassName>
                            <liquibaseChangelog>${project.basedir}/src/main/resources/changelog.xml</liquibaseChangelog>
                        </dataSource>
                    </dataSources>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Commit**

```bash
git add gradle-plugin/maven-plugin/src/functionalTest/resources/it/embedded-mysql/
git commit -m "Add embedded-mysql functional test fixture"
```

---

### Task 9: Add the functional test method

**Files:**
- Modify: `gradle-plugin/maven-plugin/src/functionalTest/java/org/ethelred/kiwiproc/maven/it/KiwiProcMojoInvokerTest.java`

- [ ] **Step 1: Add the test method**

Add next to `defaultEmbeddedPostgresGeneratesConfigAtGenerateSourcesPhase()`:

```java
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
```

- [ ] **Step 2: Run the functional test**

Run: `cd gradle-plugin && ./gradlew :maven-plugin:functionalTest --tests "org.ethelred.kiwiproc.maven.it.KiwiProcMojoInvokerTest"`
Expected: PASS for all three scenarios (`default-embedded-postgres`, `multiple-datasources`, `embedded-mysql`). This requires Docker (for the embedded MySQL container, started inside the *invoked* `mvn` subprocess) and a working `mvn` on `PATH`.

- [ ] **Step 3: Commit**

```bash
git add gradle-plugin/maven-plugin/src/functionalTest/java/org/ethelred/kiwiproc/maven/it/KiwiProcMojoInvokerTest.java
git commit -m "Add functional test for embedded MySQL via maven-invoker"
```

---

## Chunk 4: Documentation

### Task 10: Update maven_plugin.adoc

**Files:**
- Modify: `docs/src/docs/asciidoc/maven_plugin.adoc:128-129` (property table)
- Modify: `docs/src/docs/asciidoc/maven_plugin.adoc:194-198` (replace the NOTE)

- [ ] **Step 1: Update the driverClassName table row**

Find (around line 128-129):

```adoc
|`driverClassName`
|JDBC driver class name. Set to `org.h2.Driver` to select embedded H2 instead of PostgreSQL (for embedded datasources), or to describe the driver of an external datasource.
```

Replace with:

```adoc
|`driverClassName`
|JDBC driver class name. Set to `org.h2.Driver` or `com.mysql.cj.jdbc.Driver` to select embedded H2 or MySQL instead of PostgreSQL (for embedded datasources), or to describe the driver of an external datasource.
```

- [ ] **Step 2: Replace the "not yet supported" NOTE with an Embedded MySQL section**

Find (lines 194-198):

```adoc
NOTE: Unlike the Gradle plugin, `kiwiproc-maven-plugin` does not yet support embedded MySQL
(setting `driverClassName` to the MySQL driver on an embedded datasource throws an error).
This is tracked in https://github.com/edward3h/kiwiproc/issues/333[issue #333]. If you need
MySQL at build time, configure an external datasource with `jdbcUrl` pointing at a database
you manage yourself (for example, started separately via Testcontainers or Docker Compose).
```

Replace with (mirroring the "Embedded H2" section above it at lines 132-151, and `gradle_plugin.adoc:100-132`):

```adoc
=== Embedded MySQL

To use embedded MySQL instead of PostgreSQL, set `driverClassName` to the MySQL driver class name.
This requires Docker to be available on the build machine (Testcontainers is used internally).

[source,xml,indent=0,subs="verbatim,attributes"]
----
<configuration>
    <dataSources>
        <dataSource>
            <name>default</name>
            <driverClassName>com.mysql.cj.jdbc.Driver</driverClassName> // <1>
            <liquibaseChangelog>${project.basedir}/src/main/resources/changelog.xml</liquibaseChangelog>
        </dataSource>
    </dataSources>
</configuration>
----
<1> Setting `driverClassName` to the MySQL driver selects embedded MySQL via Testcontainers/Docker.

See xref:databases.adoc#mysql-limitations[MySQL Limitations] for a list of features not available with MySQL.
```

- [ ] **Step 3: Build the docs to verify no AsciiDoc errors**

Run: `./gradlew :docs:asciidoctor`
Expected: BUILD SUCCESSFUL, no new "invalid reference" warnings for `mysql-limitations` (it already exists in `databases.adoc`, confirmed during spec review).

- [ ] **Step 4: Commit**

```bash
git add docs/src/docs/asciidoc/maven_plugin.adoc
git commit -m "Document embedded MySQL support in kiwiproc-maven-plugin"
```

---

## Chunk 5: Final verification

### Task 11: Full module build and root build sanity check

- [ ] **Step 1: Full build of the gradle-plugin project**

Run: `cd gradle-plugin && ./gradlew build`
Expected: BUILD SUCCESSFUL — runs `:maven-plugin:test`, `:maven-plugin:functionalTest`, checkstyle, spotlessCheck for all modules under `gradle-plugin/`.

- [ ] **Step 2: Spotless check (formatting)**

If Step 1 reports Spotless violations, run: `cd gradle-plugin && ./gradlew spotlessApply`, review the diff, then re-run `./gradlew build`.

- [ ] **Step 3: Root project sanity build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — confirms the docs change didn't break `:docs:asciidoctor` and nothing else in the root project regressed (the root project does not depend on `gradle-plugin/maven-plugin`, but this is a cheap final check per repo convention).

- [ ] **Step 4: Manual review of the full diff**

Run: `git diff main --stat` and `git diff main` to review everything before requesting code review (per `superpowers:requesting-code-review`).

---

## Out of scope (confirmed in spec)

- Embedded MySQL version/image configurability (hardcoded `mysql:8.4`).
- Changes to `EmbeddedMySQLService` (Gradle plugin) — reference implementation only, not modified.
