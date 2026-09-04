--liquibase formatted sql

--changeset kiwiproc:json-2
ALTER TABLE test_json ALTER COLUMN data DROP NOT NULL;
