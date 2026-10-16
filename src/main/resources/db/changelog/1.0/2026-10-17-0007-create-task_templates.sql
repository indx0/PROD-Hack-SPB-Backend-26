--liquibase formatted sql

--changeset indx0:2026-10-17-0007-create-task_templates dbms:postgresql
CREATE TABLE task_templates (
    id uuid NOT NULL PRIMARY KEY,
    title varchar(255) NOT NULL,
    description varchar(10000),
    stage varchar(32) NOT NULL,
    due_date_offset_days integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_task_templates_stage_title UNIQUE (stage, title),
    CONSTRAINT ck_task_templates_stage CHECK (stage IN ('BEFORE_MOVE', 'MOVING_DAY', 'AFTER_MOVE'))
);

--rollback DROP TABLE task_templates;
