# Maven invoker-based integration test for kiwiproc-maven-plugin

Resolves GH#372.

## Problem

`kiwiproc-maven-plugin` is currently tested only by `KiwiProcMojoTest`, which calls
`KiwiProcMojo.execute()` directly. That exercises the Mojo's core logic with real embedded
Postgres/H2, but bypasses real Maven wiring:

- Resolution of the plugin artifact and binding of the `generate` goal to the `generate-sources`
  phase.
- Plexus configuration mapping of the `<dataSources>` list-of-beans from XML into
  `List<DataSourceParameter>`.

None of this is exercised by any existing test. In particular, the `@Mojo`/`@Parameter`-driven
`plugin.xml` descriptor generation (via `org.gradlex.maven-plugin-development`) is currently only
checked for *generation*, never for *consumption* by a real Maven build — this is the gap this test
closes end-to-end.

## Approach

Use `org.apache.maven.shared:maven-invoker` (the library underlying `maven-invoker-plugin`) from a
JUnit 5 test, run by Gradle. It spawns real `mvn` subprocesses against small fixture Maven projects.
This achieves the same coverage as `maven-invoker-plugin` without needing a parent pom for it to run
inside — this repo is Gradle-built throughout.

### Test placement

A new `functionalTest` Gradle source set on `gradle-plugin/maven-plugin`, mirroring the existing
pattern on `gradle-plugin/plugin` (see `gradle-plugin/plugin/build.gradle.kts` — `functionalTest`
source set, wired into `check` via `tasks.named("check") { dependsOn(functionalTest) }`). This keeps
the slow, process-spawning, network-touching test separate from the fast `src/test` unit suite while
still running it in CI as part of `./gradlew build` / `check`.

### Artifact resolution

The invoked `mvn` build needs to resolve `kiwiproc-maven-plugin` (and its sole module dependency,
`org.ethelred.kiwiproc:processorconfig` — `:maven-plugin` does not depend on `:plugin`, that
dependency was removed in #370) as real Maven coordinates. Nothing is published to Maven Central
yet.

Rather than overriding `-Dmaven.repo.local` for the whole invoked build (which would force
re-resolving every dependency — Maven Core, Liquibase, JDBC drivers, embedded-postgres binaries —
into an empty repo on every run), only the two kiwiproc artifacts actually needed are published to
an isolated throwaway directory:

- `gradle-plugin/processorconfig` — already has `com.vanniktech.maven.publish` configured; add an
  additional `publishing { repositories { maven { ... } } }` entry pointing at
  `gradle-plugin/build/it-repo` (a `file://` URL).
- `gradle-plugin/maven-plugin` — has no publishing config yet (tracked separately under #370's
  "before first publish" note). Add a bare `maven-publish` plugin application (no
  `com.vanniktech.maven.publish`, no signing) purely so it can publish to the same throwaway
  directory for this test. This does not constitute "wiring up publishing" for Maven Central.

`gradle-plugin/plugin` is not involved at all — it's not on `:maven-plugin`'s dependency graph, so
the invoked build never needs to resolve it.

The `functionalTest` task depends on the two modules' publish-to-`it-repo` tasks. Fixture
`pom.xml`s declare `gradle-plugin/build/it-repo` as an additional `file://` `<repository>` and
`<pluginRepository>` (snapshots enabled, releases disabled). Everything else the invoked build needs
(Maven Core, plugin-api, Liquibase, JDBC drivers, embedded-postgres binaries) resolves from/caches in
the ambient `~/.m2/repository` as normal — it is never overridden or wiped.

The plugin/dependency version is not hardcoded in the fixture poms. It's passed through as a Maven
property (`kiwiproc.version`) via the invoker request, sourced from `project.version` at test
runtime, and referenced in the fixture poms as `${kiwiproc.version}`.

This relies on the `org.gradlex.maven-plugin-development` plugin's generated `plugin.xml` descriptor
(at `META-INF/maven/plugin.xml`) actually being packaged into the jar that the bare `maven-publish`
block publishes — i.e. that it's wired into `processResources`/`jar` and not a separate,
unpublished task output. This is the single load-bearing assumption for the whole approach (without
it, the invoked `mvn` build can't resolve the `generate` goal at all) and should be checked first
during implementation.

Since the invoked build resolves snapshot artifacts and the ambient `~/.m2/repository` is
deliberately left untouched (so it accumulates Maven's normal snapshot-resolution cache across
runs), the invoker request passes `-U` (force update) to bypass that cache and guarantee each test
run picks up the freshly-published artifacts from `it-repo` rather than a stale cached snapshot.

### Fixtures

Two scenarios under `gradle-plugin/maven-plugin/src/functionalTest/resources/it/`, each a minimal,
hermetic (no Docker, no external network DB) Maven project:

1. **`default-embedded-postgres/`** — a bare `pom.xml` declaring just the plugin + the `generate`
   execution, no `<configuration>` block, plus `src/main/resources/changelog.xml` (same minimal
   `widget` table changelog already used in `KiwiProcMojoTest`). Exercises the default-path branch
   (`KiwiProcMojo.effectiveDataSources()` falling back to the single `liquibaseChangelog` parameter)
   and a real embedded Postgres instance.

2. **`multiple-datasources/`** — a `pom.xml` with a `<dataSources>` list containing two entries:
   - `default`: embedded H2 (`driverClassName=org.h2.Driver`) + a changelog resource.
   - `external`: H2 with an explicit `jdbcUrl` (e.g. `jdbc:h2:mem:extdb`), no changelog — mirrors the
     existing `passesThroughExternalDataSourceUnchanged` unit test's "external, pass-through" case.

   Exercises Plexus's mapping of repeated `<dataSource>` XML elements into
   `List<DataSourceParameter>`.

Neither fixture needs `src/main/java` or a dependency on `:shared`/`:runtime`/the annotation
processor — the scope here is Maven wiring and config generation, not full DAO codegen, which is
already covered by `ProcessorTest` and `KiwiProcMojoTest`.

### Assertions per scenario

- Invoke `mvn generate-sources` (deliberately *not* `compile` or a later phase) and assert exit code
  0. Running only as far as `generate-sources` and still seeing the generated files proves the goal
  is actually bound to (at the latest) that phase, rather than silently deferred to something later
  in the default lifecycle.
- `target/kiwiproc/config.json` exists and parses (via the existing `io.avaje.jsonb` /
  `ProcessorConfig` types already used in `KiwiProcMojoTest`) to the expected datasource
  count/names/urls/driver class.
- `target/generated-test-resources/kiwiproc/application-test.properties` exists with the expected
  `datasources.<name>.url` keys.

### Verification

No new unit tests are needed for this change — the functional test itself is the deliverable.
Before considering this done:

- Run both scenarios locally and confirm they pass.
- Deliberately break the phase binding (e.g. temporarily change `defaultPhase` away from
  `GENERATE_SOURCES`) and confirm the test fails, proving it actually catches a wiring regression.
- Run the full `cd gradle-plugin && ./gradlew build` to confirm no regressions in the rest of the
  module.

The `functionalTest` task runs its scenarios sequentially (no `maxParallelForks` configured, same as
`:plugin`'s existing `functionalTest`), so the shared `it-repo` directory and any embedded-database
ports allocated per scenario aren't a concurrency risk here. Port allocation itself is the existing
`EmbeddedPostgresManager`/`EmbeddedH2Manager`'s responsibility, same as in `KiwiProcMojoTest` today —
not a new risk introduced by this test.

## Out of scope

- Embedded MySQL support (#371).
- Publishing `kiwiproc-maven-plugin` to Maven Central / resolving #370's remaining "before first
  publish" POM-content check.
- Full DAO-codegen compilation in the fixture projects.
