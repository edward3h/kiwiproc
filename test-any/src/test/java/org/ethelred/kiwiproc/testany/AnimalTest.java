/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testany;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

public class AnimalTest {
    static AnimalDAO dao = initializeDAO();

    private static AnimalDAO initializeDAO() {
        var propertiesUrl = AnimalTest.class.getResource("/application-test.properties");
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
        return new $AnimalDAO$Provider(dataSource);
    }

    @Test
    void insertAndFindByIdReturnsCorrectRow() {
        dao.insert("Whiskers", AnimalKind.CAT, AnimalTag.INDOOR);

        var all = dao.listAll();
        assertThat(all).isNotEmpty();
        var row = all.stream()
                .filter(r -> r.name().equals("Whiskers"))
                .findFirst()
                .orElseThrow();
        assertThat(row.kind()).isEqualTo(AnimalKind.CAT);
        assertThat(row.tag()).isEqualTo(AnimalTag.INDOOR);
    }

    @Test
    void insertWithNullTagAndFindById() {
        dao.insert("Rex", AnimalKind.DOG, null);

        var all = dao.listAll();
        var row = all.stream().filter(r -> r.name().equals("Rex")).findFirst().orElseThrow();
        assertThat(row.kind()).isEqualTo(AnimalKind.DOG);
        assertThat(row.tag()).isNull();
    }

    @Test
    void findByIdReturnsNullForUnknownId() {
        var result = dao.findById(Integer.MAX_VALUE);
        assertThat(result).isNull();
    }
}
