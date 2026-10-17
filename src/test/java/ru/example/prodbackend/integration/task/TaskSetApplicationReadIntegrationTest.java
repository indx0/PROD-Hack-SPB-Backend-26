package ru.example.prodbackend.integration.task;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.example.prodbackend.support.AuthIntegrationTestSupport;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskSetApplicationReadIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    private String owner, member, projectUrl, applicationsUrl;
    private UUID project, folder, secondFolder, set, secondSet;

    @BeforeEach
    void prepare() throws Exception {
        owner = registerUser().getAccessToken();
        member = registerUser("member@example.com").getAccessToken();
        set = createSet(owner, "Первый шаблон");
        secondSet = createSet(owner, "Второй шаблон");
        folder = UUID.randomUUID();
        secondFolder = UUID.randomUUID();
        project = id(call(owner, post("/api/move-projects"), Map.of(
                "name", "Атомарный план", "moveDate", "2026-11-01T09:00:00Z",
                "participants", List.of(Map.of("email", "member@example.com")),
                "folders", List.of(folder(folder, "Подготовка", "mint"), folder(secondFolder, "После переезда", "cyan")),
                "taskSetApplications", List.of(Map.of("taskSetId", set, "folderId", folder), Map.of("taskSetId", secondSet))))
                .andExpect(status().isCreated()));
        projectUrl = "/api/move-projects/" + project;
        applicationsUrl = projectUrl + "/task-set-applications";
    }

    @Test
    void ownerAndParticipantConfirmAtomicCreationUsingSameTokenAsTaskReadWithoutAnyWrites() throws Exception {
        var before = snapshot();
        var expected = jdbc.queryForList("SELECT id FROM task_set_applications WHERE project_id=? ORDER BY applied_at,id", UUID.class, project);
        for (String token : List.of(owner, member)) {
            assertErrorResponse(call(member, get("/api/task-sets/" + set), null), 404, "TASK_SET_NOT_FOUND");
            String taskId = jdbc.queryForObject("SELECT id FROM move_tasks WHERE project_id=? LIMIT 1", UUID.class, project).toString();
            call(token, get(projectUrl + "/tasks/" + taskId), null).andExpect(status().isOk());
            for (int repeat = 0; repeat < 2; repeat++) {
                var page = json(call(token, get(applicationsUrl), null).andExpect(status().isOk()));
                assertThat(page.get("page").asInt()).isZero();
                assertThat(page.get("size").asInt()).isEqualTo(20);
                assertThat(page.get("totalElements").asInt()).isEqualTo(2);
                assertThat(page.get("totalPages").asInt()).isEqualTo(1);
                assertThat(ids(page.get("content"))).containsExactlyElementsOf(expected);
                for (var application : page.get("content")) {
                    boolean first = application.get("taskSetId").asText().equals(set.toString());
                    assertThat(application.get("projectId").asText()).isEqualTo(project.toString());
                    assertThat(application.get("taskSetName").asText()).isEqualTo(first ? "Первый шаблон" : "Второй шаблон");
                    assertThat(application.get("appliedAt").isTextual()).isTrue();
                    assertThat(application.has("folderId")).isTrue();
                    if (first) assertThat(application.get("folderId").asText()).isEqualTo(folder.toString());
                    else assertThat(application.get("folderId").isNull()).isTrue();
                    assertThat(application.get("tasks").size()).isEqualTo(3);
                    List<String> titles = new ArrayList<>();
                    for (var task : application.get("tasks")) {
                        titles.add(task.get("title").asText());
                        assertThat(task.get("taskSetApplicationId")).isEqualTo(application.get("id"));
                        assertThat(task.get("folderId")).isEqualTo(application.get("folderId"));
                    }
                    assertThat(titles).containsExactly("Вторая", "Первая", "Третья");
                }
            }
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void readsHistoricalFolderAndOnlyCurrentTasksAfterMovesDeletionAndTemplateChanges() throws Exception {
        var first = application(set);
        var second = application(secondSet);
        String moved = first.get("tasks").get(0).get("id").asText();
        String unfiled = first.get("tasks").get(1).get("id").asText();
        String removed = first.get("tasks").get(2).get("id").asText();
        call(member, patch(projectUrl + "/tasks/" + moved), Map.of("folderId", secondFolder, "title", "Изменено вручную", "description", "Сохранить изменения",
                "dueType", "FIXED", "dueDate", "2026-12-03", "assigneeId", userRepository.findByEmail("member@example.com").orElseThrow().getId()))
                .andExpect(status().isOk());
        call(member, patch(projectUrl + "/tasks/" + moved + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        call(member, delete(projectUrl + "/tasks/" + removed), null).andExpect(status().isNoContent());
        call(member, delete(projectUrl + "/task-folders/" + folder), null).andExpect(status().isNoContent());
        call(owner, put("/api/task-sets/" + set), setBody("Другое название каталога")).andExpect(status().isOk());
        call(owner, delete("/api/task-sets/" + set), null).andExpect(status().isNoContent());
        for (var task : second.get("tasks")) call(member, delete(projectUrl + "/tasks/" + task.get("id").asText()), null).andExpect(status().isNoContent());
        var currentMoved = json(call(member, get(projectUrl + "/tasks/" + moved), null).andExpect(status().isOk()));
        var currentUnfiled = json(call(member, get(projectUrl + "/tasks/" + unfiled), null).andExpect(status().isOk()));
        var before = snapshot();
        var read = application(set);
        assertThat(read.get("id")).isEqualTo(first.get("id"));
        assertThat(read.get("appliedAt")).isEqualTo(first.get("appliedAt"));
        assertThat(read.get("taskSetName").asText()).isEqualTo("Первый шаблон");
        assertThat(read.get("folderId").asText()).isEqualTo(folder.toString());
        assertThat(read.get("tasks").size()).isEqualTo(2);
        assertThat(read.get("tasks").get(0)).isEqualTo(currentMoved);
        assertThat(read.get("tasks").get(1)).isEqualTo(currentUnfiled);
        assertThat(read.get("tasks").get(0).get("folderId").asText()).isEqualTo(secondFolder.toString());
        assertThat(read.get("tasks").get(1).get("folderId").isNull()).isTrue();
        assertThat(application(secondSet).get("tasks").isEmpty()).isTrue();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_folders WHERE id=?", Long.class, folder)).isZero();
    }

    @Test
    void readsApplicationsOfOtherMembersAndArchivedProjectsWithoutAllowingMutations() throws Exception {
        UUID memberSet = createSet(member, "Личный набор участника");
        call(member, put(projectUrl + "/task-sets/" + memberSet), Map.of("folderId", secondFolder)).andExpect(status().isOk());
        call(member, put(projectUrl + "/archive"), null).andExpect(status().isOk());
        var before = snapshot();
        for (String token : List.of(owner, member)) {
            var page = json(call(token, get(applicationsUrl), null).andExpect(status().isOk()));
            assertThat(page.get("totalElements").asInt()).isEqualTo(3);
            List<String> setIds = new ArrayList<>();
            for (var application : page.get("content")) setIds.add(application.get("taskSetId").asText());
            assertThat(setIds).containsExactlyInAnyOrder(set.toString(), secondSet.toString(), memberSet.toString());
        }
        assertErrorResponse(call(member, put(projectUrl + "/task-sets/" + memberSet), null), 409, "DATA_CONFLICT");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void linkedItemProjectionIsCurrentAndUsesTheSameTaskResponse() throws Exception {
        String taskId = application(set).get("tasks").get(0).get("id").asText();
        UUID room = id(call(owner, post(projectUrl + "/rooms"), Map.of("name", "Кухня", "iconKey", "kitchen")).andExpect(status().isCreated()));
        UUID box = id(call(owner, post("/api/projects/" + project + "/boxes"), Map.of("roomId", room, "number", 1)).andExpect(status().isCreated()));
        UUID item = id(call(owner, post(projectUrl + "/boxes/" + box + "/items"), Map.of("name", "Зарядка")).andExpect(status().isCreated()));
        call(owner, patch(projectUrl + "/tasks/" + taskId), Map.of("linkedItemId", item)).andExpect(status().isOk());
        call(owner, patch(projectUrl + "/boxes/" + box + "/items/" + item), Map.of("name", "Новое название вещи")).andExpect(status().isOk());
        var currentTask = json(call(owner, get(projectUrl + "/tasks/" + taskId), null).andExpect(status().isOk()));
        assertThat(application(set).get("tasks").get(0)).isEqualTo(currentTask);
        assertThat(currentTask.get("linkedItemName").asText()).isEqualTo("Новое название вещи");
        assertThat(currentTask.get("roomIconKey").asText()).isEqualTo("kitchen");
        call(owner, delete(projectUrl + "/boxes/" + box + "/items/" + item), null).andExpect(status().isNoContent());
        assertThat(application(set).get("tasks").get(0).get("linkedItemId").isNull()).isTrue();
    }

    @Test
    void paginatesAtDatabaseLevelWithStableTimestampAndUuidOrderingAndEmptyPages() throws Exception {
        jdbc.update("UPDATE task_set_applications SET applied_at=? WHERE task_set_id=?", Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")), secondSet);
        jdbc.update("UPDATE task_set_applications SET applied_at=? WHERE task_set_id=?", Timestamp.from(Instant.parse("2026-10-02T00:00:00Z")), set);
        call(member, get(applicationsUrl).param("page", "0").param("size", "1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskSetId").value(secondSet.toString())).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        call(owner, get(applicationsUrl).param("page", "1").param("size", "1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskSetId").value(set.toString()));
        call(owner, get(applicationsUrl).param("page", "2").param("size", "1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        jdbc.update("UPDATE task_set_applications SET applied_at=? WHERE project_id=?", Timestamp.from(Instant.parse("2026-10-03T00:00:00Z")), project);
        var expected = jdbc.queryForList("SELECT id FROM task_set_applications WHERE project_id=? ORDER BY applied_at,id", UUID.class, project);
        var page = json(call(owner, get(applicationsUrl).param("size", "100"), null).andExpect(status().isOk()));
        assertThat(ids(page.get("content"))).containsExactlyElementsOf(expected);
        UUID empty = id(call(owner, post("/api/move-projects"), Map.of("name", "Пустой переезд", "moveDate", "2026-11-01T09:00:00Z")).andExpect(status().isCreated()));
        call(owner, get("/api/move-projects/" + empty + "/task-set-applications"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=invalid", "size=invalid"})
    void rejectsInvalidPaginationWithoutMutations(String query) throws Exception {
        var before = snapshot();
        assertErrorResponse(call(owner, get(applicationsUrl + "?" + query), null), 422, "VALIDATION_ERROR");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void requiresValidSessionAndProjectMembershipAndHidesMissingProjects() throws Exception {
        String outsider = registerUser("outsider@example.com").getAccessToken();
        assertErrorResponse(call(null, get(applicationsUrl), null), 401, "UNAUTHORIZED");
        assertErrorResponse(call("invalid-token", get(applicationsUrl), null), 401, "UNAUTHORIZED");
        assertErrorResponse(call(outsider, get(applicationsUrl), null), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(call(owner, get("/api/move-projects/" + UUID.randomUUID() + "/task-set-applications"), null), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(call(owner, get("/api/move-projects/not-a-uuid/task-set-applications"), null), 400, "INVALID_REQUEST_PARAMETER");
    }

    @Test
    void publishesExactPaginatedReadContractInOpenApiAlongsideExistingApply() throws Exception {
        var api = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        var operation = api.get("paths").get("/api/move-projects/{projectId}/task-set-applications").get("get");
        assertThat(operation.get("security").toString()).contains("jwtAuth");
        assertThat(operation.path("requestBody").isMissingNode()).isTrue();
        assertThat(operation.get("description").asText()).contains("appliedAt ASC, id ASC", "исторический", "архивного", "не восстанавливает");
        var response = operation.get("responses").get("200").get("content").get("application/json").get("schema");
        String reference = response.get("$ref").asText();
        var properties = api.get("components").get("schemas").get(reference.substring(reference.lastIndexOf('/') + 1)).get("properties");
        assertThat(properties.propertyNames()).containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
        assertThat(properties.get("content").get("items").get("$ref").asText()).isEqualTo("#/components/schemas/TaskSetApplicationResponse");
        for (String status : List.of("400", "401", "404", "422")) assertThat(operation.get("responses").has(status)).isTrue();
        Map<String, JsonNode> parameters = new HashMap<>();
        for (var parameter : operation.get("parameters")) parameters.put(parameter.get("name").asText(), parameter);
        assertThat(parameters.keySet()).containsExactlyInAnyOrder("projectId", "page", "size");
        assertThat(parameters.get("page").get("schema").get("default").asInt()).isZero();
        assertThat(parameters.get("size").get("schema").get("default").asInt()).isEqualTo(20);
        assertThat(api.get("paths").get("/api/move-projects/{projectId}/task-sets/{setId}").has("put")).isTrue();
    }

    private JsonNode application(UUID setId) throws Exception {
        var page = json(call(member, get(applicationsUrl), null).andExpect(status().isOk()));
        for (var application : page.get("content")) if (application.get("taskSetId").asText().equals(setId.toString())) return application;
        throw new AssertionError("Missing application for " + setId);
    }
    private UUID createSet(String token, String name) throws Exception { return id(call(token, post("/api/task-sets"), setBody(name)).andExpect(status().isCreated())); }
    private Object setBody(String name) {
        return Map.of("name", name, "items", List.of(
                Map.of("title", "Вторая", "stage", "BEFORE_MOVE", "offsetDays", -2),
                Map.of("title", "Первая", "stage", "MOVING_DAY", "offsetDays", 0),
                Map.of("title", "Третья", "stage", "AFTER_MOVE", "offsetDays", 1)));
    }
    private Object folder(UUID id, String name, String color) { return Map.of("id", id, "name", name, "colorKey", color); }
    private List<UUID> ids(JsonNode nodes) { var ids = new ArrayList<UUID>(); for (var node : nodes) ids.add(UUID.fromString(node.get("id").asText())); return ids; }
    private Map<String, List<Map<String, Object>>> snapshot() {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("move_projects", "project_participants", "task_folders", "task_set_applications", "move_tasks", "task_sets", "task_set_items", "project_events", "items", "boxes", "rooms"))
            snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1,2"));
        return snapshot;
    }
    private UUID id(ResultActions result) throws Exception { return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
