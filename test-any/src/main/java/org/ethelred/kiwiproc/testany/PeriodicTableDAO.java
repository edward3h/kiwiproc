/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testany;

import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.jspecify.annotations.Nullable;

@DAO(dataSourceName = "periodic-table")
public interface PeriodicTableDAO {
    record ElementStub(int AtomicNumber, @Nullable String Element, @Nullable String Symbol) {}

    @SqlQuery(
            """
            select "AtomicNumber", "Element", "Symbol"
            from periodic_table
            where "Year" is null or "Year" < :year
            """)
    List<ElementStub> elementsDiscoveredBefore(int year);
}
