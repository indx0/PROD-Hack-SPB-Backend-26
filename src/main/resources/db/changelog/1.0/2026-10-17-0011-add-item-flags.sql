--liquibase formatted sql

--changeset indx0:2026-10-17-0011-add-item-flags dbms:postgresql rollbackSplitStatements:false
--validCheckSum: 9:f70ce7f9cda6ef9b256ba56a6f627210
ALTER TABLE items
    ADD COLUMN fragile boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS first_day boolean NOT NULL DEFAULT false,
    ADD COLUMN carry_personally boolean NOT NULL DEFAULT false;

--rollback ALTER TABLE items DROP COLUMN fragile, DROP COLUMN carry_personally;
--rollback DO $rollback$ BEGIN IF NOT EXISTS (SELECT 1 FROM databasechangelog WHERE id = '2026-10-17-0011-add-item-quantity-first-day' AND author = 'timn2020') THEN ALTER TABLE items DROP COLUMN first_day; END IF; END $rollback$;
