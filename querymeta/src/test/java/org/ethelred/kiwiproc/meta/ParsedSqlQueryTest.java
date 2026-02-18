/* (C) Edward Harman 2024 */
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
                // basic happy path
                sampleQuery("SELECT a FROM test", "SELECT a FROM test", List.of()),
                sampleQuery("SELECT a FROM test WHERE b = :btest", "SELECT a FROM test WHERE b = ?", List.of("btest")),
                sampleQuery(
                        "INSERT INTO flavour (name, calories) VALUES (:name, :calories)",
                        "INSERT INTO flavour (name, calories) VALUES (?, ?)",
                        List.of("name", "calories")),
                // parameter at start of SQL
                sampleQuery(":param = 1", "? = 1", List.of("param")),
                // parameter at end of SQL
                sampleQuery("SELECT * FROM t WHERE a = :end", "SELECT * FROM t WHERE a = ?", List.of("end")),
                // duplicate parameter names
                sampleQuery(
                        "SELECT * FROM t WHERE a = :x OR b = :x",
                        "SELECT * FROM t WHERE a = ? OR b = ?",
                        List.of("x", "x")),
                // PostgreSQL ::cast syntax should not be treated as a parameter
                sampleQuery("SELECT col::text FROM t", "SELECT col::text FROM t", List.of()),
                sampleQuery("SELECT :val::integer FROM t", "SELECT ?::integer FROM t", List.of("val")),
                // parameters inside single-quoted string literals should be ignored
                sampleQuery(
                        "SELECT * FROM t WHERE name = ':not_a_param'",
                        "SELECT * FROM t WHERE name = ':not_a_param'",
                        List.of()),
                // parameters inside single-line comments should be ignored
                sampleQuery(
                        "SELECT * FROM t -- :fake_param\nWHERE a = :real",
                        "SELECT * FROM t -- :fake_param\nWHERE a = ?",
                        List.of("real")),
                // parameters inside block comments should be ignored
                sampleQuery(
                        "SELECT * FROM t /* :not_param */ WHERE a = :real",
                        "SELECT * FROM t /* :not_param */ WHERE a = ?",
                        List.of("real")));
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
