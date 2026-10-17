package ru.example.prodbackend.integration.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.support.AuthIntegrationTestSupport;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskFolderIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ProjectAccessService projectAccess;
    private String owner;
    private String member;
    private String outsider;
    private UUID projectId;
    private UUID otherProjectId;
    private String projectUrl;
    private String url;

    @BeforeEach
    void prepare() throws Exception {
        owner = registerUser().getAccessToken();
        member = registerUser("member@example.com").getAccessToken();
        outsider = registerUser("outsider@example.com").getAccessToken();
        projectId = id(call(owner, post("/api/move-projects"), Map.of("name", "Переезд", "moveDate", "2026-11-01T09:00:00Z",
                "includeFirstDayChecklist", false, "participants", List.of(Map.of("email", "member@example.com")))).andExpect(status().isCreated()));
        otherProjectId = id(call(outsider, post("/api/move-projects"), Map.of("name", "Другой", "moveDate", "2026-11-01T09:00:00Z",
                "includeFirstDayChecklist", false)).andExpect(status().isCreated()));
        projectUrl = "/api/move-projects/" + projectId;
        url = projectUrl + "/task-folders";
    }

    @Test
    void membersCreateReadAndEditOnAnotherDeviceAndNoOpsPreserveTimestamps() throws Exception {
        UUID folder = UUID.randomUUID();
        var created = json(call(member, post(url), body(folder, "  Документы  ", "mint")).andExpect(status().isCreated()));
        assertThat(created.get("projectId").asText()).isEqualTo(projectId.toString());
        assertThat(created.get("name").asText()).isEqualTo("Документы");
        assertThat(created.get("taskCount").asLong()).isZero();
        assertThat(created.get("completedTaskCount").asLong()).isZero();
        assertThat(Instant.parse(created.get("createdAt").asText())).isEqualTo(Instant.parse(created.get("updatedAt").asText()));
        String device = authenticateUser(EMAIL).getAccessToken();
        assertThat(json(call(device, get(url + "/" + folder), null).andExpect(status().isOk()))).isEqualTo(created);
        assertThat(json(call(owner, post(url), body(folder, "Документы", "mint")).andExpect(status().isOk()))).isEqualTo(created);
        assertThat(json(call(owner, patch(url + "/" + folder), Map.of()).andExpect(status().isOk()))).isEqualTo(created);
        assertThat(json(call(member, patch(url + "/" + folder), Map.of("name", " Документы ", "colorKey", "mint")).andExpect(status().isOk()))).isEqualTo(created);
        var changed = json(call(owner, patch(url + "/" + folder), Map.of("name", " Первый вечер ", "colorKey", "cyan")).andExpect(status().isOk()));
        assertThat(changed.get("id")).isEqualTo(created.get("id"));
        assertThat(changed.get("createdAt")).isEqualTo(created.get("createdAt"));
        assertThat(Instant.parse(changed.get("updatedAt").asText())).isAfter(Instant.parse(created.get("updatedAt").asText()));
        assertThat(changed.get("name").asText()).isEqualTo("Первый вечер");
        assertThat(changed.get("colorKey").asText()).isEqualTo("cyan");
        assertThat(json(call(member, get(url + "/" + folder), null).andExpect(status().isOk()))).isEqualTo(changed);
        assertThat(json(call(owner, patch(url + "/" + folder), Map.of("colorKey", "cyan")).andExpect(status().isOk()))).isEqualTo(changed);
        var renamed = json(call(member, patch(url + "/" + folder), Map.of("name", "Ещё вечер")).andExpect(status().isOk()));
        assertThat(renamed.get("colorKey").asText()).isEqualTo("cyan");
        var recolored = json(call(member, patch(url + "/" + folder), Map.of("colorKey", "coral")).andExpect(status().isOk()));
        assertThat(recolored.get("name").asText()).isEqualTo("Ещё вечер");
    }

    @Test
    void replayNeverOverwritesEditsOrAcceptsChangedOriginalPayload() throws Exception {
        UUID folder = create("Документы", "mint");
        assertErrorResponse(call(owner, post(url), body(folder, "Другое имя", "mint")), 409, "DATA_CONFLICT");
        assertErrorResponse(call(owner, post(url), body(folder, "Документы", "cyan")), 409, "DATA_CONFLICT");
        var edited = json(call(member, patch(url + "/" + folder), Map.of("name", "Моя правка", "colorKey", "forest")).andExpect(status().isOk()));
        assertErrorResponse(call(owner, post(url), body(folder, "Документы", "mint")), 409, "DATA_CONFLICT");
        assertErrorResponse(call(owner, post(url), body(folder, "Моя правка", "forest")), 409, "DATA_CONFLICT");
        assertThat(json(call(member, get(url + "/" + folder), null).andExpect(status().isOk()))).isEqualTo(edited);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_folders WHERE project_id = ?", Long.class, projectId)).isEqualTo(1);
    }

    @Test
    void namesMayRepeatAndPaginationUsesCreationTimeThenUuidEvenAfterRename() throws Exception {
        UUID high = UUID.fromString("20000000-0000-0000-0000-000000000000");
        UUID low = UUID.fromString("10000000-0000-0000-0000-000000000000");
        for (UUID id : List.of(high, low)) call(owner, post(url), body(id, "Одинаковые", "sand")).andExpect(status().isCreated());
        jdbc.update("UPDATE task_folders SET created_at = '2026-10-01T00:00:00Z' WHERE project_id = ?", projectId);
        UUID later = create("А в конце", "lilac");
        call(owner, patch(url + "/" + low), Map.of("name", "Я в начале")).andExpect(status().isOk());
        call(outsider, post(otherUrl()), body(UUID.randomUUID(), "Чужая", "forest")).andExpect(status().isCreated());
        call(member, get(url + "?page=0&size=1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(low.toString())).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3));
        call(owner, get(url + "?page=1&size=1"), null).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(high.toString()));
        call(owner, get(url + "?page=2&size=1"), null).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(later.toString()));
        call(owner, get(url + "?page=3&size=1"), null).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        call(owner, get(url), null).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20));
        for (String query : List.of("page=-1", "size=0", "size=101")) assertErrorResponse(call(owner, get(url + "?" + query), null), 422, "VALIDATION_ERROR");
        assertErrorResponse(call(owner, get(url + "?page=bad"), null), 422, "VALIDATION_ERROR");
    }

    @Test
    void invalidFieldsAreSpecificAndPatchIsAtomic() throws Exception {
        UUID folder = create("Имя", "mint");
        var before = json(call(owner, get(url + "/" + folder), null).andExpect(status().isOk()));
        for (String name : List.of("", " \t\n ", "x".repeat(81))) {
            assertFieldError(call(owner, post(url), body(UUID.randomUUID(), name, "mint")), "name");
            assertFieldError(call(owner, patch(url + "/" + folder), Map.of("name", name, "colorKey", "forest")), "name");
        }
        for (String color : List.of("", "#00ff00", "rgb(1,2,3)", "MINT", " mint ", "blue")) {
            assertFieldError(call(owner, post(url), body(UUID.randomUUID(), "Имя", color)), "colorKey");
            assertFieldError(call(owner, patch(url + "/" + folder), Map.of("name", "Новая правка", "colorKey", color)), "colorKey");
        }
        for (String field : List.of("name", "colorKey")) {
            assertFieldError(call(owner, patch(url + "/" + folder), "{\"" + field + "\":null}"), field);
            var absent = new HashMap<String, Object>(body(UUID.randomUUID(), "Имя", "mint"));
            absent.remove(field);
            assertFieldError(call(owner, post(url), absent), field);
            absent.put(field, null);
            assertFieldError(call(owner, post(url), absent), field);
        }
        assertFieldError(call(owner, post(url), Map.of("name", "Имя", "colorKey", "mint")), "id");
        assertFieldError(call(owner, post(url), "{\"id\":null,\"name\":\"Имя\",\"colorKey\":\"mint\"}"), "id");
        assertErrorResponse(call(owner, post(url), Map.of("id", "bad", "name", "Имя", "colorKey", "mint")), 400, "INVALID_REQUEST_BODY");
        assertErrorResponse(call(owner, patch(url + "/" + folder), Map.of("unknown", "value")), 400, "INVALID_REQUEST_BODY");
        assertThat(json(call(owner, get(url + "/" + folder), null).andExpect(status().isOk()))).isEqualTo(before);
        create(" " + "x".repeat(80) + " ", "mint");
        create("😀".repeat(80), "cyan");
        for (String color : List.of("mint", "cyan", "coral", "sand", "lilac", "forest")) create("Папка", color);
    }

    @Test
    void accessChecksHideForeignProjectsAndFoldersAndAllEndpointsRequireAuth() throws Exception {
        UUID folder = create("Своя", "mint");
        UUID foreign = UUID.randomUUID();
        call(outsider, post(otherUrl()), body(foreign, "Чужая", "mint")).andExpect(status().isCreated());
        for (String method : List.of("GET", "PATCH", "DELETE")) {
            Object requestBody = method.equals("PATCH") ? Map.of("name", "Правка") : null;
            assertErrorResponse(call(owner, request(HttpMethod.valueOf(method), url + "/" + foreign), requestBody), 404, "TASK_FOLDER_NOT_FOUND");
            assertErrorResponse(call(outsider, request(HttpMethod.valueOf(method), url + "/" + folder), requestBody), 404, "MOVE_PROJECT_NOT_FOUND");
            call(null, request(HttpMethod.valueOf(method), url + "/" + folder), requestBody).andExpect(status().isUnauthorized());
        }
        assertErrorResponse(call(outsider, get(url), null), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(call(outsider, post(url), body(UUID.randomUUID(), "Нет доступа", "mint")), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(call(owner, post(url), body(foreign, "Чужая", "mint")), 409, "DATA_CONFLICT");
        UUID missing = UUID.randomUUID();
        assertErrorResponse(call(owner, get(url + "/" + missing), null), 404, "TASK_FOLDER_NOT_FOUND");
        assertErrorResponse(call(owner, patch(url + "/" + missing), Map.of()), 404, "TASK_FOLDER_NOT_FOUND");
        call(owner, delete(url + "/" + missing), null).andExpect(status().isNoContent());
        call(null, get(url), null).andExpect(status().isUnauthorized());
        call(null, post(url), body(UUID.randomUUID(), "Без токена", "mint")).andExpect(status().isUnauthorized());
    }

    @Test
    void archiveAllowsReadsButForbidsAllWritesIncludingRetriesAndNoOps() throws Exception {
        UUID folder = create("Архив", "mint");
        var before = json(call(owner, get(url + "/" + folder), null).andExpect(status().isOk()));
        call(member, put(projectUrl + "/archive"), null).andExpect(status().isOk());
        call(member, get(url), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(json(call(member, get(url + "/" + folder), null).andExpect(status().isOk()))).isEqualTo(before);
        assertErrorResponse(call(member, post(url), body(folder, "Архив", "mint")), 409, "DATA_CONFLICT");
        assertErrorResponse(call(owner, post(url), body(UUID.randomUUID(), "Новая", "mint")), 409, "DATA_CONFLICT");
        assertErrorResponse(call(member, patch(url + "/" + folder), Map.of()), 409, "DATA_CONFLICT");
        assertErrorResponse(call(member, delete(url + "/" + folder), null), 409, "DATA_CONFLICT");
        assertErrorResponse(call(member, delete(url + "/" + UUID.randomUUID()), null), 409, "DATA_CONFLICT");
        call(member, delete(projectUrl + "/archive"), null).andExpect(status().isOk());
        call(member, patch(url + "/" + folder), Map.of("colorKey", "forest")).andExpect(status().isOk());
        call(member, delete(url + "/" + folder), null).andExpect(status().isNoContent());
        call(owner, delete(url + "/" + folder), null).andExpect(status().isNoContent());
    }

    @Test
    void countsSpanAllStagesAndDeletingPopulatedFolderPreservesEveryOtherTaskFieldAndProgress() throws Exception {
        UUID folder = create("Полная", "mint");
        UUID empty = create("Пустая", "cyan");
        List<UUID> taskIds = new ArrayList<>();
        for (String stage : List.of("BEFORE_MOVE", "MOVING_DAY", "AFTER_MOVE")) {
            UUID task = id(call(owner, post(projectUrl + "/tasks"), Map.of("title", stage, "stage", stage,
                    "description", "Сохранить", "dueType", "RELATIVE", "offsetDays", -2, "assigneeName", "Анна")).andExpect(status().isCreated()));
            taskIds.add(task);
            jdbc.update("UPDATE move_tasks SET folder_id = ? WHERE id = ?", folder, task);
        }
        call(owner, patch(projectUrl + "/tasks/" + taskIds.get(0) + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        UUID unfiled = id(call(owner, post(projectUrl + "/tasks"), Map.of("title", "Без папки", "stage", "BEFORE_MOVE")).andExpect(status().isCreated()));
        call(owner, get(url + "/" + folder), null).andExpect(status().isOk()).andExpect(jsonPath("$.taskCount").value(3)).andExpect(jsonPath("$.completedTaskCount").value(1));
        call(member, get(url), null).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].taskCount").value(3))
                .andExpect(jsonPath("$.content[0].completedTaskCount").value(1)).andExpect(jsonPath("$.content[1].taskCount").value(0));
        call(owner, get(url + "/" + empty), null).andExpect(status().isOk()).andExpect(jsonPath("$.taskCount").value(0)).andExpect(jsonPath("$.completedTaskCount").value(0));
        var tasks = jdbc.queryForList("SELECT * FROM move_tasks WHERE project_id = ? ORDER BY id", projectId);
        tasks.forEach(row -> row.put("folder_id", null));
        var progress = json(call(member, get("/api/projects/" + projectId + "/progress/tasks"), null).andExpect(status().isOk()));
        call(member, delete(url + "/" + folder), null).andExpect(status().isNoContent());
        call(owner, delete(url + "/" + folder), null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT * FROM move_tasks WHERE project_id = ? ORDER BY id", projectId)).isEqualTo(tasks);
        assertThat(json(call(owner, get("/api/projects/" + projectId + "/progress/tasks"), null).andExpect(status().isOk()))).isEqualTo(progress);
        for (UUID task : taskIds) call(owner, get(projectUrl + "/tasks/" + task), null).andExpect(status().isOk());
        call(owner, get(projectUrl + "/tasks/" + unfiled), null).andExpect(status().isOk());
    }

    @Test
    void concurrentRetriesProduceOneFolderAndOneCreationResponse() throws Exception {
        UUID folder = UUID.randomUUID();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> create = () -> { start.await(); return call(owner, post(url), body(folder, "Повтор", "mint")).andReturn().getResponse().getStatus(); };
            var one = pool.submit(create);
            var two = pool.submit(create);
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 201);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM task_folders WHERE id = ?", Long.class, folder)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void deletionPreservesTemplateOriginsAccountAssignmentAndApplicationId() throws Exception {
        UUID folder = create("Из шаблонов", "mint");
        UUID set = id(call(owner, post("/api/task-sets"), Map.of("name", "Набор", "items", List.of(
                Map.of("title", "Из набора", "description", "Описание", "stage", "AFTER_MOVE", "offsetDays", 2)))).andExpect(status().isCreated()));
        var application = json(call(owner, put(projectUrl + "/task-sets/" + set), null).andExpect(status().isOk()));
        UUID copied = UUID.fromString(application.get("tasks").get(0).get("id").asText());
        var templates = json(call(owner, get(projectUrl + "/tasks/templates"), null).andExpect(status().isOk()));
        UUID template = UUID.fromString(templates.get("content").get(0).get("id").asText());
        UUID assignee = userRepository.findByEmail("member@example.com").orElseThrow().getId();
        UUID single = id(call(owner, post(projectUrl + "/tasks/from-template"), Map.of("templateId", template,
                "assigneeId", assignee)).andExpect(status().isCreated()));
        call(owner, patch(projectUrl + "/tasks/" + copied + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        jdbc.update("UPDATE move_tasks SET folder_id = ? WHERE id IN (?, ?)", folder, copied, single);
        var before = jdbc.queryForList("SELECT * FROM move_tasks WHERE project_id = ? ORDER BY id", projectId);
        assertThat(before).anySatisfy(row -> assertThat(row.get("template_id")).isEqualTo(template));
        assertThat(before).anySatisfy(row -> assertThat(row.get("task_set_application_id")).isEqualTo(UUID.fromString(application.get("id").asText())));
        assertThat(before).anySatisfy(row -> assertThat(row.get("assignee_id")).isEqualTo(assignee));
        before.forEach(row -> row.put("folder_id", null));
        call(member, get(url + "/" + folder), null).andExpect(status().isOk()).andExpect(jsonPath("$.taskCount").value(2)).andExpect(jsonPath("$.completedTaskCount").value(1));
        call(member, delete(url + "/" + folder), null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT * FROM move_tasks WHERE project_id = ? ORDER BY id", projectId)).isEqualTo(before);
        call(owner, put(projectUrl + "/task-sets/" + set), null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(application.get("id").asText()))
                .andExpect(jsonPath("$.tasks[0].id").value(copied.toString()));
    }

    @Test
    void sameUuidRacingAcrossProjectsProducesOneWinnerWithoutServerError() throws Exception {
        UUID folder = UUID.randomUUID();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> { start.await(); return call(owner, post(url), body(folder, "Повтор", "mint")).andReturn().getResponse().getStatus(); });
            var two = pool.submit(() -> { start.await(); return call(outsider, post(otherUrl()), body(folder, "Повтор", "mint")).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void deletionRollsBackWithItsTaskChangesAndProjectDeletionCleansUpFolders() throws Exception {
        UUID folder = create("Откат", "mint");
        UUID task = id(call(owner, post(projectUrl + "/tasks"), Map.of("title", "Задача", "stage", "BEFORE_MOVE")).andExpect(status().isCreated()));
        jdbc.update("UPDATE move_tasks SET folder_id = ? WHERE id = ?", folder, task);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            jdbc.update("DELETE FROM task_folders WHERE id = ?", folder);
            assertThat(jdbc.queryForObject("SELECT folder_id FROM move_tasks WHERE id = ?", UUID.class, task)).isNull();
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT folder_id FROM move_tasks WHERE id = ?", UUID.class, task)).isEqualTo(folder);
        call(owner, get(url + "/" + folder), null).andExpect(status().isOk()).andExpect(jsonPath("$.taskCount").value(1));
        call(member, delete(projectUrl), null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_folders WHERE project_id = ?", Long.class, projectId)).isZero();
    }

    @Test
    void deletionWaitsForProjectLockAndObservesCommittedTask() throws Exception {
        UUID folder = create("Синхронизация", "mint");
        UUID task = id(call(owner, post(projectUrl + "/tasks"), Map.of("title", "Задача", "stage", "BEFORE_MOVE")).andExpect(status().isCreated()));
        UUID ownerId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var assigning = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                projectAccess.getAccessibleProjectForUpdate(projectId, ownerId);
                jdbc.update("UPDATE move_tasks SET folder_id = ? WHERE id = ?", folder, task);
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var deleting = pool.submit(() -> call(member, delete(url + "/" + folder), null).andExpect(status().isNoContent()));
            assertThatThrownBy(() -> deleting.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assigning.get(10, TimeUnit.SECONDS);
            deleting.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT folder_id FROM move_tasks WHERE id = ?", UUID.class, task)).isNull();
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void swaggerDescribesFolderApiErrorsPaginationAndSchemas() throws Exception {
        var docs = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        var list = docs.get("paths").get("/api/move-projects/{projectId}/task-folders");
        assertThat(list.get("post").get("responses").propertyNames()).contains("200", "201", "409", "422");
        assertThat(list.get("get").get("parameters").toString()).contains("page", "size");
        var detail = docs.get("paths").get("/api/move-projects/{projectId}/task-folders/{folderId}");
        assertThat(detail.get("delete").get("responses").propertyNames()).contains("204", "404", "409");
        assertThat(detail.get("patch").get("responses").propertyNames()).contains("200", "422");
        var schemas = docs.get("components").get("schemas");
        assertThat(schemas.get("TaskFolderResponse").get("properties").propertyNames()).containsExactlyInAnyOrder(
                "id", "projectId", "name", "colorKey", "taskCount", "completedTaskCount", "createdAt", "updatedAt");
        assertThat(schemas.get("CreateTaskFolderRequest").get("required").toString()).contains("id", "name", "colorKey");
        assertThat(schemas.get("UpdateTaskFolderRequest").get("properties").propertyNames()).containsExactlyInAnyOrder("name", "colorKey");
        assertThat(schemas.get("CreateTaskFolderRequest").get("properties").get("colorKey").toString()).contains("mint", "cyan", "coral", "sand", "lilac", "forest");
    }

    private String otherUrl() { return "/api/move-projects/" + otherProjectId + "/task-folders"; }
    private UUID create(String name, String color) throws Exception {
        UUID folder = UUID.randomUUID();
        call(owner, post(url), body(folder, name, color)).andExpect(status().isCreated());
        return folder;
    }
    private Map<String, Object> body(UUID id, String name, String color) { return Map.of("id", id, "name", name, "colorKey", color); }
    private void assertFieldError(ResultActions result, String field) throws Exception {
        assertErrorResponse(result, 422, "VALIDATION_ERROR").andExpect(jsonPath("$.fieldErrors[0].field").value(field));
    }
    private UUID id(ResultActions result) throws Exception { return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
