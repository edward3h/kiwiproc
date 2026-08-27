# SQLite Support — Design

Date: 2026-08-27
Status: Approved for planning

## Goal

Support SQLite as a fully supported kiwiproc database, on par with the existing
PostgreSQL, H2, and MySQL support: query validation/introspection against a
real SQLite instance at build time, generated DAO code, embedded-database
handling in both the Gradle and Maven plugins, an integration test module, and
user-facing documentation.

## Background

Kiwiproc already has a `DatabaseDialect` abstraction
(`querymeta/src/main/java/org/ethelred/kiwiproc/meta/`) implemented for
Postgres, H2, and MySQL, dispatched via `DatabaseDialects.fromConfig` based on
`driverClassName`/JDBC URL prefix. Core metadata introspection
(`DatabaseWrapper`, `ColumnMetaData`) uses standard `java.sql.DatabaseMetaData`
/ `ParameterMetaData` and is dialect-agnostic; only a few Postgres-only
features (native enum catalog lookup, array component-type resolution) are
isolated behind dialect-specific overrides, defaulting to no-ops for H2/MySQL.

The processor's type mapping (`SqlTypeMappingRegistry`) is keyed by standard
`java.sql.JDBCType`, not Postgres OIDs, so it is largely DB-agnostic already;
dialect-specific carve-outs exist only for genuinely dialect-specific
behaviour (e.g. MySQL reporting all parameters as `JDBCType.OTHER`).

The weaker spot is the Gradle/Maven plugin layer, where each supported
database's "embedded instance for build-time introspection" is hand-rolled:
`EmbeddedPostgresService`/`EmbeddedH2Service`/`EmbeddedMySQLService` (Gradle)
and their Maven-plugin equivalents, selected via repeated
`isMySQL()`/`isH2()`-style string checks in `KiwiProcConfigTask`,
`KiwiProcPlugin`, `KiwiProcMojo`, and `DataSourceParameter` — there is no
central enum/registry tying dialect selection to embedded-instance selection.
Adding SQLite follows this existing (if not ideal) pattern rather than
introducing a new abstraction, to stay consistent with how H2 and MySQL were
added.

## Non-goals

- No refactor of the `isMySQL()/isH2()`-style branching into a shared
  enum/registry. That's a legitimate future cleanup but is out of scope here;
  this work adds `isSqlite()` following the existing pattern.
- No SQLite-specific SQL features beyond what standard JDBC/`RETURNING`
  already expose (e.g. JSON1 extension functions, `ON CONFLICT` clauses)
  — normal SQL kiwiproc already generates code for should work if the JDBC
  driver reports correct metadata; anything requiring bespoke parsing is a
  separate future increment (see JSON support: GH#407).
- No investigation into Liquibase extensions beyond what's needed to get
  migrations running against SQLite with `liquibase-core`. If a real gap
  is found during implementation, it becomes its own follow-up.

## Design

### 1. `querymeta` — `SqliteDialect`

- Add `org.xerial:sqlite-jdbc` as a dependency of `querymeta/build.gradle.kts`.
- New `SqliteDialect implements DatabaseDialect` in
  `org.ethelred.kiwiproc.meta`, registered in `DatabaseDialects.fromConfig`
  for `driverClassName=org.sqlite.JDBC` / JDBC URL prefix `jdbc:sqlite:`.
- `getParameters`: SQLite's JDBC parameter metadata is unreliable, similar to
  H2/MySQL — override with the same synthetic-parameter fallback (count `?`
  placeholders in the SQL text) those dialects already use.
- `queryEnumConstants` / `componentType`: no-op defaults, matching H2/MySQL
  (SQLite has no native enum or array column types).

### 2. `processor` — type mapping

- No code changes expected up front. SQLite's type-affinity system reports
  `java.sql.Types` reasonably close to standard JDBC for common declared
  types (`INTEGER`, `TEXT`, `REAL`, `BLOB`, `NUMERIC`), and
  `SqlTypeMappingRegistry`'s existing JDBCType-keyed table should cover them.
- Any real gaps found while building the `test-sqlite` module (see below)
  get fixed as part of this work and documented as limitations, following
  the precedent set by the H2/MySQL "Limitations" doc sections — not
  speculated up front.

### 3. Gradle plugin — embedded SQLite

- New `EmbeddedSQLiteService`: per build, create a fresh SQLite database file
  under the Gradle build directory (e.g. `build/kiwiproc/sqlite/<name>.db`),
  removing any stale file first. Run the project's Liquibase migrations
  against `jdbc:sqlite:<path>`. Unlike `EmbeddedH2Service`, no server process
  or keep-alive connection is needed — SQLite is file-based, so the processor
  opens its own connection to the same file path to introspect.
- Register the service in `KiwiProcPlugin` alongside the other three.
- Extend `KiwiProcConfigTask`'s `isMySQL()`/`isH2()`-style branching with an
  `isSqlite()` check, in both the embedded-instance path and the
  external-datasource path, to select the service and build the
  `DataSourceConfig`.
- Add `org.xerial:sqlite-jdbc` (a `libs.versions.toml` entry plus an
  `implementation` dependency) to `gradle-plugin/plugin/build.gradle.kts`,
  matching how that module already separately declares `libs.h2`/`libs.mysql`
  alongside `querymeta`'s own copies (needed since `EmbeddedSQLiteService`
  opens JDBC connections directly for Liquibase, same as
  `EmbeddedH2Service`/`EmbeddedMySQLService`).

### 4. Maven plugin — mirror of the Gradle plugin

- New `EmbeddedSQLiteManager` (singleton, same fresh-temp-file approach) in
  the maven-plugin module, mirroring `EmbeddedH2Manager`/`EmbeddedMySQLManager`.
- Extend `KiwiProcMojo` and `DataSourceParameter`'s `isMySQL()`/`isH2()`-style
  checks with `isSqlite()`.
- Add `org.xerial:sqlite-jdbc` to
  `gradle-plugin/maven-plugin/build.gradle.kts`, same rationale as the
  Gradle plugin above.

### 5. Test module — `test-sqlite`

- New top-level integration test module mirroring `test-h2`/`test-mysql`'s
  `Product`/`ProductDAO`-style schema and DAO, exercising the generated DAO
  against the embedded SQLite instance end-to-end.
- Additionally include a `RETURNING`-based insert test. This is new territory
  for the file-based/embedded-DB tier: the only existing `RETURNING` example
  in the codebase is Postgres's `PetClinicDAO.addVisit` (in `test-spring`,
  which is Postgres-only) — `databases.adoc` explicitly documents `RETURNING`
  as *unsupported* for both H2 and MySQL today. So there's no H2/MySQL
  precedent to mirror; this test specifically validates whether
  `org.xerial:sqlite-jdbc`'s metadata reporting is good enough where H2's and
  MySQL's wasn't, following the shape of the Postgres example:

  ```java
  @SqlQuery("""
          INSERT INTO visits (pet_id, visit_date, description)
          SELECT p.id as pet_id, :visit_date, :description FROM pets p WHERE p.name = :pet_name
          RETURNING id""")
  Optional<Integer> addVisit(Visit visit);
  ```

  Kiwiproc has no bespoke `RETURNING` parsing anywhere in the processor or
  querymeta — result/parameter shape is determined generically via
  `PreparedStatement.getMetaData()`/`getParameterMetaData()`
  (`DatabaseWrapper`), so this exercises whether `org.xerial:sqlite-jdbc`'s
  metadata reporting for a `RETURNING` insert is correct (SQLite itself has
  supported `RETURNING` since 3.35, which recent `sqlite-jdbc` releases
  bundle). If it turns out to be unreliable, that becomes a documented
  limitation rather than a blocker for the rest of the work. This test may
  need its own small schema addition (e.g. a second table) alongside the
  `Product`-style base schema, since `Product` alone may not naturally need
  a `RETURNING` insert.

### 6. Docs

- New `=== SQLite` section in `docs/src/docs/asciidoc/databases.adoc`,
  following the existing H2/MySQL template: feature list plus an explicit
  "Limitations" subsection covering whatever is actually found during
  implementation (expected candidates: no native arrays, no native enums,
  weak/dynamic parameter typing, type-affinity caveats).

## Testing

- `SqliteDialect` unit-level coverage in `querymeta` alongside the existing
  `PostgresDialect`/`H2Dialect`/`MySQLDialect` tests.
- `test-sqlite` module as the end-to-end integration test (annotation
  processing + generated code + real query execution against SQLite),
  including the `RETURNING` case above.
- Gradle plugin: functional test coverage for `EmbeddedSQLiteService`
  analogous to existing embedded-service tests, if such tests exist for
  H2/MySQL.

## Open questions / risks

- Liquibase's SQLite support has known rough edges (SQLite's limited `ALTER
  TABLE`); if migrations used by `test-sqlite` hit a real limitation, the
  fix is to write migrations SQLite can execute, not to work around
  Liquibase itself.
- SQLite's dynamic typing means declared column types are advisory; if this
  surfaces real type-mapping bugs beyond what's anticipated above, they get
  fixed as found and documented as limitations.
