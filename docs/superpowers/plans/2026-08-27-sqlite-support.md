# SQLite Support Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support SQLite as a fully supported kiwiproc database — build-time query validation/introspection, generated DAO code, embedded-database handling in both the Gradle and Maven plugins, an integration test module, and documentation — on par with the existing PostgreSQL/H2/MySQL support.

**Architecture:** Add a `SqliteDialect` (querymeta) implementing the existing `DatabaseDialect` strategy interface; add `EmbeddedSQLiteService`/`EmbeddedSQLiteManager` (Gradle/Maven) that create a fresh temp SQLite file per requested datasource and run Liquibase against it directly (no server process needed — SQLite is file-based); wire a `SQLITE` case into the `DatabaseKind` enum and every switch that already handles `POSTGRES`/`MYSQL`/`H2`; add a `test-sqlite` integration-test module; document it in `databases.adoc`.

**Tech Stack:** Java 17, Gradle (composite build), `org.xerial:sqlite-jdbc`, Liquibase, JUnit 5, Google Truth.

**Spec:** `docs/superpowers/specs/2026-08-27-sqlite-support-design.md`

**Prerequisite:** `docs/superpowers/plans/2026-08-27-database-kind-refactor.md` must be executed and merged to `main` first — this plan adds `DatabaseKind.SQLITE` and `case SQLITE ->` arms to code that refactor introduces.

---

## Chunk 1: `querymeta` — `DatabaseKind.SQLITE` and `SqliteDialect`

### Task 1: Add `SQLITE` to `DatabaseKind`

**Files:**
- Modify: `gradle-plugin/processorconfig/src/main/java/org/ethelred/kiwiproc/processorconfig/DatabaseKind.java`
- Modify: `gradle-plugin/processorconfig/src/test/java/org/ethelred/kiwiproc/processorconfig/DatabaseKindTest.java`

- [ ] **Step 1: Add failing test cases for SQLite**

In `DatabaseKindTest.fromDriverAndUrl()`'s `Stream.of(...)`, add these arguments (alongside the existing MySQL/H2/Postgres cases):

```java
                arguments("org.sqlite.JDBC", null, DatabaseKind.SQLITE),
                arguments("org.sqlite.JDBC", "jdbc:postgresql://x", DatabaseKind.SQLITE),
                arguments(null, "jdbc:sqlite:/tmp/test.db", DatabaseKind.SQLITE),
```

- [ ] **Step 2: Run test to verify it fails to compile**

Run: `./gradlew -p gradle-plugin :processorconfig:test --tests "org.ethelred.kiwiproc.processorconfig.DatabaseKindTest"`
Expected: FAIL — `cannot find symbol: variable SQLITE`

- [ ] **Step 3: Add the `SQLITE` enum constant**

In `DatabaseKind.java`, change the enum constant list from:

```java
    POSTGRES(null, null),
    MYSQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql:"),
    H2("org.h2.Driver", "jdbc:h2:");
```

to:

```java
    POSTGRES(null, null),
    MYSQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql:"),
    H2("org.h2.Driver", "jdbc:h2:"),
    SQLITE("org.sqlite.JDBC", "jdbc:sqlite:");
```

No other changes to the file are needed — `fromDriverAndUrl`'s two-pass loop already iterates `values()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p gradle-plugin :processorconfig:test --tests "org.ethelred.kiwiproc.processorconfig.DatabaseKindTest"`
Expected: PASS, all cases (including the three new SQLite ones) green.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/processorconfig/src/main/java/org/ethelred/kiwiproc/processorconfig/DatabaseKind.java \
        gradle-plugin/processorconfig/src/test/java/org/ethelred/kiwiproc/processorconfig/DatabaseKindTest.java
git commit -m "feat: add SQLITE to DatabaseKind

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 2: Add `org.xerial:sqlite-jdbc` to the version catalog

**Files:**
- Modify: `catalog.settings.gradle.kts`

- [ ] **Step 1: Add the catalog entry**

In `catalog.settings.gradle.kts`, add a `library(...)` line alongside the existing `postgresql`/`mysql`/`h2` entries:

```kotlin
            library("sqlite", "org.xerial:sqlite-jdbc:3.53.4.0")
```

(Check https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc for a newer stable release at implementation time — this was latest as of 2026-08-26.)

- [ ] **Step 2: Verify the catalog resolves**

Run: `./gradlew help`
Expected: BUILD SUCCESSFUL (catalog syntax errors would fail configuration for every module).

- [ ] **Step 3: Commit**

```bash
git add catalog.settings.gradle.kts
git commit -m "build: add org.xerial:sqlite-jdbc to version catalog

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 3: Add `SqliteDialect`

**Files:**
- Modify: `querymeta/build.gradle.kts`
- Create: `querymeta/src/main/java/org/ethelred/kiwiproc/meta/SqliteDialect.java`
- Modify: `querymeta/src/main/java/org/ethelred/kiwiproc/meta/DatabaseDialects.java`

- [ ] **Step 1: Add the dependency**

In `querymeta/build.gradle.kts`, add to the `dependencies { ... }` block alongside the existing drivers:

```kotlin
    implementation(libs.sqlite)
```

This task is not strict test-first TDD — `SqliteDialect` is written directly in Step 2, with `SqliteDialectTest` following in Step 4, matching the existing precedent in this codebase (`H2DialectTest`/`MySQLDialect` have no dedicated pre-implementation test cycle either, and `H2DialectTest` itself only covers `normalizeColumnName`, not the `getParameters` fallback logic). If you prefer strict red/green here, write `SqliteDialectTest` first and confirm it fails to compile before Step 2 — either order produces the same result.

- [ ] **Step 2: Write `SqliteDialect`**

`SqliteDialect` mirrors `H2Dialect`/`MySQLDialect`'s structure: `createDataSource` builds a driver-specific `DataSource`, `componentType` is a no-op (SQLite has no array types), and `getParameters` uses the same try/synthetic-fallback pattern those two dialects already use (SQLite's `ParameterMetaData` support is similarly unreliable). Unlike H2/MySQL's `DataSource` types, `org.sqlite.SQLiteDataSource` has no `setUser`/`setPassword` methods — SQLite has no built-in authentication, so `config.username()`/`config.password()` are simply not used. No `normalizeColumnName` override is needed — SQLite returns column names in the case they were declared, unlike H2 (which needs lowercasing due to its uppercase-by-default JDBC metadata).

Note: `org.sqlite.SQLiteDataSource`'s setter is `setUrl` (lowercase `rl`), not `setURL` like Postgres's/MySQL's driver `DataSource` classes — this matches SQLite's own API convention, which predates the `javax.sql.DataSource` naming convention the other drivers follow. Double-check this compiles as written in Step 6 below; if the real method name differs, fix it there rather than guessing further in advance.

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.meta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.jspecify.annotations.Nullable;
import org.sqlite.SQLiteDataSource;

public class SqliteDialect implements DatabaseDialect {
    @Override
    public DataSource createDataSource(DataSourceConfig config) {
        var ds = new SQLiteDataSource();
        ds.setUrl(config.url());
        return ds;
    }

    @Override
    public @Nullable ArrayComponent componentType(Connection connection, int columnType, String columnTypeName) {
        return null;
    }

    @Override
    public List<ColumnMetaData> getParameters(Connection connection, PreparedStatement statement, String sql) {
        try {
            var pmd = statement.getParameterMetaData();
            var result = new ArrayList<ColumnMetaData>(pmd.getParameterCount());
            for (int index = 1; index <= pmd.getParameterCount(); index++) {
                try {
                    result.add(ColumnMetaData.from(this, connection, index, pmd));
                } catch (SQLException ignored) {
                    result.add(DatabaseDialect.syntheticParameter(index));
                }
            }
            return result;
        } catch (SQLException ignored) {
            long paramCount = sql.chars().filter(c -> c == '?').count();
            var result = new ArrayList<ColumnMetaData>((int) paramCount);
            for (int index = 1; index <= (int) paramCount; index++) {
                result.add(DatabaseDialect.syntheticParameter(index));
            }
            return result;
        }
    }
}
```

- [ ] **Step 3: Wire it into `DatabaseDialects.fromConfig`**

In `DatabaseDialects.java`, change:

```java
        return switch (DatabaseKind.fromConfig(config)) {
            case MYSQL -> new MySQLDialect();
            case H2 -> new H2Dialect();
            case POSTGRES -> new PostgresDialect();
        };
```

to:

```java
        return switch (DatabaseKind.fromConfig(config)) {
            case MYSQL -> new MySQLDialect();
            case H2 -> new H2Dialect();
            case SQLITE -> new SqliteDialect();
            case POSTGRES -> new PostgresDialect();
        };
```

- [ ] **Step 4: Write a `SqliteDialectTest`**

Model on `H2DialectTest`. Since `SqliteDialect` doesn't override `normalizeColumnName` (uses the default identity implementation), test `createDataSource` instead:

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.meta;

import static com.google.common.truth.Truth.assertThat;

import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.junit.jupiter.api.Test;

class SqliteDialectTest {
    SqliteDialect dialect = new SqliteDialect();

    @Test
    void createDataSource_setsUrl() throws java.sql.SQLException {
        var config = new DataSourceConfig("default", "jdbc:sqlite:/tmp/kiwiproc-test.db", null, null, null, "org.sqlite.JDBC");
        var ds = dialect.createDataSource(config);
        assertThat(ds.getConnection().getMetaData().getURL()).isEqualTo("jdbc:sqlite:/tmp/kiwiproc-test.db");
    }

    @Test
    void normalizeColumnName_isIdentity() {
        assertThat(dialect.normalizeColumnName("MixedCase")).isEqualTo("MixedCase");
    }
}
```

- [ ] **Step 5: Run querymeta's test suite**

Run: `./gradlew :querymeta:test`
Expected: BUILD SUCCESSFUL, including the new `SqliteDialectTest` and all pre-existing dialect tests unchanged.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add querymeta/build.gradle.kts \
        querymeta/src/main/java/org/ethelred/kiwiproc/meta/SqliteDialect.java \
        querymeta/src/main/java/org/ethelred/kiwiproc/meta/DatabaseDialects.java \
        querymeta/src/test/java/org/ethelred/kiwiproc/meta/SqliteDialectTest.java
git commit -m "feat: add SqliteDialect

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 2: Gradle plugin — embedded SQLite

### Task 4: Add `EmbeddedSQLiteService`

**Files:**
- Modify: `gradle-plugin/plugin/build.gradle.kts`
- Create: `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/EmbeddedSQLiteParams.java`
- Create: `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/EmbeddedSQLiteService.java`

Unlike `EmbeddedH2Service` (which runs a shared TCP server and holds keep-alive connections open so in-memory databases survive between connections), SQLite is file-based: each call creates a fresh temp file, and any later connection to that same file sees the same data — no server process or keep-alive connection needed. `close()` cleans up the temp files it created.

This task has no dedicated unit test, matching existing precedent: `EmbeddedH2Service` also has no unit test of its own — it's only exercised indirectly through `:plugin:functionalTest`. `EmbeddedSQLiteService` gets the same treatment here (a plain compile check in Step 4); Task 9's `test-sqlite` module is what actually proves it works end-to-end (starting an embedded SQLite instance, running Liquibase, and having the processor introspect it).

- [ ] **Step 1: Add the dependency**

In `gradle-plugin/plugin/build.gradle.kts`, add to the `dependencies { ... }` block alongside `libs.h2`/`libs.mysql`:

```kotlin
    implementation(libs.sqlite)
```

- [ ] **Step 2: Write the params interface**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.gradle;

import org.gradle.api.services.BuildServiceParameters;

public interface EmbeddedSQLiteParams extends BuildServiceParameters {}
```

- [ ] **Step 3: Write the service**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.gradle.api.services.BuildService;
import org.sqlite.SQLiteDataSource;

public abstract class EmbeddedSQLiteService implements BuildService<EmbeddedSQLiteParams>, AutoCloseable {
    public static final String DEFAULT_NAME = "embeddedSQLite";

    private int dbCounter = 0;
    private final List<Path> createdDatabaseFiles = new ArrayList<>();

    public record SQLiteConnectionInfo(String url) {}

    public synchronized SQLiteConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        try {
            var dbFile = Files.createTempFile("kiwiproc-sqlite-" + (++dbCounter) + "-", ".db");
            // SQLite creates the file itself on first connection; an empty placeholder confuses it.
            Files.deleteIfExists(dbFile);
            createdDatabaseFiles.add(dbFile);
            var url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

            var ds = new SQLiteDataSource();
            ds.setUrl(url);

            try (var connection = ds.getConnection()) {
                var liquibaseConnection = new JdbcConnection(connection);
                var liquibase = new Liquibase(
                        liquibaseChangelog.getName(),
                        new DirectoryResourceAccessor(liquibaseChangelog.getParentFile()),
                        liquibaseConnection);
                liquibase.update();
            }

            return new SQLiteConnectionInfo(url);
        } catch (IOException | SQLException | LiquibaseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        for (var file : createdDatabaseFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }
}
```

Note: `FileNotFoundException` is a subtype of `IOException`, so the catch clause above covers it without listing it separately (unlike `EmbeddedH2Service`, which lists it alongside `SQLException`/`LiquibaseException` — either style compiles; this plan uses the simpler form since `IOException` already appears in the clause).

- [ ] **Step 4: Compile the plugin module**

Run: `./gradlew -p gradle-plugin :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/plugin/build.gradle.kts \
        gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/EmbeddedSQLiteParams.java \
        gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/EmbeddedSQLiteService.java
git commit -m "feat: add EmbeddedSQLiteService

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 5: Register the service and wire `DatabaseKind.SQLITE` into `KiwiProcConfigTask`

**Files:**
- Modify: `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/KiwiProcPlugin.java`
- Modify: `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/KiwiProcConfigTask.java`

- [ ] **Step 1: Register `EmbeddedSQLiteService` in `KiwiProcPlugin`**

In `KiwiProcPlugin.java`'s `apply()` method, add alongside the other three `registerIfAbsent` calls:

```java
        project.getGradle()
                .getSharedServices()
                .registerIfAbsent(EmbeddedSQLiteService.DEFAULT_NAME, EmbeddedSQLiteService.class);
```

- [ ] **Step 2: Add a service reference to `KiwiProcConfigTask`**

Add alongside the other three `@ServiceReference` fields:

```java
    @ServiceReference(EmbeddedSQLiteService.DEFAULT_NAME)
    abstract Property<EmbeddedSQLiteService> getSQLiteService();
```

- [ ] **Step 3: Add the `SQLITE` case to `toDataSourceConfig`'s switch**

From Task 5 of the `database-kind-refactor` plan, `toDataSourceConfig` ends with:

```java
        var kind = DatabaseKind.fromDriverAndUrl(
                kiwiProcDataSource.getDriverClassName().getOrNull(), null);
        return switch (kind) {
            case MYSQL -> { ... }
            case H2 -> { ... }
            case POSTGRES -> { ... }
        };
```

Add a `SQLITE` case (order doesn't matter for a `switch` expression, but place it alongside `H2` for readability):

```java
            case SQLITE -> {
                var connectionInfo = getSQLiteService().get().getPreparedDatabase(liquibaseFile);
                yield new DataSourceConfig(
                        kiwiProcDataSource.getName(),
                        connectionInfo.url(),
                        null,
                        null,
                        null,
                        DatabaseKind.SQLITE.driverClassName());
            }
```

- [ ] **Step 4: Add the `SQLITE` case to `externalDataSourceConfig`'s switch**

```java
                        case SQLITE -> {
                            var sqliteDs = new SQLiteDataSource();
                            sqliteDs.setUrl(url);
                            yield sqliteDs;
                        }
```

(No username/password handling — `org.sqlite.SQLiteDataSource` has no such setters, matching `SqliteDialect.createDataSource`.)

- [ ] **Step 5: Add the import**

```java
import org.sqlite.SQLiteDataSource;
```

- [ ] **Step 6: Compile and run the plugin's test suite**

Run: `./gradlew -p gradle-plugin :plugin:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the functional test suite**

Run: `./gradlew -p gradle-plugin :plugin:functionalTest`
Expected: BUILD SUCCESSFUL — the existing `happyPathTest` fixture uses Postgres, so this doesn't yet exercise SQLite end-to-end (that's what `test-sqlite`, Chunk 4, is for), but confirms the new switch case and service registration don't break the existing build.

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/KiwiProcPlugin.java \
        gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/KiwiProcConfigTask.java
git commit -m "feat: wire EmbeddedSQLiteService into KiwiProcPlugin/KiwiProcConfigTask

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 3: Maven plugin — embedded SQLite

### Task 6: Add `EmbeddedSQLiteManager`

**Files:**
- Modify: `gradle-plugin/maven-plugin/build.gradle.kts`
- Create: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/EmbeddedSQLiteManager.java`
- Create: `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/EmbeddedSQLiteManagerTest.java`

Mirrors `EmbeddedH2Manager`'s singleton shape, but with the same file-based simplification as `EmbeddedSQLiteService` (Task 4) — no server, no keep-alive connections.

- [ ] **Step 1: Add the dependency**

In `gradle-plugin/maven-plugin/build.gradle.kts`, add alongside `libs.h2`/`libs.mysql`:

```kotlin
    implementation(libs.sqlite)
```

- [ ] **Step 2: Write the failing test**

Model directly on `EmbeddedH2ManagerTest`:

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmbeddedSQLiteManagerTest {
    @Test
    void preparesDatabaseAndAppliesLiquibase() throws IOException {
        var changelog = writeChangelog();

        var info = EmbeddedSQLiteManager.getInstance().getPreparedDatabase(changelog.toFile());

        assertThat(info.url()).startsWith("jdbc:sqlite:");
    }

    private Path writeChangelog() throws IOException {
        var dir = Files.createTempDirectory("kiwiproc-maven-test-sqlite");
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

- [ ] **Step 3: Run test to verify it fails to compile**

Run: `./gradlew -p gradle-plugin :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.EmbeddedSQLiteManagerTest"`
Expected: FAIL — `cannot find symbol: class EmbeddedSQLiteManager`

- [ ] **Step 4: Write `EmbeddedSQLiteManager`**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import org.sqlite.SQLiteDataSource;

/**
 * Process-wide singleton that creates a fresh temp SQLite database file per requested datasource
 * and applies its Liquibase changelog. SQLite is file-based, so unlike {@link EmbeddedH2Manager}
 * there is no shared server process or keep-alive connection to manage — any later connection to
 * the same file sees the same data.
 */
public final class EmbeddedSQLiteManager {
    private static final EmbeddedSQLiteManager INSTANCE = new EmbeddedSQLiteManager();

    private int dbCounter = 0;

    public record SQLiteConnectionInfo(String url) {}

    private EmbeddedSQLiteManager() {}

    public static EmbeddedSQLiteManager getInstance() {
        return INSTANCE;
    }

    public synchronized SQLiteConnectionInfo getPreparedDatabase(File liquibaseChangelog) {
        try {
            Path dbFile = Files.createTempFile("kiwiproc-maven-sqlite-" + (++dbCounter) + "-", ".db");
            Files.deleteIfExists(dbFile);
            var url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

            var ds = new SQLiteDataSource();
            ds.setUrl(url);

            try (var connection = ds.getConnection()) {
                var liquibaseConnection = new JdbcConnection(connection);
                var liquibase = new Liquibase(
                        liquibaseChangelog.getName(),
                        new DirectoryResourceAccessor(liquibaseChangelog.getParentFile()),
                        liquibaseConnection);
                liquibase.update();
            }

            return new SQLiteConnectionInfo(url);
        } catch (IOException | SQLException | LiquibaseException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew -p gradle-plugin :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.EmbeddedSQLiteManagerTest"`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/maven-plugin/build.gradle.kts \
        gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/EmbeddedSQLiteManager.java \
        gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/EmbeddedSQLiteManagerTest.java
git commit -m "feat: add EmbeddedSQLiteManager

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 7: Wire `DatabaseKind.SQLITE` into `KiwiProcMojo`

**Files:**
- Modify: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java`

- [ ] **Step 1: Add the `SQLITE` case to `toDataSourceConfig`'s switch**

From Task 7 of the `database-kind-refactor` plan, `toDataSourceConfig` switches on `dataSource.getDatabaseKind()`. Add:

```java
            case SQLITE -> {
                var connectionInfo = EmbeddedSQLiteManager.getInstance().getPreparedDatabase(liquibaseFile);
                yield new DataSourceConfig(
                        dataSource.getName(),
                        connectionInfo.url(),
                        null,
                        null,
                        null,
                        DatabaseKind.SQLITE.driverClassName());
            }
```

- [ ] **Step 2: Add the `SQLITE` case to `externalDataSourceConfig`'s switch**

```java
                        case SQLITE -> {
                            var sqliteDs = new SQLiteDataSource();
                            sqliteDs.setUrl(url);
                            yield sqliteDs;
                        }
```

- [ ] **Step 3: Add the import**

```java
import org.sqlite.SQLiteDataSource;
```

- [ ] **Step 4: Run the Maven plugin's test suite**

Run: `./gradlew -p gradle-plugin :maven-plugin:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the functional test suite**

Run: `./gradlew -p gradle-plugin :maven-plugin:functionalTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java
git commit -m "feat: wire EmbeddedSQLiteManager into KiwiProcMojo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 4: `test-sqlite` integration test module

### Task 8: Create the module skeleton

**Files:**
- Create: `test-sqlite/build.gradle.kts`
- Create: `test-sqlite/src/main/resources/changelog/changelog.xml`
- Modify: `settings.gradle.kts`

`test-sqlite` mirrors `test-h2`'s `Product`/`ProductDAO` schema and structure exactly (same table, same DAO shape), since the goal is parity — plus one addition (Task 10) for the `RETURNING` case.

- [ ] **Step 1: Add `test-sqlite` to settings**

In `settings.gradle.kts`, add `"test-sqlite"` to the existing `include(...)` call:

```kotlin
include("shared", "querymeta", "processor", "runtime", "spring-autoconfigure", "test-spring", "test-micronaut", "docs", ":docs:example", "test-any", "test-mysql", "test-h2", "test-sqlite")
```

- [ ] **Step 2: Write `test-sqlite/build.gradle.kts`**

```kotlin

plugins {
    id("java-convention")
    id("org.ethelred.kiwiproc")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))
    implementation(libs.jakarta.inject)
    testImplementation(libs.sqlite)
}

kiwiProc {
    dataSources {
        register("default") {
            driverClassName = "org.sqlite.JDBC"
            liquibaseChangelog = file("$projectDir/src/main/resources/changelog/changelog.xml")
        }
    }
}
```

- [ ] **Step 3: Write the changelog**

Identical schema to `test-h2`'s (same `product` table):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="1" author="kiwiproc">
        <createTable tableName="product">
            <column name="id" type="INT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="price" type="DECIMAL(10,2)">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 4: Verify Gradle sees the new module**

Run: `./gradlew :test-sqlite:tasks`
Expected: BUILD SUCCESSFUL, task list printed (module recognised, plugin applies cleanly).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts test-sqlite/build.gradle.kts test-sqlite/src/main/resources/changelog/changelog.xml
git commit -m "build: add test-sqlite module skeleton

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 9: Port `ProductDAO` and its test

**Files:**
- Create: `test-sqlite/src/main/java/org/ethelred/kiwiproc/testsqlite/ProductDAO.java`
- Create: `test-sqlite/src/test/java/org/ethelred/kiwiproc/testsqlite/ProductDAOTest.java`

- [ ] **Step 1: Write `ProductDAO`**

Identical to `test-h2`'s `ProductDAO`, package renamed:

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testsqlite;

import java.util.Collection;
import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlBatch;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.jspecify.annotations.Nullable;

@DAO
public interface ProductDAO {
    record Product(int id, String name, double price) {}

    @SqlUpdate("INSERT INTO product (name, price) VALUES (:name, :price)")
    void insertProduct(String name, double price);

    @SqlQuery("SELECT id, name, price FROM product WHERE id = :id")
    @Nullable Product findById(int id);

    @SqlQuery("SELECT id, name, price FROM product ORDER BY id")
    List<Product> listAll();

    @SqlQuery("SELECT id, name, price FROM product ORDER BY id")
    Collection<Product> listAllAsCollection();

    @SqlQuery("SELECT id, name, price FROM product ORDER BY id")
    Iterable<Product> listAllAsIterable();

    @SqlQuery("SELECT name FROM product ORDER BY id")
    String[] listAllNamesAsArray();

    @SqlQuery(value = "SELECT id, name, price FROM product ORDER BY id", fetchSize = 5)
    List<Product> listAllWithFetchSize();

    @SqlBatch(value = "INSERT INTO product (name, price) VALUES (:name, :price)", batchSize = 2)
    void batchInsertWithSize(List<String> name, List<Double> price);

    @SqlUpdate("DELETE FROM product")
    void deleteAll();
}
```

- [ ] **Step 2: Write `ProductDAOTest`**

Identical to `test-h2`'s `ProductDAOTest`, package renamed, `JdbcDataSource`/H2 import swapped for `org.sqlite.SQLiteDataSource`:

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testsqlite;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

public class ProductDAOTest {
    static ProductDAO dao = initializeDAO();

    private static ProductDAO initializeDAO() {
        var propertiesUrl = ProductDAOTest.class.getResource("/application-test.properties");
        if (propertiesUrl == null) {
            throw new AssertionError("DB properties not found");
        }
        var properties = new Properties();
        try (var inputStream = propertiesUrl.openStream();
                var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new AssertionError("Failed to read DB properties file", e);
        }
        var dataSource = new SQLiteDataSource();
        dataSource.setUrl(properties.getProperty("datasources.default.url"));
        return new $ProductDAO$Provider(dataSource);
    }

    @BeforeEach
    void clearData() {
        dao.deleteAll();
    }

    @Test
    void insertAndFindById() {
        dao.insertProduct("Widget", 9.99);
        var all = dao.listAll();
        assertThat(all).isNotEmpty();
        var last = all.get(all.size() - 1);
        var found = dao.findById(last.id());
        assertThat(found).isNotNull();
        assertThat(found.name()).isEqualTo("Widget");
    }

    @Test
    void listAllReturnsInsertedProducts() {
        dao.insertProduct("Gadget", 19.99);
        dao.insertProduct("Doohickey", 4.99);
        var all = dao.listAll();
        assertThat(all).isNotEmpty();
    }

    @Test
    void findByIdReturnsNullWhenNotFound() {
        var result = dao.findById(Integer.MAX_VALUE);
        assertThat(result).isNull();
    }

    @Test
    void listAllAsCollectionReturnsProducts() {
        dao.insertProduct("Alpha", 1.00);
        dao.insertProduct("Beta", 2.00);
        var result = dao.listAllAsCollection();
        assertThat(result).hasSize(2);
    }

    @Test
    void listAllAsIterableReturnsProducts() {
        dao.insertProduct("Alpha", 1.00);
        var result = dao.listAllAsIterable();
        assertThat(result).hasSize(1);
    }

    @Test
    void listAllNamesAsArrayReturnsNames() {
        dao.insertProduct("Widget", 9.99);
        dao.insertProduct("Gadget", 19.99);
        var names = dao.listAllNamesAsArray();
        assertThat(names).asList().containsExactly("Widget", "Gadget").inOrder();
    }

    @Test
    void listAllWithFetchSizeReturnsCorrectResults() {
        dao.insertProduct("X", 1.00);
        dao.insertProduct("Y", 2.00);
        var result = dao.listAllWithFetchSize();
        assertThat(result).hasSize(2);
    }

    @Test
    void batchInsertWithCustomSizeInsertsAllRows() {
        dao.batchInsertWithSize(List.of("P1", "P2", "P3", "P4", "P5"), List.of(1.0, 2.0, 3.0, 4.0, 5.0));
        assertThat(dao.listAll()).hasSize(5);
    }
}
```

- [ ] **Step 3: Run the module's tests**

Run: `./gradlew :test-sqlite:test`
Expected: BUILD SUCCESSFUL, all 8 tests pass. This is the first real end-to-end proof that annotation processing + code generation + query execution work against SQLite: the plugin starts an embedded SQLite file via `EmbeddedSQLiteService`, runs the changelog against it, the processor introspects that same file through `SqliteDialect` to generate `$ProductDAO$Provider`/`$ProductDAO$Impl`, and — since `KiwiProcConfigTask` writes that file's connection URL into `application-test.properties` (the same `toProperties`/`processTestResources` wiring H2 and MySQL already use) — `ProductDAOTest` connects to and exercises that identical file at test time, not a second one. The temp file is only deleted when `EmbeddedSQLiteService.close()` runs at the end of the Gradle session.

If any test fails here due to a type-mapping or metadata issue not anticipated in the spec (e.g. SQLite's type-affinity system reporting something unexpected for `DECIMAL(10,2)` or `VARCHAR(255)`), stop and diagnose before proceeding — this is the point the spec flagged as "any real gaps get fixed as part of this work." Do not skip ahead to Task 10 with a known-broken base schema.

- [ ] **Step 4: Format and commit**

```bash
./gradlew spotlessApply
git add test-sqlite/src/main/java/org/ethelred/kiwiproc/testsqlite/ProductDAO.java \
        test-sqlite/src/test/java/org/ethelred/kiwiproc/testsqlite/ProductDAOTest.java
git commit -m "test: add test-sqlite ProductDAO integration tests

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 10: Add the `RETURNING` test

**Files:**
- Modify: `test-sqlite/src/main/java/org/ethelred/kiwiproc/testsqlite/ProductDAO.java`
- Modify: `test-sqlite/src/test/java/org/ethelred/kiwiproc/testsqlite/ProductDAOTest.java`

Per the spec, this validates whether `org.xerial:sqlite-jdbc`'s metadata reporting for a `RETURNING` insert is correct — there is no H2/MySQL precedent (`databases.adoc` documents `RETURNING` as unsupported for both), only Postgres's `PetClinicDAO.addVisit`. Rather than adding a second table to mirror `addVisit`'s exact shape, this uses `product`'s own auto-increment `id` — simpler, and equally exercises the same JDBC metadata path (`PreparedStatement.getMetaData()`/`getParameterMetaData()` via `DatabaseWrapper`, with no bespoke `RETURNING` parsing anywhere in kiwiproc).

- [ ] **Step 1: Write the failing test**

Add to `ProductDAOTest.java`:

```java
    @Test
    void insertProductReturningId_returnsGeneratedId() {
        var id = dao.insertProductReturningId("Returned", 3.50);
        assertThat(id).isPresent();
        var found = dao.findById(id.get());
        assertThat(found).isNotNull();
        assertThat(found.name()).isEqualTo("Returned");
    }
```

No new import is needed in the test file — `id` is inferred as `Optional<Integer>` via `var`, and only its `isPresent()`/`get()` methods are called, so `Optional` is never referenced by name here.

- [ ] **Step 2: Run test to verify it fails to compile**

Run: `./gradlew :test-sqlite:test --tests "org.ethelred.kiwiproc.testsqlite.ProductDAOTest"`
Expected: FAIL — `cannot find symbol: method insertProductReturningId(...)`

- [ ] **Step 3: Add the DAO method**

In `ProductDAO.java`, add:

```java
    @SqlQuery("INSERT INTO product (name, price) VALUES (:name, :price) RETURNING id")
    Optional<Integer> insertProductReturningId(String name, double price);
```

Add the import:

```java
import java.util.Optional;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :test-sqlite:test --tests "org.ethelred.kiwiproc.testsqlite.ProductDAOTest"`
Expected: PASS.

This can fail two different ways, both pointing at the same underlying cause (`org.xerial:sqlite-jdbc`'s `PreparedStatement.getMetaData()` not reporting usable column metadata for a `RETURNING` insert), but reached differently:

- **Compile-time error from the annotation processor** — the processor couldn't determine `insertProductReturningId`'s result shape at all. This is the clean failure mode.
- **Runtime failure** — the code generates and compiles, but the test itself fails: a thrown `SQLException`, an empty `Optional` where one was expected, or a wrong value. This means the driver reported *something* for the metadata, just not something correct — a subtler variant of the same underlying gap.

In either case: do not force it or work around it with driver-specific hacks. Remove the method and test, and instead add a `RETURNING` bullet to the "Limitations" section written in Task 11, documenting that `RETURNING` is not currently supported for SQLite (matching how H2/MySQL are already documented) — then proceed to Task 11 with that adjustment. The only case that would warrant actually debugging further (rather than documenting the limitation) is if the failure looks like a mistake in this plan's own SQL/test code rather than a driver limitation — e.g. a typo in the query — so read the failure output before concluding it's a driver gap.

- [ ] **Step 5: Run the full module test suite**

Run: `./gradlew :test-sqlite:test`
Expected: BUILD SUCCESSFUL, all 9 tests pass.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add test-sqlite/src/main/java/org/ethelred/kiwiproc/testsqlite/ProductDAO.java \
        test-sqlite/src/test/java/org/ethelred/kiwiproc/testsqlite/ProductDAOTest.java
git commit -m "test: add RETURNING insert test for test-sqlite

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 5: Documentation

### Task 11: Document SQLite in `databases.adoc`

**Files:**
- Modify: `docs/src/docs/asciidoc/databases.adoc`

- [ ] **Step 1: Add the SQLite section**

Following the existing H2/MySQL template. Note the file's current section order is PostgreSQL → MySQL (intro) → H2 (intro + `[#h2-limitations]`) → `[#mysql-limitations]`, with `[#mysql-limitations]` — not `[#h2-limitations]` — actually ending the file. Add the new `=== SQLite` section (with its own `[#sqlite-limitations]` subsection) at the end of the file, after the existing `[#mysql-limitations]` section:

```asciidoc
=== SQLite

SQLite is supported as a lightweight, file-based alternative for build-time schema validation.
No Docker or server process is required — each build creates a fresh temporary SQLite database
file, which is deleted afterwards.

To select embedded SQLite, set `driverClassName` to `org.sqlite.JDBC` in the datasource configuration:

[source,kotlin,indent=0,subs="verbatim,attributes",role="primary"]
----
kiwiProc {
    dataSources {
        register("default") {
            driverClassName = "org.sqlite.JDBC" // <1>
            liquibaseChangelog = file("$projectDir/src/main/resources/changelog.xml")
        }
    }
}
----
<1> Setting `driverClassName` to the SQLite driver selects embedded SQLite — no Docker required.

[#sqlite-limitations]
==== SQLite Limitations

1. *Parameter type checking may be weaker* — SQLite's JDBC driver does not always provide reliable parameter metadata; types fall back to `UNKNOWN` when unavailable.
2. *SQL arrays are not supported* — array type introspection is not implemented for SQLite.
3. *No native enum type* — SQLite has no catalog of user-defined enum types to introspect.
4. *Dynamic typing* — SQLite uses type affinity rather than strict column types; declared column types are advisory, not enforced by the database itself.
```

If Task 10's `RETURNING` test had to be removed (driver metadata gap), also add:

```asciidoc
5. *The `RETURNING` clause is not supported* — use a separate `SELECT` query after an `INSERT` if you need generated values.
```

- [ ] **Step 2: Build the docs module to check for asciidoc syntax errors**

Run: `./gradlew :docs:asciidoctor` (or the project's actual docs-build task — check `docs/build.gradle.kts` for the exact task name if this doesn't exist)
Expected: BUILD SUCCESSFUL, no asciidoc syntax warnings for the new section.

- [ ] **Step 3: Commit**

```bash
git add docs/src/docs/asciidoc/databases.adoc
git commit -m "docs: document SQLite support

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 6: Full verification

### Task 12: Run the full build

**Files:** none (verification only)

- [ ] **Step 1: Run the full root build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — includes `:test-sqlite:test`, `:querymeta:test`, `:processor:test`, and all other existing modules unchanged.

- [ ] **Step 2: Run the full gradle-plugin composite build**

Run: `./gradlew -p gradle-plugin build`
Expected: BUILD SUCCESSFUL — covers `:processorconfig:test`, `:plugin:test`, `:plugin:functionalTest`, `:maven-plugin:test`, `:maven-plugin:functionalTest`.

- [ ] **Step 3: Confirm `DatabaseKind` is exhaustive everywhere**

Since `DatabaseKind` is a plain enum (not `sealed`), the Java compiler won't force every `switch` to handle `SQLITE` — check no switch was missed:

Run: `grep -rn "DatabaseKind.fromDriverAndUrl\|DatabaseKind.fromConfig\|getDatabaseKind()" --include="*.java" gradle-plugin querymeta | grep -v /build/ | grep -v Test.java`

For each result, confirm (by inspection) the corresponding `switch` has a `SQLITE` case. Expected matches: `DatabaseDialects.java`, `KiwiProcConfigTask.java` (two switches), `KiwiProcMojo.java` (two switches), `DataSourceParameter.java` (the `getDatabaseKind()` definition itself, not a switch).

- [ ] **Step 4: Push**

```bash
git push -u origin feature/sqlite-support
```
