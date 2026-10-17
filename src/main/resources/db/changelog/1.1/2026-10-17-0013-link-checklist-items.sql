--liquibase formatted sql

--changeset timn2020:2026-10-17-0013-link-checklist-items dbms:postgresql
ALTER TABLE first_day_checklist_items
    ADD COLUMN item_id uuid,
    ADD CONSTRAINT fk_first_day_items_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE SET NULL,
    ADD CONSTRAINT uk_first_day_items_item UNIQUE (item_id);

--rollback ALTER TABLE first_day_checklist_items DROP CONSTRAINT fk_first_day_items_item, DROP CONSTRAINT uk_first_day_items_item, DROP COLUMN item_id;
