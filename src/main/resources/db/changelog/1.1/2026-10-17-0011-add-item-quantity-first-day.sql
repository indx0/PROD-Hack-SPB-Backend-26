--liquibase formatted sql

--changeset timn2020:2026-10-17-0011-add-item-quantity-first-day dbms:postgresql
ALTER TABLE items
    ADD COLUMN quantity integer NOT NULL DEFAULT 1,
    ADD COLUMN first_day boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT chk_items_quantity_positive CHECK (quantity > 0);

--rollback ALTER TABLE items DROP CONSTRAINT chk_items_quantity_positive, DROP COLUMN first_day, DROP COLUMN quantity;
