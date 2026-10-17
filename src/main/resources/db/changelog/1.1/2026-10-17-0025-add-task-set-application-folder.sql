--liquibase formatted sql

--changeset timn2020:2026-10-17-0025-add-task-set-application-folder dbms:postgresql
-- Historical destination: deliberately no FK, so deleting a folder preserves this UUID.
ALTER TABLE task_set_applications ADD COLUMN folder_id uuid;

--rollback ALTER TABLE task_set_applications ADD CONSTRAINT ck_task_set_applications_rollback_unfiled CHECK (folder_id IS NULL);
--rollback ALTER TABLE task_set_applications DROP COLUMN folder_id;
