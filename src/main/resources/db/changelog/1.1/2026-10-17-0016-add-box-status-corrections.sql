--liquibase formatted sql

--changeset timn2020:2026-10-17-0016-add-box-status-corrections dbms:postgresql
ALTER TABLE project_events
    ADD COLUMN previous_status varchar(32),
    ADD COLUMN new_status varchar(32),
    ADD CONSTRAINT ck_project_events_previous_status CHECK (previous_status IN ('PACKING', 'PACKED', 'LOADED', 'DELIVERED', 'UNPACKED')),
    ADD CONSTRAINT ck_project_events_new_status CHECK (new_status IN ('PACKING', 'PACKED', 'LOADED', 'DELIVERED', 'UNPACKED')),
    DROP CONSTRAINT ck_project_events_event_type,
    ADD CONSTRAINT ck_project_events_event_type CHECK (event_type IN ('PROJECT_CREATED', 'BOX_CREATED', 'BOX_STATUS_CHANGED', 'BOX_STATUS_CORRECTED', 'TASK_COMPLETED'));

-- Downgrade retains audit records and descriptions, using the legacy status-change event type.
--rollback ALTER TABLE project_events DROP CONSTRAINT ck_project_events_event_type;
--rollback UPDATE project_events SET event_type = 'BOX_STATUS_CHANGED' WHERE event_type = 'BOX_STATUS_CORRECTED';
--rollback ALTER TABLE project_events ADD CONSTRAINT ck_project_events_event_type CHECK (event_type IN ('PROJECT_CREATED', 'BOX_CREATED', 'BOX_STATUS_CHANGED', 'TASK_COMPLETED')), DROP CONSTRAINT ck_project_events_previous_status, DROP CONSTRAINT ck_project_events_new_status, DROP COLUMN previous_status, DROP COLUMN new_status;
