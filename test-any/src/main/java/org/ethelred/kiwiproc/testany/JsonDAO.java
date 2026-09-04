/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testany;

import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.jspecify.annotations.Nullable;

@DAO(dataSourceName = "enum")
public interface JsonDAO {
    record JsonRow(int id, String data) {}

    // TODO: drop the ::jsonb cast once json/jsonb parameters bind via setObject(..., Types.OTHER)
    // (GH#407, see docs/superpowers/plans/2026-09-04-json-column-support.md Task 5)
    @SqlUpdate("INSERT INTO test_json (id, data) VALUES (:id, :data::jsonb)")
    void insert(int id, String data);

    @SqlQuery("SELECT id, data FROM test_json WHERE id = :id")
    @Nullable JsonRow findById(int id);

    @SqlQuery("SELECT id, data FROM test_json ORDER BY id")
    List<JsonRow> listAll();
}
