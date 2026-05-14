/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testany;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

public class UuidTest {
    static UuidDAO dao = initializeDAO();

    private static UuidDAO initializeDAO() {
        var propertiesUrl = UuidTest.class.getResource("/application-test.properties");
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

        return new $UuidDAO$Provider(dataSource);
    }

    @Test
    void insertAndFindByIdReturnsCorrectRow() {
        var id = UUID.randomUUID();
        var label = "test-label-" + id;
        dao.insert(id, label);

        var result = dao.findById(id);
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.label()).isEqualTo(label);
    }

    @Test
    void findByIdReturnsNullForUnknownId() {
        var result = dao.findById(UUID.randomUUID());
        assertThat(result).isNull();
    }

    @Test
    void listAllContainsInsertedRow() {
        var id = UUID.randomUUID();
        var label = "list-test-" + id;
        dao.insert(id, label);

        var all = dao.listAll();
        assertThat(all).isNotEmpty();
        assertThat(all.stream().anyMatch(r -> r.id().equals(id))).isTrue();
    }
}
