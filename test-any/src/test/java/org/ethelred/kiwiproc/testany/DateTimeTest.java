/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testany;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

public class DateTimeTest {
    static DateTimeDAO dao = initializeDAO();

    private static DateTimeDAO initializeDAO() {
        var propertiesUrl = DateTimeTest.class.getResource("/application-test.properties");
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
        dataSource.setURL(properties.getProperty("datasources.datetime.url"));

        return new $DateTimeDAO$Provider(dataSource);
    }

    @Test
    void insertAndRetrieveTimestamp() {
        var before = dao.getTestTimestamps().size();
        dao.nextTimestamp();
        var after = dao.getTestTimestamps();
        assertThat(after).hasSize(before + 1);
        assertThat(after.get(after.size() - 1).createdAt()).isNotNull();
    }
}
