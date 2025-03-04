package org.ethelred.kiwiproc.testany;

import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@DAO
public interface DateTimeDAO {
    record TestTz(int id, OffsetDateTime createdAt){}

    @SqlQuery("""
            SELECT id, created_at FROM test_tz
            """)
    List<TestTz> getTestTimezone();
}
