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
                runLiquibase(schema, false);
                assertTables(jdbc, schema);
                assertHistory(jdbc);
                assertConstraints(jdbc);

                var users = jdbc.queryForList("SELECT id, email FROM users ORDER BY email");
                assertThat(users).hasSize(2);
                runLiquibase(schema, false);
                assertHistory(jdbc);
                assertThat(jdbc.queryForList("SELECT id, email FROM users ORDER BY email")).isEqualTo(users);

                runLiquibase(schema, true);
                assertThat(jdbc.queryForList("""
                        SELECT tablename FROM pg_tables
                        WHERE schemaname = ? AND tablename NOT IN ('databasechangelog', 'databasechangeloglock')
                        """, String.class, schema)).isEmpty();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Long.class)).isZero();

                runLiquibase(schema, false);
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

    private void runLiquibase(String schema, boolean rollback) throws Exception {
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
                if (rollback) {
                    liquibase.rollback(11, new Contexts(), new LabelExpression());
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
                .hasSize(11).containsOnly("indx0", "timn2020");
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

        UUID item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO items (id, box_id, name, lost, created_at, updated_at)
                VALUES (?, ?, 'Тарелки', false, now(), now())
                """, item, box);
        assertThat(jdbc.queryForObject("SELECT quantity FROM items WHERE id = ?", Integer.class, item)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT first_day FROM items WHERE id = ?", Boolean.class, item)).isFalse();

        jdbc.update("UPDATE items SET quantity = 6, first_day = true WHERE id = ?", item);
        assertThat(jdbc.queryForObject("SELECT quantity FROM items WHERE id = ?", Integer.class, item)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT first_day FROM items WHERE id = ?", Boolean.class, item)).isTrue();

        rejects(jdbc, "UPDATE items SET quantity = 0 WHERE id = ?", "23514", item);
        rejects(jdbc, "UPDATE items SET quantity = -1 WHERE id = ?", "23514", item);
        rejects(jdbc, "UPDATE items SET quantity = NULL WHERE id = ?", "23502", item);
        rejects(jdbc, "UPDATE items SET first_day = NULL WHERE id = ?", "23502", item);
    }

    private void rejects(JdbcTemplate jdbc, String sql, String sqlState, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args))
                .hasRootCauseInstanceOf(SQLException.class)
                .rootCause().extracting(error -> ((SQLException) error).getSQLState()).isEqualTo(sqlState);
    }
}
