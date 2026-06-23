# Embedded MySQL support for kiwiproc-maven-plugin

Resolves GH#371.

## Problem

`kiwiproc-maven-plugin` currently supports embedded PostgreSQL (default) and embedded H2
(`driverClassName=org.h2.Driver`), but throws `IllegalArgumentException` for any datasource with
`driverClassName=com.mysql.cj.jdbc.Driver` (`KiwiProcMojo.toDataSourceConfig`, lines 103-107):

```
"kiwiproc-maven-plugin: embedded MySQL is not yet supported ... Use an external datasource with
jdbcUrl instead."
```

The Gradle plugin already supports embedded MySQL via `EmbeddedMySQLService`
(`gradle-plugin/plugin/src/main/java/.../EmbeddedMySQLService.java`), using TestContainers
(`mysql:8.4`). This work brings the Maven plugin to parity.

While reading the existing code, a second, related bug was found: `externalDataSourceConfig()`
only branches on `isH2()` vs. "everything else", so an *external* MySQL datasource with a
`liquibaseChangelog` would be wrapped in a `PGSimpleDataSource` and fail. This is untested today
(the existing `passesThroughExternalDataSourceUnchanged` test has no changelog). Fixed as part of
this work since it's the same code path being touched.

## Approach

Mirror the Maven plugin's existing process-wide-singleton pattern
(`EmbeddedH2Manager`/`EmbeddedPostgresManager`), using `EmbeddedMySQLService`'s algorithm
(container setup, numbered-database creation, Liquibase wiring) as the reference implementation —
it's already proven working in the Gradle plugin.

### New class: `EmbeddedMySQLManager`

`gradle-plugin/maven-plugin/src/main/java/org/ethelred/kiwiproc/maven/EmbeddedMySQLManager.java`,
following `EmbeddedH2Manager`'s shape (private constructor, static `getInstance()`, no
`AutoCloseable` — the `mvn` process ending is the only lifecycle this needs, same as the existing
managers):

- `private synchronized MySQLContainer<?> getContainer()` — lazily creates and starts
  `new MySQLContainer<>("mysql:8.4").withEnv("MYSQL_ROOT_HOST", "%")`.
- `record MySQLConnectionInfo(String url, String username, String password)`.
- `public synchronized MySQLConnectionInfo getPreparedDatabase(File liquibaseChangelog)` — same
  logic as `EmbeddedMySQLService.getPreparedDatabase`: numbered `kiwi_N` database via a root
  connection (`CREATE DATABASE` + `GRANT ALL PRIVILEGES`), build the connection URL
  (`allowPublicKeyRetrieval=true&useSSL=false&user=...&password=...`), apply the Liquibase
  changelog via `MysqlDataSource` + `JdbcConnection`, return connection info.

No Liquibase synchronization concerns beyond what's already `synchronized` on the manager method
(same as `EmbeddedH2Manager`/`EmbeddedPostgresManager`).

### `KiwiProcMojo` changes

- `toDataSourceConfig()`: replace the `isMySQL()` throw with a branch that calls
  `EmbeddedMySQLManager.getInstance().getPreparedDatabase(liquibaseFile)` and returns
  `new DataSourceConfig(dataSource.getName(), info.url(), null, info.username(), info.password(),
  "com.mysql.cj.jdbc.Driver")`. Must be checked before the H2 check (same position the throw is in
  today) since both are mutually exclusive driver-class checks.
- `externalDataSourceConfig()`: add a MySQL branch alongside the existing H2/else-Postgres
  branches — build a `MysqlDataSource`, `setURL`, optionally `setUser`/`setPassword`, use it for
  the optional Liquibase update. The returned `DataSourceConfig` is unaffected (already passes
  through `jdbcUrl`/`database`/`username`/`password`/`driverClassName` as given).

### Dependencies

`gradle-plugin/maven-plugin/build.gradle.kts`: add `implementation(libs.mysql)` and
`implementation(libs.testcontainers.mysql)` — both already in `catalog.settings.gradle.kts`
(`com.mysql:mysql-connector-j:9.7.0`, `org.testcontainers:mysql:1.21.4`) and already used the same
way by `:plugin` and `:test-mysql`. No new CI/Docker requirement — `ubuntu-latest` runners already
have Docker, and `:test-mysql` already requires it unconditionally in the same CI workflow.

### Tests

- `EmbeddedMySQLManagerTest` (unit, mirrors `EmbeddedH2ManagerTest`): prepares a database against a
  trivial changelog, asserts the URL starts with `jdbc:mysql://` and contains the expected query
  parameters.
- `KiwiProcMojoTest`: add `generatesConfigForEmbeddedMySQL()` (mirrors
  `generatesConfigForEmbeddedH2()`) asserting the generated `config.json` datasource URL/driver
  class. Add a new external-datasource test exercising MySQL + a changelog, covering the
  `externalDataSourceConfig()` bug fix (assert the Liquibase update succeeds and the
  passed-through fields are unchanged in `config.json`).
- Functional test (maven-invoker, mirrors `default-embedded-postgres`): new fixture
  `src/functionalTest/resources/it/embedded-mysql/` (`pom.xml` + `changelog.xml`) and a new test
  method in `KiwiProcMojoInvokerTest` invoking `generate-sources` and asserting the generated
  config's datasource URL starts with `jdbc:mysql://`.

### Docs

`docs/src/docs/asciidoc/maven_plugin.adoc`:

- Replace the "not yet supported" `NOTE` (lines 194-198) with an "Embedded MySQL" section mirroring
  the existing "Embedded H2" section (lines 132-151) and the Gradle plugin's MySQL section
  (`gradle_plugin.adoc:100-132`) — example `<dataSource>` config with
  `driverClassName=com.mysql.cj.jdbc.Driver`, and a callout that this requires Docker
  (Testcontainers). Add `xref:databases.adoc#mysql-limitations[MySQL Limitations]`, mirroring the
  H2 section's `h2-limitations` xref (line 151) and the Gradle plugin's MySQL section
  (`gradle_plugin.adoc:132`) — the shared `databases.adoc` content is already build-tool-agnostic,
  so no new limitations content needs writing, just the link. The removed `NOTE` references issue
  #333 (the broader umbrella issue, not #371 itself); no replacement reference is needed since the
  limitation it described no longer exists.
- Update the `driverClassName` property table row (currently only mentions H2) to also mention the
  MySQL driver class.

## Out of scope

- Embedded MySQL version/image configurability (hardcoded `mysql:8.4`, matching the Gradle
  plugin — no existing precedent for making this configurable).
- Changes to `EmbeddedMySQLService` itself (Gradle plugin) — it's the reference implementation,
  not part of this work.
