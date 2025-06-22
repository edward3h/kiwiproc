package org.ethelred.kiwiproc.example.quickstart;

import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.jspecify.annotations.Nullable;

// tag::body[]
@DAO // <1>
public interface CountryCityDao {
    @SqlQuery("""
            SELECT id, name, code
            FROM country
            WHERE code = :code
            """) // <2>
    @Nullable
    Country findCountryByCode(String code);

    @SqlUpdate("""
            INSERT INTO city(name, country_id)
            VALUES (:name, :country_id)
            """)
    boolean addCity(String name, int countryId);

}
// end::body[]
