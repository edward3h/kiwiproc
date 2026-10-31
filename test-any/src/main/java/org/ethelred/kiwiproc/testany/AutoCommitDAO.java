/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testany;

import org.ethelred.kiwiproc.annotation.AutoCommitMode;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;

@DAO(dataSourceName = "periodic-table", autoCommit = true)
public interface AutoCommitDAO {
    @SqlQuery("""
            select "AtomicNumber"
            from periodic_table
            where "Symbol" = :symbol
            """)
    int atomicNumberOf(String symbol);

    @SqlQuery(value = """
            select "AtomicNumber"
            from periodic_table
            where "Symbol" = :symbol
            for update
            """, autoCommit = AutoCommitMode.DISABLED)
    int atomicNumberForUpdate(String symbol);
}
