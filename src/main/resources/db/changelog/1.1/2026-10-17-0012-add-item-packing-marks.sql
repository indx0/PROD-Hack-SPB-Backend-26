--liquibase formatted sql

--changeset timn2020:2026-10-17-0012-add-item-packing-marks dbms:postgresql
ALTER TABLE items
    ADD COLUMN packed_at timestamp with time zone,
    ADD COLUMN unpacked_at timestamp with time zone;

--rollback ALTER TABLE items DROP COLUMN unpacked_at, DROP COLUMN packed_at;
