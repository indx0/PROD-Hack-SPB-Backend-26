--liquibase formatted sql

--changeset timn2020:2026-10-17-0026-archive-first-day-checklist dbms:postgresql
CREATE TABLE first_day_checklist_migrations (
    project_id uuid PRIMARY KEY REFERENCES move_projects(id) ON DELETE CASCADE,
    folder_id uuid NOT NULL,
    source_count bigint NOT NULL,
    completed_count bigint NOT NULL,
    migrated_at timestamp with time zone NOT NULL
);

-- Historical IDs deliberately have no FK to live tasks, folders or inventory.
CREATE TABLE first_day_checklist_archive (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES move_projects(id) ON DELETE CASCADE,
    target_task_id uuid NOT NULL UNIQUE,
    folder_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    item_id uuid,
    item_name varchar(255),
    box_id uuid,
    box_number integer,
    room_id uuid,
    room_name varchar(255),
    room_icon_key varchar(255)
);
CREATE INDEX idx_first_day_archive_project_created ON first_day_checklist_archive(project_id, created_at, id);

-- Refuse a downgrade that would discard the recovery snapshot or migration proof.
--rollback ALTER TABLE first_day_checklist_archive ADD CONSTRAINT ck_first_day_archive_rollback_empty CHECK (false);
--rollback ALTER TABLE first_day_checklist_migrations ADD CONSTRAINT ck_first_day_migrations_rollback_empty CHECK (false);
--rollback DROP TABLE first_day_checklist_archive;
--rollback DROP TABLE first_day_checklist_migrations;
