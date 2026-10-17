package ru.example.prodbackend.integration.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.example.prodbackend.support.AuthIntegrationTestSupport;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskSetFolderIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    private String owner;
    private String member;
    private UUID project;
    private UUID folder;
    private UUID secondFolder;
    private UUID foreignFolder;
    private UUID set;
    private String projectUrl;
    private String applicationUrl;

    @BeforeEach
    void prepare() throws Exception {
        owner = registerUser().getAccessToken();
        member = registerUser("member@example.com").getAccessToken();
        project = id(call(owner, post("/api/move-projects"), Map.of("name", "Переезд", "moveDate", "2026-11-01T09:00:00Z",
                "includeFirstDayChecklist", false, "participants", List.of(Map.of("email", "member@example.com")))).andExpect(status().isCreated()));
        UUID otherProject = id(call(owner, post("/api/move-projects"), Map.of("name", "Другой переезд", "moveDate", "2026-11-01T09:00:00Z",
                "includeFirstDayChecklist", false)).andExpect(status().isCreated()));
        projectUrl = "/api/move-projects/" + project;
        folder = createFolder(project, "Документы", "mint");
        secondFolder = createFolder(project, "Кухня", "cyan");
        foreignFolder = createFolder(otherProject, "Другой переезд", "forest");
        set = id(call(owner, post("/api/task-sets"), setBody("Мой шаблон")).andExpect(status().isCreated()));
        applicationUrl = projectUrl + "/task-sets/" + set;
    }

    @Test
    void appliesAllRelativeTasksToChosenFolderAndReturnsPersistedHistoricalDestination() throws Exception {
        var template = json(call(owner, get("/api/task-sets/" + set), null).andExpect(status().isOk()));
        var first = json(apply(Map.of("folderId", folder)).andExpect(status().isOk()));
        assertThat(first.get("folderId").asText()).isEqualTo(folder.toString());
        assertThat(first.get("taskSetId").asText()).isEqualTo(set.toString());
        assertThat(first.get("tasks").size()).isEqualTo(3);
        int[] offsets = {-2, 0, 3};
        for (int i = 0; i < 3; i++) {
            var task = first.get("tasks").get(i);
            assertThat(task.get("folderId").asText()).isEqualTo(folder.toString());
            assertThat(task.get("dueType").asText()).isEqualTo("RELATIVE");
            assertThat(task.get("offsetDays").asInt()).isEqualTo(offsets[i]);
            assertThat(task.get("dueDate").asText()).isEqualTo(LocalDate.parse("2026-11-01").plusDays(offsets[i]).toString());
            assertThat(task.get("completed").asBoolean()).isFalse();
            assertThat(task.get("assigneeId").isNull()).isTrue();
            assertThat(task.get("taskSetApplicationId")).isEqualTo(first.get("id"));
        }
        assertThat(json(apply(Map.of("folderId", folder)).andExpect(status().isOk()))).isEqualTo(first);
        assertThat(json(apply(null).andExpect(status().isOk()))).isEqualTo(first);
        assertThat(json(apply(Map.of()).andExpect(status().isOk()))).isEqualTo(first);
        assertThat(json(call(owner, get("/api/task-sets/" + set), null).andExpect(status().isOk()))).isEqualTo(template);
        call(member, get(projectUrl + "/task-folders/" + folder), null).andExpect(status().isOk()).andExpect(jsonPath("$.taskCount").value(3));
    }

    @Test
    void newFolderKeepsOwnNameAndColorIndependentOfTemplateEditsAndDeletion() throws Exception {
        UUID custom = createFolder(project, "Первый вечер", "lilac");
        var first = json(apply(Map.of("folderId", custom)).andExpect(status().isOk()));
        var tasks = taskRows();
        call(owner, put("/api/task-sets/" + set), Map.of("name", "Другой шаблон", "items", List.of(item("Замена", "AFTER_MOVE", 7)))).andExpect(status().isOk());
        call(owner, delete("/api/task-sets/" + set), null).andExpect(status().isNoContent());
        assertThat(json(apply(Map.of("folderId", custom)).andExpect(status().isOk()))).isEqualTo(first);
        assertThat(taskRows()).isEqualTo(tasks);
        call(member, get(projectUrl + "/task-folders/" + custom), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Первый вечер")).andExpect(jsonPath("$.colorKey").value("lilac"));
    }

    @Test
    void repeatsReturnCurrentRemainingTasksWithoutUndoingManualMovesEditsCompletionOrDeletion() throws Exception {
        var first = json(apply(Map.of("folderId", folder)).andExpect(status().isOk()));
        String removed = first.get("tasks").get(0).get("id").asText();
        String moved = first.get("tasks").get(1).get("id").asText();
        String unfiled = first.get("tasks").get(2).get("id").asText();
        UUID assignee = userRepository.findByEmail("member@example.com").orElseThrow().getId();
        call(member, patch(projectUrl + "/tasks/" + moved), Map.of("folderId", secondFolder, "title", "Моя правка", "description", "Моё описание",
                "dueDate", "2026-12-01", "assigneeId", assignee)).andExpect(status().isOk());
        call(member, patch(projectUrl + "/tasks/" + moved + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        call(member, patch(projectUrl + "/tasks/" + unfiled), "{\"folderId\":null}").andExpect(status().isOk());
        call(member, delete(projectUrl + "/tasks/" + removed), null).andExpect(status().isNoContent());
        var before = taskRows();
        for (Object request : List.of(Map.of("folderId", folder), Map.of())) {
            var repeat = json(apply(request).andExpect(status().isOk()));
            assertThat(repeat.get("id")).isEqualTo(first.get("id"));
            assertThat(repeat.get("appliedAt")).isEqualTo(first.get("appliedAt"));
            assertThat(repeat.get("folderId").asText()).isEqualTo(folder.toString());
            assertThat(repeat.get("tasks").size()).isEqualTo(2);
            assertThat(repeat.get("tasks").get(0).get("id").asText()).isEqualTo(moved);
            assertThat(repeat.get("tasks").get(0).get("folderId").asText()).isEqualTo(secondFolder.toString());
            assertThat(repeat.get("tasks").get(1).get("folderId").isNull()).isTrue();
            assertThat(taskRows()).isEqualTo(before);
        }
        apply(null).andExpect(status().isOk()).andExpect(jsonPath("$.tasks.length()").value(2));
        call(member, get("/api/projects/" + project + "/progress/tasks"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2)).andExpect(jsonPath("$.completedTasks").value(1));
    }

    @Test
    void explicitDifferentDestinationIncludingNullConflictsWithoutChangingAnyTasksOrApplication() throws Exception {
        var first = json(apply(Map.of("folderId", folder)).andExpect(status().isOk()));
        var before = taskRows();
        for (Object body : List.of(Map.of("folderId", secondFolder), Map.of("folderId", foreignFolder), Map.of("folderId", UUID.randomUUID()), "{\"folderId\":null}")) {
            assertErrorResponse(apply(body), 409, "TASK_SET_ALREADY_APPLIED");
            assertThat(taskRows()).isEqualTo(before);
        }
        assertThat(json(apply(null).andExpect(status().isOk()))).isEqualTo(first);
        assertThat(applicationCount()).isEqualTo(1);
    }

    @Test
    void unfiledApplicationSupportsOldRequestsAndExplicitNullButRejectsLaterFolderSelection() throws Exception {
        var first = json(apply(null).andExpect(status().isOk()));
        assertThat(first.has("folderId") && first.get("folderId").isNull()).isTrue();
        for (var task : first.get("tasks")) assertThat(task.get("folderId").isNull()).isTrue();
        assertThat(json(apply("{\"folderId\":null}").andExpect(status().isOk()))).isEqualTo(first);
        assertThat(json(apply(Map.of()).andExpect(status().isOk()))).isEqualTo(first);
        assertErrorResponse(apply(Map.of("folderId", folder)), 409, "TASK_SET_ALREADY_APPLIED");
        UUID secondSet = id(call(owner, post("/api/task-sets"), setBody("Явный null")).andExpect(status().isCreated()));
        call(owner, put(projectUrl + "/task-sets/" + secondSet), "{\"folderId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.folderId").isEmpty());
    }

    @Test
    void deletedDestinationRetainsHistoricalUuidAndRepeatsDoNotRestoreFolderOrTaskLinks() throws Exception {
        var first = json(apply(Map.of("folderId", folder)).andExpect(status().isOk()));
        String moved = first.get("tasks").get(0).get("id").asText();
        call(member, patch(projectUrl + "/tasks/" + moved), Map.of("folderId", secondFolder)).andExpect(status().isOk());
        call(member, delete(projectUrl + "/task-folders/" + folder), null).andExpect(status().isNoContent());
        call(owner, delete("/api/task-sets/" + set), null).andExpect(status().isNoContent());
        var before = taskRows();
        for (Object body : List.of(Map.of("folderId", folder), Map.of())) {
            var repeat = json(apply(body).andExpect(status().isOk()));
            assertThat(repeat.get("folderId").asText()).isEqualTo(folder.toString());
            assertThat(repeat.get("tasks").get(0).get("folderId").asText()).isEqualTo(secondFolder.toString());
            assertThat(repeat.get("tasks").get(1).get("folderId").isNull()).isTrue();
            assertThat(taskRows()).isEqualTo(before);
        }
        apply(null).andExpect(status().isOk()).andExpect(jsonPath("$.folderId").value(folder.toString()));
        assertErrorResponse(call(owner, get(projectUrl + "/task-folders/" + folder), null), 404, "TASK_FOLDER_NOT_FOUND");
        assertErrorResponse(apply("{\"folderId\":null}"), 409, "TASK_SET_ALREADY_APPLIED");
        for (var task : first.get("tasks")) call(owner, delete(projectUrl + "/tasks/" + task.get("id").asText()), null).andExpect(status().isNoContent());
        apply(Map.of("folderId", folder)).andExpect(status().isOk()).andExpect(jsonPath("$.tasks").isEmpty());
        call(owner, get(projectUrl + "/task-folders"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void invalidFolderOrTemplateNeverLeavesPartialApplicationsAndNewFolderRemainsAvailable() throws Exception {
        UUID custom = createFolder(project, "Оставить пустой", "sand");
        for (UUID unavailable : List.of(UUID.randomUUID(), foreignFolder)) {
            assertErrorResponse(apply(Map.of("folderId", unavailable)), 404, "TASK_FOLDER_NOT_FOUND");
            assertThat(applicationCount()).isZero();
            assertThat(taskRows()).isEmpty();
        }
        UUID foreignSet = id(call(member, post("/api/task-sets"), setBody("Личный шаблон участника")).andExpect(status().isCreated()));
        for (UUID unavailable : List.of(UUID.randomUUID(), foreignSet)) assertErrorResponse(call(owner, put(projectUrl + "/task-sets/" + unavailable), Map.of("folderId", custom)), 404, "TASK_SET_NOT_FOUND");
        call(owner, delete("/api/task-sets/" + set), null).andExpect(status().isNoContent());
        assertErrorResponse(apply(Map.of("folderId", custom)), 404, "TASK_SET_NOT_FOUND");
        call(member, get(projectUrl + "/task-folders/" + custom), null).andExpect(status().isOk()).andExpect(jsonPath("$.taskCount").value(0));
        assertThat(applicationCount()).isZero();
        assertThat(taskRows()).isEmpty();
    }

    @Test
    void databaseFailureDuringCopyRollsBackApplicationAndEveryTaskButKeepsPreviouslyCreatedFolder() throws Exception {
        UUID custom = createFolder(project, "Моя новая папка", "coral");
        jdbc.execute("ALTER TABLE move_tasks ADD CONSTRAINT ck_test_task_set_folder_application CHECK (title <> 'В день')");
        try {
            assertErrorResponse(apply(Map.of("folderId", custom)), 500, "INTERNAL_ERROR");
            assertThat(applicationCount()).isZero();
            assertThat(taskRows()).isEmpty();
            call(owner, get(projectUrl + "/task-folders/" + custom), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Моя новая папка")).andExpect(jsonPath("$.colorKey").value("coral")).andExpect(jsonPath("$.taskCount").value(0));
        } finally { jdbc.execute("ALTER TABLE move_tasks DROP CONSTRAINT ck_test_task_set_folder_application"); }
        apply(Map.of("folderId", custom)).andExpect(status().isOk()).andExpect(jsonPath("$.tasks.length()").value(3));
    }

    @Test
    void concurrentIdenticalRequestsCreateOneApplicationAndOneCopyOfEachTask() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<JsonNode> request = () -> { start.await(); return json(apply(Map.of("folderId", folder)).andExpect(status().isOk())); };
            var one = pool.submit(request);
            var two = pool.submit(request);
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS)).isEqualTo(two.get(15, TimeUnit.SECONDS));
            assertThat(applicationCount()).isEqualTo(1);
            assertThat(taskRows()).hasSize(3);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentDifferentDestinationsHaveOneWinnerAndOneConflict() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> { start.await(); return apply(Map.of("folderId", folder)).andReturn().getResponse(); });
            var two = pool.submit(() -> { start.await(); return apply(Map.of("folderId", secondFolder)).andReturn().getResponse(); });
            start.countDown();
            var responses = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertThat(responses.stream().map(r -> r.getStatus()).toList()).containsExactlyInAnyOrder(200, 409);
            for (var response : responses) if (response.getStatus() == 409) assertThat(jsonMapper.readTree(response.getContentAsByteArray()).get("code").asText()).isEqualTo("TASK_SET_ALREADY_APPLIED");
            var winner = json(apply(null).andExpect(status().isOk()));
            for (var task : winner.get("tasks")) assertThat(task.get("folderId")).isEqualTo(winner.get("folderId"));
            assertThat(applicationCount()).isEqualTo(1);
            assertThat(taskRows()).hasSize(3);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void singleTemplateConfirmsFolderPreservesDuplicateRuleAndRejectsUnavailableDestinations() throws Exception {
        var catalog = json(call(owner, get(projectUrl + "/tasks/templates"), null).andExpect(status().isOk())).get("content");
        String template = catalog.get(0).get("id").asText();
        String path = projectUrl + "/tasks/from-template";
        for (UUID unavailable : List.of(UUID.randomUUID(), foreignFolder)) {
            assertErrorResponse(call(owner, post(path), Map.of("templateId", template, "folderId", unavailable)), 404, "TASK_FOLDER_NOT_FOUND");
            assertThat(taskRows()).isEmpty();
        }
        UUID assignee = userRepository.findByEmail("member@example.com").orElseThrow().getId();
        var task = json(call(owner, post(path), Map.of("templateId", template, "folderId", folder, "assigneeId", assignee)).andExpect(status().isCreated()));
        assertThat(task.get("folderId").asText()).isEqualTo(folder.toString());
        assertThat(task.get("assigneeId").asText()).isEqualTo(assignee.toString());
        assertThat(task.get("dueType").asText()).isEqualTo("RELATIVE");
        assertErrorResponse(call(owner, post(path), Map.of("templateId", template, "folderId", secondFolder)), 409, "DATA_CONFLICT");
        call(owner, post(path), Map.of("templateId", catalog.get(1).get("id").asText())).andExpect(status().isCreated()).andExpect(jsonPath("$.folderId").isEmpty());
        call(owner, post(path), "{\"templateId\":\"" + catalog.get(2).get("id").asText() + "\",\"folderId\":null}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.folderId").isEmpty());
        assertThat(taskRows()).hasSize(3);
    }

    @Test
    void membersApplyOwnTemplatesWhileArchiveBlocksEvenRetriesAndMalformedBodiesNeverGetIgnored() throws Exception {
        apply(Map.of("folderId", folder)).andExpect(status().isOk());
        assertErrorResponse(call(member, put(applicationUrl), Map.of("folderId", folder)), 404, "TASK_SET_NOT_FOUND");
        UUID own = id(call(member, post("/api/task-sets"), setBody("Участник")).andExpect(status().isCreated()));
        call(member, put(projectUrl + "/task-sets/" + own), Map.of("folderId", folder)).andExpect(status().isOk());
        assertErrorResponse(apply(Map.of("folderId", "bad-uuid")), 400, "INVALID_REQUEST_BODY");
        assertErrorResponse(apply(Map.of("unknownFolder", folder)), 400, "INVALID_REQUEST_BODY");
        call(null, put(applicationUrl), Map.of("folderId", folder)).andExpect(status().isUnauthorized());
        var before = taskRows();
        call(member, put(projectUrl + "/archive"), null).andExpect(status().isOk());
        assertErrorResponse(apply(Map.of("folderId", folder)), 409, "DATA_CONFLICT");
        assertErrorResponse(apply(null), 409, "DATA_CONFLICT");
        assertErrorResponse(call(member, put(projectUrl + "/task-sets/" + own), Map.of("folderId", folder)), 409, "DATA_CONFLICT");
        assertThat(taskRows()).isEqualTo(before);
    }

    @Test
    void swaggerDescribesOptionalBodyHistoricalDestinationAndConflictAndSingleTemplateFolder() throws Exception {
        var api = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        var apply = api.get("paths").get("/api/move-projects/{projectId}/task-sets/{setId}").get("put");
        assertThat(apply.get("requestBody").path("required").asBoolean()).isFalse();
        assertThat(apply.get("responses").get("409").get("description").asText()).contains("TASK_SET_ALREADY_APPLIED");
        assertThat(apply.get("responses").get("404").get("description").asText()).contains("TASK_FOLDER_NOT_FOUND");
        var schemas = api.get("components").get("schemas");
        for (String type : List.of("TaskSetApplicationResponse", "ApplyTaskSetRequest", "ApplyTaskTemplateRequest")) {
            var property = schemas.get(type).get("properties").get("folderId");
            assertThat(property.get("format").asText()).isEqualTo("uuid");
            assertThat(property.get("type").toString()).contains("null");
        }
        assertThat(schemas.get("ApplyTaskSetRequest").get("properties").propertyNames()).containsExactly("folderId");
        assertThat(schemas.get("TaskSetApplicationResponse").get("properties").get("folderId").get("description").asText()).contains("первоначальной", "удаления");
    }

    private Object setBody(String name) { return Map.of("name", name, "items", List.of(item("До", "BEFORE_MOVE", -2), item("В день", "MOVING_DAY", 0), item("После", "AFTER_MOVE", 3))); }
    private Map<String, Object> item(String title, String stage, int offset) { return Map.of("title", title, "description", "Описание", "stage", stage, "offsetDays", offset); }
    private UUID createFolder(UUID project, String name, String color) throws Exception { return id(call(owner, post("/api/move-projects/" + project + "/task-folders"), Map.of("id", UUID.randomUUID(), "name", name, "colorKey", color)).andExpect(status().isCreated())); }
    private long applicationCount() { return jdbc.queryForObject("SELECT count(*) FROM task_set_applications WHERE project_id = ?", Long.class, project); }
    private List<Map<String, Object>> taskRows() { return jdbc.queryForList("SELECT * FROM move_tasks WHERE project_id = ? ORDER BY id", project); }
    private ResultActions apply(Object body) throws Exception { return call(owner, put(applicationUrl), body); }
    private UUID id(ResultActions result) throws Exception { return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
