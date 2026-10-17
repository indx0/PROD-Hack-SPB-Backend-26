package ru.example.prodbackend.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Очищает только данные сценариев, сохраняя общий каталог и историю Liquibase. */
public final class DatabaseCleaner {
    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void clean() {
        // Один вызов JDBC с фиксированным числом DELETE, независимо от числа проектов.
        // TRUNCATE users CASCADE удалил бы также общий каталог task_templates.
        jdbc.execute("""
                DELETE FROM items;
                DELETE FROM boxes;
                DELETE FROM rooms;
                DELETE FROM move_tasks;
                DELETE FROM first_day_checklist_items;
                DELETE FROM project_events;
                DELETE FROM move_projects;
                DELETE FROM refresh_tokens;
                DELETE FROM users;
                """);
    }
}
