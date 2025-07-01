/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testany;

import java.time.LocalDateTime;
import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.ethelred.kiwiproc.api.TransactionalDAO;

@DAO(dataSourceName = "datetime")
public interface DateTimeDAO extends TransactionalDAO<DateTimeDAO> {
    record TestTz(int id, LocalDateTime createdAt) {}

    @SqlQuery("""
            SELECT id, created_at FROM test_tz
            """)
    List<TestTz> getTestTimestamps();

    @SqlUpdate("""
            INSERT INTO test_tz DEFAULT VALUES
            """)
    void nextTimestamp();
}
