/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testany;

import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;

@DAO(dataSourceName = "periodic-table")
public interface ArrayDAO {
    record BoxedList(List<Long> ids, long total) {}

    record PrimitiveArray(long[] ids, long total) {}

    record BoxedArray(Long[] ids, long total) {}

    @SqlQuery("select array[3, 1, 2]::bigint[] as ids, 3::bigint as total")
    BoxedList boxedList();

    @SqlQuery("select array[3, 1, 2]::bigint[] as ids, 3::bigint as total")
    PrimitiveArray primitiveArray();

    @SqlQuery("select array[3, 1, 2]::bigint[] as ids, 3::bigint as total")
    BoxedArray boxedArray();

    record IntArray(int[] values) {}

    @SqlQuery("select array[3, 1, 2]::int[] as values")
    IntArray intArray();
}
