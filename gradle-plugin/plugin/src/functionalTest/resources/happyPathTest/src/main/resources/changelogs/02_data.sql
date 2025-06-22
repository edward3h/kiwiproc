--liquibase formatted sql
--changeset edward3h:2

-- Country codes: https://en.wikipedia.org/wiki/List_of_ISO_3166_country_codes
INSERT INTO country(name, code) VALUES ('United States of America', 'USA');
INSERT INTO country(name, code) VALUES ('Netherlands', 'NLD');

INSERT INTO city(name, country_id)
SELECT 'Detroit', country.id FROM country WHERE country.code = 'USA';
INSERT INTO city(name, country_id)
SELECT 'Dallas', country.id FROM country WHERE country.code = 'USA';
INSERT INTO city(name, country_id)
SELECT 'Denver', country.id FROM country WHERE country.code = 'USA';
INSERT INTO city(name, country_id)
SELECT 'Rotterdam', country.id FROM country WHERE country.code = 'NLD';