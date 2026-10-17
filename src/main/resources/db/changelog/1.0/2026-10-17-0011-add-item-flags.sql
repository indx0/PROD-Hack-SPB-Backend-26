--liquibase formatted sql

--changeset indx0:2026-10-17-0011-add-item-flags dbms:postgresql
ALTER TABLE items
    ADD COLUMN fragile boolean NOT NULL DEFAULT false,
    ADD COLUMN first_day boolean NOT NULL DEFAULT false,
    ADD COLUMN carry_personally boolean NOT NULL DEFAULT false;

--rollback ALTER TABLE items DROP COLUMN fragile, DROP COLUMN first_day, DROP COLUMN carry_personally;
