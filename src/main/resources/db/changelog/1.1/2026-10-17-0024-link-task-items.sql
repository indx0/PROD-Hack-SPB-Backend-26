--liquibase formatted sql

--changeset timn2020:2026-10-17-0024-link-task-items dbms:postgresql
ALTER TABLE move_tasks
    ADD COLUMN linked_item_id uuid,
    ADD CONSTRAINT fk_move_tasks_linked_item FOREIGN KEY (linked_item_id) REFERENCES items(id) ON DELETE SET NULL;
CREATE INDEX idx_move_tasks_linked_item ON move_tasks(linked_item_id);

-- Refuse a downgrade that would silently discard existing task/item links.
--rollback ALTER TABLE move_tasks ADD CONSTRAINT ck_move_tasks_rollback_unlinked CHECK (linked_item_id IS NULL);
--rollback ALTER TABLE move_tasks DROP COLUMN linked_item_id;
