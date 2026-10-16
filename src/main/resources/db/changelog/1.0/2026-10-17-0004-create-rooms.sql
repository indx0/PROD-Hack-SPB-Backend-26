--liquibase formatted sql

--changeset indx0:2026-10-17-0004-create-rooms dbms:postgresql
CREATE TABLE rooms (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_rooms_project FOREIGN KEY (project_id) REFERENCES move_projects (id),
    CONSTRAINT uk_rooms_project_name UNIQUE (project_id, name),
    CONSTRAINT uk_rooms_id_project UNIQUE (id, project_id)
);

CREATE INDEX idx_rooms_project ON rooms (project_id);

--rollback DROP TABLE rooms;
