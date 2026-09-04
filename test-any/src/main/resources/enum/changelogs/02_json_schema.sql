--liquibase formatted sql

--changeset kiwiproc:json-1
CREATE TABLE test_json (
    id INT PRIMARY KEY,
    data JSONB NOT NULL
);
