--liquibase formatted sql

--changeset timn2020:2026-10-17-0011-add-item-quantity-first-day dbms:postgresql rollbackSplitStatements:false
--validCheckSum: 9:08472927e041d45ec81a4149f9b5621b
ALTER TABLE items
    ADD COLUMN quantity integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS first_day boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT chk_items_quantity_positive CHECK (quantity > 0);

--rollback ALTER TABLE items DROP CONSTRAINT chk_items_quantity_positive, DROP COLUMN quantity;
--rollback DO $rollback$ BEGIN IF NOT EXISTS (SELECT 1 FROM databasechangelog WHERE id = '2026-10-17-0011-add-item-flags' AND author = 'indx0') THEN ALTER TABLE items DROP COLUMN first_day; END IF; END $rollback$;
