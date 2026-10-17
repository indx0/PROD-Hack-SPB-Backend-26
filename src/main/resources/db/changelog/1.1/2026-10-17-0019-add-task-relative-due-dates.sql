--liquibase formatted sql

--changeset timn2020:2026-10-17-0019-add-task-relative-due-dates dbms:postgresql
ALTER TABLE move_tasks
    ADD COLUMN due_type varchar(16) NOT NULL DEFAULT 'FIXED',
    ADD COLUMN offset_days integer,
    ADD CONSTRAINT ck_move_tasks_due_type CHECK (due_type IN ('FIXED', 'RELATIVE')),
    ADD CONSTRAINT ck_move_tasks_due_fields CHECK (
        (due_type = 'FIXED' AND offset_days IS NULL)
        OR (due_type = 'RELATIVE' AND offset_days IS NOT NULL AND due_date IS NOT NULL)
    );

--rollback ALTER TABLE move_tasks DROP CONSTRAINT ck_move_tasks_due_fields, DROP CONSTRAINT ck_move_tasks_due_type, DROP COLUMN offset_days, DROP COLUMN due_type;
