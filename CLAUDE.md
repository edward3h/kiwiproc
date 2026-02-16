# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kiwiproc is a compile-time framework for type-safe JDBC query usage (not an ORM). It generates DAO implementations from annotated Java interfaces using annotation processing. Currently PostgreSQL only.

## Build Commands

```bash
# Full build (includes tests, formatting, code generation)
./gradlew build

# Run tests for a specific module
./gradlew :processor:test
./gradlew :test-spring:test
./gradlew :test-micronaut:test
./gradlew :test-any:test

# Run a single test class
./gradlew :processor:test --tests "org.ethelred.kiwiproc.processor.ProcessorTest"

# Check/apply code formatting (Spotless with Palantir Java Format)
./gradlew spotlessCheck
./gradlew spotlessApply
```

## Module Structure

- **shared** - Public annotations (`@DAO`, `@SqlQuery`, `@SqlUpdate`, `@SqlBatch`) and the `TransactionalDAO` API
- **querymeta** - SQL parsing (`:paramName` → `?` conversion) and PostgreSQL metadata introspection
- **processor** - Annotation processor that validates queries against PostgreSQL and generates implementation code using JavaPoet
- **runtime** - Base classes for generated code (`AbstractTransactionalDAO`, `AbstractDAOInstance`, `DAOContext`)
- **gradle-plugin** - Gradle plugin (`org.ethelred.kiwiproc`) that manages embedded PostgreSQL and processor configuration
- **test-spring** / **test-micronaut** / **test-any** - Integration tests for each framework
- **docs** / **docs:example** - AsciiDoc documentation

## Architecture

### Code Generation Flow

1. Developer writes a `@DAO`-annotated interface with `@SqlQuery`/`@SqlUpdate`/`@SqlBatch` methods
2. The Gradle plugin (`KiwiProcPlugin`) starts an embedded PostgreSQL, runs Liquibase migrations, and generates a processor config JSON
3. During compilation, `KiwiProcessor` parses SQL, introspects the database for column types, validates type compatibility, then generates two classes per DAO:
   - **`$DaoName$Provider`** - extends `AbstractTransactionalDAO`, handles DI registration and transaction management, delegates method calls via `call()`/`run()`
   - **`$DaoName$Impl`** - extends `AbstractDAOInstance`, contains the actual JDBC code (PreparedStatement, ResultSet mapping)

### Type System

The processor uses a sealed `KiwiType` hierarchy (`processor/src/main/java/.../processor/types/`) to represent Java types during compilation. Key permits: `RecordType`, `CollectionType`, `OptionalType`, `MapType`, `PrimitiveKiwiType`, `ObjectType`, `SqlArrayType`, `VoidType`.

Type conversions between SQL and Java are modelled by the sealed `Conversion` interface. `CoreTypes` defines all supported Java types and assignability rules. `SqlTypeMappingRegistry` maps JDBC types to Java types.

### DI Styles

The `DependencyInjectionStyle` enum controls generated annotations: `JAKARTA` (default, `@Inject`/`@Named`/`@Singleton`) or `SPRING` (`@Autowired`/`@Qualifier`/`@Component`).

## Code Style

- **Java 17** toolchain
- **Spotless** with Palantir Java Format, import ordering, unused import removal, CleanThat
- **Licence header**: `/* (C) Edward Harman $YEAR */`
- **Testing**: JUnit 5 with Google Truth assertions
- **Nullability**: jspecify `@Nullable`/`@NonNull` annotations
- **Records**: used extensively; `@RecordBuilderFull` for builders on internal data types
