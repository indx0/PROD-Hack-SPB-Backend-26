--liquibase formatted sql

--changeset timn2020:2026-10-17-0018-create-project-participants dbms:postgresql
CREATE TABLE project_participants (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES move_projects(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uk_project_participants_project_user UNIQUE (project_id, user_id)
);
CREATE INDEX idx_project_participants_user ON project_participants(user_id);

--rollback DROP TABLE project_participants;
