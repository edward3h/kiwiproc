/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class NameUtilsTest {
    public static Stream<Arguments> testNameEquivalence() {
        return Stream.of(
                arguments("first", "first", true),
                arguments("first_name", "firstName", true),
                arguments("a_long_name_with_lots_of_underscores", "aLongNameWithLotsOfUnderscores", true),
                arguments("1st one", "firstOne", false));
    }

    @ParameterizedTest
    @MethodSource
    public void testNameEquivalence(String sqlNameString, String javaNameString, boolean equivalent) {
        var sqlName = new SqlName(sqlNameString);
        var javaName = new JavaName(javaNameString);
        assertEquals(equivalent, sqlName.equivalent(javaName), () -> "%s expected to %sbe equivalent to %s"
                .formatted(sqlName, equivalent ? "" : "not ", javaName));
    }
}
