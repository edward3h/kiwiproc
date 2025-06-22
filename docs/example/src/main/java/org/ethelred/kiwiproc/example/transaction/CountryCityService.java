package org.ethelred.kiwiproc.example.transaction;

import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

public class CountryCityService {
    private final CountryCityDao dao;

    @Inject
    public CountryCityService(CountryCityDao dao) {
        this.dao = dao;
    }

    // tag::body[]
    public boolean addCityInCountry(String cityName, String countryCode) {
        return dao.call(d -> { // <1>
           Country country = d.findCountryByCode(countryCode);
           if (country != null) {
               return d.addCity(cityName, country.id());
           }
           return false;
        });
    }
    // end::body[]
}
