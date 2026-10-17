--liquibase formatted sql

--changeset timn2020:2026-10-17-0022-add-task-assignee dbms:postgresql
ALTER TABLE move_tasks ADD COLUMN assignee_id uuid REFERENCES users(id);
CREATE INDEX idx_move_tasks_project_assignee ON move_tasks(project_id, assignee_id);

--rollback ALTER TABLE move_tasks DROP COLUMN assignee_id;
