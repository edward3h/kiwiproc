/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testany;

import java.time.LocalDateTime;
import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;

@DAO
public interface DateTimeDAO {
    record TestTz(int id, LocalDateTime createdAt) {}

    @SqlQuery("""
            SELECT id, created_at FROM test_tz
            """)
    List<TestTz> getTestTimezone();
}
