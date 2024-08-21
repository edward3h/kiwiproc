package org.ethelred.kiwiproc.meta;

import com.google.common.truth.Truth;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ParsedSqlQueryTest {
    public static Stream<Arguments> sampleQueries() {
        return Stream.of(
                sampleQuery("SELECT a FROM test", "SELECT a FROM test", List.of()),
                sampleQuery("SELECT a FROM test WHERE b = :btest", "SELECT a FROM test WHERE b = ?", List.of("btest")),
                sampleQuery(
                        "INSERT INTO flavour (name, calories) VALUES (:name, :calories)",
                        "INSERT INTO flavour (name, calories) VALUES (?, ?)",
                        List.of("name", "calories")));
    }

    private static Arguments sampleQuery(String rawSql, String expectedParsedSql, List<String> expectedParameterNames) {
        return Arguments.arguments(rawSql, expectedParsedSql, expectedParameterNames);
    }

    @ParameterizedTest
    @MethodSource("sampleQueries")
    public void testQueryParsing(String rawSql, String expectedParsedSql, List<String> expectedParameterNames) {
        var parsedQuery = ParsedQuery.parse(rawSql);
        Truth.assertThat(parsedQuery.parsedSql()).isEqualTo(expectedParsedSql);
        Truth.assertThat(parsedQuery.parameterNames()).isEqualTo(expectedParameterNames);
    }
}
