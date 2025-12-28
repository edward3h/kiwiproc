/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testany;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

public class PeriodicTableTest {
    static PeriodicTableDAO dao = initializeDAO();

    private static PeriodicTableDAO initializeDAO() {
        var propertiesUrl = PeriodicTableTest.class.getResource("/application-test.properties");
        if (propertiesUrl == null) {
            throw new AssertionError("DB properties not found");
        }
        var properties = new Properties();
        try (var inputStream = propertiesUrl.openStream();
                var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new AssertionError("Failed to read DB properties file", e);
        }
        var dataSource = new PGSimpleDataSource();
        dataSource.setURL(properties.getProperty("datasources.periodic-table.url"));

        return new $PeriodicTableDAO$Provider(dataSource);
    }

    @Test
    void whenExactlyOneResultIsExpectedAndOneResultIsReturnedNoExceptionIsThrown() {
        var result = dao.atomicNumberQuery(2);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void whenExactlyOneResultIsExpectedAndZeroResultIsReturnedIllegalStateExceptionIsThrown() {
        var ex = Assertions.assertThrows(IllegalStateException.class, () -> {
            var r = dao.atomicNumberQuery(0);
        });
        assertThat(ex).hasMessageThat().contains("xpected exactly one row in result, but none were selected.");
    }

    @Test
    void whenExactlyOneResultIsExpectedAndMultipleResultsAreReturnedIllegalStateExceptionIsThrown() {
        var ex = Assertions.assertThrows(IllegalStateException.class, () -> {
            var r = dao.atomicNumberQuery(4);
        });
        assertThat(ex).hasMessageThat().contains("Expected exactly one row in result, but more were selected.");
    }
}
