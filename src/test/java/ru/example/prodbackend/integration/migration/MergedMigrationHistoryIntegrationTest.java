package ru.example.prodbackend.integration.migration;

import ru.example.prodbackend.support.BaseIntegrationTest;

import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MergedMigrationHistoryIntegrationTest extends BaseIntegrationTest {
    @Autowired private DataSource dataSource;
    @TempDir Path oldResources;
    private static final String ROOT = "db/changelog/db.changelog-master.xml";
    private static final String FLAGS = "1.0/2026-10-17-0011-add-item-flags.sql";
    private static final String QUANTITY = "1.1/2026-10-17-0011-add-item-quantity-first-day.sql";

    @ParameterizedTest
    @ValueSource(strings = {"master", "feature"})
    void upgradesBothPreviouslyAppliedHistoriesWithoutResettingChecksumsOrData(String history) throws Exception {
        prepareOldResources(history);
        String schema = "merge_history_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        try {
            try (var resources = new DirectoryResourceAccessor(oldResources)) {
                update(schema, resources);
            }
            try (var connection = dataSource.getConnection()) {
                connection.setSchema(schema);
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                try {
                    UUID user = UUID.randomUUID(), project = UUID.randomUUID(), room = UUID.randomUUID(), box = UUID.randomUUID(), item = UUID.randomUUID();
                    jdbc.update("INSERT INTO users(id,email,password,role) VALUES (?, 'history@example.com', 'hash', 'USER')", user);
                    jdbc.update("INSERT INTO move_projects(id,owner_id,name,move_date,created_at,updated_at) VALUES (?,?,'Move',now(),now(),now())", project, user);
                    jdbc.update("INSERT INTO rooms(id,project_id,name,created_at,updated_at) VALUES (?,?,'Room',now(),now())", room, project);
                    jdbc.update("INSERT INTO boxes(id,project_id,room_id,number,status,contents_deferred,created_at,updated_at) VALUES (?,?,?,1,'PACKING',false,now(),now())", box, project, room);
                    jdbc.update("INSERT INTO items(id,box_id,name,lost,first_day,created_at,updated_at) VALUES (?,?,'Legacy',false,true,now(),now())", item, box);
                    if (history.equals("master")) jdbc.update("UPDATE items SET fragile=true,carry_personally=true WHERE id=?", item);
                    else jdbc.update("UPDATE items SET quantity=7 WHERE id=?", item);
                    var originalHistory = jdbc.queryForList("SELECT id,author,filename,md5sum FROM databasechangelog ORDER BY orderexecuted");
                    try (var resources = new ClassLoaderResourceAccessor()) { update(schema, resources); }
                    assertThat(jdbc.queryForObject("SELECT first_day FROM items WHERE id=?", Boolean.class, item)).isTrue();
                    assertThat(jdbc.queryForObject("SELECT quantity FROM items WHERE id=?", Integer.class, item)).isEqualTo(history.equals("feature") ? 7 : 1);
                    assertThat(jdbc.queryForObject("SELECT fragile AND carry_personally FROM items WHERE id=?", Boolean.class, item)).isEqualTo(history.equals("master"));
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Long.class)).isEqualTo(28);
                    assertThat(jdbc.queryForList("SELECT id,author,filename,md5sum FROM databasechangelog ORDER BY orderexecuted")).containsAll(originalHistory);
                } finally { connection.setSchema("public"); }
            }
        } finally { admin.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }

    private void prepareOldResources(String history) throws Exception {
        String root = read(ROOT);
        var includes = root.lines().filter(line -> line.contains("<include"))
                .filter(line -> !line.contains("0026-archive-first-day") && !line.contains("0027-migrate-first-day") && !line.contains("0021-add-personal-task-templates") && !line.contains("0022-add-task-assignee") && !line.contains("0023-create-task-folders") && !line.contains("0024-link-task-items") && !line.contains("0025-add-task-set-application-folder"))
                .filter(line -> history.equals("master") ? line.contains("1.0/") : !line.contains(FLAGS)).toList();
        String header = root.substring(0, root.indexOf("    <include"));
        Path master = oldResources.resolve(ROOT);
        Files.createDirectories(master.getParent());
        Files.writeString(master, header + String.join("\n", includes) + "\n</databaseChangeLog>\n");
        for (String line : root.lines().filter(l -> l.contains("<include")).toList()) {
            String path = line.substring(line.indexOf("file=\"") + 6);
            path = path.substring(0, path.indexOf('"'));
            Path target = oldResources.resolve("db/changelog/" + path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, read("db/changelog/" + path));
        }
        for (String path : new String[]{FLAGS, QUANTITY}) {
            Path file = oldResources.resolve("db/changelog/" + path);
            Files.createDirectories(file.getParent());
            // Restore original SQL, producing exactly the pre-merge V9 checksums.
            String old = read("db/changelog/" + path).replace("ADD COLUMN IF NOT EXISTS first_day", "ADD COLUMN first_day");
            old = old.replaceAll("(?m)^--validCheckSum:.*\\R", "");
            Files.writeString(file, old);
        }
    }
    private String read(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) { return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
    }
    private void update(String schema, ResourceAccessor resources) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(schema);
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(new SingleConnectionDataSource(connection, true).getConnection()));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (var liquibase = new Liquibase(ROOT, resources, database)) { liquibase.validate(); liquibase.update(new Contexts(), new LabelExpression()); }
            finally { connection.setSchema("public"); }
        }
    }
}
