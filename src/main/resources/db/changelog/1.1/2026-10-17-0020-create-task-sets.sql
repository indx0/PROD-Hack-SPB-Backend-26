--liquibase formatted sql

--changeset timn2020:2026-10-17-0020-create-task-sets dbms:postgresql
CREATE TABLE task_sets (
    id uuid NOT NULL PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name varchar(255) NOT NULL,
    description varchar(10000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone
);
CREATE INDEX idx_task_sets_owner ON task_sets(owner_id);
CREATE TABLE task_set_items (
    id uuid NOT NULL PRIMARY KEY,
    task_set_id uuid NOT NULL REFERENCES task_sets(id) ON DELETE CASCADE,
    title varchar(255) NOT NULL,
    description varchar(10000),
    stage varchar(32) NOT NULL CHECK (stage IN ('BEFORE_MOVE', 'MOVING_DAY', 'AFTER_MOVE')),
    offset_days integer NOT NULL,
    position integer NOT NULL CHECK (position >= 0),
    CONSTRAINT uk_task_set_items_position UNIQUE (task_set_id, position)
);
CREATE TABLE task_set_applications (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES move_projects(id) ON DELETE CASCADE,
    task_set_id uuid NOT NULL REFERENCES task_sets(id) ON DELETE CASCADE,
    task_set_name varchar(255) NOT NULL,
    applied_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_task_set_applications_project_set UNIQUE (project_id, task_set_id)
);
CREATE INDEX idx_task_set_applications_set ON task_set_applications(task_set_id);
ALTER TABLE move_tasks
    ADD COLUMN task_set_application_id uuid REFERENCES task_set_applications(id) ON DELETE SET NULL,
    ADD COLUMN task_set_position integer,
    ADD CONSTRAINT uk_move_tasks_set_application_position UNIQUE (task_set_application_id, task_set_position),
    ADD CONSTRAINT ck_move_tasks_set_position CHECK (task_set_position IS NULL OR task_set_position >= 0);

--rollback ALTER TABLE move_tasks DROP CONSTRAINT ck_move_tasks_set_position, DROP CONSTRAINT uk_move_tasks_set_application_position, DROP COLUMN task_set_position, DROP COLUMN task_set_application_id;
--rollback DROP TABLE task_set_applications;
--rollback DROP TABLE task_set_items;
--rollback DROP TABLE task_sets;
