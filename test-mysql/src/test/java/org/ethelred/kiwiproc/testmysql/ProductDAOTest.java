/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testmysql;

import static com.google.common.truth.Truth.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProductDAOTest {
    static ProductDAO dao = initializeDAO();

    private static ProductDAO initializeDAO() {
        var propertiesUrl = ProductDAOTest.class.getResource("/application-test.properties");
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
        var dataSource = new MysqlDataSource();
        dataSource.setURL(properties.getProperty("datasources.default.url"));
        return new $ProductDAO$Provider(dataSource);
    }

    @BeforeEach
    void clearData() {
        dao.deleteAll();
    }

    @Test
    void insertAndFindById() {
        dao.insertProduct("Widget", 9.99);
        var all = dao.listAll();
        assertThat(all).isNotEmpty();
        var last = all.get(all.size() - 1);
        var found = dao.findById(last.id());
        assertThat(found).isNotNull();
        assertThat(found.name()).isEqualTo("Widget");
    }

    @Test
    void listAllReturnsInsertedProducts() {
        dao.insertProduct("Gadget", 19.99);
        dao.insertProduct("Doohickey", 4.99);
        var all = dao.listAll();
        assertThat(all).isNotEmpty();
    }

    @Test
    void findByIdReturnsNullWhenNotFound() {
        var result = dao.findById(Integer.MAX_VALUE);
        assertThat(result).isNull();
    }

    @Test
    void deleteByIdReturnsBooleanAffectedRows() {
        dao.insertProduct("ToDelete", 5.00);
        var id = dao.listAll().get(0).id();

        var deleted = dao.deleteById(id);
        assertThat(deleted).isTrue();

        var notDeleted = dao.deleteById(id);
        assertThat(notDeleted).isFalse();
    }

    @Test
    void batchInsertReturnsCountsArray() {
        var counts = dao.batchInsertProducts(List.of("BatchA", "BatchB", "BatchC"), List.of(1.0, 2.0, 3.0));
        assertThat(counts).hasLength(3);
        assertThat(dao.listAll()).hasSize(3);
    }

    // No processor changes were needed for MySQL JSON support. On the read side, MySQL reports
    // JSON columns as LONGVARCHAR (dbType "JSON"), already covered by the pre-existing
    // LONGVARCHAR -> String mapping. On the write side, MySQL's parameter metadata is unavailable
    // for virtually all parameters (see MySQLDialect.getParameters()'s fallback to
    // DatabaseDialect.syntheticParameter()), so every parameter already falls back to the generic
    // OTHER/AssignmentConversion path and an untyped setObject() bind -- which MySQL accepts
    // against a JSON column without complaint. This is a different (and more incidental)
    // mechanism than the Types.OTHER binding Task 5 added specifically for Postgres; the
    // isJsonDbType check in DAOParameterInfo never actually fires here.
    @Test
    void insertAndReadJsonMetadataRoundTrips() {
        dao.insertProductWithMetadata("Widget", 9.99, "{\"color\":\"red\"}");
        var all = dao.listAll();
        var id = all.get(all.size() - 1).id();
        var metadata = dao.findMetadataById(id);
        // MySQL's JSON type canonicalizes stored text (adds a space after ':'); this may need
        // revisiting on a MySQL major-version bump.
        assertThat(metadata).isEqualTo("{\"color\": \"red\"}");
    }
}
