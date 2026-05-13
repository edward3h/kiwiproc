/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testh2;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.h2.jdbcx.JdbcDataSource;
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
        var dataSource = new JdbcDataSource();
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
    void listAllAsCollectionReturnsProducts() {
        dao.insertProduct("Alpha", 1.00);
        dao.insertProduct("Beta", 2.00);
        var result = dao.listAllAsCollection();
        assertThat(result).hasSize(2);
    }

    @Test
    void listAllAsIterableReturnsProducts() {
        dao.insertProduct("Alpha", 1.00);
        var result = dao.listAllAsIterable();
        assertThat(result).hasSize(1);
    }

    @Test
    void listAllNamesAsArrayReturnsNames() {
        dao.insertProduct("Widget", 9.99);
        dao.insertProduct("Gadget", 19.99);
        var names = dao.listAllNamesAsArray();
        assertThat(names).asList().containsExactly("Widget", "Gadget").inOrder();
    }

    @Test
    void listAllWithFetchSizeReturnsCorrectResults() {
        dao.insertProduct("X", 1.00);
        dao.insertProduct("Y", 2.00);
        var result = dao.listAllWithFetchSize();
        assertThat(result).hasSize(2);
    }

    @Test
    void batchInsertWithCustomSizeInsertsAllRows() {
        dao.batchInsertWithSize(List.of("P1", "P2", "P3", "P4", "P5"), List.of(1.0, 2.0, 3.0, 4.0, 5.0));
        assertThat(dao.listAll()).hasSize(5);
    }
}
