--liquibase formatted sql

--changeset timn2020:2026-10-17-0023-create-task-folders dbms:postgresql
CREATE TABLE task_folders (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES move_projects(id) ON DELETE CASCADE,
    name varchar(80) NOT NULL,
    color_key varchar(16) NOT NULL,
    initial_name varchar(80) NOT NULL,
    initial_color_key varchar(16) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_task_folders_name CHECK (char_length(name) BETWEEN 1 AND 80 AND name = btrim(name)),
    CONSTRAINT ck_task_folders_initial_name CHECK (char_length(initial_name) BETWEEN 1 AND 80 AND initial_name = btrim(initial_name)),
    CONSTRAINT ck_task_folders_color CHECK (color_key IN ('mint', 'cyan', 'coral', 'sand', 'lilac', 'forest')),
    CONSTRAINT ck_task_folders_initial_color CHECK (initial_color_key IN ('mint', 'cyan', 'coral', 'sand', 'lilac', 'forest')),
    CONSTRAINT uk_task_folders_project_id UNIQUE (project_id, id)
);
CREATE INDEX idx_task_folders_project_created ON task_folders(project_id, created_at, id);

ALTER TABLE move_tasks
    ADD COLUMN folder_id uuid,
    ADD CONSTRAINT fk_move_tasks_folder FOREIGN KEY (project_id, folder_id)
        REFERENCES task_folders(project_id, id) ON DELETE SET NULL (folder_id);
CREATE INDEX idx_move_tasks_folder ON move_tasks(folder_id);

-- Refuse a downgrade that would discard user-created folders.
--rollback ALTER TABLE task_folders ADD CONSTRAINT ck_task_folders_rollback_empty CHECK (false);
--rollback ALTER TABLE move_tasks DROP CONSTRAINT fk_move_tasks_folder, DROP COLUMN folder_id;
--rollback DROP TABLE task_folders;
