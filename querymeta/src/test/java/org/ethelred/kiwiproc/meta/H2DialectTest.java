/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class H2DialectTest {
    H2Dialect dialect = new H2Dialect();

    public static Stream<Arguments> normalizeColumnName() {
        return Stream.of(
                arguments("id", "id"),
                arguments("ID", "id"),
                arguments("NAME", "name"),
                arguments("ProductName", "productname"),
                arguments("already_lower", "already_lower"),
                arguments("MIXED_CASE", "mixed_case"));
    }

    @ParameterizedTest
    @MethodSource
    void normalizeColumnName(String input, String expected) {
        assertEquals(expected, dialect.normalizeColumnName(input));
    }
}
