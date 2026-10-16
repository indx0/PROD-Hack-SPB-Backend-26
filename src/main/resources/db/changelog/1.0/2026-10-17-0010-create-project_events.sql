--liquibase formatted sql

--changeset indx0:2026-10-17-0010-create-project_events dbms:postgresql
CREATE TABLE project_events (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    actor_id uuid,
    event_type varchar(32) NOT NULL,
    object_type varchar(32) NOT NULL,
    object_id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    description varchar(2000) NOT NULL,
    CONSTRAINT fk_project_events_project FOREIGN KEY (project_id) REFERENCES move_projects (id),
    CONSTRAINT fk_project_events_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT ck_project_events_event_type CHECK (event_type IN ('PROJECT_CREATED', 'BOX_CREATED', 'BOX_STATUS_CHANGED', 'TASK_COMPLETED')),
    CONSTRAINT ck_project_events_object_type CHECK (object_type IN ('PROJECT', 'BOX', 'TASK'))
);

CREATE INDEX idx_project_events_project_time ON project_events (project_id, occurred_at);
CREATE INDEX idx_project_events_actor ON project_events (actor_id);

--rollback DROP TABLE project_events;
