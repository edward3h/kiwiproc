/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testany;

import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.jspecify.annotations.Nullable;

@DAO(dataSourceName = "enum")
public interface JsonDAO {
    record JsonRow(int id, @Nullable String data) {}

    @SqlUpdate("INSERT INTO test_json (id, data) VALUES (:id, :data)")
    void insert(int id, String data);

    // Covers binding a null json/jsonb parameter: exercises the setNull(index, Types.OTHER)
    // branch alongside the setObject(index, value, Types.OTHER) branch insert() above covers.
    @SqlUpdate("INSERT INTO test_json (id, data) VALUES (:id, :data)")
    void insertNullable(int id, @Nullable String data);

    @SqlQuery("SELECT id, data FROM test_json WHERE id = :id")
    @Nullable JsonRow findById(int id);

    @SqlQuery("SELECT id, data FROM test_json ORDER BY id")
    List<JsonRow> listAll();
}
