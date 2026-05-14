/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testany;

import java.util.List;
import java.util.UUID;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.jspecify.annotations.Nullable;

@DAO(dataSourceName = "periodic-table")
public interface UuidDAO {
    record UuidRow(UUID id, String label) {}

    @SqlUpdate("INSERT INTO test_uuid (id, label) VALUES (:id, :label)")
    void insert(UUID id, String label);

    @SqlQuery("SELECT id, label FROM test_uuid WHERE id = :id")
    @Nullable UuidRow findById(UUID id);

    @SqlQuery("SELECT id, label FROM test_uuid ORDER BY label")
    List<UuidRow> listAll();
}
