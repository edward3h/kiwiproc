/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.impl;

import static com.google.common.truth.Truth.assertThat;

import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ResultSetStreamTest {

    @Test
    void streamsRowsFromResultSet() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:ResultSetStreamTest;MODE=PostgreSQL")) {
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS nums (n INT)");
            conn.createStatement().execute("DELETE FROM nums");
            conn.createStatement().execute("INSERT INTO nums VALUES (1),(2),(3)");
            var stmt = conn.prepareStatement("SELECT n FROM nums ORDER BY n");
            var rs = stmt.executeQuery();

            List<Integer> result;
            try (var stream = ResultSetStream.of(stmt, rs, r -> r.getInt("n"))) {
                result = stream.toList();
            }

            assertThat(result).containsExactly(1, 2, 3).inOrder();
            assertThat(stmt.isClosed()).isTrue();
        }
    }
}
