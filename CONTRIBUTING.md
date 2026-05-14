# Contributing to Kiwiproc

Contributions are welcome and appreciated. Please read this guide before opening an issue or submitting a pull request.

By participating in this project, you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## Ways to Contribute

- **Bug reports** — open a GitHub Issue using the bug report template
- **Feature requests** — open a GitHub Issue using the feature request template
- **Code contributions** — fork the repository and submit a pull request

---

## Development Prerequisites

Before you begin, ensure you have the following installed:

- **Java 17** — the build uses Gradle toolchain resolution, so the correct JDK is configured automatically. A `.tool-versions` file is provided for [asdf](https://asdf-vm.com/) or SDKMAN users.
- **Docker** — required for the Testcontainers-based integration tests in the `test-spring`, `test-micronaut`, and `test-mysql` subprojects. Tests in those modules will fail if Docker is not running.
- **Gradle Wrapper** — included in the repository; no separate Gradle installation is needed.

---

## Building and Testing

```bash
# Full build — compiles, runs all tests, and checks code formatting
./gradlew build

# Run tests for a specific module
./gradlew :processor:test
./gradlew :test-spring:test
./gradlew :test-micronaut:test
./gradlew :test-any:test
./gradlew :test-h2:test

# Run a single test class
./gradlew :processor:test --tests "org.ethelred.kiwiproc.processor.ProcessorTest"
```

> **Note:** Running `./gradlew build` for the first time registers a **git pre-commit hook** that automatically runs `./gradlew build` before every commit. This means commits are blocked if tests or formatting checks fail — fix them first, then retry.

---

## Code Style

This project uses [Spotless](https://github.com/diffplug/spotless) with [Palantir Java Format](https://github.com/palantir/palantir-java-format).

```bash
# Auto-format all source files
./gradlew spotlessApply

# Check formatting without applying changes (what CI runs)
./gradlew spotlessCheck
```

Key conventions:

- Licence header (`/* (C) Edward Harman $YEAR */`) is added automatically by Spotless — do not add it manually.
- Use [jspecify](https://jspecify.dev/) `@Nullable` and `@NonNull` annotations to express nullability.
- Tests use [JUnit 5](https://junit.org/junit5/) with [Google Truth](https://truth.dev/) assertions.

---

## Submitting a Pull Request

1. Fork the repository and create a branch from `main` using a descriptive prefix:
   - `feat/` for new features
   - `fix/` for bug fixes
   - `docs/` for documentation-only changes

2. Ensure `./gradlew build` passes locally (the pre-commit hook enforces this automatically).

3. **Documentation** — if your change affects user-facing behaviour, check whether the AsciiDoc documentation in `docs/` needs updating.

4. **Integration tests** — if your change affects query processing, type mapping, or runtime behaviour, add or update integration tests in the relevant `test-*` subproject (`test-spring`, `test-micronaut`, `test-any`, `test-h2`, `test-mysql`).

5. Open a pull request against `main` and fill in the PR template. Reference any related issue with `Closes #<number>`.

**AI-generated contributions** are acceptable, provided the change is reasonable for a human reviewer to understand and verify. Pull requests that are excessively long or complex may be asked to be split or simplified before review.

---

## Licence

Kiwiproc is licensed under the [Apache 2.0 Licence](LICENSE). By submitting a pull request, you agree that your contributions will be licensed under the same terms.
