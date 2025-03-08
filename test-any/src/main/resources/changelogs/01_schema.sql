--liquibase formatted sql
--changeset edward3h:1

CREATE TABLE IF NOT EXISTS test_tz (
  id         INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);