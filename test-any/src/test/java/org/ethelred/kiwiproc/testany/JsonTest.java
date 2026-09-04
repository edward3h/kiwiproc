/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testany;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

public class JsonTest {
    static JsonDAO dao = initializeDAO();

    private static JsonDAO initializeDAO() {
        var propertiesUrl = JsonTest.class.getResource("/application-test.properties");
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
        dataSource.setURL(properties.getProperty("datasources.enum.url"));

        return new $JsonDAO$Provider(dataSource);
    }

    @Test
    void insertAndFindByIdReturnsRawJsonText() {
        dao.insert(1, "{\"a\":1}");

        var result = dao.findById(1);
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1);
        assertThat(result.data()).isEqualTo("{\"a\": 1}"); // Postgres normalizes jsonb text on storage
    }

    @Test
    void findByIdReturnsNullForUnknownId() {
        assertThat(dao.findById(999)).isNull();
    }

    @Test
    void listAllContainsInsertedRow() {
        dao.insert(2, "{\"b\":2}");
        var all = dao.listAll();
        assertThat(all.stream().anyMatch(r -> r.id() == 2)).isTrue();
    }
}
