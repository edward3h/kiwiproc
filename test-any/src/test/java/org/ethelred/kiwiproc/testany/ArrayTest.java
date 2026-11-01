/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testany;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

public class ArrayTest {
    static ArrayDAO dao = initializeDAO();

    private static ArrayDAO initializeDAO() {
        var properties = new Properties();
        try (var in = ArrayTest.class.getResourceAsStream("/application-test.properties");
                var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (Exception e) {
            throw new AssertionError("Failed to read DB properties file", e);
        }
        var dataSource = new PGSimpleDataSource();
        dataSource.setURL(properties.getProperty("datasources.periodic-table.url"));
        return new $ArrayDAO$Provider(dataSource);
    }

    @Test
    void bigintArrayToListOfLongInRecord() {
        var result = dao.boxedList();
        assertThat(result.ids()).containsExactly(3L, 1L, 2L).inOrder();
        assertThat(result.total()).isEqualTo(3L);
    }

    @Test
    void bigintArrayToPrimitiveLongArrayInRecord() {
        var result = dao.primitiveArray();
        assertThat(result.ids()).asList().containsExactly(3L, 1L, 2L).inOrder();
        assertThat(result.total()).isEqualTo(3L);
    }

    @Test
    void bigintArrayToBoxedLongArrayInRecord() {
        var result = dao.boxedArray();
        assertThat(result.ids()).asList().containsExactly(3L, 1L, 2L).inOrder();
        assertThat(result.total()).isEqualTo(3L);
    }

    @Test
    void intArrayToPrimitiveIntArrayInRecord() {
        assertThat(dao.intArray().values()).asList().containsExactly(3, 1, 2).inOrder();
    }

    @Test
    void topLevelPrimitiveArray() {
        assertThat(dao.topLevelPrimitiveArray())
                .asList()
                .containsExactly(3L, 1L, 2L)
                .inOrder();
    }

    @Test
    void topLevelBoxedArray() {
        assertThat(dao.topLevelBoxedArray())
                .asList()
                .containsExactly(3L, 1L, 2L)
                .inOrder();
    }

    @Test
    void topLevelIntArray() {
        assertThat(dao.topLevelIntArray()).asList().containsExactly(3, 1, 2).inOrder();
    }

    @Test
    void topLevelList() {
        assertThat(dao.topLevelList()).containsExactly(3L, 1L, 2L).inOrder();
    }

    @Test
    void topLevelStringArray() {
        assertThat(dao.topLevelStringArray()).asList().containsExactly("b", "a").inOrder();
    }

    @Test
    void nullArraysInNullableRecordComponents() {
        var result = dao.nullArraysInRecord();
        assertThat(result.ids()).isNull();
        assertThat(result.names()).isNull();
    }

    @Test
    void presentArraysInNullableRecordComponents() {
        var result = dao.presentArraysInNullableRecord();
        assertThat(result.ids()).asList().containsExactly(1L);
        assertThat(result.names()).containsExactly("a");
    }

    @Test
    void nullArrayTopLevelNullable() {
        assertThat(dao.topLevelNullableNullArray()).isNull();
    }

    @Test
    void nullArrayTopLevelNonNullableThrowsClearError() {
        var e = assertThrows(NullPointerException.class, dao::topLevelNonNullableNullArray);
        assertThat(e).hasMessageThat().contains("not @Nullable");
    }

    @Test
    void nullArrayNonNullableRecordComponentThrowsClearError() {
        var e = assertThrows(NullPointerException.class, dao::nullArrayInNonNullableRecord);
        assertThat(e).hasMessageThat().contains("not @Nullable");
    }
}
