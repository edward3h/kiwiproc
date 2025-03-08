--liquibase formatted sql
--changeset edward3h:3

CREATE TABLE IF NOT EXISTS sql_type_mapping_test (
    col_bit BIT,
    col_smallint smallint,
    col_integer integer,
    col_bigint bigint,
    col_real real,
    col_double double precision,
    col_numeric numeric,
    col_char char,
    col_varchar varchar,
    col_date date,
    col_time time,
    col_timestamp timestamp,
    col_boolean boolean,
    col_nchar nchar,
    col_time_with_timezone time with time zone,
    col_timestamp_with_timezone timestamp with time zone
);
