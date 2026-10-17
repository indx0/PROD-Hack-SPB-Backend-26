--liquibase formatted sql

--changeset timn2020:2026-10-17-0017-add-project-archive dbms:postgresql
ALTER TABLE move_projects ADD COLUMN archived_at timestamp with time zone;
ALTER TABLE project_events
    DROP CONSTRAINT ck_project_events_event_type,
    ADD CONSTRAINT ck_project_events_event_type CHECK (event_type IN ('PROJECT_CREATED', 'PROJECT_ARCHIVED', 'PROJECT_RESTORED', 'BOX_CREATED', 'BOX_STATUS_CHANGED', 'BOX_STATUS_CORRECTED', 'TASK_COMPLETED'));

-- Archive history is preserved: the legacy CHECK must validate before the column is removed.
--rollback ALTER TABLE project_events DROP CONSTRAINT ck_project_events_event_type, ADD CONSTRAINT ck_project_events_event_type CHECK (event_type IN ('PROJECT_CREATED', 'BOX_CREATED', 'BOX_STATUS_CHANGED', 'BOX_STATUS_CORRECTED', 'TASK_COMPLETED'));
--rollback ALTER TABLE move_projects DROP COLUMN archived_at;
