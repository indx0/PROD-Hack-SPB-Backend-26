package ru.example.prodbackend.support;

import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;

public final class LegacyChecklistTestSupport {
    private LegacyChecklistTestSupport() { }

    public static String backfillSql() {
        try (var stream = LegacyChecklistTestSupport.class.getClassLoader().getResourceAsStream(
                "db/changelog/1.1/2026-10-17-0027-migrate-first-day-checklist.sql")) {
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return source.substring(source.indexOf("DO $migration$"), source.indexOf("$migration$;") + "$migration$;".length());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    public static void backfill(JdbcTemplate jdbc) { jdbc.execute(backfillSql()); }

    // Seed an immutable historical snapshot for tests unrelated to the conversion itself.
    public static void snapshot(JdbcTemplate jdbc) {
        jdbc.execute("""
                INSERT INTO first_day_checklist_archive(id, project_id, target_task_id, folder_id, name,
                    completed_at, created_at, updated_at, item_id, item_name, box_id, box_number, room_id, room_name, room_icon_key)
                SELECT c.id, c.project_id, c.id, gen_random_uuid(), c.name, c.completed_at, c.created_at, c.updated_at,
                    c.item_id, i.name, b.id, b.number, r.id, r.name, r.icon_key
                FROM first_day_checklist_items c LEFT JOIN items i ON i.id = c.item_id
                LEFT JOIN boxes b ON b.id = i.box_id LEFT JOIN rooms r ON r.id = b.room_id
                ON CONFLICT (id) DO NOTHING
                """);
    }
}
