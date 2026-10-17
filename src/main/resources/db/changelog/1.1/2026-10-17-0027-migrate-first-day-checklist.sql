--liquibase formatted sql

--changeset timn2020:2026-10-17-0027-migrate-first-day-checklist dbms:postgresql splitStatements:false
-- Liquibase holds its startup lock; the whole backfill also runs in one transaction.
-- Project completion proof is independent of live tasks and folders: reruns cannot resurrect them.
DO $migration$
DECLARE
    project_record record;
    source_record record;
    destination_folder uuid;
    destination_task uuid;
    source_count bigint;
    completed_count bigint;
BEGIN
    FOR project_record IN
        SELECT p.id FROM move_projects p
        WHERE EXISTS (SELECT 1 FROM first_day_checklist_items c WHERE c.project_id = p.id)
          AND NOT EXISTS (SELECT 1 FROM first_day_checklist_migrations m WHERE m.project_id = p.id)
        ORDER BY p.id FOR UPDATE
    LOOP
        IF EXISTS (SELECT 1 FROM first_day_checklist_migrations WHERE project_id = project_record.id) THEN
            CONTINUE;
        END IF;
        LOOP
            destination_folder := gen_random_uuid();
            EXIT WHEN NOT EXISTS (SELECT 1 FROM task_folders WHERE id = destination_folder);
        END LOOP;
        INSERT INTO task_folders(id, project_id, name, color_key, initial_name, initial_color_key, created_at, updated_at)
        VALUES (destination_folder, project_record.id, 'Личные задачи', 'mint', 'Личные задачи', 'mint', now(), now());
        source_count := 0;
        completed_count := 0;
        FOR source_record IN
            SELECT c.*, i.name AS item_name, b.id AS box_id, b.number AS box_number,
                   r.id AS room_id, r.name AS room_name, r.icon_key AS room_icon_key
            FROM first_day_checklist_items c
            LEFT JOIN items i ON i.id = c.item_id
            LEFT JOIN boxes b ON b.id = i.box_id
            LEFT JOIN rooms r ON r.id = b.room_id
            WHERE c.project_id = project_record.id ORDER BY c.id
        LOOP
            destination_task := source_record.id;
            WHILE EXISTS (SELECT 1 FROM move_tasks WHERE id = destination_task)
               OR EXISTS (SELECT 1 FROM first_day_checklist_archive WHERE target_task_id = destination_task) LOOP
                destination_task := gen_random_uuid();
            END LOOP;
            INSERT INTO first_day_checklist_archive
                (id, project_id, target_task_id, folder_id, name, completed_at, created_at, updated_at,
                 item_id, item_name, box_id, box_number, room_id, room_name, room_icon_key)
            VALUES (source_record.id, project_record.id, destination_task, destination_folder, source_record.name,
                    source_record.completed_at, source_record.created_at, source_record.updated_at, source_record.item_id,
                    source_record.item_name, source_record.box_id, source_record.box_number, source_record.room_id,
                    source_record.room_name, source_record.room_icon_key);
            INSERT INTO move_tasks
                (id, project_id, folder_id, title, stage, due_type, linked_item_id, completed_at, created_at, updated_at)
            VALUES (destination_task, project_record.id, destination_folder, source_record.name, 'BEFORE_MOVE', 'FIXED',
                    source_record.item_id, source_record.completed_at, source_record.created_at, source_record.updated_at);
            source_count := source_count + 1;
            IF source_record.completed_at IS NOT NULL THEN completed_count := completed_count + 1; END IF;
        END LOOP;
        INSERT INTO first_day_checklist_migrations(project_id, folder_id, source_count, completed_count, migrated_at)
        VALUES (project_record.id, destination_folder, source_count, completed_count, now());
    END LOOP;
END
$migration$;

-- Never silently undo a conversion: ordinary tasks may already have been edited or deleted.
--rollback ALTER TABLE first_day_checklist_migrations ADD CONSTRAINT ck_first_day_backfill_rollback_empty CHECK (false);
--rollback ALTER TABLE first_day_checklist_migrations DROP CONSTRAINT ck_first_day_backfill_rollback_empty;
