package org.ethelred.kiwiproc.example.quickstart;

import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

// tag::body[]
public class CountryService {
    private final CountryCityDao dao;

    @Inject
    public CountryService(CountryCityDao dao) {
        this.dao = dao;
    }

    public @Nullable Country getCountryByCode(String code) {
        return dao.findCountryByCode(code);
    }
}
// end::body[]
