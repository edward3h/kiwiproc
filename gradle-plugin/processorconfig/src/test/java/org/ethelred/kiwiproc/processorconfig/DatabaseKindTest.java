/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processorconfig;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DatabaseKindTest {

    public static Stream<Arguments> fromDriverAndUrl() {
        return Stream.of(
                // driver-class match wins, regardless of URL
                arguments("com.mysql.cj.jdbc.Driver", null, DatabaseKind.MYSQL),
                arguments("com.mysql.cj.jdbc.Driver", "jdbc:postgresql://x", DatabaseKind.MYSQL),
                arguments("org.h2.Driver", null, DatabaseKind.H2),
                arguments("org.h2.Driver", "jdbc:postgresql://x", DatabaseKind.H2),
                // URL-prefix fallback when driver class is null/unrecognised
                arguments(null, "jdbc:mysql://localhost/db", DatabaseKind.MYSQL),
                arguments(null, "jdbc:h2:mem:test", DatabaseKind.H2),
                arguments(null, "jdbc:postgresql://localhost/db", DatabaseKind.POSTGRES),
                // no driver, no url -> postgres default
                arguments(null, null, DatabaseKind.POSTGRES),
                // unrecognised driver, no url-prefix match -> postgres default
                arguments("org.postgresql.Driver", null, DatabaseKind.POSTGRES),
                // conflicting kinds: driver-class match wins over a contradictory URL-prefix
                arguments("org.h2.Driver", "jdbc:mysql://host/db", DatabaseKind.H2));
    }

    @ParameterizedTest
    @MethodSource
    void fromDriverAndUrl(String driverClassName, String url, DatabaseKind expected) {
        assertThat(DatabaseKind.fromDriverAndUrl(driverClassName, url)).isEqualTo(expected);
    }

    @Test
    void fromConfig_delegatesToFromDriverAndUrl() {
        var config = new DataSourceConfig("default", "jdbc:h2:mem:test", null, null, null, null);
        assertThat(DatabaseKind.fromConfig(config)).isEqualTo(DatabaseKind.H2);
    }
}
