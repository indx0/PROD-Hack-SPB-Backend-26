--liquibase formatted sql

--changeset timn2020:2026-10-17-0021-add-personal-task-templates dbms:postgresql rollbackSplitStatements:false
ALTER TABLE task_templates
    ADD COLUMN owner_id uuid REFERENCES users(id) ON DELETE CASCADE,
    ADD COLUMN deleted_at timestamp with time zone,
    DROP CONSTRAINT uk_task_templates_stage_title;

CREATE UNIQUE INDEX uk_task_templates_common_stage_title ON task_templates(stage, title) WHERE owner_id IS NULL;
CREATE INDEX idx_task_templates_owner ON task_templates(owner_id) WHERE deleted_at IS NULL;

--rollback DO $rollback$ BEGIN IF EXISTS (SELECT 1 FROM task_templates WHERE owner_id IS NOT NULL) THEN RAISE EXCEPTION 'Cannot roll back personal templates while account-owned templates exist'; END IF; END $rollback$;
--rollback DROP INDEX idx_task_templates_owner;
--rollback DROP INDEX uk_task_templates_common_stage_title;
--rollback ALTER TABLE task_templates DROP COLUMN owner_id, DROP COLUMN deleted_at, ADD CONSTRAINT uk_task_templates_stage_title UNIQUE (stage, title);
