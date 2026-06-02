--liquibase formatted sql

--changeset edward3h:enum-1
CREATE TYPE animal_kind AS ENUM ('CAT', 'DOG', 'BIRD');

CREATE TABLE test_animal (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    kind animal_kind  NOT NULL,
    tag  VARCHAR(50)
);
