--liquibase formatted sql

--changeset timn2020:2026-10-17-0014-add-box-details dbms:postgresql
ALTER TABLE boxes
    ADD COLUMN title varchar(255) NOT NULL DEFAULT '',
    ADD COLUMN fragile boolean NOT NULL DEFAULT false,
    ADD COLUMN first_day boolean NOT NULL DEFAULT false,
    ADD COLUMN carry_with_me boolean NOT NULL DEFAULT false;

--rollback ALTER TABLE boxes DROP COLUMN carry_with_me, DROP COLUMN first_day, DROP COLUMN fragile, DROP COLUMN title;
