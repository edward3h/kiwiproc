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
`KiwiProcMojo`, and `DataSourceParameter` — the same driver-class/URL-prefix
cascade is duplicated a fourth time in querymeta's own `DatabaseDialects`.
There is no central enum/registry tying dialect selection to
embedded-instance selection.
Adding a fourth database (SQLite) as a fourth copy-pasted `isX()` method and
`if` branch in each of these files is the point at which that duplication
stops being tolerable, so this work includes consolidating it into a shared
`DatabaseKind` enum (see section 0) rather than repeating the pattern once
more.

## Non-goals

- No SQLite-specific SQL features beyond what standard JDBC/`RETURNING`
  already expose (e.g. JSON1 extension functions, `ON CONFLICT` clauses)
  — normal SQL kiwiproc already generates code for should work if the JDBC
  driver reports correct metadata; anything requiring bespoke parsing is a
  separate future increment (see JSON support: GH#407).
- No investigation into Liquibase extensions beyond what's needed to get
  migrations running against SQLite with `liquibase-core`. If a real gap
  is found during implementation, it becomes its own follow-up.

## Design

### 0. Shared refactor: `DatabaseKind` enum

Introduce a single `DatabaseKind` enum (`POSTGRES`, `MYSQL`, `H2`, `SQLITE`)
that centralizes driver-class/URL-prefix detection, replacing the
`isMySQL()`/`isH2()`-style methods and the repeated cascading `if` chains
across the plugin layer.

- Lives in `org.ethelred.kiwiproc.processorconfig`, module
  `gradle-plugin/processorconfig`. This module was already extracted
  specifically to hold light, framework-agnostic classes (its only
  dependencies are `jspecify` and `avaje-json`) shared across the plugin
  frontends without pulling in `:plugin`'s heavy embedded-database runtime
  deps (see GH#370) — `plugin` and `maven-plugin` depend on it directly as a
  project dependency, and `querymeta` depends on it via the published
  artifact coordinate (resolved through Gradle composite-build substitution,
  not an actual Maven Central publish), so no new module or
  dependency-graph change is needed.
- **Why a separate enum from `DatabaseDialect`, not one merged
  abstraction:** `DatabaseDialect` implementations do real work against a
  live JDBC connection (unwrapping `org.postgresql.core.BaseConnection`,
  running `pg_type`/`pg_enum` catalog queries) and so can only exist where
  the actual JDBC drivers are on the classpath — `querymeta`. `DatabaseKind`
  is pure identity (a driver-class/URL string match, no driver dependency),
  which is exactly what `maven-plugin` needs without pulling in every
  database's JDBC driver just to know "is this MySQL." `DatabaseKind` is
  what a driver-less module classifies with; `DatabaseDialect` is what a
  driver-bearing module does with that classification once it has it.
- `DatabaseKind.fromDriverAndUrl(String driverClassName, String url)` (or an
  overload taking `DataSourceConfig` directly) is the single canonical
  dispatch point: driver-class match first, URL-prefix fallback, `POSTGRES`
  as the trailing default — mirroring `DatabaseDialects.fromConfig`'s
  existing (and correct) dispatch shape.
- Consumers switch on `DatabaseKind` instead of re-deriving it from strings:
  - `querymeta`'s `DatabaseDialects.fromConfig` calls
    `DatabaseKind.fromDriverAndUrl(...)` and switches on the result to
    construct the right `DatabaseDialect`.
  - `KiwiProcConfigTask` (Gradle) switches on `DatabaseKind` in both the
    embedded-instance path (service selection) and the external-datasource
    path (`DataSource` construction), replacing the two `isMySQL()/isH2()`
    cascades there.
  - `KiwiProcMojo` + `DataSourceParameter` (Maven) — same: `isMySQL()`/
    `isH2()` booleans are replaced with a `getDatabaseKind()` accessor that
    callers switch on.
- Incidental fix: the exploration for this spec found `KiwiProcMojo`'s
  `isMySQL()` check has no URL-prefix fallback (only driver-class), while
  the Gradle task's does (`isMySQL(...) || url.startsWith("jdbc:mysql:")`)
  — an external MySQL datasource configured by URL alone currently behaves
  inconsistently between the two plugin frontends. Routing both through the
  same `DatabaseKind.fromDriverAndUrl` fixes this as a side effect.
- Net effect for the SQLite-specific work below: adding SQLite becomes one
  new enum constant plus one new `case SQLITE ->` arm added to each existing
  `DatabaseKind` switch (in `DatabaseDialects.fromConfig`, and in
  `KiwiProcConfigTask`'s and `KiwiProcMojo`'s embedded-instance and
  external-datasource paths), instead of a fourth copy-pasted `isSqlite()`
  method plus a fourth `if` branch repeated across `DatabaseDialects`,
  `KiwiProcConfigTask`, `KiwiProcMojo`, and `DataSourceParameter`.

### 1. `querymeta` — `SqliteDialect`

- Add `org.xerial:sqlite-jdbc` as a `library(...)` entry in
  `catalog.settings.gradle.kts` (this repo's version-catalog definition,
  alongside the existing `postgresql`/`mysql`/`h2` entries), then depend on
  it as `libs.sqlite` from `querymeta/build.gradle.kts`, matching how the
  other three drivers are already sourced there.
- New `SqliteDialect implements DatabaseDialect` in
  `org.ethelred.kiwiproc.meta`. `DatabaseKind.SQLITE` (section 0) matches
  `driverClassName=org.sqlite.JDBC` / JDBC URL prefix `jdbc:sqlite:`;
  `DatabaseDialects.fromConfig`'s `DatabaseKind` switch gets a
  `case SQLITE -> new SqliteDialect()` arm.
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
- Add a `case SQLITE ->` arm to `KiwiProcConfigTask`'s `DatabaseKind` switch
  (section 0), in both the embedded-instance path and the
  external-datasource path, to select the service and build the
  `DataSourceConfig`.
- Add `libs.sqlite` (the same catalog entry from section 1) as an
  `implementation` dependency of `gradle-plugin/plugin/build.gradle.kts`,
  matching how that module already separately declares `libs.h2`/`libs.mysql`
  alongside `querymeta`'s own copies (needed since `EmbeddedSQLiteService`
  opens JDBC connections directly for Liquibase, same as
  `EmbeddedH2Service`/`EmbeddedMySQLService`).

### 4. Maven plugin — mirror of the Gradle plugin

- New `EmbeddedSQLiteManager` (singleton, same fresh-temp-file approach) in
  the maven-plugin module, mirroring `EmbeddedH2Manager`/`EmbeddedMySQLManager`.
- Add a `case SQLITE ->` arm to `KiwiProcMojo`'s `DatabaseKind` switch, and
  a corresponding `SQLITE` case wherever `DataSourceParameter.getDatabaseKind()`
  is consumed (section 0).
- Add `libs.sqlite` to `gradle-plugin/maven-plugin/build.gradle.kts`, same
  rationale as the Gradle plugin above.

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

- `DatabaseKind.fromDriverAndUrl` unit tests covering all four databases by
  both driver-class and URL-prefix matching, plus the Postgres fallback —
  this is the single point the whole detection behaviour now funnels
  through, so it needs direct coverage.
- `SqliteDialect` unit-level coverage in `querymeta` alongside the existing
  `PostgresDialect`/`H2Dialect`/`MySQLDialect` tests.
- `test-sqlite` module as the end-to-end integration test (annotation
  processing + generated code + real query execution against SQLite),
  including the `RETURNING` case above.
- Gradle plugin: functional test coverage for `EmbeddedSQLiteService`
  analogous to existing embedded-service tests, if such tests exist for
  H2/MySQL.

## Open questions / risks

- The `DatabaseKind` refactor (section 0) touches the existing Postgres/H2/
  MySQL detection paths, not just new SQLite code, so it carries real
  regression risk to those three databases. Mitigate by doing the refactor
  as a distinct, separately-reviewable step/commit before adding any
  SQLite-specific code, with the full existing test suite (including
  `test-h2`/`test-mysql`) passing unchanged before SQLite work builds on
  top of it.
- Liquibase's SQLite support has known rough edges (SQLite's limited `ALTER
  TABLE`); if migrations used by `test-sqlite` hit a real limitation, the
  fix is to write migrations SQLite can execute, not to work around
  Liquibase itself.
- SQLite's dynamic typing means declared column types are advisory; if this
  surfaces real type-mapping bugs beyond what's anticipated above, they get
  fixed as found and documented as limitations.
