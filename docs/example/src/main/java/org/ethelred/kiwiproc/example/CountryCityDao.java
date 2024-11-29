package org.ethelred.kiwiproc.example;

import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// tag::body[]
@DAO // <1>
public interface CountryCityDao {
    @SqlQuery("""
            SELECT id, name, code
            FROM country
            """) // <2>
    List<Country> findAllCountries();

    record CityDTO(int city_id, String city_name, int country_id, String country_name, String country_code){} // <3>

    @SqlQuery("""
            SELECT city.id AS city_id, city.name AS city_name, country.id AS country_id, country.name AS country_name, country.code AS country_code
            FROM city
            JOIN country on city.country_id = country.id;
            """)
    List<CityDTO> findAllCityDTO();

    default Map<Country, Set<City>> findAllCities() { // <4>
        return findAllCityDTO()
                .stream()
                .map(dto -> new City(dto.city_id, dto.city_name, new Country(dto.country_id, dto.country_name, dto.country_code)))
                .collect(Collectors.groupingBy(City::country, Collectors.toSet()));
    }
}
// end::body[]
