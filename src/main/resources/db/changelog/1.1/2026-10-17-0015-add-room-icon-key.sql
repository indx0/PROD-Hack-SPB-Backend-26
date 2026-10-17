--liquibase formatted sql

--changeset timn2020:2026-10-17-0015-add-room-icon-key dbms:postgresql
ALTER TABLE rooms ADD COLUMN icon_key varchar(64);

--rollback ALTER TABLE rooms DROP COLUMN icon_key;
