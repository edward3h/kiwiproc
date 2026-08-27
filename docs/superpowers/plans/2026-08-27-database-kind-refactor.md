# DatabaseKind Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the duplicated `isMySQL()`/`isH2()`-style driver-class/URL detection scattered across `DatabaseDialects` (querymeta), `KiwiProcConfigTask` (Gradle plugin), and `KiwiProcMojo`/`DataSourceParameter` (Maven plugin) with a single shared `DatabaseKind` enum, without changing any observable behaviour.

**Architecture:** A new `DatabaseKind` enum (`POSTGRES`, `MYSQL`, `H2`) lives in `gradle-plugin/processorconfig` — the module already extracted to hold light, framework-agnostic classes shared by `querymeta`, `plugin`, and `maven-plugin` without pulling JDBC drivers into the Maven plugin's classpath. `DatabaseKind.fromDriverAndUrl(driverClassName, url)` is the single dispatch point (driver-class match, then URL-prefix fallback, `POSTGRES` as trailing default). Each of the three existing call sites is rewritten to switch on `DatabaseKind` instead of re-deriving it from strings.

**Tech Stack:** Java 17, Gradle (composite build `gradle-plugin` + root build), JUnit 5, Google Truth.

**Spec:** `docs/superpowers/specs/2026-08-27-sqlite-support-design.md` (section 0)

**Prerequisite for:** `docs/superpowers/plans/2026-08-27-sqlite-support.md` (do not start that plan until this one is merged — it builds SQLite's `DatabaseKind.SQLITE` case on top of this refactor).

---

## Chunk 1: `DatabaseKind` enum

### Task 1: Add test infrastructure to `processorconfig`

The `processorconfig` module currently has no test source set — it was extracted as a lean, dependency-free-of-JDBC-drivers module and has never needed one. Adding `DatabaseKind` gives it its first unit-testable logic.

**Files:**
- Modify: `gradle-plugin/processorconfig/build.gradle.kts`

- [ ] **Step 1: Add JUnit dependencies and test task config**

Add to the `dependencies { ... }` block in `gradle-plugin/processorconfig/build.gradle.kts` (after the existing `api(libs.avaje.json.asProvider())` line):

```kotlin
    val junitVersion = "6.1.3"
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.truth:truth:1.4.5")
```

Add this block after the `java { ... }` block (before `repositories { ... }` or after — either is fine, match existing style by placing after `repositories`):

```kotlin
tasks.named<Test>("test") {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Verify the module still configures**

Run: `./gradlew -p gradle-plugin :processorconfig:dependencies --configuration testRuntimeClasspath`
Expected: BUILD SUCCESSFUL, output lists `junit-jupiter`, `junit-platform-launcher`, `truth` in the resolved dependency tree.

- [ ] **Step 3: Commit**

```bash
git add gradle-plugin/processorconfig/build.gradle.kts
git commit -m "build: add test infrastructure to processorconfig module

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 2: Write the failing `DatabaseKind` test

**Files:**
- Create: `gradle-plugin/processorconfig/src/test/java/org/ethelred/kiwiproc/processorconfig/DatabaseKindTest.java`

- [ ] **Step 1: Write the test**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processorconfig;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DatabaseKindTest {

    public static Stream<Arguments> fromDriverAndUrl() {
        return Stream.of(
                // driver-class match wins, regardless of URL
                arguments("com.mysql.cj.jdbc.Driver", null, DatabaseKind.MYSQL),
                arguments("com.mysql.cj.jdbc.Driver", "jdbc:postgresql://x", DatabaseKind.MYSQL),
                arguments("org.h2.Driver", null, DatabaseKind.H2),
                arguments("org.h2.Driver", "jdbc:postgresql://x", DatabaseKind.H2),
                // URL-prefix fallback when driver class is null/unrecognised
                arguments(null, "jdbc:mysql://localhost/db", DatabaseKind.MYSQL),
                arguments(null, "jdbc:h2:mem:test", DatabaseKind.H2),
                arguments(null, "jdbc:postgresql://localhost/db", DatabaseKind.POSTGRES),
                // no driver, no url -> postgres default
                arguments(null, null, DatabaseKind.POSTGRES),
                // unrecognised driver, no url-prefix match -> postgres default
                arguments("org.postgresql.Driver", null, DatabaseKind.POSTGRES));
    }

    @ParameterizedTest
    @MethodSource
    void fromDriverAndUrl(String driverClassName, String url, DatabaseKind expected) {
        assertThat(DatabaseKind.fromDriverAndUrl(driverClassName, url)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void fromConfig_delegatesToFromDriverAndUrl() {
        var config = new DataSourceConfig("default", "jdbc:h2:mem:test", null, null, null, null);
        assertThat(DatabaseKind.fromConfig(config)).isEqualTo(DatabaseKind.H2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails to compile (DatabaseKind doesn't exist yet)**

Run: `./gradlew -p gradle-plugin :processorconfig:test --tests "org.ethelred.kiwiproc.processorconfig.DatabaseKindTest"`
Expected: FAIL — compilation error, `cannot find symbol: class DatabaseKind`

### Task 3: Implement `DatabaseKind`

**Files:**
- Create: `gradle-plugin/processorconfig/src/main/java/org/ethelred/kiwiproc/processorconfig/DatabaseKind.java`

- [ ] **Step 1: Write the implementation**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processorconfig;

import org.jspecify.annotations.Nullable;

/**
 * Identifies which database a {@link DataSourceConfig} (or a plugin-frontend-specific
 * datasource declaration) targets, by driver class name and/or JDBC URL prefix.
 *
 * <p>This is a pure classification enum with no JDBC driver dependency, so it can be used from
 * modules (like the Maven plugin) that must not pull every supported database's driver onto
 * their classpath just to answer "which database is this." Modules that need to do real,
 * driver-specific work per database (see {@code DatabaseDialect} in {@code querymeta}) switch on
 * this enum rather than re-deriving the classification from strings themselves.
 */
public enum DatabaseKind {
    POSTGRES(null, null),
    MYSQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql:"),
    H2("org.h2.Driver", "jdbc:h2:");

    private final @Nullable String driverClassName;
    private final @Nullable String urlPrefix;

    DatabaseKind(@Nullable String driverClassName, @Nullable String urlPrefix) {
        this.driverClassName = driverClassName;
        this.urlPrefix = urlPrefix;
    }

    /**
     * The canonical JDBC driver class name for this kind, or {@code null} for {@link #POSTGRES}
     * (which has historically been left unset in generated {@link DataSourceConfig}s).
     */
    public @Nullable String driverClassName() {
        return driverClassName;
    }

    /**
     * Classifies a database given its driver class name and/or JDBC URL. Driver-class match is
     * checked first (across all kinds), then URL-prefix match (across all kinds), then falls
     * back to {@link #POSTGRES}. Either argument may be {@code null}.
     */
    public static DatabaseKind fromDriverAndUrl(@Nullable String driverClassName, @Nullable String url) {
        for (var kind : values()) {
            if (kind.driverClassName != null && kind.driverClassName.equals(driverClassName)) {
                return kind;
            }
        }
        var effectiveUrl = url != null ? url : "";
        for (var kind : values()) {
            if (kind.urlPrefix != null && effectiveUrl.startsWith(kind.urlPrefix)) {
                return kind;
            }
        }
        return POSTGRES;
    }

    /** Convenience overload for classifying an existing {@link DataSourceConfig}. */
    public static DatabaseKind fromConfig(DataSourceConfig config) {
        return fromDriverAndUrl(config.driverClassName(), config.url());
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew -p gradle-plugin :processorconfig:test --tests "org.ethelred.kiwiproc.processorconfig.DatabaseKindTest"`
Expected: PASS, all parameterized cases + the `fromConfig` test green.

- [ ] **Step 3: Format and commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/processorconfig/src/main/java/org/ethelred/kiwiproc/processorconfig/DatabaseKind.java \
        gradle-plugin/processorconfig/src/test/java/org/ethelred/kiwiproc/processorconfig/DatabaseKindTest.java
git commit -m "feat: add DatabaseKind enum to processorconfig

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 2: Migrate `querymeta`'s `DatabaseDialects`

### Task 4: Switch `DatabaseDialects.fromConfig` to use `DatabaseKind`

**Files:**
- Modify: `querymeta/src/main/java/org/ethelred/kiwiproc/meta/DatabaseDialects.java`

There's an existing test suite for dialect selection behaviour to check first:

- [ ] **Step 1: Check for existing `DatabaseDialects` tests**

Run: `find querymeta/src/test -iname "*DatabaseDialects*"`
If a test file exists, read it before proceeding so the refactor doesn't break its assumptions (it should not need changes — behaviour is unchanged — but confirm the dispatch order it relies on still matches). If none exists, proceed to Step 2 as-is; this refactor does not need to add new coverage here since `DatabaseKind` itself is now the tested dispatch logic (Task 2/3), and `DatabaseDialects` becomes a thin adapter.

- [ ] **Step 2: Replace the implementation**

Replace the full contents of `querymeta/src/main/java/org/ethelred/kiwiproc/meta/DatabaseDialects.java`:

```java
/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import org.ethelred.kiwiproc.processorconfig.DatabaseKind;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;

public class DatabaseDialects {
    public static DatabaseDialect fromConfig(DataSourceConfig config) {
        return switch (DatabaseKind.fromConfig(config)) {
            case MYSQL -> new MySQLDialect();
            case H2 -> new H2Dialect();
            case POSTGRES -> new PostgresDialect();
        };
    }
}
```

- [ ] **Step 3: Run querymeta's test suite**

Run: `./gradlew :querymeta:test`
Expected: BUILD SUCCESSFUL, all existing tests (including `H2DialectTest` and any dialect-selection tests found in Step 1) still pass unchanged.

- [ ] **Step 4: Format and commit**

```bash
./gradlew spotlessApply
git add querymeta/src/main/java/org/ethelred/kiwiproc/meta/DatabaseDialects.java
git commit -m "refactor: DatabaseDialects.fromConfig delegates to DatabaseKind

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 3: Migrate the Gradle plugin's `KiwiProcConfigTask`

### Task 5: Switch `KiwiProcConfigTask` to use `DatabaseKind`

**Files:**
- Modify: `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/KiwiProcConfigTask.java`

This task has no dedicated unit test today (it's covered by the `:plugin` functional test suite, `KiwiProcPluginTest`, which exercises it end-to-end through a real Gradle build). Since this is a pure internal refactor with no behaviour change, the functional test suite is the regression check — no new test file is needed here.

- [ ] **Step 1: Replace the `isMySQL`/`isH2`/`toDataSourceConfig`/`externalDataSourceConfig` methods**

In `gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/KiwiProcConfigTask.java`, remove the `isMySQL` and `isH2` private methods (lines 139–145) and replace `toDataSourceConfig` and `externalDataSourceConfig` (lines 147–213) with:

```java
    private DataSourceConfig toDataSourceConfig(KiwiProcDataSource kiwiProcDataSource) {
        if (isExternal(kiwiProcDataSource)) {
            return externalDataSourceConfig(kiwiProcDataSource);
        }
        var liquibaseFile = kiwiProcDataSource.getLiquibaseChangelog().get().getAsFile();
        var kind = DatabaseKind.fromDriverAndUrl(
                kiwiProcDataSource.getDriverClassName().getOrNull(), null);
        return switch (kind) {
            case MYSQL -> {
                var connectionInfo = getMySQLService().get().getPreparedDatabase(liquibaseFile);
                yield new DataSourceConfig(
                        kiwiProcDataSource.getName(),
                        connectionInfo.url(),
                        null,
                        connectionInfo.username(),
                        connectionInfo.password(),
                        DatabaseKind.MYSQL.driverClassName());
            }
            case H2 -> {
                var connectionInfo = getH2Service().get().getPreparedDatabase(liquibaseFile);
                yield new DataSourceConfig(
                        kiwiProcDataSource.getName(),
                        connectionInfo.url(),
                        null,
                        null,
                        null,
                        DatabaseKind.H2.driverClassName());
            }
            case POSTGRES -> {
                var connectionInfo = getService().get().getPreparedDatabase(liquibaseFile);
                yield new DataSourceConfig(
                        kiwiProcDataSource.getName(),
                        "jdbc:postgresql://localhost:%d/%s?user=%s"
                                .formatted(
                                        connectionInfo.getPort(),
                                        connectionInfo.getDbName(),
                                        connectionInfo.getUser()),
                        connectionInfo.getDbName(),
                        connectionInfo.getUser(),
                        null,
                        null);
            }
        };
    }

    private DataSourceConfig externalDataSourceConfig(KiwiProcDataSource kiwiProcDataSource) {
        if (kiwiProcDataSource.getLiquibaseChangelog().isPresent()) {
            var url = kiwiProcDataSource.getJdbcUrl().get();
            var kind = DatabaseKind.fromDriverAndUrl(
                    kiwiProcDataSource.getDriverClassName().getOrNull(), url);
            DataSource ds =
                    switch (kind) {
                        case MYSQL -> {
                            var mysqlDs = new MysqlDataSource();
                            mysqlDs.setURL(url);
                            ifPresent(kiwiProcDataSource.getUsername(), mysqlDs::setUser);
                            ifPresent(kiwiProcDataSource.getPassword(), mysqlDs::setPassword);
                            yield mysqlDs;
                        }
                        case H2 -> {
                            var h2Ds = new JdbcDataSource();
                            h2Ds.setURL(url);
                            ifPresent(kiwiProcDataSource.getUsername(), h2Ds::setUser);
                            ifPresent(kiwiProcDataSource.getPassword(), h2Ds::setPassword);
                            yield h2Ds;
                        }
                        case POSTGRES -> {
                            var pgDs = new PGSimpleDataSource();
                            pgDs.setURL(url);
                            ifPresent(kiwiProcDataSource.getDatabase(), pgDs::setDatabaseName);
                            ifPresent(kiwiProcDataSource.getUsername(), pgDs::setUser);
                            ifPresent(kiwiProcDataSource.getPassword(), pgDs::setPassword);
                            yield pgDs;
                        }
                    };
            liquibaseUpdate(
                    kiwiProcDataSource.getLiquibaseChangelog().getAsFile().get(), ds);
        }

        return new DataSourceConfig(
                kiwiProcDataSource.getName(),
                kiwiProcDataSource.getJdbcUrl().get(),
                kiwiProcDataSource.getDatabase().getOrNull(),
                kiwiProcDataSource.getUsername().getOrNull(),
                kiwiProcDataSource.getPassword().getOrNull(),
                kiwiProcDataSource.getDriverClassName().getOrNull());
    }
```

Note: `isExternal` stays unchanged — only `isMySQL`/`isH2` are removed.

- [ ] **Step 2: Add the `DatabaseKind` import**

Add to the import block (alongside the existing `org.ethelred.kiwiproc.processorconfig.*` imports):

```java
import org.ethelred.kiwiproc.processorconfig.DatabaseKind;
```

- [ ] **Step 3: Compile and run the plugin's test suite**

Run: `./gradlew -p gradle-plugin :plugin:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the functional test suite (exercises `KiwiProcConfigTask` end-to-end)**

Run: `./gradlew -p gradle-plugin :plugin:functionalTest`
Expected: BUILD SUCCESSFUL. This is the real regression check for this task — it runs a full Gradle build through the plugin against embedded Postgres/H2/MySQL, so a behaviour change in the switch logic would surface here.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/plugin/src/main/java/org/ethelred/kiwiproc/gradle/KiwiProcConfigTask.java
git commit -m "refactor: KiwiProcConfigTask uses DatabaseKind instead of isMySQL/isH2

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 4: Migrate the Maven plugin's `KiwiProcMojo` and `DataSourceParameter`

### Task 6: Add `getDatabaseKind()` to `DataSourceParameter`

**Files:**
- Modify: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/DataSourceParameter.java`

This change also fixes a real, pre-existing inconsistency: `isMySQL()` here has no URL-prefix fallback (only driver-class), while `isH2()` does (`jdbcUrl.startsWith("jdbc:h2:")`), and `KiwiProcConfigTask`'s Gradle-side check does OR in a URL check for MySQL. Routing both through `DatabaseKind.fromDriverAndUrl` makes MySQL and H2 detection symmetric, matching the Gradle plugin.

- [ ] **Step 1: Write the failing test**

Check for an existing `DataSourceParameterTest`:

Run: `find gradle-plugin/maven-plugin/src/test -iname "*DataSourceParameter*"`

If none exists, create `gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/DataSourceParameterTest.java`:

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import static com.google.common.truth.Truth.assertThat;

import org.ethelred.kiwiproc.processorconfig.DatabaseKind;
import org.junit.jupiter.api.Test;

class DataSourceParameterTest {

    @Test
    void getDatabaseKind_byDriverClassName() {
        var param = new DataSourceParameter();
        param.setDriverClassName("com.mysql.cj.jdbc.Driver");
        assertThat(param.getDatabaseKind()).isEqualTo(DatabaseKind.MYSQL);
    }

    @Test
    void getDatabaseKind_mysqlByUrlAlone_previouslyUnsupportedNowWorks() {
        var param = new DataSourceParameter();
        param.setJdbcUrl("jdbc:mysql://localhost:3306/test");
        assertThat(param.getDatabaseKind()).isEqualTo(DatabaseKind.MYSQL);
    }

    @Test
    void getDatabaseKind_h2ByUrlAlone() {
        var param = new DataSourceParameter();
        param.setJdbcUrl("jdbc:h2:mem:test");
        assertThat(param.getDatabaseKind()).isEqualTo(DatabaseKind.H2);
    }

    @Test
    void getDatabaseKind_defaultsToPostgres() {
        var param = new DataSourceParameter();
        assertThat(param.getDatabaseKind()).isEqualTo(DatabaseKind.POSTGRES);
    }
}
```

If a `DataSourceParameterTest` already exists, add these four test methods to it instead of creating a new file, keeping any existing tests intact.

- [ ] **Step 2: Run test to verify it fails to compile**

Run: `./gradlew -p gradle-plugin :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.DataSourceParameterTest"`
Expected: FAIL — `cannot find symbol: method getDatabaseKind()`

- [ ] **Step 3: Replace `isMySQL()`/`isH2()` with `getDatabaseKind()`**

In `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/DataSourceParameter.java`, replace the `isMySQL()`/`isH2()` methods (lines 83–89) with:

```java
    public DatabaseKind getDatabaseKind() {
        return DatabaseKind.fromDriverAndUrl(driverClassName, jdbcUrl);
    }
```

Add the import:

```java
import org.ethelred.kiwiproc.processorconfig.DatabaseKind;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew -p gradle-plugin :maven-plugin:test --tests "org.ethelred.kiwiproc.maven.DataSourceParameterTest"`
Expected: PASS, all four cases green.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/DataSourceParameter.java \
        gradle-plugin/maven-plugin/src/test/java/org/ethelred/kiwiproc/maven/DataSourceParameterTest.java
git commit -m "feat: DataSourceParameter.getDatabaseKind() replaces isMySQL/isH2

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 7: Switch `KiwiProcMojo` to use `getDatabaseKind()`

**Files:**
- Modify: `gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java`

- [ ] **Step 1: Replace `toDataSourceConfig` and `externalDataSourceConfig`**

Replace `toDataSourceConfig` (lines 100–129) with:

```java
    private DataSourceConfig toDataSourceConfig(DataSourceParameter dataSource) {
        if (dataSource.isExternal()) {
            return externalDataSourceConfig(dataSource);
        }
        var liquibaseFile = requireLiquibaseChangelog(dataSource);
        return switch (dataSource.getDatabaseKind()) {
            case MYSQL -> {
                var connectionInfo = EmbeddedMySQLManager.getInstance().getPreparedDatabase(liquibaseFile);
                yield new DataSourceConfig(
                        dataSource.getName(),
                        connectionInfo.url(),
                        null,
                        connectionInfo.username(),
                        connectionInfo.password(),
                        DatabaseKind.MYSQL.driverClassName());
            }
            case H2 -> {
                var connectionInfo = EmbeddedH2Manager.getInstance().getPreparedDatabase(liquibaseFile);
                yield new DataSourceConfig(
                        dataSource.getName(),
                        connectionInfo.url(),
                        null,
                        null,
                        null,
                        DatabaseKind.H2.driverClassName());
            }
            case POSTGRES -> {
                var connectionInfo = EmbeddedPostgresManager.getInstance().getPreparedDatabase(liquibaseFile);
                yield new DataSourceConfig(
                        dataSource.getName(),
                        "jdbc:postgresql://localhost:%d/%s?user=%s"
                                .formatted(
                                        connectionInfo.getPort(),
                                        connectionInfo.getDbName(),
                                        connectionInfo.getUser()),
                        connectionInfo.getDbName(),
                        connectionInfo.getUser(),
                        null,
                        null);
            }
        };
    }
```

Replace `externalDataSourceConfig` (lines 140–189) with:

```java
    private DataSourceConfig externalDataSourceConfig(DataSourceParameter dataSource) {
        var changelog = dataSource.getLiquibaseChangelog();
        if (changelog != null && changelog.exists()) {
            var url = dataSource.getJdbcUrl();
            DataSource ds =
                    switch (dataSource.getDatabaseKind()) {
                        case H2 -> {
                            var h2Ds = new JdbcDataSource();
                            h2Ds.setURL(url);
                            if (dataSource.getUsername() != null) {
                                h2Ds.setUser(dataSource.getUsername());
                            }
                            if (dataSource.getPassword() != null) {
                                h2Ds.setPassword(dataSource.getPassword());
                            }
                            yield h2Ds;
                        }
                        case MYSQL -> {
                            var mysqlDs = new MysqlDataSource();
                            mysqlDs.setURL(url);
                            if (dataSource.getUsername() != null) {
                                mysqlDs.setUser(dataSource.getUsername());
                            }
                            if (dataSource.getPassword() != null) {
                                mysqlDs.setPassword(dataSource.getPassword());
                            }
                            yield mysqlDs;
                        }
                        case POSTGRES -> {
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
                            yield pgDs;
                        }
                    };
            liquibaseUpdate(dataSource.getName(), changelog, ds);
        }

        return new DataSourceConfig(
                dataSource.getName(),
                dataSource.getJdbcUrl(),
                dataSource.getDatabase(),
                dataSource.getUsername(),
                dataSource.getPassword(),
                dataSource.getDriverClassName());
    }
```

- [ ] **Step 2: Add the `DatabaseKind` import**

Add alongside the existing `org.ethelred.kiwiproc.processorconfig.*` imports:

```java
import org.ethelred.kiwiproc.processorconfig.DatabaseKind;
```

- [ ] **Step 3: Run the Maven plugin's test suite**

Run: `./gradlew -p gradle-plugin :maven-plugin:test`
Expected: BUILD SUCCESSFUL — including `KiwiProcMojoTest`'s existing `driverClassName()` assertions (they check the resulting `DataSourceConfig`, which is unchanged).

- [ ] **Step 4: Run the functional test suite**

Run: `./gradlew -p gradle-plugin :maven-plugin:functionalTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply
git add gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/KiwiProcMojo.java
git commit -m "refactor: KiwiProcMojo uses DataSourceParameter.getDatabaseKind()

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Chunk 5: Full verification

### Task 8: Run the full build

**Files:** none (verification only)

- [ ] **Step 1: Run the full root build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — this exercises `querymeta`, `processor`, `test-h2`, `test-mysql`, `test-spring`, etc., which transitively depend on `DatabaseDialects` and prove Postgres/H2/MySQL dialect selection is unchanged end-to-end.

- [ ] **Step 2: Run the full gradle-plugin composite build**

Run: `./gradlew -p gradle-plugin build`
Expected: BUILD SUCCESSFUL — covers `:processorconfig:test`, `:plugin:test`, `:plugin:functionalTest`, `:maven-plugin:test`, `:maven-plugin:functionalTest`.

- [ ] **Step 3: Confirm no `isMySQL`/`isH2` string-detection logic remains outside `DatabaseKind`**

Run: `grep -rn "isMySQL\|isH2\b" --include="*.java" gradle-plugin querymeta | grep -v /build/`
Expected: no output (all call sites migrated to `DatabaseKind`/`getDatabaseKind()`).

- [ ] **Step 4: Final commit (if any cleanup was needed) and push**

```bash
git push -u origin feature/database-kind-refactor
```

(Branch name: create `feature/database-kind-refactor` from `main` before Task 1 if not already on a dedicated branch — do not commit this work on `main`.)

---

## Post-plan

Once this plan's branch is merged to `main`, proceed with `docs/superpowers/plans/2026-08-27-sqlite-support.md`, which adds `DatabaseKind.SQLITE` and the corresponding `case SQLITE ->` arms introduced here.
