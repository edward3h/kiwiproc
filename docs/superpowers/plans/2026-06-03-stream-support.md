# Stream Support Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable DAO methods to return `Stream<T>` for true cursor-based streaming, where rows are fetched lazily from the database rather than buffered into a `List`.

**Architecture:** Add `StreamType` to the `KiwiType` sealed hierarchy; add runtime helpers (`ResultSetMapper`, `ResultSetStream`, `AbstractTransactionalDAO.streamCall()`); modify the annotation processor to recognise `Stream<T>` return types, generate lambda-based row mapping in the Impl, and bypass `call()` in the Provider. The user is responsible for closing the stream (which closes the statement and commits/closes the connection).

**Tech Stack:** Java 17, JavaPoet (code generation), JUnit 5, Google Truth, PostgreSQL (embedded for tests), Gradle.

---

## Chunk 1: Runtime helpers

### Task 1: Add `ResultSetMapper<T>` functional interface

**Files:**
- Create: `runtime/src/main/java/org/ethelred/kiwiproc/impl/ResultSetMapper.java`

- [ ] **Step 1: Write the file**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.impl;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultSetMapper<T> {
    T map(ResultSet rs) throws SQLException;
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :runtime:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add runtime/src/main/java/org/ethelred/kiwiproc/impl/ResultSetMapper.java
git commit -m "feat: add ResultSetMapper functional interface"
```

---

### Task 2: Add `ResultSetStream` factory class

**Files:**
- Create: `runtime/src/main/java/org/ethelred/kiwiproc/impl/ResultSetStream.java`

- [ ] **Step 1: Write the failing test**

Create `runtime/src/test/java/org/ethelred/kiwiproc/impl/ResultSetStreamTest.java`:

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.impl;

import static com.google.common.truth.Truth.assertThat;

import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ResultSetStreamTest {

    @Test
    void streamsRowsFromResultSet() throws Exception {
        var conn = DriverManager.getConnection("jdbc:h2:mem:test;MODE=PostgreSQL");
        conn.createStatement().execute("CREATE TABLE IF NOT EXISTS nums (n INT)");
        conn.createStatement().execute("INSERT INTO nums VALUES (1),(2),(3)");
        var stmt = conn.prepareStatement("SELECT n FROM nums ORDER BY n");
        var rs = stmt.executeQuery();

        List<Integer> result;
        try (var stream = ResultSetStream.of(stmt, rs, r -> r.getInt("n"))) {
            result = stream.toList();
        }

        assertThat(result).containsExactly(1, 2, 3).inOrder();
        assertThat(stmt.isClosed()).isTrue();
        conn.close();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :runtime:test --tests "org.ethelred.kiwiproc.impl.ResultSetStreamTest"`
Expected: FAIL (ResultSetStream not found)

- [ ] **Step 3: Write the implementation**

Create `runtime/src/main/java/org/ethelred/kiwiproc/impl/ResultSetStream.java`:

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;

public final class ResultSetStream {
    private ResultSetStream() {}

    public static <T> Stream<T> of(PreparedStatement statement, ResultSet rs, ResultSetMapper<T> mapper) {
        var spliterator = new Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(java.util.function.Consumer<? super T> action) {
                try {
                    if (rs.next()) {
                        action.accept(mapper.map(rs));
                        return true;
                    }
                    return false;
                } catch (SQLException e) {
                    throw new UncheckedSQLException(e);
                }
            }
        };
        return StreamSupport.stream(spliterator, false).onClose(() -> {
            try {
                statement.close();
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            }
        });
    }
}
```

Note: `statement.close()` implicitly closes the `ResultSet`.

- [ ] **Step 4: Add H2 test dependency to `runtime/build.gradle.kts`**

Read `runtime/build.gradle.kts`. The runtime module currently has no test dependencies. Add:
```kotlin
testImplementation(libs.h2)
```
(`libs.h2` is already defined in the version catalog as `com.h2database:h2:2.4.240`; do not use a bare string coordinate.)

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :runtime:test --tests "org.ethelred.kiwiproc.impl.ResultSetStreamTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add runtime/src/main/java/org/ethelred/kiwiproc/impl/ResultSetStream.java \
        runtime/src/test/java/org/ethelred/kiwiproc/impl/ResultSetStreamTest.java \
        runtime/build.gradle.kts
git commit -m "feat: add ResultSetStream for cursor-based streaming"
```

---

### Task 3: Add `streamCall()` to `AbstractTransactionalDAO`

This method manages a dedicated connection for streaming, similar to `call()` but returning a `Stream<R>` whose `onClose` handler commits and closes the connection.

**Files:**
- Modify: `runtime/src/main/java/org/ethelred/kiwiproc/impl/AbstractTransactionalDAO.java`

- [ ] **Step 1: Add the `streamCall` method**

Open `runtime/src/main/java/org/ethelred/kiwiproc/impl/AbstractTransactionalDAO.java` and add after the `run()` method:

```java
public <R> Stream<R> streamCall(DAOCallable<T, Stream<R>> callback) {
    Connection connection;
    try {
        connection = dataSource.getConnection();
        connection.setAutoCommit(false);
    } catch (SQLException e) {
        throw new UncheckedSQLException(e);
    }
    try {
        var resultStream = callback.call(withContext(() -> connection));
        return resultStream.onClose(() -> {
            try {
                connection.commit();
                connection.close();
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException re) { e.addSuppressed(re); }
                try { connection.close(); } catch (SQLException ce) { e.addSuppressed(ce); }
                throw new UncheckedSQLException(e);
            }
        });
    } catch (SQLException e) {
        try { connection.close(); } catch (SQLException ce) { e.addSuppressed(ce); }
        throw new UncheckedSQLException(e);
    } catch (RuntimeException e) {
        try { connection.close(); } catch (SQLException ce) { e.addSuppressed(ce); }
        throw e;
    }
}
```

Add the required imports at the top:
```java
import java.sql.Connection;
import java.util.stream.Stream;
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :runtime:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add runtime/src/main/java/org/ethelred/kiwiproc/impl/AbstractTransactionalDAO.java
git commit -m "feat: add streamCall() to AbstractTransactionalDAO"
```

---

## Chunk 2: Type system — `StreamType`

### Task 4: Add `StreamType` to `KiwiType` sealed hierarchy

**Files:**
- Create: `processor/src/main/java/org/ethelred/kiwiproc/processor/types/StreamType.java`
- Modify: `processor/src/main/java/org/ethelred/kiwiproc/processor/types/KiwiType.java`

- [ ] **Step 1: Create `StreamType.java`**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processor.types;

import org.ethelred.kiwiproc.processor.RowCount;

public record StreamType(KiwiType containedType) implements KiwiType {

    @Override
    public String packageName() {
        return "java.util.stream";
    }

    @Override
    public String className() {
        return "Stream";
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public RowCount expectedRows() {
        return RowCount.MANY;
    }

    @Override
    public KiwiType valueComponentType() {
        return containedType.valueComponentType();
    }

    @Override
    public String toString() {
        return "Stream<" + containedType + ">";
    }
}
```

- [ ] **Step 2: Add `StreamType` to `KiwiType` permits clause**

In `KiwiType.java`, change the `permits` clause from:
```java
public sealed interface KiwiType
        permits CollectionType,
                EnumType,
                MapType,
                ObjectType,
                OptionalType,
                PrimitiveKiwiType,
                RecordType,
                SqlArrayType,
                UnsupportedType,
                VoidType {
```
to:
```java
public sealed interface KiwiType
        permits CollectionType,
                EnumType,
                MapType,
                ObjectType,
                OptionalType,
                PrimitiveKiwiType,
                RecordType,
                SqlArrayType,
                StreamType,
                UnsupportedType,
                VoidType {
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add processor/src/main/java/org/ethelred/kiwiproc/processor/types/StreamType.java \
        processor/src/main/java/org/ethelred/kiwiproc/processor/types/KiwiType.java
git commit -m "feat: add StreamType to KiwiType sealed hierarchy"
```

---

### Task 5: Add `StreamTypeHandler` and wire into `KiwiTypeVisitor`

**Files:**
- Create: `processor/src/main/java/org/ethelred/kiwiproc/processor/types/StreamTypeHandler.java`
- Modify: `processor/src/main/java/org/ethelred/kiwiproc/processor/types/KiwiTypeVisitor.java`

- [ ] **Step 1: Write the failing test**

Open `processor/src/test/java/org/ethelred/kiwiproc/processor/TypeValidatorTest.java` and find the `testQueryReturn` parameterised test and its static `testQueryReturn()` source method. Look at the existing `Arguments` entries to understand the pattern (they use a helper such as `testCase(KiwiType returnType, boolean expectedResult, String message, ColumnMetaData... cols)`).

Add these two entries to the `testQueryReturn()` stream using the `testCase(...)` helper that is already used by the other entries in that method:

```java
testCase(new StreamType(ofClass(Integer.class, false)), true, null, col(false, JDBCType.INTEGER)),
testCase(new StreamType(ofClass(String.class, false)),  true, null, col(false, JDBCType.VARCHAR)),
```

Read the existing `testQueryReturn()` entries first to confirm the exact `testCase(...)` signature and import. The helper signature is typically `testCase(KiwiType returnType, boolean expectedResult, @Nullable String message, ColumnMetaData... cols)`. Use that form exactly.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :processor:test --tests "org.ethelred.kiwiproc.processor.TypeValidatorTest"`
Expected: FAIL (StreamType not yet handled → UnsupportedType instead)

- [ ] **Step 3: Create `StreamTypeHandler.java`**

```java
/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processor.types;

import java.util.stream.Stream;
import javax.lang.model.type.DeclaredType;

public class StreamTypeHandler extends DeclaredTypeHandler {

    StreamTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    @Override
    public boolean test(DeclaredType t) {
        return isSameType(t, Stream.class);
    }

    @Override
    public KiwiType apply(DeclaredType t) {
        var containedType = t.getTypeArguments().get(0);
        var containedKiwiType = visit(containedType);
        if (containedKiwiType instanceof RecordType || containedKiwiType.isSimple()) {
            return new StreamType(containedKiwiType.withIsNullable(false));
        }
        return KiwiType.unsupported("Stream element type must be a simple type or record, got: " + containedKiwiType);
    }
}
```

- [ ] **Step 4: Add `StreamTypeHandler` to `KiwiTypeVisitor`**

In `KiwiTypeVisitor.java`, add `new StreamTypeHandler(this, utils)` to the `declaredTypeHandlers` list. Add it before `new RecordTypeHandler(this, utils)` so it takes priority over generic record detection:

```java
declaredTypeHandlers = List.of(
        new BoxedTypeHandler(this, utils),
        new EnumTypeHandler(this, utils),
        new ObjectTypeHandler(this, utils),
        collectionTypeHandler,
        new MapTypeHandler(this, utils),
        new StreamTypeHandler(this, utils),
        new RecordTypeHandler(this, utils),
        new OptionalTypeHandler(this, utils));
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :processor:test --tests "org.ethelred.kiwiproc.processor.TypeValidatorTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add processor/src/main/java/org/ethelred/kiwiproc/processor/types/StreamTypeHandler.java \
        processor/src/main/java/org/ethelred/kiwiproc/processor/types/KiwiTypeVisitor.java \
        processor/src/test/java/org/ethelred/kiwiproc/processor/TypeValidatorTest.java
git commit -m "feat: add StreamTypeHandler to recognise Stream<T> return types"
```

---

### Task 6: Handle `StreamType` in `TypeValidator`

**Files:**
- Modify: `processor/src/main/java/org/ethelred/kiwiproc/processor/TypeValidator.java`

- [ ] **Step 1: Add `StreamType` to `validateGeneral()`**

In `TypeValidator.java`, find the `validateGeneral(KiwiType type)` private method. After the existing `CollectionType` check, add:

```java
if (type instanceof StreamType st) {
    var contained = st.containedType();
    return ((contained instanceof RecordType) || (contained.isSimple())) && validateGeneral(contained);
}
```

- [ ] **Step 2: Add `StreamType` to `validateReturn()`**

In `TypeValidator.java`, in the `validateReturn()` method, add handling for `StreamType` BEFORE the `CollectionType` block (around line 205). Insert:

```java
if (returnType instanceof StreamType streamType) {
    debug("Return type Stream<%s>".formatted(streamType.containedType()));
    return validateReturn(context.withAsParameter(true), streamType.containedType(), mappedColumn);
}
```

- [ ] **Step 3: Run the full processor tests**

Run: `./gradlew :processor:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add processor/src/main/java/org/ethelred/kiwiproc/processor/TypeValidator.java
git commit -m "feat: handle StreamType in TypeValidator"
```

---

## Chunk 3: Code generation

### Task 7: Update `KiwiTypeConverter` and `RuntimeTypes`

**Files:**
- Modify: `processor/src/main/java/org/ethelred/kiwiproc/processor/generator/KiwiTypeConverter.java`
- Modify: `processor/src/main/java/org/ethelred/kiwiproc/processor/generator/RuntimeTypes.java`

- [ ] **Step 1: Add `RESULT_SET_STREAM` to `RuntimeTypes`**

In `RuntimeTypes.java`, add:
```java
public static final ClassName RESULT_SET_STREAM =
        ClassName.get("org.ethelred.kiwiproc.impl", "ResultSetStream");
```

- [ ] **Step 2: Handle `StreamType` in `KiwiTypeConverter.fromKiwiType()`**

In `KiwiTypeConverter.java`, in the `fromKiwiType(KiwiType kiwiType, boolean asParameter)` method, add a branch after the `CollectionType` check:

```java
} else if (kiwiType instanceof StreamType streamType) {
    return ParameterizedTypeName.get(
            ClassName.get("java.util.stream", "Stream"),
            fromKiwiType(streamType.containedType(), asParameter));
}
```

Add the import for `StreamType` at the top of the file.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add processor/src/main/java/org/ethelred/kiwiproc/processor/generator/KiwiTypeConverter.java \
        processor/src/main/java/org/ethelred/kiwiproc/processor/generator/RuntimeTypes.java
git commit -m "feat: add StreamType to KiwiTypeConverter and RuntimeTypes"
```

---

### Task 8: Generate stream-body in `InstanceGenerator`

This is the most complex task. The generated Impl code for a `Stream<T>` return uses a lambda to map each ResultSet row, instead of a while-loop that fills a list.

**Files:**
- Modify: `processor/src/main/java/org/ethelred/kiwiproc/processor/generator/InstanceGenerator.java`

#### Generated code shape (for `Stream<PetType> streamPetTypes()` where `PetType` is `record PetType(int id, String name)`)

```java
@Override
public Stream<PetType> streamPetTypes() {
    var connection = context.getConnection();
    try {
        var statement = connection.prepareStatement("SELECT id, name FROM types");
        try {
            var resultSet = statement.executeQuery();
            return ResultSetStream.of(statement, resultSet, rs -> {
                var idRaw = rs.getInt("id");
                int id = idRaw;
                var nameRaw = rs.getString("name");
                String name = nameRaw;
                var simpleValue = new PetType(
                    id,
                    name
                );
                return simpleValue;
            });
        } catch (SQLException e) {
            try { statement.close(); } catch (SQLException ce) { e.addSuppressed(ce); }
            throw e;
        }
    } catch (SQLException e) {
        throw new UncheckedSQLException(e);
    }
}
```

Note the inner `try/catch` wrapping `executeQuery()`: if the query fails, the statement is closed before rethrowing (with suppressed handling), preventing a resource leak. The outer catch converts `SQLException` to `UncheckedSQLException`.

Key differences from the non-stream Impl method:
1. `try (var statement = ...)` → plain `try` + `var statement = ...` (no auto-close; stream keeps statement open)
2. `var rs = statement.executeQuery()` → `var resultSet = statement.executeQuery()` (avoids name clash with lambda param)
3. No while-loop; instead a `ResultSetStream.of(statement, resultSet, rs -> { ... })` lambda
4. The column-reading code inside the lambda uses `rs` (the lambda parameter)
5. Lambda body `return`s the constructed value

- [ ] **Step 1: Modify `buildMethod()` to use a plain `try` for stream returns**

In `buildMethod()`, replace the current block that opens the try and sets fetchSize:
```java
methodSpecBuilder.addStatement("var connection = context.getConnection()");
methodSpecBuilder.beginControlFlow(
        "try (var statement = connection.prepareStatement($S))",
        methodInfo.parsedSql().parsedSql());
if (methodInfo.fetchSize() != Integer.MIN_VALUE) {
    methodSpecBuilder.addStatement("statement.setFetchSize($L)", methodInfo.fetchSize());
}
```
with a branch (note `fetchSize` is preserved in both paths):

```java
boolean isStreamReturn = methodInfo.signature().returnType() instanceof StreamType;
methodSpecBuilder.addStatement("var connection = context.getConnection()");
if (isStreamReturn) {
    methodSpecBuilder.beginControlFlow("try");
    methodSpecBuilder.addStatement(
            "var statement = connection.prepareStatement($S)",
            methodInfo.parsedSql().parsedSql());
} else {
    methodSpecBuilder.beginControlFlow(
            "try (var statement = connection.prepareStatement($S))",
            methodInfo.parsedSql().parsedSql());
}
if (methodInfo.fetchSize() != Integer.MIN_VALUE) {
    methodSpecBuilder.addStatement("statement.setFetchSize($L)", methodInfo.fetchSize());
}
```

And update the switch:
```java
methodSpecBuilder.addCode(
        switch (methodInfo.kind()) {
            case QUERY -> isStreamReturn
                    ? methodBodyForStreamQuery(methodInfo)
                    : methodBodyForQuery(methodInfo);
            case UPDATE -> methodBodyForUpdate(methodInfo);
            case BATCH -> methodBodyForBatch(methodInfo);
            case DEFAULT -> throw new IllegalArgumentException();
        });
```

Leave the existing `nextControlFlow` / `endControlFlow` block that follows the switch **unchanged**:
```java
methodSpecBuilder
        .nextControlFlow("catch ($T e)", SQLException.class)
        .addStatement("throw new $T(e)", UNCHECKED_SQL_EXCEPTION)
        .endControlFlow();
```
This outer catch is still needed in the stream case — it catches any `SQLException` thrown by `connection.prepareStatement()` or by the inner `try` rethrow.

- [ ] **Step 2: Add `methodBodyForStreamQuery()`**

Add this private method to `InstanceGenerator` (place it near `methodBodyForQuery`):

```java
private CodeBlock methodBodyForStreamQuery(DAOMethodInfo methodInfo) {
    var builder = builderWithParameters(methodInfo.parameterMapping());
    var streamType = (StreamType) methodInfo.signature().returnType();
    var elementType = streamType.containedType();

    // Inner try so that if executeQuery() throws, the statement is closed before propagating
    builder.beginControlFlow("try");
    builder.addStatement("var resultSet = statement.executeQuery()");

    // Open lambda — note: outer RS is "resultSet", lambda param is "rs"
    builder.add("return $T.of(statement, resultSet, rs -> {\n", RESULT_SET_STREAM);
    builder.indent();

    var columns = methodInfo.columns();
    if (columns.isEmpty()) {
        throw new IllegalStateException("Expected columns for stream query");
    }

    // Column reading — uses "rs" (the lambda parameter)
    for (DAOResultColumn column : columns) {
        String rawName = patchName(columnName(column) + "Raw");
        String accessorSuffix = column.sqlTypeMapping().accessorSuffix();
        TypeName typeName = kiwiTypeConverter.fromKiwiType(column.sqlTypeMapping().kiwiType());
        if ("Object".equals(accessorSuffix)) {
            builder.addStatement("$1T $2L = rs.getObject($3S, $1T.class)", typeName, rawName, column.name());
        } else {
            builder.addStatement("$T $L = rs.get$L($S)", typeName, rawName, accessorSuffix, column.name());
        }
        if (column.sqlTypeMapping().isNullable()) {
            builder.beginControlFlow("if (rs.wasNull())")
                    .addStatement("$L = null", rawName)
                    .endControlFlow();
        }
        var varName = patchName(columnName(column));
        buildConversion(builder, methodInfo, column.conversion(), column.asTypeMapping().target(), varName, rawName, true);
    }

    // Construct record if element type is not simple; otherwise return raw column value
    if (!elementType.isSimple()) {
        var resultVar = patchName(prefixName(ResultPart.SIMPLE, "Value"));
        var params = columns.stream()
                .filter(column -> ResultPart.SIMPLE.equals(column.resultPart()))
                .map(p -> CodeBlock.of("$L", patchedNames.get(columnName(p))))
                .collect(CodeBlock.joining(",\n"));
        params = CodeBlock.builder().indent().add(params).unindent().build();
        builder.add("""
                var $L = new $T(
                $L
                );
                """, resultVar, kiwiTypeConverter.fromKiwiType(elementType), params);
        builder.addStatement("return $L", resultVar);
    } else {
        var firstSimple = columns.stream()
                .filter(c -> c.resultPart() == ResultPart.SIMPLE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No SIMPLE column for stream query"));
        String patchedValueVariable = patchedNames.get(columnName(firstSimple));
        builder.addStatement("return $L", patchedValueVariable);
    }

    builder.unindent();
    builder.add("});\n");

    // Close the inner try — if executeQuery() threw, close statement before propagating
    // statement.close() itself may throw; suppress that exception so the original is not lost
    builder.nextControlFlow("catch ($T e)", SQLException.class)
            .beginControlFlow("try")
            .addStatement("statement.close()")
            .nextControlFlow("catch ($T ce)", SQLException.class)
            .addStatement("e.addSuppressed(ce)")
            .endControlFlow()
            .addStatement("throw e")
            .endControlFlow();

    return builder.build();
}
```

Add `import org.ethelred.kiwiproc.processor.types.StreamType;` and `import static org.ethelred.kiwiproc.processor.generator.RuntimeTypes.RESULT_SET_STREAM;` at the top of `InstanceGenerator.java`.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run processor unit tests**

Run: `./gradlew :processor:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add processor/src/main/java/org/ethelred/kiwiproc/processor/generator/InstanceGenerator.java
git commit -m "feat: generate stream body in InstanceGenerator"
```

---

### Task 9: Update `ProviderGenerator` to use `streamCall()`

For `Stream<T>`-returning methods, the Provider must bypass `call()` and use `streamCall()` instead.

**Files:**
- Modify: `processor/src/main/java/org/ethelred/kiwiproc/processor/generator/ProviderGenerator.java`

#### Generated Provider code shape

```java
@Override
public Stream<PetType> streamPetTypes() {
    return streamCall(dao -> dao.streamPetTypes());
}
```

- [ ] **Step 1: Modify `buildMethod()` in `ProviderGenerator`**

In `ProviderGenerator.java`, find the `buildMethod()` method and update the if/else to add a `StreamType` branch:

```java
private MethodSpec buildMethod(DAOMethodInfo methodInfo) {
    var builder = MethodSpec.overriding(methodInfo.methodElement());
    var signature = methodInfo.signature();
    var params = String.join(", ", signature.paramNames());
    if (signature.returnType() instanceof StreamType) {
        builder.addStatement("return streamCall(dao -> dao.$L($L))", signature.methodName(), params);
    } else if (signature.returnType() instanceof VoidType) {
        builder.addStatement("run(dao -> dao.$L($L))", signature.methodName(), params);
    } else {
        builder.addStatement("return call(dao -> dao.$L($L))", signature.methodName(), params);
    }
    return builder.build();
}
```

Add the import: `import org.ethelred.kiwiproc.processor.types.StreamType;`

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all processor tests**

Run: `./gradlew :processor:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add processor/src/main/java/org/ethelred/kiwiproc/processor/generator/ProviderGenerator.java
git commit -m "feat: use streamCall() in ProviderGenerator for Stream return types"
```

---

## Chunk 4: Integration test

### Task 10: Add Stream method to the Spring integration test DAO

**Files:**
- Modify: `test-spring/src/main/java/org/ethelred/kiwiproc/testspring/PetClinicDAO.java`
- Modify: `test-spring/src/test/java/org/ethelred/kiwiproc/testspring/PetClinicTest.java`

- [ ] **Step 1: Write the failing test**

In `PetClinicTest.java`, add:

```java
@Test
void canStreamPetTypes() {
    List<PetType> result;
    try (var stream = dao.streamPetTypes()) {
        result = stream.toList();
    }
    assertThat(result).isNotEmpty();
    assertThat(result.stream().map(PetType::name).toList())
            .containsAtLeast("cat", "dog");
}
```

- [ ] **Step 2: Add `streamPetTypes()` to `PetClinicDAO`**

In `PetClinicDAO.java`, add (with appropriate imports for `Stream`):

```java
@SqlQuery("SELECT id, name FROM types ORDER BY id")
Stream<PetType> streamPetTypes();
```

Add `import java.util.stream.Stream;` at the top.

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :test-spring:test --tests "org.ethelred.kiwiproc.testspring.PetClinicTest.canStreamPetTypes"`
Expected: FAIL (method not yet recognised by processor — compile error expected)

- [ ] **Step 4: Run full build to see the complete picture**

Run: `./gradlew :test-spring:build`

If there are compile errors in the generated code, read the generated sources (typically under `test-spring/build/generated/sources/`) and debug. Common issues:
- Missing import for `StreamType` in some processor class
- `patchedNames` lookup returning null (check `columnName()` → `prefixName()` → `patchName()` call chain)
- Lambda throws exception mismatch

- [ ] **Step 5: Run the integration test**

Run: `./gradlew :test-spring:test --tests "org.ethelred.kiwiproc.testspring.PetClinicTest.canStreamPetTypes"`
Expected: PASS

- [ ] **Step 6: Run the full test suite to check for regressions**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 7: Commit**

```bash
git add test-spring/src/main/java/org/ethelred/kiwiproc/testspring/PetClinicDAO.java \
        test-spring/src/test/java/org/ethelred/kiwiproc/testspring/PetClinicTest.java
git commit -m "test: add Stream<T> integration test to PetClinicTest"
```

---

## Chunk 5: Processor unit test and error case

### Task 11: Add `ProcessorTest` for Stream return type (compile-time test)

**Files:**
- Modify: `processor/src/test/java/org/ethelred/kiwiproc/processor/ProcessorTest.java`

- [ ] **Step 1: Add a test that verifies a DAO with `Stream<T>` compiles successfully**

In `ProcessorTest.java`, add a new test method near the existing success-path tests. Follow the existing pattern of `configuredCompiler()` + `JavaFileObjects.forSourceString(...)` + `assertThat(compilation).succeeded()`:

```java
@Test
void streamQueryCompiles() throws Exception {
    var compilation = configuredCompiler()
            .compile(JavaFileObjects.forSourceString(
                    "com.example.StreamDAO",
                    // language=java
                    """
                    package com.example;

                    import java.util.stream.Stream;
                    import org.ethelred.kiwiproc.annotation.DAO;
                    import org.ethelred.kiwiproc.annotation.SqlQuery;

                    @DAO
                    public interface StreamDAO {
                        @SqlQuery("SELECT id, name FROM types ORDER BY id")
                        Stream<com.example.PetType> streamTypes();
                    }
                    """),
            JavaFileObjects.forSourceString(
                    "com.example.PetType",
                    """
                    package com.example;
                    public record PetType(int id, String name) {}
                    """));
    assertThat(compilation).succeeded();
}
```

Check existing `ProcessorTest` methods for the exact pattern used (config file, database connection, etc.) and follow it precisely.

- [ ] **Step 2: Run to verify it passes**

Run: `./gradlew :processor:test --tests "org.ethelred.kiwiproc.processor.ProcessorTest.streamQueryCompiles"`
Expected: PASS

- [ ] **Step 3: Add a test that verifies an invalid stream element type produces a compile error**

Add:

```java
@Test
void streamOfUnsupportedTypeFailsCompilation() throws Exception {
    var compilation = configuredCompiler()
            .compile(JavaFileObjects.forSourceString(
                    "com.example.BadStreamDAO",
                    """
                    package com.example;

                    import java.util.stream.Stream;
                    import java.util.List;
                    import org.ethelred.kiwiproc.annotation.DAO;
                    import org.ethelred.kiwiproc.annotation.SqlQuery;

                    @DAO
                    public interface BadStreamDAO {
                        @SqlQuery("SELECT id, name FROM types")
                        Stream<List<String>> streamTypes();
                    }
                    """));
    assertThat(compilation).hadErrorContaining("Stream element type must be");
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :processor:test --tests "org.ethelred.kiwiproc.processor.ProcessorTest.streamOfUnsupportedTypeFailsCompilation"`
Expected: PASS

- [ ] **Step 5: Run all processor tests**

Run: `./gradlew :processor:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add processor/src/test/java/org/ethelred/kiwiproc/processor/ProcessorTest.java
git commit -m "test: add processor compile tests for Stream<T> return type"
```

---

## Chunk 6: Final verification and PR

### Task 12: Full build and PR

- [ ] **Step 1: Run the complete build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass

If Spotless fails:
```bash
./gradlew spotlessApply
./gradlew build
```

- [ ] **Step 2: Create the PR**

```bash
gh pr create \
  --title "feat: support Stream<T> return type for cursor-based streaming (#77)" \
  --body "Closes #77

## Summary
- Add \`StreamType\` to \`KiwiType\` sealed hierarchy
- Add \`StreamTypeHandler\` to recognise \`Stream<T>\` in DAO return types
- Add \`ResultSetMapper\` functional interface and \`ResultSetStream\` factory to runtime
- Add \`AbstractTransactionalDAO.streamCall()\` for connection lifecycle management
- Generate lambda-based row mapping in Impl; use \`streamCall()\` in Provider
- Integration test in \`test-spring\` module

## Usage
\`\`\`java
@SqlQuery(\"SELECT id, name FROM types ORDER BY id\")
Stream<PetType> streamPetTypes();
\`\`\`

\`\`\`java
try (var stream = petClinicDAOProvider.streamPetTypes()) {
    stream.forEach(pet -> process(pet));
}
\`\`\`

The user must close the stream (use try-with-resources). Closing the stream commits the transaction and closes the connection. Use \`@SqlQuery(fetchSize = N)\` to enable server-side cursor batching in PostgreSQL."
```
