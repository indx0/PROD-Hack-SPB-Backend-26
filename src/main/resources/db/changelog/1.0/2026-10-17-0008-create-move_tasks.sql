--liquibase formatted sql

--changeset indx0:2026-10-17-0008-create-move_tasks dbms:postgresql
CREATE TABLE move_tasks (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    description varchar(10000),
    stage varchar(32) NOT NULL,
    due_date date,
    assignee_name varchar(255),
    completed_at timestamp with time zone,
    template_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_move_tasks_project FOREIGN KEY (project_id) REFERENCES move_projects (id),
    CONSTRAINT fk_move_tasks_template FOREIGN KEY (template_id) REFERENCES task_templates (id),
    CONSTRAINT uk_move_tasks_project_template UNIQUE (project_id, template_id),
    CONSTRAINT ck_move_tasks_stage CHECK (stage IN ('BEFORE_MOVE', 'MOVING_DAY', 'AFTER_MOVE'))
);

CREATE INDEX idx_move_tasks_project ON move_tasks (project_id);
CREATE INDEX idx_move_tasks_template ON move_tasks (template_id);

--rollback DROP TABLE move_tasks;
