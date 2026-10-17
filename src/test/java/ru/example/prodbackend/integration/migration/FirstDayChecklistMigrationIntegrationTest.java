package ru.example.prodbackend.integration.migration;

import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import ru.example.prodbackend.support.BaseIntegrationTest;
import ru.example.prodbackend.support.LegacyChecklistTestSupport;

import static org.assertj.core.api.Assertions.*;

class FirstDayChecklistMigrationIntegrationTest extends BaseIntegrationTest {
    @Autowired private DataSource dataSource;

    @Test
    void upgradesPopulatedPreviousReleaseAtomicallyRetriesFailureAndRefusesLossyDowngrade() throws Exception {
        String schema = "checklist_migration_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        try {
            migrate(schema, 26, false);
            try (var connection = dataSource.getConnection()) {
                connection.setSchema(schema);
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                try {
                    UUID user = UUID.randomUUID(), project = UUID.randomUUID();
                    UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
                    UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
                    jdbc.update("INSERT INTO users(id,email,password,role) VALUES (?,'legacy@example.com','hash','USER')", user);
                    jdbc.update("INSERT INTO move_projects(id,owner_id,name,move_date,archived_at,created_at,updated_at) VALUES (?,?,'Legacy',now(),now(),now(),now())", project, user);
                    for (UUID id : new UUID[]{first, second}) jdbc.update("""
                            INSERT INTO first_day_checklist_items(id,project_id,name,completed_at,created_at,updated_at)
                            VALUES (?,?,'Одинаковое название','2025-01-01 12:34:56.123456+00','2024-12-01 01:02:03.456789+00','2025-02-03 04:05:06.789012+00')
                            """, id, project);
                    var source = jdbc.queryForList("SELECT id,project_id,name,completed_at,created_at,updated_at FROM first_day_checklist_items ORDER BY id");
                    var history = jdbc.queryForList("SELECT id,author,filename,md5sum FROM databasechangelog ORDER BY orderexecuted");
                    // Fail after the first task insert, proving rollback includes the folder, archive and all earlier tasks.
                    jdbc.execute("""
                            CREATE FUNCTION fail_second_legacy_task() RETURNS trigger LANGUAGE plpgsql AS $$
                            BEGIN
                                IF NEW.id = '00000000-0000-0000-0000-000000000002'::uuid THEN
                                    RAISE EXCEPTION 'injected backfill failure';
                                END IF;
                                RETURN NEW;
                            END $$;
                            CREATE TRIGGER fail_second_legacy_task BEFORE INSERT ON move_tasks
                                FOR EACH ROW EXECUTE FUNCTION fail_second_legacy_task();
                            """);
                    assertThatThrownBy(() -> migrate(schema, 0, false)).hasStackTraceContaining("injected backfill failure");
                    for (String table : new String[]{"move_tasks", "task_folders", "first_day_checklist_archive", "first_day_checklist_migrations"})
                        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
                    assertThat(jdbc.queryForList("SELECT id,project_id,name,completed_at,created_at,updated_at FROM first_day_checklist_items ORDER BY id")).isEqualTo(source);
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Long.class)).isEqualTo(27);
                    jdbc.execute("DROP TRIGGER fail_second_legacy_task ON move_tasks; DROP FUNCTION fail_second_legacy_task();");
                    migrate(schema, 0, false);
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Long.class)).isEqualTo(28);
                    assertThat(jdbc.queryForList("SELECT id,author,filename,md5sum FROM databasechangelog ORDER BY orderexecuted")).containsAll(history);
                    assertThat(jdbc.queryForList("SELECT id,project_id,name,completed_at,created_at,updated_at FROM first_day_checklist_archive ORDER BY id")).isEqualTo(source);
                    assertThat(jdbc.queryForList("SELECT id,project_id,title AS name,completed_at,created_at,updated_at FROM move_tasks ORDER BY id")).isEqualTo(source);
                    var tasks = jdbc.queryForList("SELECT * FROM move_tasks ORDER BY id");
                    var archive = jdbc.queryForList("SELECT * FROM first_day_checklist_archive ORDER BY id");
                    migrate(schema, 0, false);
                    assertThat(jdbc.queryForList("SELECT * FROM move_tasks ORDER BY id")).isEqualTo(tasks);
                    assertThatThrownBy(() -> migrate(schema, 1, true)).hasStackTraceContaining("ck_first_day_backfill_rollback_empty");
                    assertThat(jdbc.queryForList("SELECT * FROM first_day_checklist_archive ORDER BY id")).isEqualTo(archive);
                    jdbc.update("DELETE FROM move_tasks WHERE id=?", first);
                    jdbc.execute("DELETE FROM task_folders");
                    LegacyChecklistTestSupport.backfill(jdbc);
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks", Long.class)).isEqualTo(1);
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM task_folders", Long.class)).isZero();
                    assertThat(jdbc.queryForList("SELECT * FROM first_day_checklist_archive ORDER BY id")).isEqualTo(archive);
                } finally { connection.setSchema("public"); }
            }
        } finally { admin.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }

    private void migrate(String schema, int count, boolean rollback) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        try (Connection connection = dataSource.getConnection(); var resources = new ClassLoaderResourceAccessor()) {
            connection.setSchema(schema);
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(
                    new SingleConnectionDataSource(connection, true).getConnection()));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (var liquibase = new Liquibase("db/changelog/db.changelog-master.xml", resources, database)) {
                if (rollback) liquibase.rollback(count, new Contexts(), new LabelExpression());
                else if (count > 0) liquibase.update(count, new Contexts(), new LabelExpression());
                else liquibase.update(new Contexts(), new LabelExpression());
            } finally { connection.setSchema("public"); }
        }
    }
}
