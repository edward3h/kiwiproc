package org.ethelred.kiwiproc.example.transaction;

import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.ethelred.kiwiproc.api.TransactionalDAO;
import org.jspecify.annotations.Nullable;

// tag::body[]
@DAO
public interface CountryCityDao extends TransactionalDAO<CountryCityDao> { // <1>
    // end::body[]
    @SqlQuery("""
            SELECT id, name, code
            FROM country
            WHERE code = :code
            """)
    @Nullable
    Country findCountryByCode(String code);

    @SqlUpdate("""
            INSERT INTO city(name, country_id)
            VALUES (:name, :country_id)
            """)
    boolean addCity(String name, int countryId);

}
