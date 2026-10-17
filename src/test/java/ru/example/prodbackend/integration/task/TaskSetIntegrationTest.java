package ru.example.prodbackend.integration.task;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.taskset.repository.TaskSetRepository;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskSetIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TaskSetRepository sets;
    @Autowired private PlatformTransactionManager transactionManager;
    private String token;
    private String member;
    private UUID ownerId;
    private UUID setId;
    private UUID projectId;
    private String url;

    @BeforeEach
    void prepare() throws Exception {
        token = registerUser().getAccessToken();
        member = registerUser("family@example.com").getAccessToken();
        ownerId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        setId = id(call(token, post("/api/task-sets"), setBody("  Переезд с котом  ")).andExpect(status().isCreated()));
        projectId = id(call(token, post("/api/move-projects"), projectBody(List.of())).andExpect(status().isCreated()));
        url = "/api/move-projects/" + projectId;
    }

    @Test
    void crudPersistsOrderAndFullReplacementAndSoftDeletion() throws Exception {
        var first = json(call(token, get("/api/task-sets/" + setId), null).andExpect(status().isOk()));
        assertThat(first.get("name").asText()).isEqualTo("Переезд с котом");
        assertThat(first.get("items").size()).isEqualTo(3);
        assertThat(first.get("items").get(0).get("title").asText()).isEqualTo("Подготовить переноску");
        assertThat(first.get("items").get(0).get("position").asInt()).isZero();
        assertThat(first.get("items").get(1).get("stage").asText()).isEqualTo("MOVING_DAY");
        assertThat(first.get("items").get(2).get("offsetDays").asInt()).isEqualTo(3);
        var replacement = Map.of("name", "Новый набор", "items", List.of(item("После", "AFTER_MOVE", 4), item("До", "BEFORE_MOVE", -5)));
        var changed = json(call(token, put("/api/task-sets/" + setId), replacement).andExpect(status().isOk()));
        assertThat(changed.get("items").size()).isEqualTo(2);
        assertThat(changed.get("items").get(0).get("title").asText()).isEqualTo("После");
        assertThat(changed.get("description").isNull()).isTrue();
        assertThat(changed.get("createdAt")).isEqualTo(first.get("createdAt"));
        assertThat(json(call(token, get("/api/task-sets/" + setId), null).andExpect(status().isOk()))).isEqualTo(changed);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_set_items WHERE task_set_id = ?", Long.class, setId)).isEqualTo(2);
        call(token, delete("/api/task-sets/" + setId), null).andExpect(status().isNoContent());
        assertErrorResponse(call(token, get("/api/task-sets/" + setId), null), 404, "TASK_SET_NOT_FOUND");
        call(token, get("/api/task-sets"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(sets.findById(setId).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void paginationContainsOnlyOwnActiveSetsWithCorrectTotals() throws Exception {
        var second = id(call(token, post("/api/task-sets"), setBody("Второй")).andExpect(status().isCreated()));
        var deleted = id(call(token, post("/api/task-sets"), setBody("Удалённый")).andExpect(status().isCreated()));
        call(member, post("/api/task-sets"), setBody("Чужой")).andExpect(status().isCreated());
        call(token, delete("/api/task-sets/" + deleted), null).andExpect(status().isNoContent());
        call(token, get("/api/task-sets?size=1&page=0"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(second.toString())).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        call(token, get("/api/task-sets?size=1&page=1"), null).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(setId.toString()));
        call(token, get("/api/task-sets?page=100"), null).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        call(token, get("/api/task-sets?size=0"), null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void onlySetOwnerCanReadEditDeleteOrApplyAndAllEndpointsRequireAuth() throws Exception {
        for (var method : List.of("GET", "PUT", "DELETE")) {
            var request = org.springframework.http.HttpMethod.valueOf(method);
            assertErrorResponse(call(member, request(request, "/api/task-sets/" + setId), method.equals("PUT") ? setBody("Чужой") : null), 404, "TASK_SET_NOT_FOUND");
            call(null, request(request, "/api/task-sets/" + setId), method.equals("PUT") ? setBody("Чужой") : null).andExpect(status().isUnauthorized());
        }
        call(null, post("/api/task-sets"), setBody("Без токена")).andExpect(status().isUnauthorized());
        call(null, get("/api/task-sets"), null).andExpect(status().isUnauthorized());
        call(null, put(url + "/task-sets/" + setId), null).andExpect(status().isUnauthorized());
        assertErrorResponse(call(member, put(url + "/task-sets/" + setId), null), 404, "TASK_SET_NOT_FOUND");
        String stranger = registerUser("stranger@example.com").getAccessToken();
        assertErrorResponse(call(stranger, put(url + "/task-sets/" + setId), null), 404, "MOVE_PROJECT_NOT_FOUND");
    }

    @Test
    void appliesIndependentRelativeCopiesInOrderAndRepeatsWithoutChanges() throws Exception {
        var first = json(apply().andExpect(status().isOk()));
        assertThat(first.get("tasks").size()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            var task = first.get("tasks").get(i);
            int offset = new int[]{-2, 0, 3}[i];
            assertThat(task.get("dueType").asText()).isEqualTo("RELATIVE");
            assertThat(task.get("dueDate").asText()).isEqualTo(LocalDate.parse("2026-11-09").plusDays(offset).toString());
            assertThat(task.get("offsetDays").asInt()).isEqualTo(offset);
            assertThat(task.get("completed").asBoolean()).isFalse();
            assertThat(task.get("assigneeName").isNull()).isTrue();
            assertThat(task.get("templateId").isNull()).isTrue();
            assertThat(task.get("taskSetApplicationId")).isEqualTo(first.get("id"));
        }
        assertThat(json(apply().andExpect(status().isOk()))).isEqualTo(first);
        assertThat(count("task_set_applications")).isEqualTo(1);
        assertThat(count("move_tasks")).isEqualTo(3);
        UUID secondProject = id(call(token, post("/api/move-projects"), projectBody(List.of())).andExpect(status().isCreated()));
        var second = json(call(token, put("/api/move-projects/" + secondProject + "/task-sets/" + setId), null).andExpect(status().isOk()));
        assertThat(second.get("id")).isNotEqualTo(first.get("id"));
        assertThat(count("move_tasks")).isEqualTo(6);
    }

    @Test
    void editingSetAndDeletingCopiedTaskNeverRecreatesOrRewritesCopies() throws Exception {
        var first = json(apply().andExpect(status().isOk()));
        String retainedId = first.get("tasks").get(1).get("id").asText();
        call(token, patch(url + "/tasks/" + retainedId), Map.of("title", "Моя правка", "assigneeName", "Анна", "dueDate", "2026-12-01")).andExpect(status().isOk());
        call(token, patch(url + "/tasks/" + retainedId + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        call(token, delete(url + "/tasks/" + first.get("tasks").get(0).get("id").asText()), null).andExpect(status().isNoContent());
        var before = json(call(token, get(url + "/tasks/" + retainedId), null).andExpect(status().isOk()));
        call(token, put("/api/task-sets/" + setId), Map.of("name", "Переписан", "items", List.of(item("Замена", "AFTER_MOVE", 99)))).andExpect(status().isOk());
        var repeat = json(apply().andExpect(status().isOk()));
        assertThat(repeat.get("id")).isEqualTo(first.get("id"));
        assertThat(repeat.get("appliedAt")).isEqualTo(first.get("appliedAt"));
        assertThat(repeat.get("taskSetName").asText()).isEqualTo("Переезд с котом");
        assertThat(repeat.get("tasks").size()).isEqualTo(2);
        assertThat(repeat.get("tasks").get(0)).isEqualTo(before);
        assertThat(count("move_tasks")).isEqualTo(2);
    }

    @Test
    void deletedSetKeepsApplicationAndTasksAndForbidsNewApplication() throws Exception {
        var first = json(apply().andExpect(status().isOk()));
        call(token, delete("/api/task-sets/" + setId), null).andExpect(status().isNoContent());
        assertThat(json(apply().andExpect(status().isOk()))).isEqualTo(first);
        var other = id(call(token, post("/api/move-projects"), projectBody(List.of())).andExpect(status().isCreated()));
        assertErrorResponse(call(token, put("/api/move-projects/" + other + "/task-sets/" + setId), null), 404, "TASK_SET_NOT_FOUND");
        for (var task : first.get("tasks")) call(token, delete(url + "/tasks/" + task.get("id").asText()), null).andExpect(status().isNoContent());
        apply().andExpect(status().isOk()).andExpect(jsonPath("$.tasks").isEmpty());
        assertThat(count("task_set_applications")).isEqualTo(1);
    }

    @Test
    void participantAppliesOwnSetWithoutSharingPrivateCatalogAndArchiveBlocksApply() throws Exception {
        UUID memberSet = id(call(member, post("/api/task-sets"), setBody("Набор участника")).andExpect(status().isCreated()));
        var path = url + "/task-sets/" + memberSet;
        call(member, put(path), null).andExpect(status().isOk());
        call(token, get(url + "/tasks"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        assertErrorResponse(call(token, get("/api/task-sets/" + memberSet), null), 404, "TASK_SET_NOT_FOUND");
        call(member, put(url + "/archive"), null).andExpect(status().isOk());
        assertErrorResponse(call(member, put(path), null), 409, "DATA_CONFLICT");
        assertErrorResponse(apply(), 409, "DATA_CONFLICT");
        call(member, put("/api/task-sets/" + memberSet), setBody("Каталог работает в архиве")).andExpect(status().isOk());
        call(member, delete(url + "/archive"), null).andExpect(status().isOk());
        call(member, put(path), null).andExpect(status().isOk());
    }

    @Test
    void projectCreationWithoutChoiceHasNoTasksAndSelectedSetsAreAtomicAndDeduplicated() throws Exception {
        assertThat(count("move_tasks")).isZero();
        assertThat(count("first_day_checklist_items")).isEqualTo(4);
        var secondSet = id(call(token, post("/api/task-sets"), setBody("Другой набор")).andExpect(status().isCreated()));
        UUID created = id(call(token, post("/api/move-projects"), projectBody(List.of(setId, secondSet, setId))).andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks WHERE project_id = ?", Long.class, created)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_set_applications WHERE project_id = ?", Long.class, created)).isEqualTo(2);
        long projects = count("move_projects"), tasks = count("move_tasks"), apps = count("task_set_applications"), points = count("first_day_checklist_items"), participants = count("project_participants"), events = count("project_events");
        UUID foreign = id(call(member, post("/api/task-sets"), setBody("Чужой")).andExpect(status().isCreated()));
        for (UUID unavailable : List.of(foreign, UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))) {
            assertErrorResponse(call(token, post("/api/move-projects"), projectBody(List.of(setId, unavailable))), 404, "TASK_SET_NOT_FOUND");
            assertThat(count("move_projects")).isEqualTo(projects);
            assertThat(count("move_tasks")).isEqualTo(tasks);
            assertThat(count("task_set_applications")).isEqualTo(apps);
            assertThat(count("first_day_checklist_items")).isEqualTo(points);
            assertThat(count("project_participants")).isEqualTo(participants);
            assertThat(count("project_events")).isEqualTo(events);
        }
        call(token, post("/api/move-projects"), "{\"name\":\"Без выбора\",\"moveDate\":\"2026-11-01T09:00:00Z\"}")
                .andExpect(status().isCreated());
        assertThat(count("move_tasks")).isEqualTo(tasks);
    }

    @Test
    void validationOfSetAndSelectionDoesNotPartiallyReplaceData() throws Exception {
        var first = json(call(token, get("/api/task-sets/" + setId), null).andExpect(status().isOk()));
        for (Object body : List.of(Map.of("name", " ", "items", List.of(item("Task", "MOVING_DAY", 0))),
                Map.of("name", "Set", "items", List.of()), Map.of("name", "Set", "items", Collections.nCopies(101, item("Task", "MOVING_DAY", 0))),
                Map.of("name", "x".repeat(256), "items", List.of(item("Task", "MOVING_DAY", 0))),
                Map.of("name", "Set", "items", List.of(Map.of("title", "Без смещения", "stage", "MOVING_DAY"))),
                Map.of("name", "Set", "items", List.of(item(" ", "BEFORE_MOVE", 0))),
                "{\"name\":\"Set\",\"items\":[null]}")) {
            call(token, post("/api/task-sets"), body).andExpect(status().isUnprocessableEntity());
            call(token, put("/api/task-sets/" + setId), body).andExpect(status().isUnprocessableEntity());
        }
        assertThat(json(call(token, get("/api/task-sets/" + setId), null).andExpect(status().isOk()))).isEqualTo(first);
        call(token, post("/api/task-sets"), Map.of("name", "Set", "items", List.of(item("Task", "UNKNOWN", 0)))).andExpect(status().isBadRequest());
        call(token, post("/api/move-projects"), "{\"name\":\"Set\",\"moveDate\":\"2026-11-01T09:00:00Z\",\"taskSetIds\":[null]}").andExpect(status().isUnprocessableEntity());
        call(token, post("/api/move-projects"), "{\"name\":\"Set\",\"moveDate\":\"2026-11-01T09:00:00Z\",\"taskSetIds\":null}").andExpect(status().isBadRequest());
    }

    @Test
    void concurrentApplicationCreatesOnlyOneApplicationAndOneCopyOfEachTask() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> { start.await(); return json(apply().andExpect(status().isOk())); });
            var two = pool.submit(() -> { start.await(); return json(apply().andExpect(status().isOk())); });
            start.countDown();
            assertThat(two.get(15, TimeUnit.SECONDS)).isEqualTo(one.get(15, TimeUnit.SECONDS));
            assertThat(count("task_set_applications")).isEqualTo(1);
            assertThat(count("move_tasks")).isEqualTo(3);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void applicationWaitsForSetEditAndCopiesWholeCommittedComposition() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var editing = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                var set = sets.lockOwned(setId, ownerId).orElseThrow();
                set.setName("После изменения");
                set.getItems().forEach(i -> { i.setTitle("Обновлено " + i.getPosition()); i.setOffsetDays(4); });
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var applying = pool.submit(() -> json(apply().andExpect(status().isOk())));
            assertThatThrownBy(() -> applying.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            editing.get(10, TimeUnit.SECONDS);
            var result = applying.get(10, TimeUnit.SECONDS);
            assertThat(result.get("taskSetName").asText()).isEqualTo("После изменения");
            for (var task : result.get("tasks")) {
                assertThat(task.get("title").asText()).startsWith("Обновлено");
                assertThat(task.get("offsetDays").asInt()).isEqualTo(4);
            }
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void deletingProjectRemovesApplicationsAndCopiesButPreservesPrivateSet() throws Exception {
        apply().andExpect(status().isOk());
        call(member, delete(url), null).andExpect(status().isNoContent());
        assertThat(count("task_set_applications")).isZero();
        assertThat(count("move_tasks")).isZero();
        call(token, get("/api/task-sets/" + setId), null).andExpect(status().isOk());
    }

    @Test
    void copiedTasksSupportExplicitRescheduleAndSwaggerShowsNewApi() throws Exception {
        var copied = json(apply().andExpect(status().isOk()));
        UUID task = UUID.fromString(copied.get("tasks").get(0).get("id").asText());
        call(member, post(url + "/reschedule"), Map.of("moveDate", "2026-12-01T09:00:00Z", "taskIds", List.of(task))).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].dueDate").value("2026-11-29"));
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/task-sets'].post").exists())
                .andExpect(jsonPath("$.paths['/api/task-sets/{setId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/move-projects/{projectId}/task-sets/{setId}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateMoveProjectRequest.properties.taskSetIds").exists());
    }

    private Map<String, Object> projectBody(List<UUID> ids) { return Map.of("name", "Переезд", "moveDate", "2026-11-10T00:30:00+03:00", "participants", List.of(Map.of("email", "family@example.com")), "taskSetIds", ids); }
    private Object setBody(String name) { return Map.of("name", name, "description", "  С питомцем  ", "items", List.of(item("  Подготовить переноску  ", "BEFORE_MOVE", -2), item("Поставить миски", "MOVING_DAY", 0), item("Освоиться", "AFTER_MOVE", 3))); }
    private Map<String, Object> item(String title, String stage, int offset) { return Map.of("title", title, "stage", stage, "offsetDays", offset); }
    private ResultActions apply() throws Exception { return call(token, put(url + "/task-sets/" + setId), null); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private UUID id(ResultActions result) throws Exception { return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    private ResultActions call(String accessToken, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (accessToken != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
