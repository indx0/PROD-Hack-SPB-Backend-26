package ru.example.prodbackend.integration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiquibaseMigrationIntegrationTest extends BaseIntegrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final String[] TABLES = {
            "users", "refresh_tokens", "move_projects", "rooms", "boxes", "items",
            "task_templates", "move_tasks", "first_day_checklist_items", "project_events"
    };

    @Autowired
    private DataSource dataSource;

    @Test
    void migratesOncePreservesDataAndSupportsRollbackAndReapplication() throws Exception {
        // Lifecycle checks run outside the application's schema, including destructive rollback.
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        try (Connection queryConnection = dataSource.getConnection()) {
            queryConnection.setSchema(schema);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(queryConnection, true));
            try {
                runLiquibase(schema, 0);
                assertTables(jdbc, schema);
                assertHistory(jdbc);
                assertConstraints(jdbc);

                // Roll back only the new columns, then upgrade a schema that already has items.
                var items = jdbc.queryForList("SELECT id, name, quantity, first_day FROM items ORDER BY id");
                var boxes = jdbc.queryForList("SELECT id, status FROM boxes ORDER BY id");
                var rooms = jdbc.queryForList("SELECT id, name FROM rooms ORDER BY id");
                var checklist = jdbc.queryForList("SELECT id, name, completed_at FROM first_day_checklist_items ORDER BY id");
                var events = jdbc.queryForList("SELECT id, actor_id, object_id, description, occurred_at FROM project_events ORDER BY id");
                runLiquibase(schema, 5);
                assertThat(jdbc.queryForList("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'items'
                        """, String.class, schema)).doesNotContain("packed_at", "unpacked_at");
                assertThat(jdbc.queryForList("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'boxes'
                        """, String.class, schema)).doesNotContain("title", "fragile", "first_day", "carry_with_me");
                assertThat(jdbc.queryForList("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'rooms'
                        """, String.class, schema)).doesNotContain("icon_key");
                assertThat(jdbc.queryForList("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'project_events'
                        """, String.class, schema)).doesNotContain("previous_status", "new_status");
                assertThat(jdbc.queryForList("SELECT event_type FROM project_events", String.class)).containsOnly("BOX_STATUS_CHANGED", "BOX_CREATED");
                runLiquibase(schema, 0);
                assertHistory(jdbc);
                assertThat(jdbc.queryForList("SELECT id, name, quantity, first_day FROM items ORDER BY id")).isEqualTo(items);
                assertThat(jdbc.queryForList("SELECT id, status FROM boxes ORDER BY id")).isEqualTo(boxes);
                assertThat(jdbc.queryForList("SELECT id, name FROM rooms ORDER BY id")).isEqualTo(rooms);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms WHERE icon_key IS NOT NULL", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM boxes WHERE title <> '' OR fragile OR first_day OR carry_with_me", Long.class)).isZero();
                assertThat(jdbc.queryForList("SELECT id, name, completed_at FROM first_day_checklist_items ORDER BY id")).isEqualTo(checklist);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items WHERE item_id IS NOT NULL", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE packed_at IS NOT NULL OR unpacked_at IS NOT NULL", Long.class)).isZero();
                assertThat(jdbc.queryForList("SELECT id, actor_id, object_id, description, occurred_at FROM project_events ORDER BY id")).isEqualTo(events);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM project_events WHERE previous_status IS NOT NULL OR new_status IS NOT NULL", Long.class)).isZero();

                var users = jdbc.queryForList("SELECT id, email FROM users ORDER BY email");
                assertThat(users).hasSize(2);
                runLiquibase(schema, 0);
                assertHistory(jdbc);
                assertThat(jdbc.queryForList("SELECT id, email FROM users ORDER BY email")).isEqualTo(users);

                runLiquibase(schema, 16);
                assertThat(jdbc.queryForList("""
                        SELECT tablename FROM pg_tables
                        WHERE schemaname = ? AND tablename NOT IN ('databasechangelog', 'databasechangeloglock')
                        """, String.class, schema)).isEmpty();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Long.class)).isZero();

                runLiquibase(schema, 0);
                assertTables(jdbc, schema);
                assertHistory(jdbc);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
            } finally {
                queryConnection.setSchema("public");
            }
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private void runLiquibase(String schema, int rollbackCount) throws Exception {
        // Each operation uses a fresh instance, just like separate application/CLI launches.
        // Liquibase also caches the up-to-date result globally within the JVM across instances.
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        try (Connection connection = dataSource.getConnection();
             ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()) {
            // Establish the schema before Liquibase starts a transaction, including rollback.
            connection.setSchema(schema);
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(
                    new SingleConnectionDataSource(connection, true).getConnection()));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                if (rollbackCount > 0) {
                    liquibase.rollback(rollbackCount, new Contexts(), new LabelExpression());
                } else {
                    liquibase.validate();
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                // Liquibase's close is suppressed so the schema is reset before returning to the pool.
                connection.setSchema("public");
            }
        }
    }

    private void assertTables(JdbcTemplate jdbc, String schema) {
        assertThat(jdbc.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = ? AND tablename NOT IN ('databasechangelog', 'databasechangeloglock')
                """, String.class, schema)).containsExactlyInAnyOrder(TABLES);
    }

    private void assertHistory(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForList("SELECT author FROM databasechangelog", String.class))
                .hasSize(16).containsOnly("indx0", "timn2020");
    }

    private void assertConstraints(JdbcTemplate jdbc) {
        UUID owner = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password, role) VALUES (?, 'owner@test', 'hash', 'USER')", owner);
        jdbc.update("INSERT INTO users (id, email, password, role) VALUES (?, 'other@test', 'hash', 'ADMIN')", otherUser);
        rejects(jdbc, "UPDATE users SET email = 'owner@test' WHERE id = ?", "23505", otherUser);
        rejects(jdbc, "UPDATE users SET role = 'UNKNOWN' WHERE id = ?", "23514", owner);

        UUID token = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO refresh_tokens (id, expiry_at, refresh_token, user_id, created_at)
                VALUES (?, now(), 'token-hash', ?, now())
                """, token, owner);
        rejects(jdbc, """
                INSERT INTO refresh_tokens (id, expiry_at, refresh_token, user_id, created_at)
                VALUES (?, now(), 'token-hash', ?, now())
                """, "23505", UUID.randomUUID(), owner);

        UUID project = UUID.randomUUID();
        UUID otherProject = UUID.randomUUID();
        for (UUID id : new UUID[]{project, otherProject}) {
            jdbc.update("""
                    INSERT INTO move_projects (id, owner_id, name, move_date, created_at, updated_at)
                    VALUES (?, ?, 'Move', now(), now(), now())
                    """, id, owner);
        }
        UUID room = UUID.randomUUID();
        UUID otherRoom = UUID.randomUUID();
        jdbc.update("INSERT INTO rooms (id, project_id, name, created_at, updated_at) VALUES (?, ?, 'Room', now(), now())",
                room, project);
        jdbc.update("INSERT INTO rooms (id, project_id, name, created_at, updated_at) VALUES (?, ?, 'Room', now(), now())",
                otherRoom, otherProject);
        assertThat(jdbc.queryForObject("SELECT icon_key FROM rooms WHERE id = ?", String.class, room)).isNull();
        jdbc.update("UPDATE rooms SET icon_key = ? WHERE id = ?", "k".repeat(64), room);
        assertThat(jdbc.queryForObject("SELECT icon_key FROM rooms WHERE id = ?", String.class, room)).hasSize(64);
        rejects(jdbc, "UPDATE rooms SET icon_key = ? WHERE id = ?", "22001", "k".repeat(65), room);
        jdbc.update("UPDATE rooms SET icon_key = 'kitchen' WHERE id = ?", room);
        jdbc.update("UPDATE rooms SET icon_key = NULL WHERE id = ?", otherRoom);
        String insertBox = """
                INSERT INTO boxes (id, number, project_id, room_id, status, contents_deferred, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PACKING', false, now(), now())
                """;
        UUID box = UUID.randomUUID();
        jdbc.update(insertBox, box, 1, project, room);
        // Reusing the number in another project is valid.
        jdbc.update(insertBox, UUID.randomUUID(), 1, otherProject, otherRoom);
        rejects(jdbc, insertBox, "23505", UUID.randomUUID(), 1, project, room);
        rejects(jdbc, insertBox, "23514", UUID.randomUUID(), 0, project, room);
        rejects(jdbc, insertBox, "23514", UUID.randomUUID(), -1, project, room);
        rejects(jdbc, insertBox, "23503", UUID.randomUUID(), 2, project, otherRoom);
        rejects(jdbc, "UPDATE boxes SET status = 'UNKNOWN' WHERE id = ?", "23514", box);

        assertThat(jdbc.queryForObject("SELECT title FROM boxes WHERE id = ?", String.class, box)).isEmpty();
        for (String column : new String[]{"fragile", "first_day", "carry_with_me"}) {
            assertThat(jdbc.queryForObject("SELECT " + column + " FROM boxes WHERE id = ?", Boolean.class, box)).isFalse();
            rejects(jdbc, "UPDATE boxes SET " + column + " = NULL WHERE id = ?", "23502", box);
        }
        rejects(jdbc, "UPDATE boxes SET title = NULL WHERE id = ?", "23502", box);
        rejects(jdbc, "UPDATE boxes SET title = ? WHERE id = ?", "22001", "x".repeat(256), box);
        jdbc.update("UPDATE boxes SET title = 'Посуда', fragile = true, first_day = true, carry_with_me = true WHERE id = ?", box);
        assertThat(jdbc.queryForObject("SELECT title FROM boxes WHERE id = ?", String.class, box)).isEqualTo("Посуда");
        assertThat(jdbc.queryForObject("SELECT fragile AND first_day AND carry_with_me FROM boxes WHERE id = ?", Boolean.class, box)).isTrue();

        UUID item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO items (id, box_id, name, lost, created_at, updated_at)
                VALUES (?, ?, 'Тарелки', false, now(), now())
                """, item, box);
        assertThat(jdbc.queryForObject("SELECT quantity FROM items WHERE id = ?", Integer.class, item)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT first_day FROM items WHERE id = ?", Boolean.class, item)).isFalse();
        assertThat(jdbc.queryForObject("SELECT packed_at FROM items WHERE id = ?", java.sql.Timestamp.class, item)).isNull();
        assertThat(jdbc.queryForObject("SELECT unpacked_at FROM items WHERE id = ?", java.sql.Timestamp.class, item)).isNull();

        jdbc.update("UPDATE items SET quantity = 6, first_day = true WHERE id = ?", item);
        assertThat(jdbc.queryForObject("SELECT quantity FROM items WHERE id = ?", Integer.class, item)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT first_day FROM items WHERE id = ?", Boolean.class, item)).isTrue();

        rejects(jdbc, "UPDATE items SET quantity = 0 WHERE id = ?", "23514", item);
        rejects(jdbc, "UPDATE items SET quantity = -1 WHERE id = ?", "23514", item);
        rejects(jdbc, "UPDATE items SET quantity = NULL WHERE id = ?", "23502", item);
        rejects(jdbc, "UPDATE items SET first_day = NULL WHERE id = ?", "23502", item);

        UUID point = UUID.randomUUID();
        UUID otherPoint = UUID.randomUUID();
        for (UUID id : new UUID[]{point, otherPoint}) {
            jdbc.update("INSERT INTO first_day_checklist_items (id, project_id, name, completed_at, created_at, updated_at) VALUES (?, ?, 'Первый день', now(), now(), now())", id, project);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items WHERE item_id IS NULL", Long.class)).isEqualTo(2);
        jdbc.update("UPDATE first_day_checklist_items SET item_id = ? WHERE id = ?", item, point);
        rejects(jdbc, "UPDATE first_day_checklist_items SET item_id = ? WHERE id = ?", "23505", item, otherPoint);
        rejects(jdbc, "UPDATE first_day_checklist_items SET item_id = ? WHERE id = ?", "23503", UUID.randomUUID(), otherPoint);
        UUID removable = UUID.randomUUID();
        jdbc.update("INSERT INTO items (id, box_id, name, lost, created_at, updated_at) VALUES (?, ?, 'Удалить', false, now(), now())", removable, box);
        jdbc.update("UPDATE first_day_checklist_items SET item_id = ? WHERE id = ?", removable, point);
        jdbc.update("DELETE FROM items WHERE id = ?", removable);
        assertThat(jdbc.queryForObject("SELECT item_id FROM first_day_checklist_items WHERE id = ?", UUID.class, point)).isNull();
        assertThat(jdbc.queryForObject("SELECT name FROM first_day_checklist_items WHERE id = ?", String.class, point)).isEqualTo("Первый день");
        assertThat(jdbc.queryForObject("SELECT completed_at FROM first_day_checklist_items WHERE id = ?", java.sql.Timestamp.class, point)).isNotNull();

        UUID event = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO project_events (id, project_id, actor_id, event_type, object_type, object_id, occurred_at, description)
                VALUES (?, ?, ?, 'BOX_CREATED', 'BOX', ?, now(), 'Создана коробка')
                """, event, project, owner, box);
        assertThat(jdbc.queryForObject("SELECT previous_status FROM project_events WHERE id = ?", String.class, event)).isNull();
        assertThat(jdbc.queryForObject("SELECT new_status FROM project_events WHERE id = ?", String.class, event)).isNull();
        UUID correction = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO project_events (id, project_id, actor_id, event_type, object_type, object_id, occurred_at, description, previous_status, new_status)
                VALUES (?, ?, ?, 'BOX_STATUS_CORRECTED', 'BOX', ?, now(), 'Исправлен статус: DELIVERED → PACKING', 'DELIVERED', 'PACKING')
                """, correction, project, owner, box);
        assertThat(jdbc.queryForObject("SELECT previous_status FROM project_events WHERE id = ?", String.class, correction)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT new_status FROM project_events WHERE id = ?", String.class, correction)).isEqualTo("PACKING");
        rejects(jdbc, "UPDATE project_events SET event_type = 'UNKNOWN' WHERE id = ?", "23514", correction);
        rejects(jdbc, "UPDATE project_events SET previous_status = 'UNKNOWN' WHERE id = ?", "23514", correction);
        rejects(jdbc, "UPDATE project_events SET new_status = 'UNKNOWN' WHERE id = ?", "23514", correction);
    }

    private void rejects(JdbcTemplate jdbc, String sql, String sqlState, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args))
                .hasRootCauseInstanceOf(SQLException.class)
                .rootCause().extracting(error -> ((SQLException) error).getSQLState()).isEqualTo(sqlState);
    }
}
