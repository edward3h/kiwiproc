--liquibase formatted sql
--changeset edward3h:4
CREATE TYPE restaurant_status AS ENUM ('OPEN', 'CLOSED');
ALTER TABLE restaurant ADD COLUMN status restaurant_status;
