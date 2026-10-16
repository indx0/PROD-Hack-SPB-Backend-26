--liquibase formatted sql

--changeset indx0:2026-10-17-0009-create-first_day_checklist_items dbms:postgresql
CREATE TABLE first_day_checklist_items (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_first_day_items_project FOREIGN KEY (project_id) REFERENCES move_projects (id)
);

CREATE INDEX idx_first_day_items_project ON first_day_checklist_items (project_id);

--rollback DROP TABLE first_day_checklist_items;
