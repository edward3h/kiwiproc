/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testany;

import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.jspecify.annotations.Nullable;

@DAO(dataSourceName = "enum")
public interface AnimalDAO {
    record AnimalRow(
            int id, String name, AnimalKind kind, @Nullable AnimalTag tag) {}

    @SqlUpdate("INSERT INTO test_animal (name, kind, tag) VALUES (:name, :kind, :tag)")
    void insert(String name, AnimalKind kind, @Nullable AnimalTag tag);

    @SqlQuery("SELECT id, name, kind, tag FROM test_animal WHERE id = :id")
    @Nullable AnimalRow findById(int id);

    @SqlQuery("SELECT id, name, kind, tag FROM test_animal ORDER BY id")
    List<AnimalRow> listAll();
}
