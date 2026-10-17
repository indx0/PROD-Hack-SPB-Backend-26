package ru.example.prodbackend.integration.checklist;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.example.prodbackend.support.AuthIntegrationTestSupport;
import ru.example.prodbackend.support.LegacyChecklistTestSupport;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FirstDayChecklistIntegrationTest extends AuthIntegrationTestSupport {
    private static final Instant CREATED = Instant.parse("2025-01-02T03:04:05.123456Z");
    private static final Instant UPDATED = CREATED.plusSeconds(50);
    private static final Instant COMPLETED = CREATED.plusSeconds(20);
    @Autowired private JdbcTemplate jdbc;
    private String token, url, itemsUrl;
    private UUID project, source, duplicate, item, box, room;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser().getAccessToken();
        project = id(call(post("/api/move-projects"), Map.of("name", "Старый план", "moveDate", "2026-11-01T09:00:00Z")));
        url = "/api/move-projects/" + project;
        room = id(call(post(url + "/rooms"), Map.of("name", "Кухня", "iconKey", "kitchen")));
        box = id(call(post("/api/projects/" + project + "/boxes"), Map.of("number", 1, "roomId", room)));
        itemsUrl = url + "/boxes/" + box + "/items";
        item = id(call(post(itemsUrl), Map.of("name", "Вещь", "firstDay", false)));
        source = UUID.randomUUID(); duplicate = UUID.randomUUID();
        jdbc.update("INSERT INTO first_day_checklist_items(id,project_id,name,item_id,completed_at,created_at,updated_at) VALUES (?,?,'  Одинаковое название  ',?,?,?,?)",
                source, project, item, Timestamp.from(COMPLETED), Timestamp.from(CREATED), Timestamp.from(UPDATED));
        jdbc.update("INSERT INTO first_day_checklist_items(id,project_id,name,created_at,updated_at) VALUES (?,?,'  Одинаковое название  ',?,?)",
                duplicate, project, Timestamp.from(CREATED.plusSeconds(1)), Timestamp.from(UPDATED));
    }

    @Test
    void backfillPreservesAllFieldsAndCountsOnlyRealTasks() throws Exception {
        var sources = jdbc.queryForList("SELECT * FROM first_day_checklist_items ORDER BY id");
        var inventory = jdbc.queryForList("SELECT * FROM items");
        var boxes = jdbc.queryForList("SELECT * FROM boxes");
        LegacyChecklistTestSupport.backfill(jdbc);
        assertThat(jdbc.queryForList("SELECT * FROM first_day_checklist_items ORDER BY id")).isEqualTo(sources);
        assertThat(jdbc.queryForList("SELECT * FROM items")).isEqualTo(inventory);
        assertThat(jdbc.queryForList("SELECT * FROM boxes")).isEqualTo(boxes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_folders", Long.class)).isEqualTo(1);
        var folder = jdbc.queryForMap("SELECT * FROM task_folders");
        assertThat(folder).containsEntry("name", "Личные задачи").containsEntry("color_key", "mint");
        for (UUID task : List.of(source, duplicate)) {
            var row = jdbc.queryForMap("SELECT * FROM move_tasks WHERE id=?", task);
            assertThat(row).containsEntry("title", "  Одинаковое название  ").containsEntry("stage", "BEFORE_MOVE")
                    .containsEntry("due_type", "FIXED").containsEntry("due_date", null).containsEntry("offset_days", null)
                    .containsEntry("assignee_id", null).containsEntry("assignee_name", null).containsEntry("description", null)
                    .containsEntry("template_id", null).containsEntry("task_set_application_id", null).containsEntry("task_set_position", null)
                    .containsEntry("folder_id", folder.get("id")).containsEntry("updated_at", Timestamp.from(UPDATED));
        }
        assertThat(jdbc.queryForMap("SELECT * FROM move_tasks WHERE id=?", source)).containsEntry("completed_at", Timestamp.from(COMPLETED))
                .containsEntry("created_at", Timestamp.from(CREATED)).containsEntry("linked_item_id", item);
        assertThat(jdbc.queryForMap("SELECT * FROM move_tasks WHERE id=?", duplicate)).containsEntry("completed_at", null).containsEntry("linked_item_id", null);
        assertThat(jdbc.queryForMap("SELECT source_count,completed_count FROM first_day_checklist_migrations"))
                .containsEntry("source_count", 2L).containsEntry("completed_count", 1L);
        call(get("/api/projects/" + project + "/progress/tasks"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2)).andExpect(jsonPath("$.completedTasks").value(1));
        var tasks = jdbc.queryForList("SELECT * FROM move_tasks ORDER BY id");
        var archive = jdbc.queryForList("SELECT * FROM first_day_checklist_archive ORDER BY id");
        LegacyChecklistTestSupport.backfill(jdbc);
        assertThat(jdbc.queryForList("SELECT * FROM move_tasks ORDER BY id")).isEqualTo(tasks);
        assertThat(jdbc.queryForList("SELECT * FROM first_day_checklist_archive ORDER BY id")).isEqualTo(archive);
    }

    @Test
    void uuidConflictUsesDurableMappingAndNeverMatchesByTitle() throws Exception {
        jdbc.update("INSERT INTO move_tasks(id,project_id,title,stage,created_at,updated_at) VALUES (?,?,'Чужая задача','AFTER_MOVE',now(),now())", source, project);
        var existing = jdbc.queryForMap("SELECT * FROM move_tasks WHERE id=?", source);
        LegacyChecklistTestSupport.backfill(jdbc);
        UUID mapped = jdbc.queryForObject("SELECT target_task_id FROM first_day_checklist_archive WHERE id=?", UUID.class, source);
        assertThat(mapped).isNotEqualTo(source);
        assertThat(jdbc.queryForMap("SELECT * FROM move_tasks WHERE id=?", source)).isEqualTo(existing);
        assertThat(jdbc.queryForObject("SELECT completed_at FROM move_tasks WHERE id=?", Timestamp.class, mapped)).isEqualTo(Timestamp.from(COMPLETED));
        jdbc.update("DELETE FROM move_tasks WHERE id=?", mapped);
        LegacyChecklistTestSupport.backfill(jdbc);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks WHERE id=?", Long.class, mapped)).isZero();
        assertThat(jdbc.queryForObject("SELECT target_task_id FROM first_day_checklist_archive WHERE id=?", UUID.class, source)).isEqualTo(mapped);
    }

    @Test
    void historicalTargetUuidRemainsReservedAfterItsTaskWasDeleted() {
        UUID historicalSource = UUID.randomUUID(), historicalProject = UUID.randomUUID(), historicalFolder = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO move_projects(id,owner_id,name,move_date,created_at,updated_at)
                SELECT ?,owner_id,'Исторический проект',move_date,now(),now() FROM move_projects WHERE id=?
                """, historicalProject, project);
        jdbc.update("INSERT INTO first_day_checklist_migrations(project_id,folder_id,source_count,completed_count,migrated_at) VALUES (?,?,1,0,now())",
                historicalProject, historicalFolder);
        jdbc.update("""
                INSERT INTO first_day_checklist_archive(id,project_id,target_task_id,folder_id,name,created_at,updated_at)
                VALUES (?,?,?,?, 'Удалённая историческая задача',now(),now())
                """, historicalSource, historicalProject, source, historicalFolder);
        LegacyChecklistTestSupport.backfill(jdbc);
        UUID mapped = jdbc.queryForObject("SELECT target_task_id FROM first_day_checklist_archive WHERE id=?", UUID.class, source);
        assertThat(mapped).isNotEqualTo(source);
        assertThat(jdbc.queryForObject("SELECT target_task_id FROM first_day_checklist_archive WHERE id=?", UUID.class, historicalSource)).isEqualTo(source);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks WHERE id=?", Long.class, source)).isZero();
    }

    @Test
    void deletingOrMovingTasksAndDeletingFolderNeverResurrectsOrReattaches() throws Exception {
        LegacyChecklistTestSupport.backfill(jdbc);
        UUID oldFolder = jdbc.queryForObject("SELECT folder_id FROM first_day_checklist_migrations", UUID.class);
        UUID otherFolder = UUID.randomUUID();
        call(post(url + "/task-folders"), Map.of("id", otherFolder, "name", "Своя папка", "colorKey", "coral")).andExpect(status().isCreated());
        call(patch(url + "/tasks/" + source), Map.of("title", "Изменено", "folderId", otherFolder)).andExpect(status().isOk());
        call(delete(url + "/tasks/" + duplicate), null).andExpect(status().isNoContent());
        call(delete(url + "/task-folders/" + oldFolder), null).andExpect(status().isNoContent());
        LegacyChecklistTestSupport.backfill(jdbc);
        assertThat(jdbc.queryForMap("SELECT title,folder_id FROM move_tasks WHERE id=?", source)).containsEntry("title", "Изменено").containsEntry("folder_id", otherFolder);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_folders WHERE id=?", Long.class, oldFolder)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_archive", Long.class)).isEqualTo(2);
        call(delete(url), null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_archive", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_migrations", Long.class)).isZero();
    }

    @Test
    void archiveSnapshotSurvivesInventoryMovesRenamesFlagChangesAndDeletion() throws Exception {
        LegacyChecklistTestSupport.backfill(jdbc);
        var before = json(call(get(url + "/first-day-checklist"), null));
        UUID otherRoom = id(call(post(url + "/rooms"), Map.of("name", "Спальня")));
        UUID otherBox = id(call(post("/api/projects/" + project + "/boxes"), Map.of("number", 2, "roomId", otherRoom)));
        call(patch(itemsUrl + "/" + item), Map.of("name", "Новое имя", "firstDay", true)).andExpect(status().isOk());
        call(put(itemsUrl + "/" + item + "/box"), Map.of("targetBoxId", otherBox)).andExpect(status().isOk());
        var task = jdbc.queryForObject("SELECT linked_item_id FROM move_tasks WHERE id=?", UUID.class, source);
        assertThat(task).isEqualTo(item);
        var current = json(call(get(url + "/tasks"), null)).get("content");
        assertThat(current.toString()).contains("Новое имя", otherBox.toString(), "Спальня");
        call(delete(url + "/boxes/" + otherBox + "/items/" + item), null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT linked_item_id FROM move_tasks WHERE id=?", UUID.class, source)).isNull();
        assertThat(json(call(get(url + "/first-day-checklist"), null))).isEqualTo(before);
        assertThat(before.get("content").get(0).get("itemId").asText()).isEqualTo(item.toString());
        assertThat(before.get("content").get(0).get("roomName").asText()).isEqualTo("Кухня");
    }

    @Test
    void archivedProjectsAreBackfilledButNormalWritesStayBlockedUntilRestore() throws Exception {
        call(put(url + "/archive"), null).andExpect(status().isOk());
        LegacyChecklistTestSupport.backfill(jdbc);
        call(get(url + "/tasks"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        call(patch(url + "/tasks/" + source), Map.of("title", "Недоступно")).andExpect(status().isConflict());
        call(delete(url + "/archive"), null).andExpect(status().isOk());
        call(patch(url + "/tasks/" + source), Map.of("title", "Доступно")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PATCH", "DELETE", "PUT_LINK", "DELETE_LINK"})
    void everyLegacyWriteReturns410ForOwnerAndParticipantButDoesNotExposeForeignProject(String operation) throws Exception {
        LegacyChecklistTestSupport.backfill(jdbc);
        var rows = jdbc.queryForList("SELECT * FROM first_day_checklist_archive ORDER BY id");
        var tasks = jdbc.queryForList("SELECT * FROM move_tasks ORDER BY id");
        String foreign = registerUser("foreign@example.com").getAccessToken();
        String member = registerUser("member@example.com").getAccessToken();
        call(post(url + "/participants"), Map.of("email", "member@example.com")).andExpect(status().isOk());
        String path = url + "/first-day-checklist" + (operation.equals("POST") ? "" : "/" + source)
                + (operation.endsWith("LINK") ? "/item" : "");
        String method = operation.startsWith("PUT") ? "PUT" : operation.startsWith("DELETE") ? "DELETE" : operation;
        for (String access : List.of(token, member)) {
            for (boolean archived : List.of(false, true)) {
                if (archived) call(put(url + "/archive"), null).andExpect(status().isOk());
                mockMvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("FIRST_DAY_CHECKLIST_RETIRED"));
                if (archived) call(delete(url + "/archive"), null).andExpect(status().isOk());
            }
        }
        mockMvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MOVE_PROJECT_NOT_FOUND"));
        mockMvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForList("SELECT * FROM first_day_checklist_archive ORDER BY id")).isEqualTo(rows);
        assertThat(jdbc.queryForList("SELECT * FROM move_tasks ORDER BY id")).isEqualTo(tasks);
    }

    @Test
    void getIsPaginatedDeprecatedAndNeverMigratesOnReadAndEmptyProjectsGetNoFolder() throws Exception {
        call(get(url + "/first-day-checklist"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks", Long.class)).isZero();
        UUID empty = id(call(post("/api/move-projects"), Map.of("name", "Пустой", "moveDate", "2026-11-01T09:00:00Z")));
        LegacyChecklistTestSupport.backfill(jdbc);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_folders WHERE project_id=?", Long.class, empty)).isZero();
        call(get(url + "/first-day-checklist").param("page", "1").param("size", "1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].id").value(duplicate.toString()));
        call(get(url + "/first-day-checklist").param("size", "101"), null).andExpect(status().isUnprocessableEntity());
        var spec = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        var paths = spec.get("paths");
        for (var entry : List.of(Map.entry("/api/move-projects/{projectId}/first-day-checklist", "post"),
                Map.entry("/api/move-projects/{projectId}/first-day-checklist/{itemId}", "patch"),
                Map.entry("/api/move-projects/{projectId}/first-day-checklist/{itemId}", "delete"),
                Map.entry("/api/move-projects/{projectId}/first-day-checklist/{checklistItemId}/item", "put"),
                Map.entry("/api/move-projects/{projectId}/first-day-checklist/{checklistItemId}/item", "delete"))) {
            var endpoint = paths.get(entry.getKey()).get(entry.getValue());
            assertThat(endpoint.get("deprecated").asBoolean()).isTrue();
            assertThat(endpoint.get("responses").get("410").get("description").asText()).contains("FIRST_DAY_CHECKLIST_RETIRED");
        }
        assertThat(paths.get("/api/move-projects/{projectId}/first-day-checklist").get("get").get("description").asText()).contains("Архивный снимок", "GET ничего не переносит");
    }

    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
    private UUID id(ResultActions result) throws Exception { result.andExpect(status().isCreated()); return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
}
