package ru.example.prodbackend.integration.move;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.example.prodbackend.support.AuthIntegrationTestSupport;
import tools.jackson.databind.JsonNode;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MoveProjectPlanIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    private String owner;
    private String member;
    private UUID set;
    private UUID secondSet;
    private UUID foreignSet;
    private UUID occupiedFolder;

    @BeforeEach
    void prepare() throws Exception {
        owner = registerUser().getAccessToken();
        member = registerUser("member@example.com").getAccessToken();
        set = createSet(owner, "Первый шаблон");
        secondSet = createSet(owner, "Второй шаблон");
        foreignSet = createSet(member, "Личный шаблон участника");
        UUID existing = id(create(base("Существующий переезд")).andExpect(status().isCreated()));
        occupiedFolder = UUID.randomUUID();
        call(owner, post(url(existing) + "/task-folders"), folder(occupiedFolder, "Занятая", "mint")).andExpect(status().isCreated());
    }

    @Test
    void createsCompletePlanVisibleToAnotherParticipantImmediatelyAndPreservesAddressesAndFolderMetadata() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), empty = UUID.randomUUID();
        var request = base("  Новый дом  ");
        request.put("originAddress", " Москва, старый адрес ");
        request.put("destinationAddress", " Москва, новый адрес ");
        request.put("participants", List.of(Map.of("email", "member@example.com"), Map.of("userId", userRepository.findByEmail("member@example.com").orElseThrow().getId())));
        request.put("folders", List.of(folder(first, "  Кухня  ", "mint"), folder(second, "Документы", "lilac"), folder(empty, "Пустая", "sand")));
        request.put("taskSetApplications", List.of(application(set, first), application(secondSet, second)));
        var response = json(create(request).andExpect(status().isCreated()));
        UUID project = UUID.fromString(response.get("id").asText());
        assertThat(response.get("name").asText()).isEqualTo("Новый дом");
        assertThat(response.get("originAddress").asText()).isEqualTo("Москва, старый адрес");
        assertThat(response.get("destinationAddress").asText()).isEqualTo("Москва, новый адрес");
        call(member, get(url(project)), null).andExpect(status().isOk());
        call(member, get(url(project) + "/participants"), null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        var folders = json(call(member, get(url(project) + "/task-folders"), null).andExpect(status().isOk()));
        assertThat(folders.get("totalElements").asLong()).isEqualTo(3);
        for (var item : folders.get("content")) {
            boolean isEmpty = item.get("id").asText().equals(empty.toString());
            assertThat(item.get("taskCount").asLong()).isEqualTo(isEmpty ? 0 : 2);
            assertThat(item.get("completedTaskCount").asLong()).isZero();
        }
        call(member, get(url(project) + "/task-folders/" + first), null).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Кухня")).andExpect(jsonPath("$.colorKey").value("mint"));
        call(member, get(url(project) + "/task-folders/" + second), null).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Документы")).andExpect(jsonPath("$.colorKey").value("lilac"));
        var tasks = json(call(member, get(url(project) + "/tasks"), null).andExpect(status().isOk()));
        assertThat(tasks.get("totalElements").asLong()).isEqualTo(4);
        for (var task : tasks.get("content")) {
            assertThat(task.get("folderId").asText()).isIn(first.toString(), second.toString());
            assertThat(task.get("dueType").asText()).isEqualTo("RELATIVE");
            assertThat(task.get("taskSetApplicationId").isNull()).isFalse();
        }
        call(owner, put(url(project) + "/task-sets/" + set), null).andExpect(status().isOk()).andExpect(jsonPath("$.folderId").value(first.toString()));
        call(owner, put(url(project) + "/task-sets/" + secondSet), null).andExpect(status().isOk()).andExpect(jsonPath("$.folderId").value(second.toString()));
        call(member, get("/api/projects/" + project + "/progress/tasks"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalTasks").value(4));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items", Long.class)).isZero();
    }

    @Test
    void absentOrEmptySelectionsCreateCleanProjectsAndReadsNeverAddHiddenData() throws Exception {
        for (boolean explicit : List.of(false, true)) {
            var request = base("Пустой переезд");
            if (explicit) {
                request.put("folders", List.of());
                request.put("taskSetApplications", List.of());
                request.put("includeFirstDayChecklist", false);
            }
            UUID project = id(create(request).andExpect(status().isCreated()));
            for (int repeat = 0; repeat < 2; repeat++) {
                call(owner, get(url(project) + "/tasks"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
                call(owner, get(url(project) + "/task-folders"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
                call(owner, get(url(project) + "/first-day-checklist"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project_events WHERE project_id = ?", Long.class, project)).isEqualTo(1);
        }
    }

    @Test
    void emptyFoldersAndUnfiledApplicationsAreSupportedWithoutInferringNamesFromTemplates() throws Exception {
        UUID empty = UUID.randomUUID();
        var request = base("Папка и задачи без папки");
        request.put("folders", List.of(folder(empty, "Моё название", "forest")));
        request.put("taskSetApplications", List.of(Map.of("taskSetId", set)));
        UUID project = id(create(request).andExpect(status().isCreated()));
        call(owner, get(url(project) + "/task-folders/" + empty), null).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Моё название")).andExpect(jsonPath("$.taskCount").value(0));
        for (var task : json(call(owner, get(url(project) + "/tasks"), null).andExpect(status().isOk())).get("content")) assertThat(task.get("folderId").isNull()).isTrue();
        call(owner, put(url(project) + "/task-sets/" + set), "{\"folderId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.folderId").isEmpty());
    }

    @Test
    void oldSelectionDeduplicatesWithoutFolderAndWorksAlongsideEmptyNewSelection() throws Exception {
        var request = base("Старый клиент");
        request.put("taskSetIds", List.of(set, set));
        request.put("taskSetApplications", List.of());
        UUID project = id(create(request).andExpect(status().isCreated()));
        call(owner, get(url(project) + "/tasks"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        call(owner, get(url(project) + "/task-folders"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_set_applications WHERE project_id = ?", Long.class, project)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items", Long.class)).isZero();
    }

    @Test
    void duplicateTemplateOrMixedSelectionReturns422AndLeavesNoProjectOrPlan() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var request = base("Не сохранить");
        request.put("folders", List.of(folder(first, "Первая", "mint"), folder(second, "Вторая", "cyan")));
        request.put("taskSetApplications", List.of(application(set, first), application(set, second)));
        var before = state();
        fieldError(create(request), "taskSetApplications[1].taskSetId");
        assertThat(state()).isEqualTo(before);
        request.put("taskSetApplications", List.of(application(set, first)));
        request.put("taskSetIds", List.of(secondSet));
        fieldError(create(request), "taskSetApplications");
        assertThat(state()).isEqualTo(before);
    }

    @Test
    void duplicateFolderAndReferencesOutsideRequestReturn422WithoutAnyWrites() throws Exception {
        UUID first = UUID.randomUUID();
        var request = base("Не сохранить");
        request.put("folders", List.of(folder(first, "Первая", "mint"), folder(first, "Вторая", "cyan")));
        var before = state();
        fieldError(create(request), "folders[1].id");
        assertThat(state()).isEqualTo(before);
        request.put("folders", List.of(folder(first, "Первая", "mint")));
        for (UUID target : List.of(occupiedFolder, UUID.randomUUID())) {
            request.put("taskSetApplications", List.of(application(set, target)));
            fieldError(create(request), "taskSetApplications[0].folderId");
            assertThat(state()).isEqualTo(before);
        }
    }

    @Test
    void invalidSecondFolderNameOrColorRollsBackAlreadyInsertedProjectParticipantsAndFirstFolder() throws Exception {
        var before = state();
        for (String field : List.of("name", "colorKey")) {
            for (String invalid : field.equals("name") ? List.of("", "  ", "x".repeat(81)) : List.of("", "#aabbcc", "blue")) {
                var request = base("Не сохранить");
                request.put("participants", List.of(Map.of("email", "member@example.com")));
                var second = new HashMap<String, Object>(folder(UUID.randomUUID(), "Название", "mint"));
                second.put(field, invalid);
                request.put("folders", List.of(folder(UUID.randomUUID(), "Первая", "mint"), second));
                fieldError(create(request), "folders[1]." + field);
                assertThat(state()).isEqualTo(before);
            }
        }
    }

    @Test
    void unavailableTemplateRollsBackFoldersAndAnyEarlierSuccessfulApplications() throws Exception {
        var before = state();
        for (UUID unavailable : List.of(UUID.fromString("7fffffff-ffff-ffff-7fff-ffffffffffff"), foreignSet)) {
            UUID folder = UUID.randomUUID();
            var request = base("Не сохранить");
            request.put("participants", List.of(Map.of("email", "member@example.com")));
            request.put("folders", List.of(folder(folder, "Папка", "mint")));
            request.put("taskSetApplications", List.of(application(set, folder), application(unavailable, folder)));
            assertErrorResponse(create(request), 404, "TASK_SET_NOT_FOUND");
            assertThat(state()).isEqualTo(before);
        }
    }

    @Test
    void occupiedFolderUuidDoesNotReuseOrEditAnotherProjectsFolderAndRollsBackNewProject() throws Exception {
        var request = base("Не сохранить");
        request.put("folders", List.of(folder(UUID.randomUUID(), "Первая", "mint"), folder(occupiedFolder, "Занятая", "mint")));
        var before = state();
        assertErrorResponse(create(request), 409, "DATA_CONFLICT");
        assertThat(state()).isEqualTo(before);
    }

    @Test
    void arrayLimitsNullsAndRequiredNestedFieldsRejectInvalidRequestsBeforeCreatingAnything() throws Exception {
        var before = state();
        for (String field : List.of("folders", "taskSetApplications")) {
            var request = base("Не сохранить");
            Object element = field.equals("folders") ? folder(UUID.randomUUID(), "Папка", "mint") : Map.of("taskSetId", set);
            request.put(field, Collections.nCopies(101, element));
            fieldError(create(request), field);
            request.put(field, Collections.singletonList(null));
            create(request).andExpect(status().isUnprocessableContent());
            request.put(field, null);
            assertErrorResponse(create(request), 400, "INVALID_REQUEST_BODY");
            assertThat(state()).isEqualTo(before);
        }
        for (Object invalid : List.of(Map.of("folders", List.of(Map.of("name", "Без UUID", "colorKey", "mint"))),
                Map.of("taskSetApplications", List.of(Map.of("folderId", occupiedFolder))))) {
            var request = base("Не сохранить");
            request.putAll((Map<String, Object>) invalid);
            create(request).andExpect(status().isUnprocessableContent());
        }
        assertThat(state()).isEqualTo(before);
    }

    @Test
    void acceptsOneHundredFoldersAndDistinctApplicationsAndPersistsEntirePlan() throws Exception {
        var folders = new ArrayList<Map<String, Object>>();
        var applications = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 100; i++) {
            UUID folder = UUID.randomUUID();
            UUID template = id(call(owner, post("/api/task-sets"), Map.of("name", "Шаблон " + i,
                    "items", List.of(Map.of("title", "Задача " + i, "stage", "BEFORE_MOVE", "offsetDays", 0)))).andExpect(status().isCreated()));
            folders.add(folder(folder, "Папка " + i, "mint"));
            applications.add(application(template, folder));
        }
        var request = base("Большой план");
        request.put("folders", folders);
        request.put("taskSetApplications", applications);
        UUID project = id(create(request).andExpect(status().isCreated()));
        call(owner, get(url(project) + "/tasks").param("size", "100"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(100)).andExpect(jsonPath("$.content.length()").value(100));
        call(owner, get(url(project) + "/task-folders").param("size", "100"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(100)).andExpect(jsonPath("$.content.length()").value(100));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_set_applications WHERE project_id = ?", Long.class, project)).isEqualTo(100);
    }

    @Test
    void taskWriteFailureRollsBackNativeFolderInsertsAndEntirePlanAndAllowsCleanRetry() throws Exception {
        UUID folder = UUID.randomUUID();
        var request = base("Сбой копирования");
        request.put("participants", List.of(Map.of("email", "member@example.com")));
        request.put("folders", List.of(folder(folder, "Папка", "mint")));
        request.put("taskSetApplications", List.of(application(set, folder)));
        var before = state();
        jdbc.execute("ALTER TABLE move_tasks ADD CONSTRAINT ck_test_project_plan_tasks CHECK (title <> 'Завершить')");
        try {
            assertErrorResponse(create(request), 500, "INTERNAL_ERROR");
            assertThat(state()).isEqualTo(before);
        } finally { jdbc.execute("ALTER TABLE move_tasks DROP CONSTRAINT ck_test_project_plan_tasks"); }
        UUID project = id(create(request).andExpect(status().isCreated()));
        call(member, get(url(project) + "/tasks"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void finalEventFailureRollsBackSelectedPlanAndDeprecatedCompatibilityTasksTogether() throws Exception {
        UUID folder = UUID.randomUUID();
        var request = base("Сбой события");
        request.put("includeFirstDayChecklist", true);
        request.put("participants", List.of(Map.of("email", "member@example.com")));
        request.put("folders", List.of(folder(folder, "Папка", "mint")));
        request.put("taskSetApplications", List.of(application(set, folder)));
        var before = state();
        jdbc.execute("ALTER TABLE project_events ADD CONSTRAINT ck_test_project_plan_event CHECK (description <> 'Создан проект переезда «Сбой события»')");
        try {
            assertErrorResponse(create(request), 500, "INTERNAL_ERROR");
            assertThat(state()).isEqualTo(before);
        } finally { jdbc.execute("ALTER TABLE project_events DROP CONSTRAINT ck_test_project_plan_event"); }
    }

    @Test
    void explicitTrueCreatesOnlyOrdinaryUnscheduledTasksInAnEditableDeletableFolder() throws Exception {
        var request = base("Старый флаг");
        request.put("includeFirstDayChecklist", true);
        UUID project = id(create(request).andExpect(status().isCreated()));
        var folder = json(call(owner, get(url(project) + "/task-folders"), null).andExpect(status().isOk())).get("content").get(0);
        assertThat(folder.get("name").asText()).isEqualTo("Личные задачи");
        assertThat(folder.get("colorKey").asText()).isEqualTo("mint");
        assertThat(folder.get("taskCount").asLong()).isEqualTo(4);
        var tasks = json(call(owner, get(url(project) + "/tasks"), null).andExpect(status().isOk()));
        var titles = new HashSet<String>();
        for (var task : tasks.get("content")) {
            titles.add(task.get("title").asText());
            assertThat(task.get("folderId")).isEqualTo(folder.get("id"));
            assertThat(task.get("stage").asText()).isEqualTo("BEFORE_MOVE");
            assertThat(task.get("dueType").asText()).isEqualTo("FIXED");
            assertThat(task.get("completed").asBoolean()).isFalse();
            for (String field : List.of("dueDate", "offsetDays", "assigneeId", "assigneeName", "description", "templateId", "taskSetApplicationId", "linkedItemId", "completedAt")) assertThat(task.get(field).isNull()).as(field).isTrue();
        }
        assertThat(titles).containsExactlyInAnyOrder("Зубные щётки", "Зарядки", "Чайник", "Постельное бельё");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items", Long.class)).isZero();
        String path = url(project) + "/task-folders/" + folder.get("id").asText();
        call(owner, patch(path), Map.of("name", "Мои задачи", "colorKey", "coral")).andExpect(status().isOk());
        call(owner, delete(path), null).andExpect(status().isNoContent());
        call(owner, get(url(project) + "/tasks"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void swaggerDescribesNewSelectionsFalseDefaultAndDeprecatedCompatibilityFields() throws Exception {
        var api = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        var properties = api.get("components").get("schemas").get("CreateMoveProjectRequest").get("properties");
        assertThat(properties.get("folders").get("maxItems").asInt()).isEqualTo(100);
        assertThat(properties.get("taskSetApplications").get("maxItems").asInt()).isEqualTo(100);
        assertThat(properties.get("includeFirstDayChecklist").get("default").asBoolean()).isFalse();
        assertThat(properties.get("includeFirstDayChecklist").get("deprecated").asBoolean()).isTrue();
        assertThat(properties.get("taskSetIds").get("deprecated").asBoolean()).isTrue();
        assertThat(api.get("paths").get("/api/move-projects").get("post").get("responses").propertyNames()).contains("201", "404", "409", "422");
    }

    private Map<String, Object> base(String name) { return new HashMap<>(Map.of("name", name, "moveDate", "2026-11-01T09:00:00Z")); }
    private Map<String, Object> folder(UUID id, String name, String color) { return Map.of("id", id, "name", name, "colorKey", color); }
    private Map<String, Object> application(UUID template, UUID folder) { return Map.of("taskSetId", template, "folderId", folder); }
    private UUID createSet(String token, String name) throws Exception { return id(call(token, post("/api/task-sets"), Map.of("name", name, "items", List.of(
            Map.of("title", "Подготовиться", "stage", "BEFORE_MOVE", "offsetDays", -2), Map.of("title", "Завершить", "stage", "AFTER_MOVE", "offsetDays", 1)))).andExpect(status().isCreated())); }
    private Map<String, Object> state() {
        var result = new LinkedHashMap<String, Object>();
        for (String table : List.of("move_projects", "project_participants", "task_folders", "move_tasks", "task_set_applications", "first_day_checklist_items", "project_events")) result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        return result;
    }
    private String url(UUID project) { return "/api/move-projects/" + project; }
    private void fieldError(ResultActions result, String field) throws Exception { assertErrorResponse(result, 422, "VALIDATION_ERROR").andExpect(jsonPath("$.fieldErrors[0].field").value(field)); }
    private ResultActions create(Object body) throws Exception { return call(owner, post("/api/move-projects"), body); }
    private UUID id(ResultActions result) throws Exception { return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
