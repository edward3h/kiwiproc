/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testany;

import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.jspecify.annotations.Nullable;

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

    @SqlQuery("select array[3, 1, 2]::bigint[]")
    long[] topLevelPrimitiveArray();

    @SqlQuery("select array[3, 1, 2]::bigint[]")
    Long[] topLevelBoxedArray();

    @SqlQuery("select array[3, 1, 2]::int[]")
    int[] topLevelIntArray();

    @SqlQuery("select array[3, 1, 2]::bigint[]")
    List<Long> topLevelList();

    @SqlQuery("select array['b', 'a']::text[]")
    String[] topLevelStringArray();

    record NullableArrays(long @Nullable [] ids, @Nullable List<String> names) {}

    @SqlQuery("select null::bigint[] as ids, null::text[] as names")
    NullableArrays nullArraysInRecord();

    @SqlQuery("select array[1]::bigint[] as ids, array['a']::text[] as names")
    NullableArrays presentArraysInNullableRecord();

    @SqlQuery("select null::bigint[]")
    long @Nullable [] topLevelNullableNullArray();

    @SqlQuery("select null::bigint[]")
    List<Long> topLevelNonNullableNullArray();

    @SqlQuery("select null::bigint[] as ids, 1::bigint as total")
    BoxedList nullArrayInNonNullableRecord();
}
